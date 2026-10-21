package de.goork.songflip

import de.goork.songflip.core.cache.PendingAcousticScan
import de.goork.songflip.data.DirectShazamMatch
import de.goork.songflip.data.OfflineMatchOutcome
import de.goork.songflip.data.OfflineScanQueueProcessor
import de.goork.songflip.data.PendingScanAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Regression tests for #143: queued Offline Club Mode scans must survive transient failures.
 */
class OfflineScanQueueProcessorTest {

    private val sampleMatch = DirectShazamMatch(
        title = "Around the World",
        artist = "Daft Punk",
        artworkUrl = null,
        appleMusicId = "697195787",
        appleMusicUrl = "https://music.apple.com/de/song/697195787",
        webUrl = null
    )

    private fun scan(id: String, sig: String = "c2ln") =
        PendingAcousticScan(id = id, signatureBase64 = sig, durationMs = 6_000L, createdAt = 0L)

    @Test
    fun actionForMapsOutcomes() {
        assertEquals(PendingScanAction.DELETE_RESOLVED, OfflineScanQueueProcessor.actionFor(OfflineMatchOutcome.Matched(sampleMatch)))
        assertEquals(PendingScanAction.DELETE_NO_MATCH, OfflineScanQueueProcessor.actionFor(OfflineMatchOutcome.NoMatch))
        assertEquals(PendingScanAction.KEEP_AND_STOP, OfflineScanQueueProcessor.actionFor(OfflineMatchOutcome.TransientError("timeout")))
        assertEquals(PendingScanAction.KEEP_AND_CONTINUE, OfflineScanQueueProcessor.actionFor(OfflineMatchOutcome.Rejected("HTTP 400")))
    }

    @Test
    fun transientErrorKeepsScanAndStopsQueue() = runTest {
        val deleted = mutableListOf<String>()
        val matchedSignatures = mutableListOf<String>()
        val logs = mutableListOf<String>()
        val pending = listOf(scan("a", "sigA"), scan("b", "sigB"), scan("c", "sigC"))

        val resolved = OfflineScanQueueProcessor.process(
            pending = pending,
            match = { sig ->
                matchedSignatures += sig
                if (sig == "sigA") OfflineMatchOutcome.Matched(sampleMatch)
                else OfflineMatchOutcome.TransientError("token endpoint HTTP 503")
            },
            onMatched = { },
            delete = { deleted += it },
            log = { logs += it }
        )

        assertEquals(1, resolved)
        assertEquals("Only the resolved scan may be deleted", listOf("a"), deleted)
        assertEquals("Queue must stop after the first transient error", listOf("sigA", "sigB"), matchedSignatures)
        assertTrue("Transient error must be logged", logs.any { it.contains("token endpoint HTTP 503") })
    }

    @Test
    fun definitiveNoMatchDeletesScan() = runTest {
        val deleted = mutableListOf<String>()
        var storeCalls = 0
        val resolved = OfflineScanQueueProcessor.process(
            pending = listOf(scan("a"), scan("b")),
            match = { OfflineMatchOutcome.NoMatch },
            onMatched = { storeCalls++ },
            delete = { deleted += it },
            log = { }
        )
        assertEquals(0, resolved)
        assertEquals(0, storeCalls)
        assertEquals(listOf("a", "b"), deleted)
    }

    @Test
    fun rejectedKeepsScanButContinues() = runTest {
        val deleted = mutableListOf<String>()
        val resolved = OfflineScanQueueProcessor.process(
            pending = listOf(scan("bad", "sigBad"), scan("good", "sigGood")),
            match = { sig ->
                if (sig == "sigBad") OfflineMatchOutcome.Rejected("HTTP 400") else OfflineMatchOutcome.Matched(sampleMatch)
            },
            onMatched = { },
            delete = { deleted += it },
            log = { }
        )
        assertEquals(1, resolved)
        assertEquals(listOf("good"), deleted)
    }

    @Test
    fun blankSignatureIsRemovedWithoutMatching() = runTest {
        val deleted = mutableListOf<String>()
        var matchCalls = 0
        OfflineScanQueueProcessor.process(
            pending = listOf(scan("blank", "  ")),
            match = { matchCalls++; OfflineMatchOutcome.NoMatch },
            onMatched = { },
            delete = { deleted += it },
            log = { }
        )
        assertEquals(0, matchCalls)
        assertEquals(listOf("blank"), deleted)
    }

    @Test
    fun storeFailureKeepsScanAndContinues() = runTest {
        val deleted = mutableListOf<String>()
        var first = true
        val resolved = OfflineScanQueueProcessor.process(
            pending = listOf(scan("a"), scan("b")),
            match = { OfflineMatchOutcome.Matched(sampleMatch) },
            onMatched = {
                if (first) {
                    first = false
                    throw IllegalStateException("db locked")
                }
            },
            delete = { deleted += it },
            log = { }
        )
        assertEquals(1, resolved)
        assertEquals(listOf("b"), deleted)
    }

    @Test
    fun matcherExceptionIsTreatedAsTransient() = runTest {
        val deleted = mutableListOf<String>()
        val resolved = OfflineScanQueueProcessor.process(
            pending = listOf(scan("a"), scan("b")),
            match = { throw java.io.IOException("Unable to resolve host") },
            onMatched = { },
            delete = { deleted += it },
            log = { }
        )
        assertEquals(0, resolved)
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun cancellationIsRethrownAndNothingDeleted() = runTest {
        val deleted = mutableListOf<String>()
        try {
            OfflineScanQueueProcessor.process(
                pending = listOf(scan("a")),
                match = { OfflineMatchOutcome.Matched(sampleMatch) },
                onMatched = { throw CancellationException("sheet closed") },
                delete = { deleted += it },
                log = { }
            )
            fail("CancellationException must propagate")
        } catch (_: CancellationException) {
            // expected
        }
        assertTrue(deleted.isEmpty())
    }

    @Test
    fun classifyMatchResponseDistinguishesNoMatchFromTransient() {
        val emptyMatches = """{"results":{"matches":[]},"resources":{}}"""
        assertEquals(
            OfflineMatchOutcome.NoMatch,
            OfflineScanQueueProcessor.classifyMatchResponse(200, emptyMatches) { null }
        )
        assertEquals(
            OfflineMatchOutcome.Matched(sampleMatch),
            OfflineScanQueueProcessor.classifyMatchResponse(200, emptyMatches) { sampleMatch }
        )
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(200, "") { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(200, "<html>captive portal</html>") { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(429, null) { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(500, null) { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(503, null) { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(408, null) { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(401, null) { null } is OfflineMatchOutcome.TransientError)
        assertTrue(OfflineScanQueueProcessor.classifyMatchResponse(400, null) { null } is OfflineMatchOutcome.Rejected)
    }

    @Test
    fun combineFailedAttemptsPrefersTransient() {
        val combined = OfflineScanQueueProcessor.combineFailedAttempts(
            listOf(OfflineMatchOutcome.Rejected("HTTP 400"), OfflineMatchOutcome.TransientError("HTTP 503"))
        )
        assertTrue(combined is OfflineMatchOutcome.TransientError)
        assertTrue(
            OfflineScanQueueProcessor.combineFailedAttempts(listOf(OfflineMatchOutcome.Rejected("HTTP 400"))) is OfflineMatchOutcome.Rejected
        )
        assertTrue(OfflineScanQueueProcessor.combineFailedAttempts(emptyList()) is OfflineMatchOutcome.TransientError)
    }
}
