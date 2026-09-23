package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformResolversTest {

    // ---------------------------------------------------------
    // SpotifyResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testSpotifyResolverDirectUrlsAndOEmbed() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath.contains("oembed")) {
                if (request.url.parameters["url"]?.contains("track/valid123") == true) {
                    respond(
                        content = """{"title": "Bohemian Rhapsody - Queen", "type": "rich"}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond("Not Found", status = HttpStatusCode.NotFound)
                }
            } else {
                respond("Error", status = HttpStatusCode.InternalServerError)
            }
        }
        val resolver = SpotifyResolver(client = HttpClient(mockEngine))
        assertEquals(MusicPlatform.SPOTIFY, resolver.platform)

        // Track and Album search URLs
        assertEquals("https://open.spotify.com/search/Queen%20Bohemian", resolver.resolveTrack("Queen Bohemian"))
        assertEquals("https://open.spotify.com/search/Queen%20A%20Night%20at%20the%20Opera", resolver.resolveAlbum("Queen A Night at the Opera"))

        // Metadata extraction
        val title = resolver.extractMetadata("https://open.spotify.com/track/valid123")
        assertEquals("Bohemian Rhapsody - Queen", title)

        // Metadata extraction failure
        val missing = resolver.extractMetadata("https://open.spotify.com/track/invalid")
        assertNull(missing)
    }

    // ---------------------------------------------------------
    // AppleMusicResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testAppleMusicResolverSearchAndLookup() = runTest {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("/search")) {
                val term = request.url.parameters["term"] ?: ""
                val entity = request.url.parameters["entity"] ?: ""
                if (entity == "musicArtist") {
                    respond(
                        content = """
                        {
                            "resultCount": 1,
                            "results": [
                                {
                                    "artistName": "Korsakow",
                                    "primaryGenreName": "Punk Rock",
                                    "artistLinkUrl": "https://music.apple.com/de/artist/korsakow/123456"
                                }
                            ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else if (entity == "album") {
                    respond(
                        content = """
                        {
                            "resultCount": 1,
                            "results": [
                                { "collectionViewUrl": "https://music.apple.com/de/album/night-at-the-opera/1440650428" }
                            ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                } else {
                    respond(
                        content = """
                        {
                            "resultCount": 1,
                            "results": [
                                { "trackViewUrl": "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?i=1440650711" }
                            ]
                        }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            } else if (path.contains("/lookup")) {
                respond(
                    content = """
                    {
                        "resultCount": 1,
                        "results": [
                            { "trackName": "Bohemian Rhapsody", "artistName": "Queen" }
                        ]
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond("{}", status = HttpStatusCode.OK)
            }
        }

        val resolver = AppleMusicResolver(client = HttpClient(mockEngine))
        assertEquals(MusicPlatform.APPLE_MUSIC, resolver.platform)

        val trackUrl = resolver.resolveTrack("Queen Bohemian Rhapsody")
        assertNotNull(trackUrl)
        assertTrue(trackUrl.contains("1440650711"))

        val albumUrl = resolver.resolveAlbum("Queen A Night at the Opera")
        assertNotNull(albumUrl)
        assertTrue(albumUrl.contains("1440650428"))

        val artistUrl = resolver.resolveArtist("Korsakow")
        assertEquals("https://music.apple.com/de/artist/korsakow/123456", artistUrl)

        val metadata = resolver.extractMetadata("https://music.apple.com/de/album/night/1440650428?i=1440650711")
        assertEquals("Queen Bohemian Rhapsody", metadata)
    }

    // ---------------------------------------------------------
    // DeezerResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testDeezerResolverSearchAndArtistMatching() = runTest {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("/search/album")) {
                respond(
                    content = """
                    {
                        "data": [
                            { "link": "https://www.deezer.com/album/999111" }
                        ]
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (path.contains("/search/artist")) {
                respond(
                    content = """
                    {
                        "data": [
                            { "name": "Korsakow", "nb_fan": 120, "link": "https://www.deezer.com/artist/199943" },
                            { "name": "Nikolai Rimsky-Korsakov", "nb_fan": 50000, "link": "https://www.deezer.com/artist/9999" }
                        ]
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """
                    {
                        "data": [
                            { "link": "https://www.deezer.com/track/9997018" }
                        ]
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val resolver = DeezerResolver(client = HttpClient(mockEngine))
        assertEquals(MusicPlatform.DEEZER, resolver.platform)

        val track = resolver.resolveTrack("Queen Bohemian Rhapsody")
        assertEquals("https://www.deezer.com/track/9997018", track)

        val album = resolver.resolveAlbum("Queen A Night at the Opera")
        assertEquals("https://www.deezer.com/album/999111", album)

        val artist = resolver.resolveArtist("Korsakow")
        assertEquals("https://www.deezer.com/artist/199943", artist)
    }

    // ---------------------------------------------------------
    // TidalResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testTidalResolverSearchAndArtist() = runTest {
        val mockEngine = MockEngine { request ->
            val query = request.url.parameters["query"] ?: ""
            if (query.contains("ArtistQuery")) {
                respond(
                    content = """
                    {
                        "artists": {
                            "items": [
                                { "id": "3529079", "name": "ArtistQuery", "popularity": 50 }
                            ]
                        }
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (query.contains("AlbumQuery")) {
                respond(
                    content = """
                    {
                        "albums": {
                            "items": [
                                { "id": "12345678" }
                            ]
                        }
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """
                    {
                        "tracks": {
                            "items": [
                                { "id": "87654321" }
                            ]
                        }
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val resolver = TidalResolver(client = HttpClient(mockEngine))
        assertEquals(MusicPlatform.TIDAL, resolver.platform)

        val track = resolver.resolveTrack("TrackQuery")
        assertEquals("https://tidal.com/browse/track/87654321", track)

        val album = resolver.resolveAlbum("AlbumQuery")
        assertEquals("https://tidal.com/browse/album/12345678", album)

        val artist = resolver.resolveArtist("ArtistQuery")
        assertEquals("https://tidal.com/artist/3529079", artist)
    }

    // ---------------------------------------------------------
    // YouTubeMusicResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testYouTubeMusicResolverInnertubeAndScraping() = runTest {
        val mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            if (path.contains("youtubei/v1/search")) {
                respond(
                    content = """{"videoId":"dQw4w9WgXcQ","playlistId":"OLAK5uy_customPlaylist123"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else if (path.contains("results")) {
                val minifiedHtml = """{"contents":{"channelRenderer":{"channelId":"UC27AmlvyR6SrC3mHyV5lu-A","title":{"simpleText":"KORSAKOW - Topic"}}}}"""
                respond(minifiedHtml, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, "text/html"))
            } else {
                respond("{}", status = HttpStatusCode.OK)
            }
        }

        val resolver = YouTubeMusicResolver(client = HttpClient(mockEngine))
        assertEquals(MusicPlatform.YOUTUBE_MUSIC, resolver.platform)

        val track = resolver.resolveTrack("Rick Astley")
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", track)

        val album = resolver.resolveAlbum("Rick Astley Album")
        assertEquals("https://music.youtube.com/playlist?list=OLAK5uy_customPlaylist123", album)

        val artist = resolver.resolveArtist("KORSAKOW")
        assertEquals("https://music.youtube.com/channel/UC27AmlvyR6SrC3mHyV5lu-A", artist)
    }

    // ---------------------------------------------------------
    // SongLinkApiResolver Tests
    // ---------------------------------------------------------
    @Test
    fun testSongLinkApiResolverSuccessAndErrorCases() = runTest {
        val mockEngine = MockEngine { request ->
            val urlParam = request.url.parameters["url"] ?: ""
            if (urlParam.contains("404")) {
                respond("Not Found", status = HttpStatusCode.NotFound)
            } else if (urlParam.contains("noTarget")) {
                respond(
                    content = """
                    {
                        "linksByPlatform": {
                            "deezer": { "url": "https://deezer.com/track/123", "entityUniqueId": "DEEZER::123" }
                        },
                        "entitiesByUniqueId": {
                            "DEEZER::123": { "title": "Song", "artistName": "Artist" }
                        }
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """
                    {
                        "linksByPlatform": {
                            "spotify": {
                                "url": "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
                                "entityUniqueId": "SPOTIFY::4u7EnebtmKWzUH433cf5Qv"
                            }
                        },
                        "entitiesByUniqueId": {
                            "SPOTIFY::4u7EnebtmKWzUH433cf5Qv": {
                                "title": "Bohemian Rhapsody",
                                "artistName": "Queen",
                                "isAlbum": false
                            }
                        }
                    }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        val resolver = SongLinkApiResolver(client = HttpClient(mockEngine))

        // Success Case
        val success = resolver.resolve(
            canonicalUrl = "https://music.youtube.com/watch?v=123",
            targetPlatformKey = "spotify"
        )
        assertTrue(success is ResolutionResult.Success)
        val data = success as ResolutionResult.Success
        assertEquals("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv", data.targetUrl)
        assertEquals("Bohemian Rhapsody", data.title)
        assertEquals("Queen", data.artist)
        assertEquals("spotify:track:4u7EnebtmKWzUH433cf5Qv", data.nativeAppUri)

        // Missing Target in Response
        val missingTarget = resolver.resolve(
            canonicalUrl = "https://music.youtube.com/watch?v=noTarget",
            targetPlatformKey = "spotify"
        )
        assertTrue(missingTarget is ResolutionResult.Error)
        assertTrue((missingTarget as ResolutionResult.Error).message.contains("not found"))

        // HTTP 404 Error
        val httpError = resolver.resolve(
            canonicalUrl = "https://music.youtube.com/watch?v=404",
            targetPlatformKey = "spotify"
        )
        assertTrue(httpError is ResolutionResult.Error)
        assertTrue((httpError as ResolutionResult.Error).message.contains("404"))
    }
}
