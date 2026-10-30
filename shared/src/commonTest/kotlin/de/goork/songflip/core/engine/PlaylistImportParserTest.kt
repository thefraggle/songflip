package de.goork.songflip.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaylistImportParserTest {

    @Test
    fun testParseM3u8() {
        val m3uContent = """
            #EXTM3U
            #EXTENC:UTF-8
            #PLAYLIST:My Roadtrip

            #EXTINF:248,Daft Punk - Get Lucky
            https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq

            #EXTINF:215,The Weeknd - Blinding Lights
            https://music.apple.com/album/blinding-lights/1499378108?i=1499378607

            #EXTINF:190,Queen - Bohemian Rhapsody
            music/queen_bohemian.mp3
        """.trimIndent()

        val tracks = PlaylistImportParser.parse(m3uContent, fileNameHint = "roadtrip.m3u8")
        assertEquals(3, tracks.size)

        assertEquals("Get Lucky", tracks[0].title)
        assertEquals("Daft Punk", tracks[0].artist)
        assertEquals("https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq", tracks[0].originalUrl)

        assertEquals("Blinding Lights", tracks[1].title)
        assertEquals("The Weeknd", tracks[1].artist)

        assertEquals("Bohemian Rhapsody", tracks[2].title)
        assertEquals("Queen", tracks[2].artist)
    }

    @Test
    fun testParseCsvStandard() {
        val csvContent = """
            "Index","Title","Artist","Matched","TargetUrl","SourceUrl"
            "1","Get Lucky","Daft Punk","true","https://music.youtube.com/watch?v=5NV6Rdv1a3I","https://open.spotify.com/track/69kOkLUCkxIZYexIgSG8rq"
            "2","Starboy","The Weeknd","true","https://music.youtube.com/watch?v=dqt8Z1k0oWQ",""
        """.trimIndent()

        val tracks = PlaylistImportParser.parse(csvContent, fileNameHint = "export.csv")
        assertEquals(2, tracks.size)

        assertEquals("Get Lucky", tracks[0].title)
        assertEquals("Daft Punk", tracks[0].artist)
        assertEquals("https://music.youtube.com/watch?v=5NV6Rdv1a3I", tracks[0].originalUrl)

        assertEquals("Starboy", tracks[1].title)
        assertEquals("The Weeknd", tracks[1].artist)
    }

    @Test
    fun testParseCsvAlternativeHeaders() {
        val csvContent = """
            Track Name,Artist Name(s),Album,ISRC
            "Midnight City","M83","Hurry Up, We're Dreaming","FR9W11100096"
            "One More Time","Daft Punk","Discovery","FRZ030000001"
        """.trimIndent()

        val tracks = PlaylistImportParser.parse(csvContent)
        assertEquals(2, tracks.size)

        assertEquals("Midnight City", tracks[0].title)
        assertEquals("M83", tracks[0].artist)
        assertEquals("Hurry Up, We're Dreaming", tracks[0].album)
        assertEquals("FR9W11100096", tracks[0].isrc)

        assertEquals("One More Time", tracks[1].title)
        assertEquals("Daft Punk", tracks[1].artist)
    }

    @Test
    fun testParsePlainTextTracklist() {
        val text = """
            1. Daft Punk - Around The World
            2. Justice - D.A.N.C.E.
            [3] Kavinsky - Nightcall
            4) Phoenix - 1901
        """.trimIndent()

        val tracks = PlaylistImportParser.parse(text)
        assertEquals(4, tracks.size)

        assertEquals("Around The World", tracks[0].title)
        assertEquals("Daft Punk", tracks[0].artist)

        assertEquals("D.A.N.C.E.", tracks[1].title)
        assertEquals("Justice", tracks[1].artist)

        assertEquals("Nightcall", tracks[2].title)
        assertEquals("Kavinsky", tracks[2].artist)

        assertEquals("1901", tracks[3].title)
        assertEquals("Phoenix", tracks[3].artist)
    }
}
