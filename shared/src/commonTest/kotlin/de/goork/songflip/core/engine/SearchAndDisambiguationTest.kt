package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.ResolutionResult
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
}
