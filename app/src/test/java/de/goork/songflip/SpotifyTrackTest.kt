package de.goork.songflip

import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpotifyTrackTest {

    private val repository = OdesliRepository()

    @Before
    fun setUp() {
        LinkCacheManager.clear()
    }

    @Test
    fun testRickAstleySpotifyTrackResolves() = runBlocking {
        val urls = listOf(
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
            "https://open.spotify.com/intl-de/track/4cOdK2wGLETKBW3PvgPWqT?si=test12345",
            "https://open.spotify.com/intl-fr/track/4cOdK2wGLETKBW3PvgPWqT"
        )
        for (url in urls) {
            for (target in listOf("youtubeMusic", "appleMusic", "deezer", "tidal", "amazonMusic", "spotify")) {
                LinkCacheManager.clear()
                val result = repository.resolveTargetUrl(url, target)
                println("URL: $url -> Target: $target -> Result: $result")
                assertTrue("Expected Success for $url on $target, got: $result", result is OdesliResult.Success)
            }
        }
    }
}
