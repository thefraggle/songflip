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
    fun testPlaylistUrlReturnsPlaylistResult() = runTest {
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { respond("") }),
            cache = LinkCache()
        )
        val result = engine.resolveTargetUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "youtubeMusic")
        assertTrue(result is ResolutionResult.Playlist)
        val playlist = result as ResolutionResult.Playlist
        assertEquals("spotify", playlist.platform)
        assertEquals("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", playlist.originalUrl)
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

    @Test
    fun testL2ServerCacheResolution() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("/resolve")) {
                val auth = request.headers["Authorization"]
                if (auth == "Bearer valid-token") {
                    respond(
                        content = """
                        {
                            "status": "success",
                            "item": {
                                "title": "Server Song",
                                "artist": "Server Artist",
                                "isAlbum": false,
                                "links": {
                                    "youtubeMusic": "https://music.youtube.com/watch?v=server123"
                                }
                            }
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond("", status = HttpStatusCode.Unauthorized)
                }
            } else {
                respond("", status = HttpStatusCode.NotFound)
            }
        }

        val engine = SongLinkEngine(client = HttpClient(mockEngine), cache = LinkCache())
        val result = engine.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetPlatformKey = "youtubeMusic",
            isPro = true,
            authToken = "valid-token"
        )

        assertTrue(result is ResolutionResult.Success)
        val success = result as ResolutionResult.Success
        assertEquals("https://music.youtube.com/watch?v=server123", success.targetUrl)
        assertEquals("Server Song", success.title)
        assertEquals("Server Artist", success.artist)
    }

    @Test
    fun testPrefetchPopulatesCacheSilently() = runTest {
        val mockEngine = MockEngine { _ ->
            val html = """
            <!DOCTYPE html>
            <html>
            <body>
              <script id="__NEXT_DATA__" type="application/json">
              {
                "props": {
                  "pageProps": {
                    "pageData": {
                      "pageId": "s/4u7EnebtmKWzUH433cf5Qv",
                      "entityUniqueId": "SPOTIFY_SONG::4u7EnebtmKWzUH433cf5Qv",
                      "entityData": { "title": "Prefetch Song", "artistName": "Artist", "type": "song" },
                      "sections": [
                        {
                          "links": [
                            { "platform": "youtubeMusic", "url": "https://music.youtube.com/watch?v=prefetch123" }
                          ]
                        }
                      ]
                    }
                  }
                }
              }
              </script>
            </body>
            </html>
            """.trimIndent()
            respond(
                content = html,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html")
            )
        }
        val cache = LinkCache()
        val engine = SongLinkEngine(client = HttpClient(mockEngine), cache = cache)

        val inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        engine.prefetch(inputUrl = inputUrl, targetPlatformKey = "youtubeMusic")

        // Cache should be populated now
        val cached = cache.get(inputUrl, "youtubeMusic", getCurrentTimeMillis())
        assertNotNull(cached)
        assertEquals("https://music.youtube.com/watch?v=prefetch123", cached.targetUrl)
    }
}

