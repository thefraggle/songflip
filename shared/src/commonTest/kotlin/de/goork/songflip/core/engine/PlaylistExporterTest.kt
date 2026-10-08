package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistTrackItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistExporterTest {

    private val sampleTracks = listOf(
        PlaylistTrackItem(
            title = "Bohemian Rhapsody",
            artist = "Queen",
            matched = true,
            targetUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetId = "4u7EnebtmKWzUH433cf5Qv",
            sourceUrl = "https://deezer.com/track/123"
        ),
        PlaylistTrackItem(
            title = "Starboy \"Hit\"",
            artist = "The Weeknd, Daft Punk",
            matched = true,
            targetUrl = "https://open.spotify.com/track/7MXVkk9YM5IZxh0WSlVIxF",
            targetId = "7MXVkk9YM5IZxh0WSlVIxF",
            sourceUrl = "https://deezer.com/track/456"
        ),
        PlaylistTrackItem(
            title = "Unmatched Track",
            artist = "Indie Band",
            matched = false,
            targetUrl = null,
            targetId = null,
            sourceUrl = "https://deezer.com/track/789"
        )
    )

    @Test
    fun testExportToM3u8() {
        val m3u8 = PlaylistExporter.exportToM3u8("My Test Playlist", sampleTracks)

        assertTrue(m3u8.startsWith("#EXTM3U\n#EXTENC:UTF-8\n#PLAYLIST:My Test Playlist"))
        assertTrue(m3u8.contains("#EXTINF:-1,Queen - Bohemian Rhapsody\nhttps://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"))
        assertTrue(m3u8.contains("#EXTINF:-1,The Weeknd, Daft Punk - Starboy \"Hit\""))
        assertTrue(m3u8.contains("#EXTINF:-1,Indie Band - Unmatched Track\nhttps://deezer.com/track/789"))
    }

    @Test
    fun testExportToCsv() {
        val csv = PlaylistExporter.exportToCsv("My Test Playlist", sampleTracks)

        val lines = csv.trim().split("\n")
        assertEquals(4, lines.size)
        assertEquals("\"Index\",\"Title\",\"Artist\",\"Matched\",\"TargetUrl\",\"SourceUrl\"", lines[0])

        // Verify CSV escaping of quotes
        assertTrue(lines[2].contains("\"Starboy \"\"Hit\"\"\""))
        assertTrue(lines[1].contains("\"Bohemian Rhapsody\""))
        assertTrue(lines[3].contains("\"false\""))
    }

    @Test
    fun testCalculateChunksSplitsProperly() {
        val manyTracks = (1..125).map { i ->
            PlaylistTrackItem(
                title = "Track $i",
                artist = "Artist $i",
                matched = i % 2 == 0,
                targetId = "spotifyId$i".padEnd(22, '0'),
                targetUrl = "https://open.spotify.com/track/${"id$i".padEnd(22, 'x')}"
            )
        }

        val chunks = PlaylistExporter.calculateChunks(
            tracks = manyTracks,
            chunkSize = 50,
            targetPlatform = "spotify",
            playlistTitle = "Mega Playlist"
        )

        assertEquals(3, chunks.size)

        // Chunk 1: 1-50
        assertEquals(1, chunks[0].partIndex)
        assertEquals(1, chunks[0].rangeStart)
        assertEquals(50, chunks[0].rangeEnd)
        assertEquals(50, chunks[0].totalTracks)
        assertEquals(25, chunks[0].matchedCount)
        assertTrue(chunks[0].zeroOAuthUrl?.startsWith("spotify:trackset:") == true)

        // Chunk 2: 51-100
        assertEquals(2, chunks[1].partIndex)
        assertEquals(51, chunks[1].rangeStart)
        assertEquals(100, chunks[1].rangeEnd)
        assertEquals(50, chunks[1].totalTracks)

        // Chunk 3: 101-125
        assertEquals(3, chunks[2].partIndex)
        assertEquals(101, chunks[2].rangeStart)
        assertEquals(125, chunks[2].rangeEnd)
        assertEquals(25, chunks[2].totalTracks)
    }
}
