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
    fun testSpotifyUrlResolutionToYouTubeMusic() = runBlocking {
        // Known public track: Queen - Bohemian Rhapsody
        val spotifyUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        val result = repository.resolveTargetUrl(spotifyUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music URL, got: ${success.targetUrl}", success.targetUrl.contains("youtube"))
    }
}
