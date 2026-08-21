package com.songflip

import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
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
        // Known public track: Queen - Bohemian Rhapsody
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
    fun testDeezerToYouTubeMusicDirectPlay() = runBlocking {
        val deezerUrl = "https://www.deezer.com/track/9997018"
        val result = repository.resolveTargetUrl(deezerUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }
}
