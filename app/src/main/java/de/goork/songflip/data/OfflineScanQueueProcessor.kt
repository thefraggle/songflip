package de.goork.songflip.data

import de.goork.songflip.core.cache.PendingAcousticScan
import kotlinx.coroutines.CancellationException

/**
 * Result of matching a signature against the Shazam catalog (#143).
 *
 * Separating a definitive "no match" from transient failures is what protects queued
 * Offline Club Mode scans from being deleted when the network/token endpoint is flaky.
 */
sealed class OfflineMatchOutcome {
    data class Matched(val match: DirectShazamMatch) : OfflineMatchOutcome()

    /** Shazam answered HTTP 200 but the result contained no matches – retrying will not help. */
    object NoMatch : OfflineMatchOutcome()

    /** Token endpoint down, timeout, DNS/IO error, HTTP 5xx/429, unparsable body, ... – retry later. */
    data class TransientError(val reason: String) : OfflineMatchOutcome()

    /**
     * Shazam rejected this particular request with a non-retryable 4xx (e.g. 400 malformed signature).
     * The scan is kept (no data loss without an attempts counter in the schema), but the rest of the
     * queue is still processed so a single bad entry cannot block all other scans forever.
     */
    data class Rejected(val reason: String) : OfflineMatchOutcome()
}

/** What to do with a pending scan after a match attempt. */
enum class PendingScanAction {
    /** Matched and stored in History – remove from queue. */
    DELETE_RESOLVED,

    /** Definitive no-match – remove from queue (would never match on retry). */
    DELETE_NO_MATCH,

    /** Transient failure – keep the scan and stop processing the queue for this run. */
    KEEP_AND_STOP,

    /** Request-specific rejection – keep the scan but continue with the next one. */
    KEEP_AND_CONTINUE
}

/**
 * Pure, Android-free queue processing logic so it can be unit tested on the JVM.
 * Logging is injected because android.util.Log is not available in local unit tests.
 */
object OfflineScanQueueProcessor {

    /**
     * Classifies a single Shazam catalog HTTP response (401/403 token refresh is handled by the caller
     * before this is consulted). Only an HTTP 2xx with a well-formed JSON body that contains no match
     * counts as a definitive [OfflineMatchOutcome.NoMatch].
     */
    fun classifyMatchResponse(
        httpCode: Int,
        body: String?,
        parse: (String) -> DirectShazamMatch?
    ): OfflineMatchOutcome {
        if (httpCode in 200..299) {
            val text = body.orEmpty()
            val isJsonObject = try {
                text.isNotBlank() && com.google.gson.JsonParser.parseString(text).isJsonObject
            } catch (_: Exception) {
                // Handled explicitly below as TransientError (reason is logged by the queue processor).
                false
            }
            // A truncated/garbled body (proxy, captive portal) is not proof that the song is unknown.
            if (!isJsonObject) return OfflineMatchOutcome.TransientError("HTTP $httpCode with unparsable body")
            val parsed = parse(text)
            return if (parsed != null) OfflineMatchOutcome.Matched(parsed) else OfflineMatchOutcome.NoMatch
        }
        return when {
            httpCode == 401 || httpCode == 403 -> OfflineMatchOutcome.TransientError("HTTP $httpCode (developer token rejected)")
            httpCode == 408 || httpCode == 429 || httpCode >= 500 -> OfflineMatchOutcome.TransientError("HTTP $httpCode")
            else -> OfflineMatchOutcome.Rejected("HTTP $httpCode")
        }
    }

    /**
     * Combines the outcomes of several storefront attempts that all failed: if any failure was
     * transient a retry may succeed, so transient wins over a request-specific rejection.
     */
    fun combineFailedAttempts(failures: List<OfflineMatchOutcome>): OfflineMatchOutcome {
        val transient = failures.filterIsInstance<OfflineMatchOutcome.TransientError>()
        if (transient.isNotEmpty()) {
            return OfflineMatchOutcome.TransientError(transient.joinToString("; ") { it.reason })
        }
        val rejected = failures.filterIsInstance<OfflineMatchOutcome.Rejected>()
        if (rejected.isNotEmpty()) {
            return OfflineMatchOutcome.Rejected(rejected.joinToString("; ") { it.reason })
        }
        if (failures.any { it is OfflineMatchOutcome.NoMatch }) {
            return OfflineMatchOutcome.NoMatch
        }
        return OfflineMatchOutcome.TransientError("no storefront attempt completed")
    }

    fun actionFor(outcome: OfflineMatchOutcome): PendingScanAction = when (outcome) {
        is OfflineMatchOutcome.Matched -> PendingScanAction.DELETE_RESOLVED
        OfflineMatchOutcome.NoMatch -> PendingScanAction.DELETE_NO_MATCH
        is OfflineMatchOutcome.TransientError -> PendingScanAction.KEEP_AND_STOP
        is OfflineMatchOutcome.Rejected -> PendingScanAction.KEEP_AND_CONTINUE
    }

    /**
     * Processes [pending] scans in order and returns the number of successfully resolved scans.
     *
     * - Blank signatures are corrupt and removed.
     * - A scan is only deleted after it was resolved ([onMatched] returned normally) or after a
     *   definitive [OfflineMatchOutcome.NoMatch].
     * - On [OfflineMatchOutcome.TransientError] the scan is kept and the run stops, so we neither lose
     *   data nor hammer a failing backend with the remaining queue.
     * - If [onMatched] throws (e.g. storage error) the scan is kept for the next run and processing
     *   continues with the next scan, mirroring the previous behaviour.
     * - [CancellationException] is always rethrown.
     */
    suspend fun process(
        pending: List<PendingAcousticScan>,
        match: suspend (signatureBase64: String) -> OfflineMatchOutcome,
        onMatched: suspend (DirectShazamMatch) -> Unit,
        delete: (id: String) -> Unit,
        log: (message: String) -> Unit
    ): Int {
        var resolvedCount = 0
        for (scan in pending) {
            if (scan.signatureBase64.isBlank()) {
                log("Removing corrupt offline scan ${scan.id} (blank signature)")
                delete(scan.id)
                continue
            }

            val outcome = try {
                match(scan.signatureBase64)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                OfflineMatchOutcome.TransientError("match threw ${t::class.simpleName}: ${t.message}")
            }

            when (actionFor(outcome)) {
                PendingScanAction.DELETE_RESOLVED -> {
                    val matched = (outcome as OfflineMatchOutcome.Matched).match
                    try {
                        onMatched(matched)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        log("Failed to store offline scan ${scan.id}, keeping it for next run: ${t.message}")
                        continue
                    }
                    resolvedCount++
                    delete(scan.id)
                }
                PendingScanAction.DELETE_NO_MATCH -> {
                    log("Offline scan ${scan.id}: definitive no match, removing from queue")
                    delete(scan.id)
                }
                PendingScanAction.KEEP_AND_STOP -> {
                    val reason = (outcome as OfflineMatchOutcome.TransientError).reason
                    log("Offline scan ${scan.id}: transient error ($reason) – keeping queue, retrying on next run")
                    return resolvedCount
                }
                PendingScanAction.KEEP_AND_CONTINUE -> {
                    val reason = (outcome as OfflineMatchOutcome.Rejected).reason
                    log("Offline scan ${scan.id}: rejected by server ($reason) – keeping it, continuing with next scan")
                }
            }
        }
        return resolvedCount
    }
}
