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
    fun testToNativeSpotifyUri_Intl() {
        val webUrl = "https://open.spotify.com/intl-de/track/4u7EnebtmKWzUH433cf5Qv?si=12345"
        val nativeUri = PackageUtils.toNativeSpotifyUri(webUrl)
        assertEquals("spotify:track:4u7EnebtmKWzUH433cf5Qv", nativeUri)

        val webAlbum = "https://open.spotify.com/intl-es/album/1DFixLWuPkv3KT3TnV35m3"
        assertEquals("spotify:album:1DFixLWuPkv3KT3TnV35m3", PackageUtils.toNativeSpotifyUri(webAlbum))
    }

    @Test
    fun testToNativeDeezerUri() {
        val webTrack = "https://www.deezer.com/track/9997018"
        assertEquals("deezer://www.deezer.com/track/9997018", PackageUtils.toNativeDeezerUri(webTrack))

        val localizedTrack = "https://www.deezer.com/de/track/9997018"
        assertEquals("deezer://www.deezer.com/track/9997018", PackageUtils.toNativeDeezerUri(localizedTrack))

        val webAlbum = "https://www.deezer.com/album/12345"
        assertEquals("deezer://www.deezer.com/album/12345", PackageUtils.toNativeDeezerUri(webAlbum))
    }

    @Test
    fun testToNativeTidalUri() {
        val webTrack = "https://listen.tidal.com/track/534050211"
        assertEquals("tidal://track/534050211", PackageUtils.toNativeTidalUri(webTrack))

        val browseTrack = "https://tidal.com/browse/track/534050211"
        assertEquals("tidal://track/534050211", PackageUtils.toNativeTidalUri(browseTrack))

        val localizedTrack = "https://tidal.com/de/browse/track/534050211"
        assertEquals("tidal://track/534050211", PackageUtils.toNativeTidalUri(localizedTrack))

        val webAlbum = "https://tidal.com/album/123456"
        assertEquals("tidal://album/123456", PackageUtils.toNativeTidalUri(webAlbum))
    }

    @Test
    fun testToNativeAppUri() {
        assertEquals("spotify:track:123", PackageUtils.toNativeAppUri("https://open.spotify.com/track/123", "spotify"))
        assertEquals("deezer://www.deezer.com/track/123", PackageUtils.toNativeAppUri("https://www.deezer.com/track/123", "deezer"))
        assertEquals("tidal://track/123", PackageUtils.toNativeAppUri("https://listen.tidal.com/track/123", "tidal"))
        assertEquals("https://music.youtube.com/watch?v=123", PackageUtils.toNativeAppUri("https://music.youtube.com/watch?v=123", "youtubeMusic"))
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
