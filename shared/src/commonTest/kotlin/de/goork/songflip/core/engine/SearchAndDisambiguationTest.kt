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

class SearchAndDisambiguationTest {

    @Test
    fun testLinkCacheUpdateTargetUrl() = runTest {
        val cache = LinkCache()
        val canonicalUrl = "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"
        val initialResult = ResolutionResult.Success(
            targetUrl = "https://music.youtube.com/watch?v=initial",
            platform = "youtubeMusic",
            title = "Never Gonna Give You Up",
            artist = "Rick Astley"
        )
        cache.put(canonicalUrl, "youtubeMusic", initialResult)

        val historyBefore = cache.getHistoryEntries()
        assertEquals(1, historyBefore.size)
        assertEquals("https://music.youtube.com/watch?v=initial", historyBefore[0].targetUrl)

        val updated = cache.updateTargetUrl(
            cacheKey = historyBefore[0].cacheKey,
            newTargetUrl = "https://music.youtube.com/watch?v=corrected_version"
        )
        assertTrue(updated)

        val historyAfter = cache.getHistoryEntries()
        assertEquals(1, historyAfter.size)
        assertEquals("https://music.youtube.com/watch?v=corrected_version", historyAfter[0].targetUrl)
    }

    @Test
    fun testCatalogSearchModel() {
        val track = SearchTrackResult(
            id = "12345",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera",
            coverUrl = "https://example.com/cover.jpg",
            previewUrl = "https://example.com/preview.m4a",
            durationMs = 354000L,
            sourcePlatform = "appleMusic",
            sourceUrl = "https://music.apple.com/track/12345"
        )
        assertEquals("Bohemian Rhapsody", track.title)
        assertEquals("Queen", track.artist)
        assertEquals(354000L, track.durationMs)
    }

    @Test
    fun testDisambiguationCandidateModel() {
        val candidate = DisambiguationCandidate(
            id = "cand_1",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy (Deluxe)",
            durationMs = 230000L,
            targetUrl = "https://open.spotify.com/track/cand_1",
            isCurrentMatch = true
        )
        assertTrue(candidate.isCurrentMatch)
        assertEquals("Starboy (Deluxe)", candidate.album)
    }

    @Test
    fun testDisambiguationEngineBuildsCorrectPlatformUrls() = runTest {
        val mockEngine = MockEngine { request ->
            val host = request.url.host
            if (host.contains("qobuz.com")) {
                respond(
                    content = """{"tracks":{"items":[{"id":777888,"title":"Bohemian Rhapsody","performer":{"name":"Queen"}}]}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            } else {
                respond(
                    content = """{"data":[{"id":1,"title":"Bohemian Rhapsody","link":"https://www.deezer.com/track/1","artist":{"name":"Queen"}}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }
        val engine = DisambiguationEngine(client = HttpClient(mockEngine))

        val qobuzCandidates = engine.getCandidates("Bohemian Rhapsody", "Queen", "qobuz").getOrThrow()
        assertEquals("https://open.qobuz.com/track/777888", qobuzCandidates.single().targetUrl)

        val soundcloudCandidates = engine.getCandidates("Bohemian Rhapsody", "Queen", "soundcloud").getOrThrow()
        assertTrue(soundcloudCandidates.single().targetUrl.startsWith("https://soundcloud.com/search?q="))

        val beatportCandidates = engine.getCandidates("Bohemian Rhapsody", "Queen", "beatport").getOrThrow()
        assertTrue(beatportCandidates.single().targetUrl.startsWith("https://www.beatport.com/search?q="))
    }

    @Test
    fun testCatalogSearchEngineDeduplicatesCompilationsAndRemasters() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                      "resultCount": 5,
                      "results": [
                        {"trackId": 101, "artistName": "Madonna", "trackName": "Hung Up", "collectionName": "Celebration (Deluxe Version)", "trackViewUrl": "https://music.apple.com/track/101"},
                        {"trackId": 102, "artistName": "Madonna", "trackName": "Hung Up", "collectionName": "Confessions on a Dance Floor", "trackViewUrl": "https://music.apple.com/track/102"},
                        {"trackId": 103, "artistName": "Madonna", "trackName": "Like a Prayer (2009 Remaster)", "collectionName": "Celebration", "trackViewUrl": "https://music.apple.com/track/103"},
                        {"trackId": 104, "artistName": "Madonna", "trackName": "Like a Prayer", "collectionName": "Like a Prayer", "trackViewUrl": "https://music.apple.com/track/104"},
                        {"trackId": 105, "artistName": "The Beatles", "trackName": "Lady Madonna", "collectionName": "Past Masters", "trackViewUrl": "https://music.apple.com/track/105"}
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val engine = CatalogSearchEngine(client = HttpClient(mockEngine))
        val results = engine.search("Madonna", limit = 35).getOrThrow()

        // 5 raw results collapse into 3 distinct songs
        assertEquals(3, results.size)
        // Prefers studio album "Confessions on a Dance Floor" over "Celebration" compilation
        assertEquals("102", results[0].id)
        assertEquals("Confessions on a Dance Floor", results[0].album)
        // Prefers original "Like a Prayer" studio release over "2009 Remaster" on "Celebration"
        assertEquals("104", results[1].id)
        assertEquals("Like a Prayer", results[1].album)
        // Preserves different artist with "Madonna" in song title
        assertEquals("105", results[2].id)
        assertEquals("The Beatles", results[2].artist)
    }

    @Test
    fun testContextSensitiveVideoTitleCleaning() {
        val test1 = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("Daft Punk - Get Lucky (Official Music Video)")
        assertEquals("Daft Punk - Get Lucky", test1)

        val test2 = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("The Weeknd - Blinding Lights [4K 60FPS] (Lyrics)")
        assertEquals("The Weeknd - Blinding Lights", test2)

        val test3 = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("Dua Lipa - Levitating (feat. DaBaby) [Official Visualizer]")
        assertEquals("Dua Lipa - Levitating (feat. DaBaby)", test3)

        val test4 = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("Stromae - Papaoutai (Clip Officiel)")
        assertEquals("Stromae - Papaoutai", test4)

        // Preserves genuine musical remixes and live attributes (#208)
        val testRemix = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("The Weeknd - Blinding Lights (Major Lazer Remix) [Official Video]")
        assertEquals("The Weeknd - Blinding Lights (Major Lazer Remix)", testRemix)

        val testLive = de.goork.songflip.core.util.UrlUtils.cleanVideoTitle("Queen - Bohemian Rhapsody (Live at Wembley) [Official Video]")
        assertEquals("Queen - Bohemian Rhapsody (Live at Wembley)", testLive)
    }

    @Test
    fun testFindBestCandidateProtectsOriginalFromUnrequestedRemix() {
        data class MockItem(val title: String, val artist: String, val version: String? = null)

        val candidates = listOf(
            MockItem(title = "Blinding Lights (Major Lazer Remix)", artist = "The Weeknd", version = "Remix"),
            MockItem(title = "Blinding Lights", artist = "The Weeknd", version = null)
        )

        // 1. Searching for original studio track MUST choose non-remix track (#208)
        val bestOriginal = de.goork.songflip.core.engine.resolvers.ResolverUtils.findBestCandidate(
            candidates = candidates,
            query = "The Weeknd Blinding Lights",
            getArtist = { it.artist },
            getTitle = { it.title },
            getVersion = { it.version }
        )
        assertNotNull(bestOriginal)
        assertEquals("Blinding Lights", bestOriginal.title)

        // 2. Searching explicitly for remix MUST choose remix track (#208)
        val bestRemix = de.goork.songflip.core.engine.resolvers.ResolverUtils.findBestCandidate(
            candidates = candidates,
            query = "The Weeknd Blinding Lights Remix",
            getArtist = { it.artist },
            getTitle = { it.title },
            getVersion = { it.version }
        )
        assertNotNull(bestRemix)
        assertEquals("Blinding Lights (Major Lazer Remix)", bestRemix.title)
    }
}
