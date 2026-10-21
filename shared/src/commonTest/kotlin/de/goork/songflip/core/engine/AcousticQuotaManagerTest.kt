package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.CacheEntry
import de.goork.songflip.core.cache.CacheStorage
import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AcousticQuotaManagerTest {

    @Test
    fun testComputeYearMonthKey() {
        // 2026-10-19T10:00:00Z -> 1792404000000L
        assertEquals("2026-10", AcousticQuotaManager.computeYearMonthKey(1792404000000L))
        // 2027-01-01T00:00:00Z -> 1798761600000L
        assertEquals("2027-01", AcousticQuotaManager.computeYearMonthKey(1798761600000L))
    }

    @Test
    fun testFreeMonthlyQuotaAndMonthRollover() {
        val manager = AcousticQuotaManager(sqlStorage = null)
        val oct2026 = 1792404000000L
        val nov2026 = 1795082400000L

        assertEquals(3, manager.getRemainingFreeScans(isPro = false, timestampMs = oct2026))
        assertTrue(manager.canStartScan(isPro = false, timestampMs = oct2026))

        // Scan 1
        assertEquals(2, manager.recordSuccessfulScan(isPro = false, timestampMs = oct2026))
        // Scan 2
        assertEquals(1, manager.recordSuccessfulScan(isPro = false, timestampMs = oct2026))
        // Scan 3
        assertEquals(0, manager.recordSuccessfulScan(isPro = false, timestampMs = oct2026))

        // Quota exhausted in October for Free user
        assertFalse(manager.canStartScan(isPro = false, timestampMs = oct2026))
        assertEquals(0, manager.getRemainingFreeScans(isPro = false, timestampMs = oct2026))

        // Pro user always allowed
        assertTrue(manager.canStartScan(isPro = true, timestampMs = oct2026))
        assertEquals(Int.MAX_VALUE, manager.getRemainingFreeScans(isPro = true, timestampMs = oct2026))

        // New month (November) resets Free user quota to 3
        assertTrue(manager.canStartScan(isPro = false, timestampMs = nov2026))
        assertEquals(3, manager.getRemainingFreeScans(isPro = false, timestampMs = nov2026))
    }

    @Test
    fun testResolveAndStoreAcousticMatchPreservesAcousticFlagInHistory() = runTest {
        val memStorage = object : CacheStorage {
            val map = mutableMapOf<String, CacheEntry>()
            override fun get(key: String): CacheEntry? = map[key]
            override fun put(key: String, entry: CacheEntry) { map[key] = entry }
            override fun remove(key: String) { map.remove(key) }
            override fun clear() { map.clear() }
            override fun loadAll(): Map<String, CacheEntry> = map.toMap()
        }
        val cache = LinkCache(storage = memStorage)
        val engine = SongLinkEngine(cache = cache)
        val manager = AcousticQuotaManager(sqlStorage = null)

        val result = manager.resolveAndStoreAcousticMatch(
            engine = engine,
            title = "Around the World",
            artist = "Daft Punk",
            artworkUrl = "https://is1-ssl.mzstatic.com/cover.jpg",
            appleMusicUrl = "https://music.apple.com/de/album/around-the-world/697194953?i=697195787&uo=4",
            appleMusicId = "697195787",
            webUrl = "https://www.shazam.com/track/12345",
            targetPlatform = MusicPlatform.APPLE_MUSIC,
            isPro = false,
            timestampMs = 1792404000000L
        )

        assertEquals("Around the World", result.title)
        assertEquals("Daft Punk", result.artist)
        assertEquals(2, result.remainingFreeScans)

        val history = cache.getHistoryEntries(limit = 10, currentTimeMs = 1792404000000L)
        assertEquals(1, history.size, "Must only create 1 history entry even when appleMusicUrl contains &uo=4")
        assertTrue(history.first().isAcoustic, "Acoustic match must be flagged with isAcoustic = true in history")

        // Recognizing the exact same song a second time must NOT deduct another free scan
        val secondResult = manager.resolveAndStoreAcousticMatch(
            engine = engine,
            title = "Around the World",
            artist = "Daft Punk",
            artworkUrl = "https://is1-ssl.mzstatic.com/cover.jpg",
            appleMusicUrl = "https://music.apple.com/de/album/around-the-world/697194953?i=697195787&uo=4",
            appleMusicId = "697195787",
            webUrl = "https://www.shazam.com/track/12345",
            targetPlatform = MusicPlatform.APPLE_MUSIC,
            isPro = false,
            timestampMs = 1792404010000L
        )
        assertEquals(2, secondResult.remainingFreeScans, "Repeat recognition of a song already in history must not deduct another free scan")
        val historyAfterSecondScan = cache.getHistoryEntries(limit = 10, currentTimeMs = 1792404010000L)
        assertEquals(1, historyAfterSecondScan.size, "History must still contain only 1 entry after repeat recognition")
        assertTrue(historyAfterSecondScan.first().isAcoustic)
    }

    @Test
    fun testSelfHealingDeduplicatesExistingNormalAndAcousticEntries() = runTest {
        val memStorage = object : CacheStorage {
            val map = mutableMapOf<String, CacheEntry>()
            override fun get(key: String): CacheEntry? = map[key]
            override fun put(key: String, entry: CacheEntry) { map[key] = entry }
            override fun remove(key: String) { map.remove(key) }
            override fun clear() { map.clear() }
            override fun loadAll(): Map<String, CacheEntry> = map.toMap()
        }
        // Simulate pre-existing legacy storage with two separate keys for the same song (one normal, one acoustic)
        memStorage.put(
            "https://music.apple.com/de/album/world-alone/123?i=456|SPOTIFY",
            CacheEntry(
                targetUrl = "https://open.spotify.com/track/abc123",
                platform = "SPOTIFY",
                timestamp = 1792404000000L,
                title = "World Alone",
                artist = "Tony Iommi & Jorn",
                thumbnailUrl = "https://example.com/cover.jpg",
                isHistory = true,
                isFavorite = true,
                isAcoustic = false
            )
        )
        memStorage.put(
            "https://music.apple.com/de/album/world-alone/123?i=456&uo=4|SPOTIFY",
            CacheEntry(
                targetUrl = "https://open.spotify.com/track/abc123",
                platform = "SPOTIFY",
                timestamp = 1792404005000L,
                title = "World Alone",
                artist = "Tony Iommi & Jorn",
                thumbnailUrl = "https://example.com/cover.jpg",
                isHistory = true,
                isFavorite = false,
                isAcoustic = true
            )
        )

        val cache = LinkCache(storage = memStorage)
        val history = cache.getHistoryEntries(limit = 10, currentTimeMs = 1792404010000L)
        assertEquals(1, history.size, "Duplicate normal + acoustic entries for the same song must self-heal into 1 entry")
        assertTrue(history.first().isAcoustic, "Merged entry must preserve isAcoustic = true")
        assertTrue(history.first().isFavorite, "Merged entry must preserve isFavorite = true")
    }

    @Test
    fun testCancelledResolutionDoesNotConsumeQuotaOrWriteHistory() = runTest {
        val memStorage = object : CacheStorage {
            val map = mutableMapOf<String, CacheEntry>()
            override fun get(key: String): CacheEntry? = map[key]
            override fun put(key: String, entry: CacheEntry) { map[key] = entry }
            override fun remove(key: String) { map.remove(key) }
            override fun clear() { map.clear() }
            override fun loadAll(): Map<String, CacheEntry> = map.toMap()
        }
        val cache = LinkCache(storage = memStorage)
        val requestStarted = CompletableDeferred<Unit>()
        // The mock engine suspends forever so the resolution is guaranteed to be in flight when we cancel.
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine {
                requestStarted.complete(Unit)
                awaitCancellation()
            }),
            cache = cache
        )
        val manager = AcousticQuotaManager(sqlStorage = null)
        val timestamp = 1792404000000L

        val job = launch(Dispatchers.Default) {
            manager.resolveAndStoreAcousticMatch(
                engine = engine,
                title = "Around the World",
                artist = "Daft Punk",
                artworkUrl = null,
                appleMusicUrl = "https://music.apple.com/de/album/around-the-world/697194953?i=697195787",
                appleMusicId = "697195787",
                webUrl = null,
                // Non-Apple target forces the SongLinkEngine path (no Apple Music fast-path)
                targetPlatform = MusicPlatform.SPOTIFY,
                isPro = false,
                timestampMs = timestamp
            )
        }
        withContext(Dispatchers.Default) {
            withTimeout(10_000L) { requestStarted.await() }
        }
        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(0, manager.getUsedScansThisMonth(timestamp), "A cancelled resolution must not consume a free scan")
        assertEquals(
            AcousticQuotaManager.MAX_FREE_SCANS_PER_MONTH,
            manager.getRemainingFreeScans(isPro = false, timestampMs = timestamp)
        )
        assertTrue(
            cache.getHistoryEntries(limit = 10, currentTimeMs = timestamp).isEmpty(),
            "A cancelled resolution must not write a History entry"
        )
    }

    @Test
    fun testBuildBestSourceUrlEncodesSpecialCharactersInSearchQuery() {
        val url = AcousticQuotaManager.buildBestSourceUrl(
            appleMusicUrl = null,
            appleMusicId = null,
            webUrl = null,
            title = "Rock & Roll #1 + More",
            artist = "AC/DC"
        )
        assertTrue(url.startsWith("https://music.youtube.com/search?q="))
        val rawQuery = url.substringAfter("?q=")
        assertFalse(rawQuery.contains('&'), "'&' must be percent-encoded: $url")
        assertFalse(rawQuery.contains('#'), "'#' must be percent-encoded: $url")
        assertFalse(rawQuery.contains('+'), "'+' must be percent-encoded: $url")
        assertFalse(rawQuery.contains('/'), "'/' must be percent-encoded: $url")
        assertFalse(rawQuery.contains(' '), "Spaces must be percent-encoded: $url")
        // SongLinkEngine must be able to recover the original query from the source URL
        assertEquals("AC/DC Rock & Roll #1 + More", UrlUtils.extractSearchQuery(url))
    }

    @Test
    fun testFallbackSearchUrlEncodesSpecialCharacters() {
        val manager = AcousticQuotaManager(sqlStorage = null)
        val spotify = manager.buildFallbackSearchUrl(MusicPlatform.SPOTIFY, title = "Rock & Roll #1 + More", artist = "AC/DC")
        val pathSegment = spotify.substringAfter("https://open.spotify.com/search/")
        assertFalse(pathSegment.contains('/'), "'/' must not create extra path segments: $spotify")
        assertFalse(pathSegment.contains('#'), "'#' must not start a fragment: $spotify")
        assertFalse(pathSegment.contains('&'), spotify)
        assertFalse(pathSegment.contains('+'), "Literal '+' must be encoded: $spotify")
        assertEquals("AC/DC Rock & Roll #1 + More", UrlUtils.extractSearchQuery(spotify))

        val apple = manager.buildFallbackSearchUrl(MusicPlatform.APPLE_MUSIC, title = "Rock & Roll", artist = "AC/DC")
        assertEquals("AC/DC Rock & Roll", UrlUtils.extractSearchQuery(apple))
    }
}
