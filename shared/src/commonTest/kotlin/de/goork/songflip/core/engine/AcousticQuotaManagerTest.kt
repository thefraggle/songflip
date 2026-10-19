package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.CacheEntry
import de.goork.songflip.core.cache.CacheStorage
import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.MusicPlatform
import kotlinx.coroutines.test.runTest
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
            appleMusicUrl = "https://music.apple.com/de/album/around-the-world/697194953?i=697195787",
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
        assertEquals(1, history.size)
        assertTrue(history.first().isAcoustic, "Acoustic match must be flagged with isAcoustic = true in history")
    }
}
