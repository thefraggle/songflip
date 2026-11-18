package de.goork.songflip

import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistConversionState
import de.goork.songflip.ui.components.shouldAutoStartSheetConversion
import de.goork.songflip.ui.viewmodel.ActivePlaylistConversion
import de.goork.songflip.ui.viewmodel.shouldSkipDuplicateConversion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistConversionSingleFlightTest {

    @Test
    fun externallyManagedSheetNeverAutoStartsConversionEvenBeforeExternalStateArrives() {
        assertFalse(
            shouldAutoStartSheetConversion(
                hasImportedTracks = false,
                isSamePlatform = false,
                isExternallyManaged = true,
                hasExternalState = false,
                isPro = false,
                isCurrentlyLimited = false
            )
        )
    }

    @Test
    fun standaloneSheetAutoStartsOnlyWhenNoExternalStateExists() {
        assertTrue(
            shouldAutoStartSheetConversion(
                hasImportedTracks = false,
                isSamePlatform = false,
                isExternallyManaged = false,
                hasExternalState = false,
                isPro = false,
                isCurrentlyLimited = false
            )
        )
        assertFalse(
            shouldAutoStartSheetConversion(
                hasImportedTracks = false,
                isSamePlatform = false,
                isExternallyManaged = false,
                hasExternalState = true,
                isPro = false,
                isCurrentlyLimited = false
            )
        )
    }

    @Test
    fun viewModelSkipsDuplicateInFlightConversionForSameUrlAndTarget() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val current = ActivePlaylistConversion(
            playlistUrl = url,
            targetPlatformKey = "youtubeMusic",
            state = PlaylistConversionState.Loading()
        )
        assertTrue(
            shouldSkipDuplicateConversion(
                current = current,
                isJobActive = true,
                playlistUrl = url,
                targetPlatformKey = "youtubeMusic",
                isPro = false,
                force = false
            )
        )
    }

    @Test
    fun viewModelAllowsConversionWhenTargetPlatformChanges() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val current = ActivePlaylistConversion(
            playlistUrl = url,
            targetPlatformKey = "youtubeMusic",
            state = PlaylistConversionState.Loading()
        )
        assertFalse(
            shouldSkipDuplicateConversion(
                current = current,
                isJobActive = true,
                playlistUrl = url,
                targetPlatformKey = "appleMusic",
                isPro = false,
                force = false
            )
        )
    }

    @Test
    fun viewModelAllowsUpgradeReconversionWhenProUnlocksLimitedResult() {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
        val limitedResult = PlaylistConversionResult(
            title = "Test",
            sourcePlatform = "spotify",
            targetPlatform = "youtubeMusic",
            totalTracks = 5,
            convertedTracks = 5,
            matchedCount = 5,
            isLimited = true
        )
        val current = ActivePlaylistConversion(
            playlistUrl = url,
            targetPlatformKey = "youtubeMusic",
            state = PlaylistConversionState.Success(limitedResult)
        )
        assertFalse(
            shouldSkipDuplicateConversion(
                current = current,
                isJobActive = false,
                playlistUrl = url,
                targetPlatformKey = "youtubeMusic",
                isPro = true,
                force = false
            )
        )
    }
}
