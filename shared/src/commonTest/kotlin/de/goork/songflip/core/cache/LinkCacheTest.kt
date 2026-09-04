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
}
