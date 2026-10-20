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

    @Test
    fun testForeignUnsupportedDomainFastFailsWithoutNetworkCall() = runTest {
        val engine = SongLinkEngine(
            client = HttpClient(MockEngine { error("Upstream network must not be called for foreign domains (#132)") }),
            cache = LinkCache()
        )

        val tiktokResult = engine.resolveTargetUrl("https://vm.tiktok.com/ZGe12345/", "spotify")
        assertTrue(tiktokResult is ResolutionResult.UnsupportedEntity)
        val unsupported = tiktokResult as ResolutionResult.UnsupportedEntity
        assertEquals("unsupported_domain", unsupported.entityType)
        assertEquals("https://vm.tiktok.com/ZGe12345/", unsupported.originalUrl)

        val instaResult = engine.resolveTargetUrl("https://www.instagram.com/reel/C123456/?igsh=abc", "appleMusic")
        assertTrue(instaResult is ResolutionResult.UnsupportedEntity)
        assertEquals("unsupported_domain", (instaResult as ResolutionResult.UnsupportedEntity).entityType)
    }

    @Test
    fun testAudiophileCandidateScoringPrioritizesHiResMasterOverRadioEditAndCovers() {
        val query = "Daft Punk Get Lucky"

        val studioHiResScore = de.goork.songflip.core.engine.resolvers.ResolverUtils.scoreAudiophileCandidate(
            query = query,
            candidateArtist = "Daft Punk",
            candidateTitle = "Get Lucky",
            candidateVersion = null,
            albumTitle = "Random Access Memories",
            albumArtist = "Daft Punk",
            isHiRes = true,
            isLossless = true,
            isSpatialOrAtmos = true,
            bitDepth = 24,
            samplingRate = 88.2,
            albumTrackCount = 13
        )

        val radioEditScore = de.goork.songflip.core.engine.resolvers.ResolverUtils.scoreAudiophileCandidate(
            query = query,
            candidateArtist = "Daft Punk",
            candidateTitle = "Get Lucky",
            candidateVersion = "Radio Edit",
            albumTitle = "Get Lucky (Radio Edit)",
            albumArtist = "Daft Punk",
            isHiRes = false,
            isLossless = true,
            bitDepth = 16,
            samplingRate = 44.1,
            albumTrackCount = 1
        )

        val compilationScore = de.goork.songflip.core.engine.resolvers.ResolverUtils.scoreAudiophileCandidate(
            query = query,
            candidateArtist = "Daft Punk",
            candidateTitle = "Get Lucky",
            candidateVersion = null,
            albumTitle = "Summer Hits Top 100",
            albumArtist = "Various Artists",
            isHiRes = false,
            isLossless = true
        )

        val coverBandScore = de.goork.songflip.core.engine.resolvers.ResolverUtils.scoreAudiophileCandidate(
            query = query,
            candidateArtist = "Chiptune Radio",
            candidateTitle = "Get Lucky (Originally performed by Daft Punk)",
            albumTitle = "2014 Smash Bits",
            isHiRes = false
        )

        assertTrue(studioHiResScore > radioEditScore, "Studio Hi-Res Master ($studioHiResScore) must score higher than Radio Edit ($radioEditScore)")
        assertTrue(radioEditScore > compilationScore, "Artist single ($radioEditScore) must score higher than Various Artists compilation ($compilationScore)")
        assertTrue(compilationScore > coverBandScore, "Real artist track ($compilationScore) must score higher than tribute/cover ($coverBandScore)")
    }

    @Test
    fun testAudiophileModeUpgradesToHiResMasterOnTidal() = runTest {
        val mockEngine = MockEngine { request ->
            val urlStr = request.url.toString()
            when {
                urlStr.contains("song.link") -> {
                    val html = """
                    <!DOCTYPE html>
                    <html><body>
                    <script id="__NEXT_DATA__" type="application/json">
                    {
                      "props": {
                        "pageProps": {
                          "pageData": {
                            "pageId": "s/getlucky",
                            "entityUniqueId": "SPOTIFY_SONG::getlucky",
                            "entityData": { "title": "Get Lucky (Radio Edit)", "artistName": "Daft Punk", "type": "song" },
                            "sections": [
                              {
                                "links": [
                                  { "platform": "tidal", "url": "https://tidal.com/browse/track/11111_radio_edit" }
                                ]
                              }
                            ]
                          }
                        }
                      }
                    }
                    </script>
                    </body></html>
                    """.trimIndent()
                    respond(html, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "text/html"))
                }
                urlStr.contains("tidal.com/v1/search") -> {
                    val tidalJson = """
                    {
                      "tracks": {
                        "items": [
                          {
                            "id": "11111_radio_edit",
                            "title": "Get Lucky",
                            "version": "Radio Edit",
                            "audioQuality": "LOSSLESS",
                            "audioModes": ["STEREO"],
                            "mediaMetadata": { "tags": ["LOSSLESS"] },
                            "album": { "title": "Get Lucky (Radio Edit)" },
                            "artist": { "name": "Daft Punk" },
                            "popularity": 85
                          },
                          {
                            "id": "99999_studio_hires",
                            "title": "Get Lucky",
                            "version": null,
                            "audioQuality": "HI_RES_LOSSLESS",
                            "audioModes": ["STEREO", "DOLBY_ATMOS"],
                            "mediaMetadata": { "tags": ["LOSSLESS", "HIRES_LOSSLESS", "DOLBY_ATMOS"] },
                            "album": { "title": "Random Access Memories" },
                            "artist": { "name": "Daft Punk" },
                            "popularity": 90
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                    respond(tidalJson, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                }
                else -> respond("", status = HttpStatusCode.OK)
            }
        }

        val engine = SongLinkEngine(client = HttpClient(mockEngine), cache = LinkCache())

        // 1. Standard mode (audiophileMode = false): uses standard Odesli match (11111_radio_edit)
        val standardRes = engine.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzHof",
            targetPlatformKey = "tidal",
            isPro = true,
            forceRefresh = true,
            audiophileMode = false
        )
        assertTrue(standardRes is ResolutionResult.Success)
        assertTrue((standardRes as ResolutionResult.Success).targetUrl.contains("11111_radio_edit"))

        // 2. Audiophile mode (isPro = true, audiophileMode = true): upgrades to Hi-Res / Atmos Studio Master (99999_studio_hires)
        val audiophileRes = engine.resolveTargetUrl(
            inputUrl = "https://open.spotify.com/track/2Foc5Q5nqNiosCNqttzHof",
            targetPlatformKey = "tidal",
            isPro = true,
            forceRefresh = true,
            audiophileMode = true
        )
        assertTrue(audiophileRes is ResolutionResult.Success)
        assertTrue((audiophileRes as ResolutionResult.Success).targetUrl.contains("99999_studio_hires"))
    }
}

