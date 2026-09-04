package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.ResolutionResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongLinkEngineTest {

    @Test
    fun testInvalidUrlReturnsError() = runTest {
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { respond("") }),
            cache = LinkCache()
        )
        val result = engine.resolveTargetUrl("Kein Link hier", "youtubeMusic")
        assertTrue(result is ResolutionResult.Error)
        assertEquals("No valid URL found in input", (result as ResolutionResult.Error).message)
    }

    @Test
    fun testPlaylistUrlReturnsUnsupportedError() = runTest {
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { respond("") }),
            cache = LinkCache()
        )
        val result = engine.resolveTargetUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "youtubeMusic")
        assertTrue(result is ResolutionResult.Error)
        val error = result as ResolutionResult.Error
        assertEquals("PLAYLIST_NOT_SUPPORTED", error.message)
        assertTrue(error.isUnsupported)
    }

    @Test
    fun testCacheHitReturnsImmediatelyWithoutNetwork() = runTest {
        val cache = LinkCache()
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { error("Network should not be called on cache hit") }),
            cache = cache
        )

        // Pre-populate cache
        cache.put(
            canonicalUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetPlatformKey = "youtubeMusic",
            result = ResolutionResult.Success(
                targetUrl = "https://music.youtube.com/watch?v=fJ9rUzIMcZQ",
                platform = "youtubeMusic",
                title = "Bohemian Rhapsody",
                artist = "Queen"
            ),
            currentTimeMs = getCurrentTimeMillis()
        )

        val result = engine.resolveTargetUrl("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv", "youtubeMusic")
        assertTrue(result is ResolutionResult.Success)
        val success = result as ResolutionResult.Success
        assertEquals("https://music.youtube.com/watch?v=fJ9rUzIMcZQ", success.targetUrl)
        assertEquals("Bohemian Rhapsody", success.title)
    }

    @Test
    fun testSearchUrlResolution() = runTest {
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { respond("") }),
            cache = LinkCache()
        )

        val result = engine.resolveTargetUrl("https://open.spotify.com/search/Queen%20Bohemian", "youtubeMusic")
        assertTrue(result is ResolutionResult.Success)
        val success = result as ResolutionResult.Success
        assertEquals("Queen Bohemian", success.title)
        assertTrue(success.targetUrl.contains("music.youtube.com/search?q="))
    }

    @Test
    fun testCustomApiSuccess() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"url": "https://music.youtube.com/watch?v=custom123"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val engine = SongLinkEngine(client = HttpClient(mockEngine), cache = LinkCache())

        val result = engine.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetPlatformKey = "youtubeMusic",
            customApiUrl = "https://api.example.com/resolve",
            customApiToken = "secret"
        )
        assertTrue(result is ResolutionResult.Success)
        val success = result as ResolutionResult.Success
        assertEquals("https://music.youtube.com/watch?v=custom123", success.targetUrl)
        assertEquals("custom_api", success.platform)
    }
}
