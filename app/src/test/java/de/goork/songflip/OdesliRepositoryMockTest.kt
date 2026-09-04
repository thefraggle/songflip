package de.goork.songflip

import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OdesliRepositoryMockTest {

    private lateinit var server: MockWebServer
    private val repository = OdesliRepository()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        LinkCacheManager.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
        LinkCacheManager.clear()
    }

    @Test
    fun testInvalidUrlReturnsErrorImmediately() = runBlocking {
        val result = repository.resolveTargetUrl("Dies ist kein Link", "youtubeMusic")
        assertTrue(result is OdesliResult.Error)
        assertEquals("No valid URL found in input", (result as OdesliResult.Error).message)
    }

    @Test
    fun testPlaylistUrlReturnsPlaylistNotSupported() = runBlocking {
        val result = repository.resolveTargetUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "youtubeMusic")
        assertTrue(result is OdesliResult.Error)
        assertEquals("PLAYLIST_NOT_SUPPORTED", (result as OdesliResult.Error).message)
    }

    @Test
    fun testCacheHitReturnsWithoutNetwork() = runBlocking {
        val inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        LinkCacheManager.put(
            canonicalUrl = inputUrl,
            targetPlatformKey = "youtubeMusic",
            targetUrl = "https://music.youtube.com/watch?v=cached123",
            platform = "youtubeMusic",
            title = "Cached Song",
            artist = "Cached Artist"
        )

        val result = repository.resolveTargetUrl(inputUrl, "youtubeMusic")
        assertTrue(result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertEquals("https://music.youtube.com/watch?v=cached123", success.targetUrl)
        assertEquals("Cached Song", success.title)
    }

    @Test
    fun testCustomApiSuccessResolution() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"targetUrl": "https://music.youtube.com/watch?v=mockedVideo"}""")
        )

        val customApiUrl = server.url("/resolve").toString()
        val result = repository.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetPlatformKey = "youtubeMusic",
            customApiUrl = customApiUrl,
            customApiToken = "test-token"
        )

        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertEquals("https://music.youtube.com/watch?v=mockedVideo", success.targetUrl)
        assertEquals("custom_api", success.platform)
    }

    @Test
    fun testSearchQueryExtractionResolvesDirectly() = runBlocking {
        val result = repository.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/search/Queen%20Bohemian",
            targetPlatformKey = "youtubeMusic"
        )
        assertTrue(result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue(success.targetUrl.contains("music.youtube.com/"))
        assertEquals("Queen Bohemian", success.title)
    }
}
