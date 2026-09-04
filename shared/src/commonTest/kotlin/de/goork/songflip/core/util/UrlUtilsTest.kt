package de.goork.songflip.core.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UrlUtilsTest {

    @Test
    fun testSpotifyUrlNormalization() {
        val dirtyUrl = "https://open.spotify.com/track/7JRnqsOyndSyuafJxCwDXJ?si=gyfVOye7SoSdgLod0PJAew&utm_source=whatsapp&context=spotify%3Aplaylist%3A37i9dQZF1F5p3rmiWPIYgZ&rowId=d5bd0eafe40e9b29b1d3"
        val normalized = UrlUtils.normalizeUrl(dirtyUrl)
        assertEquals("https://open.spotify.com/track/7JRnqsOyndSyuafJxCwDXJ", normalized)

        val intlUrl = "https://open.spotify.com/intl-de/album/12345?si=abc"
        assertEquals("https://open.spotify.com/album/12345", UrlUtils.normalizeUrl(intlUrl))

        val artistUrl = "https://open.spotify.com/artist/0OdUWJ0sBjDrqHygGUXeCF?si=xyz"
        assertEquals("https://open.spotify.com/artist/0OdUWJ0sBjDrqHygGUXeCF", UrlUtils.normalizeUrl(artistUrl))
    }

    @Test
    fun testAppleMusicUrlNormalization() {
        val dirtyTrack = "https://music.apple.com/de/album/letzter-tanz-feat-bibiza/1761769878?i=1761770183&uo=4&at=123"
        assertEquals("https://music.apple.com/de/album/letzter-tanz-feat-bibiza/1761769878?i=1761770183", UrlUtils.normalizeUrl(dirtyTrack))

        val dirtyAlbum = "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?uo=4&app=music"
        assertEquals("https://music.apple.com/de/album/bohemian-rhapsody/1440650428", UrlUtils.normalizeUrl(dirtyAlbum))

        val songUrl = "https://music.apple.com/de/song/cruel-summer/1468058171?uo=4"
        assertEquals("https://music.apple.com/de/song/cruel-summer/1468058171", UrlUtils.normalizeUrl(songUrl))
    }

    @Test
    fun testYouTubeUrlNormalization() {
        val dirtyYt = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&si=123&feature=shared"
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", UrlUtils.normalizeUrl(dirtyYt))

        val dirtyYtMusic = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=123"
        assertEquals("https://music.youtube.com/watch?v=dQw4w9WgXcQ", UrlUtils.normalizeUrl(dirtyYtMusic))

        val dirtyShort = "https://youtu.be/dQw4w9WgXcQ?si=123"
        assertEquals("https://youtu.be/dQw4w9WgXcQ", UrlUtils.normalizeUrl(dirtyShort))
    }

    @Test
    fun testDeezerUrlNormalization() {
        val dirtyDeezer = "https://www.deezer.com/de/track/3506388961?utm_source=whatsapp"
        assertEquals("https://www.deezer.com/track/3506388961", UrlUtils.normalizeUrl(dirtyDeezer))

        val dirtyAlbum = "https://www.deezer.com/en/album/123456?utm_campaign=share"
        assertEquals("https://www.deezer.com/album/123456", UrlUtils.normalizeUrl(dirtyAlbum))
    }

    @Test
    fun testAmazonAndTidalUrlNormalization() {
        val dirtyAmazonTrack = "https://music.amazon.de/albums/B004G92QE4?trackAsin=B004G8Z8UO&ref=dm_sh_cp"
        assertEquals("https://music.amazon.de/albums/B004G92QE4?trackAsin=B004G8Z8UO", UrlUtils.normalizeUrl(dirtyAmazonTrack))

        val dirtyAmazonAlbum = "https://music.amazon.de/albums/B004G92QE4?ref=dm_sh_cp"
        assertEquals("https://music.amazon.de/albums/B004G92QE4", UrlUtils.normalizeUrl(dirtyAmazonAlbum))

        val dirtyTidal = "https://listen.tidal.com/track/196435445?ref=share"
        assertEquals("https://tidal.com/browse/track/196435445", UrlUtils.normalizeUrl(dirtyTidal))
    }

    @Test
    fun testCleanSearchQuery() {
        assertEquals("Bohemian Rhapsody", UrlUtils.cleanSearchQuery("Bohemian Rhapsody - 2011 Remaster"))
        assertEquals("Hotel California", UrlUtils.cleanSearchQuery("Hotel California (Remastered 2013)"))
        assertEquals("Billie Jean", UrlUtils.cleanSearchQuery("Billie Jean [Single Version]"))
        assertEquals("Letzter Tanz", UrlUtils.cleanSearchQuery("Letzter Tanz (Live at Rock am Ring)"))
        assertEquals("Wenn du dumm bist", UrlUtils.cleanSearchQuery("Wenn du dumm bist"))
        assertEquals("In The End", UrlUtils.cleanSearchQuery("In The End (Album Version)"))
        assertEquals("Du Hast", UrlUtils.cleanSearchQuery("Du Hast - Radio Edit"))
    }

    @Test
    fun testExtractCleanUrl() {
        assertEquals(
            "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            UrlUtils.extractCleanUrl("Hör dir das an: https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv! Voll gut!")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.extractCleanUrl("(Link: https://music.youtube.com/watch?v=dQw4w9WgXcQ)")
        )
        assertEquals(
            "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            UrlUtils.extractCleanUrl("<https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv>")
        )
        assertNull(UrlUtils.extractCleanUrl("Hier ist kein Link vorhanden"))
    }

    @Test
    fun testShortLinkDetection() {
        assertTrue(UrlUtils.isShortLinkDomain("https://spotify.link/AbCdEf"))
        assertTrue(UrlUtils.isShortLinkDomain("https://amzn.to/12345"))
        assertTrue(UrlUtils.isShortLinkDomain("https://a.co/d/12345"))
        assertTrue(UrlUtils.isShortLinkDomain("https://deezer.page.link/xyz"))
        assertTrue(UrlUtils.isShortLinkDomain("https://apple.co/abc"))
        assertFalse(UrlUtils.isShortLinkDomain("https://open.spotify.com/track/123"))
        assertFalse(UrlUtils.isShortLinkDomain("https://music.youtube.com/watch?v=123"))
    }

    @Test
    fun testContentTypeDetection() {
        assertTrue(UrlUtils.isPlaylistUrl("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertTrue(UrlUtils.isPlaylistUrl("https://music.apple.com/de/playlist/heavy-metal/pl.u-12345"))
        assertTrue(UrlUtils.isPlaylistUrl("https://music.youtube.com/playlist?list=PL12345"))
        assertFalse(UrlUtils.isPlaylistUrl("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"))

        assertTrue(UrlUtils.isAlbumUrl("https://open.spotify.com/album/1DFixLWuPkv3KT3TnV35m3"))
        assertTrue(UrlUtils.isAlbumUrl("https://music.apple.com/de/album/a-night-at-the-opera/1440650428"))
        assertFalse(UrlUtils.isAlbumUrl("https://music.apple.com/de/album/a-night-at-the-opera/1440650428?i=1440650711"))

        assertTrue(UrlUtils.isSearchUrl("https://open.spotify.com/search/Queen"))
        assertTrue(UrlUtils.isSearchUrl("https://music.apple.com/de/search?term=queen"))
    }

    @Test
    fun testSearchQueryExtractionAndBuilding() {
        val spotifySearch = UrlUtils.extractSearchQuery("https://open.spotify.com/search/Queen%20Bohemian%20Rhapsody")
        assertEquals("Queen Bohemian Rhapsody", spotifySearch)

        val appleSearch = UrlUtils.extractSearchQuery("https://music.apple.com/de/search?term=Queen%20Bohemian")
        assertEquals("Queen Bohemian", appleSearch)

        val builtYt = UrlUtils.buildSearchUrl("Queen Bohemian", "youtubeMusic")
        assertEquals("https://music.youtube.com/search?q=Queen%20Bohemian", builtYt)

        val builtSpotify = UrlUtils.buildSearchUrl("Queen Bohemian", "spotify")
        assertEquals("https://open.spotify.com/search/Queen%20Bohemian", builtSpotify)
    }

    @Test
    fun testNativeAppUriConversion() {
        // Spotify
        assertEquals("spotify:track:4u7EnebtmKWzUH433cf5Qv", UrlUtils.toNativeAppUri("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv", "spotify"))
        assertEquals("spotify:track:4u7EnebtmKWzUH433cf5Qv", UrlUtils.toNativeAppUri("https://open.spotify.com/intl-de/track/4u7EnebtmKWzUH433cf5Qv?si=123", "spotify"))
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", UrlUtils.toNativeAppUri("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", "spotify"))

        // Deezer
        assertEquals("deezer://www.deezer.com/track/12345", UrlUtils.toNativeAppUri("https://www.deezer.com/track/12345", "deezer"))
        assertEquals("deezer://www.deezer.com/track/12345", UrlUtils.toNativeAppUri("https://www.deezer.com/de/track/12345?utm_source=test", "deezer"))
        assertEquals("deezer://www.deezer.com/album/67890", UrlUtils.toNativeAppUri("https://www.deezer.com/fr/album/67890", "deezer"))

        // Tidal
        assertEquals("tidal://track/12345", UrlUtils.toNativeAppUri("https://listen.tidal.com/track/12345", "tidal"))
        assertEquals("tidal://track/12345", UrlUtils.toNativeAppUri("https://tidal.com/browse/track/12345", "tidal"))
        assertEquals("tidal://track/12345", UrlUtils.toNativeAppUri("https://tidal.com/de/browse/track/12345", "tidal"))

        // Apple Music
        assertEquals("music://music.apple.com/de/album/test/123", UrlUtils.toNativeAppUri("https://music.apple.com/de/album/test/123", "appleMusic"))
        assertEquals("music://music.apple.com/de/album/test/123", UrlUtils.toNativeAppUri("https://geo.music.apple.com/de/album/test/123", "appleMusic"))

        // Amazon Music
        assertEquals("amznmp3://music.amazon.com/albums/B004G92QE4", UrlUtils.toNativeAppUri("https://music.amazon.de/albums/B004G92QE4", "amazonMusic"))
        assertEquals("amznmp3://music.amazon.com/albums/B004G92QE4", UrlUtils.toNativeAppUri("https://music.amazon.co.uk/albums/B004G92QE4", "amazonMusic"))

        // YouTube Music
        assertEquals("youtubemusic://music.youtube.com/watch?v=dQw4w9WgXcQ", UrlUtils.toNativeAppUri("https://music.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic"))
        assertEquals("youtubemusic://music.youtube.com/watch?v=dQw4w9WgXcQ", UrlUtils.toNativeAppUri("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic"))
        assertEquals("youtubemusic://music.youtube.com/watch?v=dQw4w9WgXcQ", UrlUtils.toNativeAppUri("https://music.music.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic"))
    }

    @Test
    fun testFormatTargetUrlYouTubeMusic() {
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://m.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://music.music.youtube.com/watch?v=dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            UrlUtils.formatTargetUrl("https://youtu.be/dQw4w9WgXcQ", "youtubeMusic")
        )
        assertEquals(
            "https://music.youtube.com/playlist?list=PL123",
            UrlUtils.formatTargetUrl("https://www.youtube.com/playlist?list=PL123", "youtubeMusic")
        )
    }
}

