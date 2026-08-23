package de.goork.songflip

import de.goork.songflip.data.PackageUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class PackageUtilsTest {

    @Test
    fun testToNativeSpotifyUri_Track() {
        val webUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv?si=12345"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:track:4u7EnebtmKWzUH433cf5Qv", nativeUri)
    }

    @Test
    fun testToNativeSpotifyUri_Album() {
        val webUrl = "https://open.spotify.com/album/1DFixLWuPkv3KT3TnV35m3"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:album:1DFixLWuPkv3KT3TnV35m3", nativeUri)
    }

    @Test
    fun testToNativeSpotifyUri_Artist() {
        val webUrl = "https://open.spotify.com/artist/1dfeR4HaWDbWqFHLkxsg1d"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:artist:1dfeR4HaWDbWqFHLkxsg1d", nativeUri)
    }

    @Test
    fun testToNativeSpotifyUri_Playlist() {
        val webUrl = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M", nativeUri)
    }

    @Test
    fun testToNativeSpotifyUri_Search() {
        val webUrl = "https://open.spotify.com/search/Queen%20Bohemian"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:search:Queen%20Bohemian", nativeUri)
    }

    @Test
    fun testToNativeSpotifyUri_AlreadyNative() {
        val nativeUrl = "spotify:track:4u7EnebtmKWzUH433cf5Qv"
        val result = PackageUtils.toNativeSpotifyUri(nativeUrl)
        assertEquals(nativeUrl, result)
    }

    @Test
    fun testGetPlatformDisplayName() {
        assertEquals("YouTube Music", PackageUtils.getPlatformDisplayName("youtubeMusic"))
        assertEquals("Apple Music", PackageUtils.getPlatformDisplayName("appleMusic"))
        assertEquals("Spotify", PackageUtils.getPlatformDisplayName("spotify"))
        assertEquals("Tidal", PackageUtils.getPlatformDisplayName("tidal"))
        assertEquals("Deezer", PackageUtils.getPlatformDisplayName("deezer"))
        assertEquals("Amazon Music", PackageUtils.getPlatformDisplayName("amazonMusic"))
    }
}
