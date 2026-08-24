package de.goork.songflip

import de.goork.songflip.data.LinkCacheManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LinkCacheManagerTest {

    @Before
    fun setUp() {
        LinkCacheManager.clear()
    }

    @Test
    fun testCachePutAndGet() {
        val inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv?si=abc"
        val targetPlatform = "youtubeMusic"
        val targetUrl = "https://music.youtube.com/watch?v=fJ9rUzIMcZQ"

        LinkCacheManager.put(
            canonicalUrl = inputUrl,
            targetPlatformKey = targetPlatform,
            targetUrl = targetUrl,
            platform = targetPlatform,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            isAlbum = false
        )

        val cached = LinkCacheManager.get(inputUrl, targetPlatform)
        assertNotNull(cached)
        assertEquals(targetUrl, cached?.targetUrl)
        assertEquals("Bohemian Rhapsody", cached?.title)
        assertEquals("Queen", cached?.artist)
        assertFalse(cached?.isAlbum ?: true)
    }

    @Test
    fun testCacheMiss() {
        val nonExistent = LinkCacheManager.get("https://music.apple.com/song/123", "spotify")
        assertNull(nonExistent)
    }

    @Test
    fun testCacheClear() {
        LinkCacheManager.put(
            canonicalUrl = "https://song.link/s/123",
            targetPlatformKey = "appleMusic",
            targetUrl = "https://music.apple.com/song/123",
            platform = "appleMusic"
        )
        assertEquals(1, LinkCacheManager.size())
        LinkCacheManager.clear()
        assertEquals(0, LinkCacheManager.size())
    }

    @Test
    fun testHistoryRetrievalAndLimit() {
        for (i in 1..15) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic",
                title = "Song $i",
                artist = "Artist $i"
            )
            // Small sleep to ensure distinct timestamps
            Thread.sleep(2)
        }

        val freeHistory = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, freeHistory.size)
        // Newest should be Song 15
        assertEquals("Song 15", freeHistory.first().title)
        assertEquals("Song 6", freeHistory.last().title)

        val proHistory = LinkCacheManager.getHistoryEntries(limit = 100)
        assertEquals(15, proHistory.size)
        assertEquals("Song 15", proHistory.first().title)
        assertEquals("Song 1", proHistory.last().title)
    }

    @Test
    fun testHistoryItemDeletionAndRefill() {
        for (i in 1..15) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic",
                title = "Song $i",
                artist = "Artist $i"
            )
            Thread.sleep(2)
        }

        var history = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, history.size)
        assertEquals("Song 15", history.first().title)

        // Delete top item (Song 15)
        val topKey = history.first().cacheKey
        LinkCacheManager.removeByCacheKey(topKey)

        // New history should still have 10 items because Song 5 refills the 10th spot!
        history = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, history.size)
        assertEquals("Song 14", history.first().title)
        assertEquals("Song 5", history.last().title)

        // Song 15 should no longer be in cache
        val checkDeleted = LinkCacheManager.get("https://open.spotify.com/track/15", "youtubeMusic")
        assertNull(checkDeleted)
    }

    @Test
    fun testClearHistoryAndCache() {
        for (i in 1..5) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic"
            )
        }
        assertEquals(5, LinkCacheManager.getHistoryEntries(limit = 10).size)
        LinkCacheManager.clearHistoryAndCache()
        assertEquals(0, LinkCacheManager.getHistoryEntries(limit = 10).size)
        assertEquals(0, LinkCacheManager.getTotalCachedCount())
    }
}
