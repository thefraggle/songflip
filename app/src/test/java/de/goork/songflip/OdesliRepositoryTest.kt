package de.goork.songflip

import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OdesliRepositoryTest {

    private val repository = OdesliRepository()

    @Test
    fun testInvalidUrlReturnsError() = runBlocking {
        val result = repository.resolveTargetUrl("just random text without url", "youtubeMusic")
        assertTrue(result is OdesliResult.Error)
        assertEquals("No valid URL found in input", (result as OdesliResult.Error).message)
    }

    @Test
    fun testSpotifyUrlDirectPlaybackResolutionToYouTubeMusic() = runBlocking {
        val spotifyUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        val result = repository.resolveTargetUrl(spotifyUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected direct watch URL for instant playback, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAppleMusicToYouTubeMusicDirectPlay() = runBlocking {
        val appleUrl = "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?i=1440650711"
        val result = repository.resolveTargetUrl(appleUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAppleMusicSongFormatDirectPlay() = runBlocking {
        val appleSongUrl = "https://music.apple.com/de/song/bohemian-rhapsody/1440650711"
        val result = repository.resolveTargetUrl(appleSongUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testDeezerToYouTubeMusicDirectPlay() = runBlocking {
        val deezerUrl = "https://www.deezer.com/track/9997018"
        val result = repository.resolveTargetUrl(deezerUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testTidalToYouTubeMusicDirectPlay() = runBlocking {
        val tidalUrl = "https://listen.tidal.com/track/534050211"
        val result = repository.resolveTargetUrl(tidalUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAmazonMusicToYouTubeMusicDirectPlay() = runBlocking {
        val amazonUrl = "https://music.amazon.de/albums/B004G92QE4?trackAsin=B004G8Z8UO"
        val result = repository.resolveTargetUrl(amazonUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testL1CacheHitReturnsInstantly() = runBlocking {
        val testUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        val firstResult = repository.resolveTargetUrl(testUrl, "youtubeMusic")
        assertTrue(firstResult is OdesliResult.Success)
        val firstSuccess = firstResult as OdesliResult.Success

        // Second call should hit L1 Cache
        val secondResult = repository.resolveTargetUrl(testUrl, "youtubeMusic")
        assertTrue(secondResult is OdesliResult.Success)
        val secondSuccess = secondResult as OdesliResult.Success
        assertEquals(firstSuccess.targetUrl, secondSuccess.targetUrl)
        assertEquals(firstSuccess.platform, secondSuccess.platform)
    }
}
