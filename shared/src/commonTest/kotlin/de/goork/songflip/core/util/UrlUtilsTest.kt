package de.goork.songflip.core.util

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlUtilsTest {

    @Test
    fun testSpotifyUrlNormalization() {
        val dirtyUrl = "https://open.spotify.com/track/7JRnqsOyndSyuafJxCwDXJ?si=gyfVOye7SoSdgLod0PJAew&utm_source=whatsapp&context=spotify%3Aplaylist%3A37i9dQZF1F5p3rmiWPIYgZ&rowId=d5bd0eafe40e9b29b1d3"
        val normalized = UrlUtils.normalizeUrl(dirtyUrl)
        assertEquals("https://open.spotify.com/track/7JRnqsOyndSyuafJxCwDXJ", normalized)

        val intlUrl = "https://open.spotify.com/intl-de/album/12345?si=abc"
        assertEquals("https://open.spotify.com/album/12345", UrlUtils.normalizeUrl(intlUrl))
    }

    @Test
    fun testAppleMusicUrlNormalization() {
        val dirtyTrack = "https://music.apple.com/de/album/letzter-tanz-feat-bibiza/1761769878?i=1761770183&uo=4&at=123"
        assertEquals("https://music.apple.com/de/album/letzter-tanz-feat-bibiza/1761769878?i=1761770183", UrlUtils.normalizeUrl(dirtyTrack))

        val dirtyAlbum = "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?uo=4&app=music"
        assertEquals("https://music.apple.com/de/album/bohemian-rhapsody/1440650428", UrlUtils.normalizeUrl(dirtyAlbum))
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
    }

    @Test
    fun testCleanSearchQuery() {
        assertEquals("Bohemian Rhapsody", UrlUtils.cleanSearchQuery("Bohemian Rhapsody - 2011 Remaster"))
        assertEquals("Hotel California", UrlUtils.cleanSearchQuery("Hotel California (Remastered 2013)"))
        assertEquals("Billie Jean", UrlUtils.cleanSearchQuery("Billie Jean [Single Version]"))
        assertEquals("Letzter Tanz", UrlUtils.cleanSearchQuery("Letzter Tanz (Live at Rock am Ring)"))
        assertEquals("Wenn du dumm bist", UrlUtils.cleanSearchQuery("Wenn du dumm bist"))
    }
}
