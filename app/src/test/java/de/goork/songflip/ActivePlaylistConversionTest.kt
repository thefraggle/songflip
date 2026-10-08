package de.goork.songflip

import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistConversionState
import de.goork.songflip.core.model.PlaylistErrorCode
import de.goork.songflip.ui.viewmodel.ActivePlaylistConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePlaylistConversionTest {

    @Test
    fun testActivePlaylistConversionLoadingState() {
        val conversion = ActivePlaylistConversion(
            playlistUrl = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            targetPlatformKey = "youtubeMusic",
            state = PlaylistConversionState.Loading("Loading tracks...")
        )
        assertEquals("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M", conversion.playlistUrl)
        assertEquals("youtubeMusic", conversion.targetPlatformKey)
        assertTrue(conversion.state is PlaylistConversionState.Loading)
    }

    @Test
    fun testActivePlaylistConversionSuccessState() {
        val mockResult = PlaylistConversionResult(
            title = "Test Hits",
            sourcePlatform = "spotify",
            targetPlatform = "appleMusic",
            totalTracks = 25,
            convertedTracks = 25,
            matchedCount = 24,
            isLimited = false
        )
        val conversion = ActivePlaylistConversion(
            playlistUrl = "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            targetPlatformKey = "appleMusic",
            state = PlaylistConversionState.Success(mockResult)
        )
        val state = conversion.state as PlaylistConversionState.Success
        assertEquals("Test Hits", state.result.title)
        assertEquals(24, state.result.matchedCount)
        assertEquals(25, state.result.totalTracks)
    }

    @Test
    fun testActivePlaylistConversionErrorState() {
        val conversion = ActivePlaylistConversion(
            playlistUrl = "https://open.spotify.com/playlist/private123",
            targetPlatformKey = "youtubeMusic",
            state = PlaylistConversionState.Error(
                message = "Private playlist",
                errorCode = PlaylistErrorCode.PRIVATE_OR_RESTRICTED,
                reason = "private_playlist"
            )
        )
        val state = conversion.state as PlaylistConversionState.Error
        assertEquals(PlaylistErrorCode.PRIVATE_OR_RESTRICTED, state.errorCode)
        assertEquals("private_playlist", state.reason)
    }

    @Test
    fun testActivePlaylistConversionPropertyDeclarationOrder() {
        val fields = de.goork.songflip.ui.viewmodel.MainViewModel::class.java.declaredFields.map { it.name }
        val activeFieldIdx = fields.indexOf("_activePlaylistConversion")
        val activeFlowIdx = fields.indexOf("activePlaylistConversion")
        val uiStateIdx = fields.indexOf("_uiState")

        assertTrue("Field _uiState must be declared in MainViewModel", uiStateIdx >= 0)
        assertTrue("Field _activePlaylistConversion must be declared in MainViewModel", activeFieldIdx >= 0)
        assertTrue("Field activePlaylistConversion must be declared in MainViewModel", activeFlowIdx >= 0)
        assertTrue(
            "_activePlaylistConversion must be declared near the top alongside _uiState before init block",
            activeFieldIdx < fields.size && activeFieldIdx in (uiStateIdx - 2)..(uiStateIdx + 3)
        )
    }
}

