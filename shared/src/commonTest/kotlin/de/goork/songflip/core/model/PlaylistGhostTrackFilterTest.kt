package de.goork.songflip.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaylistGhostTrackFilterTest {

    @Test
    fun testGhostAndAiTrackDetection() {
        val aiTrack1 = PlaylistTrackItem(title = "Neon Dreams [Suno AI]", artist = "CyberMusician", matched = true)
        val aiTrack2 = PlaylistTrackItem(title = "Cybernetic Pulse", artist = "Udio Generative", matched = true)
        val aiTrack3 = PlaylistTrackItem(title = "Sunset Vibes (AI Generated)", artist = "SynthBot", matched = true)
        val realTrack1 = PlaylistTrackItem(title = "Bohemian Rhapsody", artist = "Queen", matched = true)
        val realTrack2 = PlaylistTrackItem(title = "Tamally Maak", artist = "Amr Diab", matched = true)
        val spamGhostTrack = PlaylistTrackItem(title = "White Noise For Deep Sleep #482", artist = "Binaural Sleep Lab", matched = true)

        assertTrue(aiTrack1.isSuspectedAiOrGhostTrack())
        assertTrue(aiTrack2.isSuspectedAiOrGhostTrack())
        assertTrue(aiTrack3.isSuspectedAiOrGhostTrack())
        assertTrue(spamGhostTrack.isSuspectedAiOrGhostTrack())

        assertFalse(realTrack1.isSuspectedAiOrGhostTrack())
        assertFalse(realTrack2.isSuspectedAiOrGhostTrack())
    }

    @Test
    fun testFilterGhostAndAiTracksFromResult() {
        val tracks = listOf(
            PlaylistTrackItem(title = "Track 1", artist = "Real Artist", matched = true),
            PlaylistTrackItem(title = "AI Track [Suno]", artist = "Fake Bot", matched = true),
            PlaylistTrackItem(title = "Track 2", artist = "Real Band", matched = true),
            PlaylistTrackItem(title = "Sleep Sounds Part 12", artist = "White Noise 10 Hours", matched = true),
            PlaylistTrackItem(title = "Track 3", artist = "Another Artist", matched = true)
        )

        val initialResult = PlaylistConversionResult(
            tracks = tracks,
            totalTracks = 5,
            convertedTracks = 5,
            matchedCount = 5
        )

        val cleaned = initialResult.cleanGhostAndAiTracks()
        assertEquals(3, cleaned.tracks.size)
        assertEquals(2, cleaned.ghostTracksFiltered)
        assertEquals(3, cleaned.convertedTracks)
        assertEquals(3, cleaned.matchedCount)
        assertEquals("Track 1", cleaned.tracks[0].title)
        assertEquals("Track 2", cleaned.tracks[1].title)
        assertEquals("Track 3", cleaned.tracks[2].title)
    }

    @Test
    fun testDeduplicatedPreservesNonLatinScripts() {
        val tracks = listOf(
            PlaylistTrackItem(title = "تملي معاك", artist = "عمرو دياب", matched = true),
            PlaylistTrackItem(title = "نور العين", artist = "عمرو دياب", matched = true),
            PlaylistTrackItem(title = "봄날", artist = "방탄소년단", matched = true),
            PlaylistTrackItem(title = "Dynamite", artist = "방탄소년단", matched = true),
            PlaylistTrackItem(title = "تملي معاك", artist = "عمرو دياب", matched = true) // echtes Duplikat
        )
        val initialResult = PlaylistConversionResult(
            tracks = tracks,
            totalTracks = 5,
            convertedTracks = 5,
            matchedCount = 5
        )
        val deduped = initialResult.deduplicated()
        assertEquals(4, deduped.tracks.size)
        assertEquals(1, deduped.duplicateTracksRemoved)
    }
}
