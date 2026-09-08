package de.goork.songflip

import de.goork.songflip.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class TargetPlatformDetectionTest {

    @Test
    fun testDetectDefaultTargetPlatform_OnlySpotifyInstalled() {
        val installed = setOf("spotify")
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("spotify", result)
    }

    @Test
    fun testDetectDefaultTargetPlatform_SpotifyAndYouTubeMusicInstalled() {
        // User-installed Spotify is prioritized over pre-installed YouTube Music
        val installed = setOf("youtubeMusic", "spotify")
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("spotify", result)
    }

    @Test
    fun testDetectDefaultTargetPlatform_AppleMusicAndYouTubeMusicInstalled() {
        val installed = setOf("youtubeMusic", "appleMusic")
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("appleMusic", result)
    }

    @Test
    fun testDetectDefaultTargetPlatform_TidalOnly() {
        val installed = setOf("tidal")
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("tidal", result)
    }

    @Test
    fun testDetectDefaultTargetPlatform_OnlyYouTubeMusicInstalled() {
        val installed = setOf("youtubeMusic")
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("youtubeMusic", result)
    }

    @Test
    fun testDetectDefaultTargetPlatform_NoAppInstalled_FallsBackToYouTubeMusicWeb() {
        val installed = emptySet<String>()
        val result = SettingsRepository.detectDefaultTargetPlatform { installed.contains(it) }
        assertEquals("youtubeMusic", result)
    }
}
