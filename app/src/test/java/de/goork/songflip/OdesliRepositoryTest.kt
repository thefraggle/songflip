package de.goork.songflip

import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OdesliRepositoryTest {

    private val repository = OdesliRepository()

    @Before
    fun setUp() {
        LinkCacheManager.clear()
    }

    @Test
    fun testInvalidUrlReturnsError() = runBlocking {
        val result = repository.resolveTargetUrl("just random text without url", "youtubeMusic")
        assertTrue(result is OdesliResult.Error)
        assertEquals("No valid URL found in input", (result as OdesliResult.Error).message)
    }

    @Test
    fun testSpotifyUrlDirectPlaybackResolutionToYouTubeMusic() = runBlocking {
        val spotifyUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        val result = repository.resolveTargetUrl(spotifyUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected direct watch URL for instant playback, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAppleMusicToYouTubeMusicDirectPlay() = runBlocking {
        val appleUrl = "https://music.apple.com/de/album/bohemian-rhapsody/1440650428?i=1440650711"
        val result = repository.resolveTargetUrl(appleUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAppleMusicSongFormatDirectPlay() = runBlocking {
        val appleSongUrl = "https://music.apple.com/de/song/bohemian-rhapsody/1440650711"
        val result = repository.resolveTargetUrl(appleSongUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testDeezerToYouTubeMusicDirectPlay() = runBlocking {
        val deezerUrl = "https://www.deezer.com/track/9997018"
        val result = repository.resolveTargetUrl(deezerUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testTidalToYouTubeMusicDirectPlay() = runBlocking {
        val tidalUrl = "https://tidal.com/browse/track/557454"
        val result = repository.resolveTargetUrl(tidalUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testAmazonMusicToYouTubeMusicDirectPlay() = runBlocking {
        val amazonUrl = "https://music.amazon.de/albums/B004G92QE4?trackAsin=B004G8Z8UO"
        val result = repository.resolveTargetUrl(amazonUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
    }

    @Test
    fun testEngstSpotifyTrackResolvesCorrectly() = runBlocking {
        val engstUrl = "https://open.spotify.com/track/7JRnqsOyndSyuafJxCwDXJ?si=gyfVOye7SoSdgLod0PJAew&utm_source=whatsapp&context=spotify%3Aplaylist%3A37i9dQZF1F5p3rmiWPIYgZ"
        val result = repository.resolveTargetUrl(engstUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
        assertFalse("Expected track, not album", success.isAlbum)
    }

    @Test
    fun testLetzterTanzSpotifyTrackResolvesCorrectly() = runBlocking {
        val letzterTanzUrl = "https://open.spotify.com/track/5zV9lK1r4EaEWxtlLdVM73?si=xbZIM2mNQLWYg8CSC1FKvA"
        val result = repository.resolveTargetUrl(letzterTanzUrl, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch or search link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/"))
        assertFalse("Expected track, not album", success.isAlbum)
    }

    @Test
    fun testAppleMusicTrackWithAlbumUrlResolvesAsSong() = runBlocking {
        val appleSongWithAlbum = "https://music.apple.com/de/album/dont-look-back-in-anger-remastered/1525933483?i=1525933492"
        val result = repository.resolveTargetUrl(appleSongWithAlbum, "youtubeMusic")
        assertTrue("Expected Success, got: $result", result is OdesliResult.Success)
        val success = result as OdesliResult.Success
        assertTrue("Expected YouTube Music watch link, got: ${success.targetUrl}",
            success.targetUrl.contains("music.youtube.com/watch?v="))
        assertFalse("Must be recognized as a single song, not an album", success.isAlbum)
    }

    @Test
    fun testL1CacheHitReturnsInstantly() = runBlocking {
        val testUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv"
        val firstResult = repository.resolveTargetUrl(testUrl, "youtubeMusic")
        assertTrue(firstResult is OdesliResult.Success)
        val firstSuccess = firstResult as OdesliResult.Success

        // Second call should hit L1 Cache
        val secondResult = repository.resolveTargetUrl(testUrl, "youtubeMusic")
        assertTrue(secondResult is OdesliResult.Success)
        val secondSuccess = secondResult as OdesliResult.Success
        assertEquals(firstSuccess.targetUrl, secondSuccess.targetUrl)
        assertEquals(firstSuccess.platform, secondSuccess.platform)
    }
}
