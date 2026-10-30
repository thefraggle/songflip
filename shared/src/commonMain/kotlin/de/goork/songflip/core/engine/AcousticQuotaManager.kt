package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.PendingAcousticScan
import de.goork.songflip.core.cache.SqlDelightCacheStorage
import de.goork.songflip.core.cache.createDefaultCacheStorage
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.PlatformLock
import de.goork.songflip.core.util.SongFlipLogger
import de.goork.songflip.core.util.UrlUtils
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException

/**
 * Manages the monthly Freemium quota (3 free successful scans/month) and offline signature queue
 * for "Listen & Flip" acoustic music recognition (#78).
 *
 * Failed or cancelled recognition attempts NEVER deduct from the user's free monthly allowance.
 */
class AcousticQuotaManager(
    private val sqlStorage: SqlDelightCacheStorage? = (createDefaultCacheStorage() as? SqlDelightCacheStorage)
) {
    companion object {
        const val MAX_FREE_SCANS_PER_MONTH = ProLimits.FREE_SCAN_QUOTA_PER_MONTH
        private const val QUOTA_KEY_PREFIX = "acoustic_scans_month:"
        val shared: AcousticQuotaManager by lazy { AcousticQuotaManager() }

        /**
         * Computes a deterministic UTC year-month key (e.g. "2026-10") from epoch milliseconds
         * without requiring external datetime libraries.
         */
        fun computeYearMonthKey(timestampMs: Long = getCurrentTimeMillis()): String {
            // Civil date from days since 1970-01-01 (Howard Hinnant algorithm)
            val daysSinceEpoch = timestampMs / 86_400_000L
            val z = daysSinceEpoch + 719468L
            val era = (if (z >= 0) z else z - 146096L) / 146097L
            val doe = z - era * 146097L
            val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
            val y = yoe + era * 400L
            val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
            val mp = (5L * doy + 2L) / 153L
            val m = mp + (if (mp < 10L) 3L else -9L)
            val year = y + (if (m <= 2L) 1L else 0L)
            val monthStr = m.toString().padStart(2, '0')
            return "$year-$monthStr"
        }

        /**
         * Builds the canonical source URL from an acoustic match to feed into SongLinkEngine.
         */
        fun buildBestSourceUrl(
            appleMusicUrl: String?,
            appleMusicId: String?,
            webUrl: String?,
            title: String,
            artist: String
        ): String {
            val cleanAppleUrl = appleMusicUrl?.trim()?.takeIf { it.startsWith("https://") }
            if (cleanAppleUrl != null) return UrlUtils.normalizeUrl(cleanAppleUrl)

            val cleanAppleId = appleMusicId?.trim()?.takeIf { it.isNotEmpty() && it.all { ch -> ch.isDigit() } }
            if (cleanAppleId != null) {
                return "https://music.apple.com/us/song/$cleanAppleId"
            }

            val cleanWebUrl = webUrl?.trim()?.takeIf { it.startsWith("https://") }
            if (cleanWebUrl != null) return UrlUtils.normalizeUrl(cleanWebUrl)

            val query = "$artist $title".trim()
            // Proper percent-encoding (instead of replacing spaces with '+') so titles containing
            // '&', '#', '+' or '/' (e.g. "AC/DC", "Rock & Roll") don't truncate or corrupt the query.
            return "https://music.youtube.com/search?q=${query.encodeURLParameter()}"
        }
    }

    private val quotaLock = PlatformLock()
    private val inMemoryQuotaFallback = mutableMapOf<String, Int>()

    private fun metaKeyForTime(timestampMs: Long): String =
        "$QUOTA_KEY_PREFIX${computeYearMonthKey(timestampMs)}"

    fun getUsedScansThisMonth(timestampMs: Long = getCurrentTimeMillis()): Int {
        val key = metaKeyForTime(timestampMs)
        val raw = sqlStorage?.getMetaValue(key)
        if (raw != null) {
            return raw.toIntOrNull()?.coerceAtLeast(0) ?: 0
        }
        return quotaLock.withLock {
            inMemoryQuotaFallback[key] ?: 0
        }
    }

    fun getRemainingFreeScans(isPro: Boolean, timestampMs: Long = getCurrentTimeMillis()): Int {
        if (isPro) return Int.MAX_VALUE
        val used = getUsedScansThisMonth(timestampMs)
        return (MAX_FREE_SCANS_PER_MONTH - used).coerceAtLeast(0)
    }

    fun canStartScan(isPro: Boolean, timestampMs: Long = getCurrentTimeMillis()): Boolean {
        if (isPro) return true
        return getRemainingFreeScans(isPro = false, timestampMs = timestampMs) > 0
    }

    /**
     * Records a successful acoustic match for a Free user. Returns the remaining free scans for the month.
     */
    fun recordSuccessfulScan(isPro: Boolean, timestampMs: Long = getCurrentTimeMillis()): Int {
        if (isPro) return Int.MAX_VALUE
        val key = metaKeyForTime(timestampMs)
        return quotaLock.withLock {
            val currentUsed = sqlStorage?.getMetaValue(key)?.toIntOrNull()?.coerceAtLeast(0)
                ?: inMemoryQuotaFallback[key] ?: 0
            val nextCount = currentUsed + 1
            inMemoryQuotaFallback[key] = nextCount
            sqlStorage?.setMetaValue(key, nextCount.toString())
            (MAX_FREE_SCANS_PER_MONTH - nextCount).coerceAtLeast(0)
        }
    }

    fun enqueuePendingAcousticScan(
        id: String,
        signatureBase64: String,
        durationMs: Long,
        createdAt: Long = getCurrentTimeMillis()
    ) {
        sqlStorage?.enqueuePendingAcousticScan(
            id = id,
            signatureBase64 = signatureBase64,
            durationMs = durationMs,
            createdAt = createdAt
        )
    }

    fun getPendingAcousticScans(): List<PendingAcousticScan> =
        sqlStorage?.getPendingAcousticScans() ?: emptyList()

    fun deletePendingAcousticScan(id: String) {
        sqlStorage?.deletePendingAcousticScan(id)
    }

    fun countPendingAcousticScans(): Int =
        sqlStorage?.countPendingAcousticScans() ?: 0

    /**
     * Resolves an acoustic match into the user's preferred streaming platform and stores it in History
     * with `isAcoustic = true` (#78).
     *
     * Recognizing the same song again updates its timestamp in History without deducting an additional
     * free monthly scan.
     *
     * `authToken` and `audiophileMode` are forwarded to [SongLinkEngine.resolveTargetUrl] so acoustic
     * matches get the same Pro benefits (L2 server cache, Audiophile Quality Matcher) as normal flips.
     * Both are appended with defaults to stay source-compatible with existing Kotlin callers.
     *
     * Throws [CancellationException] if the calling coroutine is cancelled during resolution; in that
     * case neither a History entry is written nor a free scan consumed.
     */
    suspend fun resolveAndStoreAcousticMatch(
        engine: SongLinkEngine = SongLinkEngine.shared,
        title: String,
        artist: String,
        artworkUrl: String?,
        appleMusicUrl: String?,
        appleMusicId: String?,
        webUrl: String?,
        targetPlatform: MusicPlatform,
        isPro: Boolean,
        timestampMs: Long = getCurrentTimeMillis(),
        authToken: String = "",
        audiophileMode: Boolean = false
    ): AcousticFlipResult {
        val sourceUrl = buildBestSourceUrl(
            appleMusicUrl = appleMusicUrl,
            appleMusicId = appleMusicId,
            webUrl = webUrl,
            title = title,
            artist = artist
        )

        val alreadyInHistoryBeforeResolve = engine.cache.hasMatchingHistoryEntry(
            canonicalUrl = sourceUrl,
            targetPlatformKey = targetPlatform.key,
            targetUrl = null,
            title = title,
            artist = artist,
            currentTimeMs = timestampMs
        )

        // Fast-path: If user's target platform is Apple Music and ShazamKit already gave us a direct Apple Music URL
        if (targetPlatform == MusicPlatform.APPLE_MUSIC && !appleMusicUrl.isNullOrBlank() && appleMusicUrl.startsWith("https://")) {
            val cleanTarget = UrlUtils.normalizeUrl(appleMusicUrl)
            val alreadyInHistory = alreadyInHistoryBeforeResolve || engine.cache.hasMatchingHistoryEntry(
                canonicalUrl = sourceUrl,
                targetPlatformKey = targetPlatform.key,
                targetUrl = cleanTarget,
                title = title,
                artist = artist,
                currentTimeMs = timestampMs
            )
            val cacheKey = engine.cache.addHistoryEntry(
                canonicalUrl = sourceUrl,
                targetPlatformKey = targetPlatform.key,
                targetUrl = cleanTarget,
                platform = targetPlatform.displayName,
                title = title,
                artist = artist,
                isAlbum = false,
                thumbnailUrl = artworkUrl,
                timestamp = timestampMs,
                isFavorite = null,
                isAcoustic = true
            )
            val remaining = if (alreadyInHistory) {
                getRemainingFreeScans(isPro = isPro, timestampMs = timestampMs)
            } else {
                recordSuccessfulScan(isPro = isPro, timestampMs = timestampMs)
            }
            return AcousticFlipResult(
                cacheKey = cacheKey,
                sourceUrl = sourceUrl,
                targetUrl = cleanTarget,
                nativeAppUri = null,
                platformName = targetPlatform.displayName,
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                remainingFreeScans = remaining
            )
        }

        // Standard resolution via SongLinkEngine with isPrefetch = true so SongLinkEngine does not
        // write a premature non-acoustic History entry before addHistoryEntry(..., isAcoustic = true)
        val resolution = try {
            engine.resolveTargetUrl(
                inputUrl = sourceUrl,
                targetPlatformKey = targetPlatform.key,
                isPro = isPro,
                authToken = authToken,
                isPrefetch = true,
                audiophileMode = isPro && audiophileMode
            )
        } catch (t: Throwable) {
            // A cancelled scan (sheet closed, app backgrounded) must abort here: falling through would
            // write a History entry and consume a free scan for a flip the user never saw.
            if (t is CancellationException) throw t
            // Other failures degrade gracefully to a platform search URL below, but must be visible.
            SongFlipLogger.w("AcousticQuotaManager", "resolveTargetUrl failed for $sourceUrl", t)
            null
        }

        val resolvedSuccess = resolution as? ResolutionResult.Success
        val finalTargetUrl = resolvedSuccess?.targetUrl?.takeIf { it.isNotBlank() }
            ?: buildFallbackSearchUrl(targetPlatform, title, artist)
        val finalTitle = resolvedSuccess?.title?.takeIf { it.isNotBlank() } ?: title
        val finalArtist = resolvedSuccess?.artist?.takeIf { it.isNotBlank() } ?: artist
        val finalArtwork = artworkUrl?.takeIf { it.isNotBlank() } ?: resolvedSuccess?.thumbnailUrl

        val alreadyInHistory = alreadyInHistoryBeforeResolve || engine.cache.hasMatchingHistoryEntry(
            canonicalUrl = sourceUrl,
            targetPlatformKey = targetPlatform.key,
            targetUrl = finalTargetUrl,
            title = finalTitle,
            artist = finalArtist,
            currentTimeMs = timestampMs
        )

        val cacheKey = engine.cache.addHistoryEntry(
            canonicalUrl = sourceUrl,
            targetPlatformKey = targetPlatform.key,
            targetUrl = finalTargetUrl,
            platform = targetPlatform.displayName,
            title = finalTitle,
            artist = finalArtist,
            isAlbum = false,
            thumbnailUrl = finalArtwork,
            timestamp = timestampMs,
            isFavorite = null,
            isAcoustic = true
        )

        val remaining = if (alreadyInHistory) {
            getRemainingFreeScans(isPro = isPro, timestampMs = timestampMs)
        } else {
            recordSuccessfulScan(isPro = isPro, timestampMs = timestampMs)
        }
        return AcousticFlipResult(
            cacheKey = cacheKey,
            sourceUrl = sourceUrl,
            targetUrl = finalTargetUrl,
            nativeAppUri = resolvedSuccess?.nativeAppUri,
            platformName = targetPlatform.displayName,
            title = finalTitle,
            artist = finalArtist,
            artworkUrl = finalArtwork,
            remainingFreeScans = remaining
        )
    }

    // internal (not private) so commonTest can verify the encoding of special characters.
    internal fun buildFallbackSearchUrl(targetPlatform: MusicPlatform, title: String, artist: String): String {
        // Percent-encode (spaces -> %20) instead of '+': several targets use the query as a path
        // segment (Spotify, Deezer, Amazon, Qobuz) where '+' is literal and '/', '#', '&' break the URL.
        val encoded = "$artist $title".trim().encodeURLParameter()
        return when (targetPlatform) {
            MusicPlatform.SPOTIFY -> "https://open.spotify.com/search/$encoded"
            MusicPlatform.APPLE_MUSIC -> "https://music.apple.com/search?term=$encoded"
            MusicPlatform.YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=$encoded"
            MusicPlatform.TIDAL -> "https://listen.tidal.com/search?q=$encoded"
            MusicPlatform.DEEZER -> "https://www.deezer.com/search/$encoded"
            MusicPlatform.AMAZON_MUSIC -> "https://music.amazon.com/search/$encoded"
            MusicPlatform.SOUNDCLOUD -> "https://soundcloud.com/search?q=$encoded"
            MusicPlatform.BANDCAMP -> "https://bandcamp.com/search?q=$encoded"
            MusicPlatform.QOBUZ -> "https://www.qobuz.com/us-en/search?q=$encoded"
            MusicPlatform.BEATPORT -> "https://www.beatport.com/search?q=$encoded"
        }
    }
}

data class AcousticFlipResult(
    val cacheKey: String,
    val sourceUrl: String,
    val targetUrl: String,
    val nativeAppUri: String?,
    val platformName: String,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val remainingFreeScans: Int
)
