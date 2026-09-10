package de.goork.songflip.core.cache

import de.goork.songflip.core.model.ResolutionResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LinkCacheTest {

    @Test
    fun testPutAndGet() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 1000L)
        val result = ResolutionResult.Success(
            targetUrl = "https://music.youtube.com/watch?v=123",
            platform = "youtubeMusic",
            title = "Test Song",
            artist = "Test Artist"
        )

        cache.put("https://open.spotify.com/track/123", "youtubeMusic", result, currentTimeMs = 1000L)
        assertEquals(1, cache.size())

        val retrieved = cache.get("https://open.spotify.com/track/123", "youtubeMusic", currentTimeMs = 1500L)
        assertNotNull(retrieved)
        assertEquals("https://music.youtube.com/watch?v=123", retrieved.targetUrl)
        assertEquals("Test Song", retrieved.title)
    }

    @Test
    fun testTtlExpiry() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 1000L)
        val result = ResolutionResult.Success(
            targetUrl = "https://music.youtube.com/watch?v=123",
            platform = "youtubeMusic"
        )

        cache.put("https://open.spotify.com/track/123", "youtubeMusic", result, currentTimeMs = 1000L)

        // After 1001ms, it should be expired
        val expired = cache.get("https://open.spotify.com/track/123", "youtubeMusic", currentTimeMs = 2001L)
        assertNull(expired)
        assertEquals(0, cache.size())
    }

    @Test
    fun testMaxEntriesEviction() = runTest {
        val cache = LinkCache(maxEntries = 3, ttlMs = 100000L)

        for (i in 1..4) {
            val result = ResolutionResult.Success(
                targetUrl = "https://music.youtube.com/watch?v=$i",
                platform = "youtubeMusic"
            )
            cache.put("https://open.spotify.com/track/$i", "youtubeMusic", result, currentTimeMs = i.toLong())
        }

        assertEquals(3, cache.size())
        // Oldest item (1) should have been evicted
        val item1 = cache.get("https://open.spotify.com/track/1", "youtubeMusic", currentTimeMs = 10L)
        assertNull(item1)

        // Items 2, 3, 4 should still exist
        assertNotNull(cache.get("https://open.spotify.com/track/2", "youtubeMusic", currentTimeMs = 10L))
        assertNotNull(cache.get("https://open.spotify.com/track/3", "youtubeMusic", currentTimeMs = 10L))
        assertNotNull(cache.get("https://open.spotify.com/track/4", "youtubeMusic", currentTimeMs = 10L))
    }

    @Test
    fun testClear() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 100000L)
        cache.put(
            "https://open.spotify.com/track/1",
            "youtubeMusic",
            ResolutionResult.Success(targetUrl = "url", platform = "yt"),
            currentTimeMs = 100L
        )
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun testStoragePersistenceAcrossInstances() = runTest {
        // In-memory fake storage simulating persistent shared storage (e.g. App Group UserDefaults)
        val sharedStorage = object : CacheStorage {
            val map = mutableMapOf<String, CacheEntry>()
            override fun get(key: String): CacheEntry? = map[key]
            override fun put(key: String, entry: CacheEntry) { map[key] = entry }
            override fun remove(key: String) { map.remove(key) }
            override fun clear() { map.clear() }
            override fun loadAll(): Map<String, CacheEntry> = map.toMap()
        }

        // Process 1 (e.g. ShareExtension): resolves and caches song
        val cache1 = LinkCache(maxEntries = 10, ttlMs = 100000L, storage = sharedStorage)
        cache1.put(
            canonicalUrl = "https://open.spotify.com/track/shared123",
            targetPlatformKey = "appleMusic",
            result = ResolutionResult.Success(targetUrl = "https://music.apple.com/song/shared123", platform = "appleMusic", title = "Shared Song"),
            currentTimeMs = 1000L
        )

        // Process 2 (e.g. Main App or App Intent): new LinkCache instance accessing the same storage
        val cache2 = LinkCache(maxEntries = 10, ttlMs = 100000L, storage = sharedStorage)
        val resolvedInProcess2 = cache2.get("https://open.spotify.com/track/shared123", "appleMusic", currentTimeMs = 2000L)

        assertNotNull(resolvedInProcess2, "Item cached in Process 1 must be retrievable in Process 2 from shared storage")
        assertEquals("https://music.apple.com/song/shared123", resolvedInProcess2.targetUrl)
        assertEquals("Shared Song", resolvedInProcess2.title)
    }
}
