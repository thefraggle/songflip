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
}
