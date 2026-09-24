package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistTrackItem
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

class PlaylistConverterEngineTest {

    @Test
    fun testConvertPlaylistSuccess() = runTest {
        val mockEngine = MockEngine { _ ->
            val jsonResponse = """
            {
                "status": "success",
                "playlistId": "abc1234567",
                "title": "Summer Vibes",
                "sourcePlatform": "spotify",
                "targetPlatform": "youtubeMusic",
                "thumbnailUrl": "https://example.com/cover.jpg",
                "totalTracks": 2,
                "convertedTracks": 2,
                "matchedCount": 2,
                "zeroOAuthUrl": "https://music.youtube.com/watch_videos?video_ids=vid1,vid2",
                "webShareUrl": "https://songflip.link/p/abc1234567",
                "isLimited": false,
                "tracks": [
                    {
                        "title": "Song 1",
                        "artist": "Artist 1",
                        "matched": true,
                        "targetId": "12345678901",
                        "targetUrl": "https://music.youtube.com/watch?v=12345678901"
                    },
                    {
                        "title": "Song 2",
                        "artist": "Artist 2",
                        "matched": true,
                        "targetId": "23456789012",
                        "targetUrl": "https://music.youtube.com/watch?v=23456789012"
                    }
                ]
            }
            """.trimIndent()
            respond(
                content = jsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val engine = PlaylistConverterEngine(client = HttpClient(mockEngine))
        val result = engine.convertPlaylist(
            url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            targetPlatformKey = "youtubeMusic",
            isPro = true
        )

        assertTrue(result.isSuccess)
        val data = result.getOrThrow()
        assertEquals("abc1234567", data.playlistId)
        assertEquals("Summer Vibes", data.title)
        assertEquals(2, data.matchedCount)
        assertEquals(2, data.tracks.size)
        assertEquals("https://music.youtube.com/watch_videos?video_ids=vid1,vid2", data.zeroOAuthUrl)
    }

    @Test
    fun testConvertPlaylistFailure() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"error": "EXTRACTION_FAILED"}""",
                status = HttpStatusCode.UnprocessableEntity,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val engine = PlaylistConverterEngine(client = HttpClient(mockEngine))
        val result = engine.convertPlaylist(
            url = "https://open.spotify.com/playlist/invalid",
            targetPlatformKey = "youtubeMusic"
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun testBuildZeroOAuthUrlYouTubeMusic() {
        val engine = PlaylistConverterEngine()
        val tracks = listOf(
            PlaylistTrackItem(title = "Track 1", artist = "Artist 1", matched = true, targetId = "12345678901"),
            PlaylistTrackItem(title = "Track 2", artist = "Artist 2", matched = false, targetId = null),
            PlaylistTrackItem(title = "Track 3", artist = "Artist 3", matched = true, targetId = "abcdefghijk")
        )

        val url = engine.buildZeroOAuthUrl("My Mix", "youtubeMusic", tracks)
        assertNotNull(url)
        assertEquals("https://www.youtube.com/watch_videos?video_ids=12345678901,abcdefghijk", url)
    }

    @Test
    fun testBuildZeroOAuthUrlSpotify() {
        val engine = PlaylistConverterEngine()
        val tracks = listOf(
            PlaylistTrackItem(title = "Track 1", artist = "Artist 1", matched = true, targetId = "trackId123"),
            PlaylistTrackItem(title = "Track 2", artist = "Artist 2", matched = true, targetId = "trackId456")
        )

        val url = engine.buildZeroOAuthUrl("Party Hits", "spotify", tracks)
        assertNotNull(url)
        assertEquals("spotify:trackset:Party%20Hits:trackId123,trackId456", url)
    }

    @Test
    fun testBuildZeroOAuthUrlEmptyMatchedReturnsNull() {
        val engine = PlaylistConverterEngine()
        val tracks = listOf(
            PlaylistTrackItem(title = "Track 1", artist = "Artist 1", matched = false)
        )

        val url = engine.buildZeroOAuthUrl("Empty", "youtubeMusic", tracks)
        assertNull(url)
    }

    @Test
    fun testParseErrorPayloadUnsupportedPlatform() {
        val engine = PlaylistConverterEngine()
        val json = """{"error": "UNSUPPORTED_PLATFORM", "code": "UNSUPPORTED_PLATFORM", "reason": "unsupported_platform", "message": "Plattform für Playlists noch nicht unterstützt"}"""
        val ex = engine.parseErrorPayload(json, 422) as de.goork.songflip.core.model.PlaylistConversionException
        assertEquals(de.goork.songflip.core.model.PlaylistErrorCode.UNSUPPORTED_PLATFORM, ex.errorCode)
        assertEquals("unsupported_platform", ex.reason)
        assertEquals("Plattform für Playlists noch nicht unterstützt", ex.message)
    }

    @Test
    fun testParseErrorPayloadPrivateOrRestricted() {
        val engine = PlaylistConverterEngine()
        val json = """{"error": "PRIVATE_OR_RESTRICTED", "code": "PRIVATE_OR_RESTRICTED", "reason": "private_playlist", "message": "Private oder personalisierte Playlist"}"""
        val ex = engine.parseErrorPayload(json, 422) as de.goork.songflip.core.model.PlaylistConversionException
        assertEquals(de.goork.songflip.core.model.PlaylistErrorCode.PRIVATE_OR_RESTRICTED, ex.errorCode)
        assertEquals("private_playlist", ex.reason)
    }

    @Test
    fun testParseErrorPayloadTimeoutAndRateLimit() {
        val engine = PlaylistConverterEngine()
        val timeoutJson = """{"error": "UPSTREAM_TIMEOUT", "code": "UPSTREAM_TIMEOUT", "reason": "timeout", "message": "Timeout"}"""
        val timeoutEx = engine.parseErrorPayload(timeoutJson, 504) as de.goork.songflip.core.model.PlaylistConversionException
        assertEquals(de.goork.songflip.core.model.PlaylistErrorCode.UPSTREAM_TIMEOUT, timeoutEx.errorCode)

        val rateLimitJson = """{"error": "RATE_LIMITED", "code": "RATE_LIMITED", "reason": "rate_limited", "message": "Too many requests"}"""
        val rateLimitEx = engine.parseErrorPayload(rateLimitJson, 429) as de.goork.songflip.core.model.PlaylistConversionException
        assertEquals(de.goork.songflip.core.model.PlaylistErrorCode.RATE_LIMITED, rateLimitEx.errorCode)
    }
}
