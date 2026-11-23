package de.goork.songflip.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.goork.songflip.core.analytics.AptabaseClient
import de.goork.songflip.core.engine.PlaylistConverterEngine
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.PlaylistConversionException
import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistConversionState
import de.goork.songflip.core.model.PlaylistErrorCode
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.data.DiagnosisSummary
import de.goork.songflip.data.DomainStatusInfo
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.LinkDiagnosisManager
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivePlaylistConversion(
    val playlistUrl: String,
    val targetPlatformKey: String,
    val state: PlaylistConversionState
)

data class MainUiState(
    val targetPlatform: String = "youtubeMusic",
    val appLanguage: String = "de",
    val themeMode: String = "system",
    val isCurrentlyPaused: Boolean = false,
    val pausedUntilTimestamp: Long = 0L,
    val domainStatus: DomainStatusInfo? = null,
    val linksActive: Boolean = false,
    val diagnosisSummary: DiagnosisSummary? = null
)

internal fun isFileOrSongFlipPlaylistUrl(playlistUrl: String): Boolean =
    playlistUrl.contains("file-import", ignoreCase = true) ||
        playlistUrl.startsWith("file://", ignoreCase = true) ||
        UrlUtils.isSongFlipUrl(playlistUrl)

internal fun resolvePlaylistSourceTelemetryKey(playlistUrl: String): String = when {
    playlistUrl.contains("file-import", ignoreCase = true) ||
        playlistUrl.startsWith("file://", ignoreCase = true) -> "file"
    else -> UrlUtils.detectPlatform(playlistUrl)?.key ?: "unknown"
}

internal fun shouldSkipDuplicateConversion(
    current: ActivePlaylistConversion?,
    isJobActive: Boolean,
    playlistUrl: String,
    targetPlatformKey: String,
    isPro: Boolean,
    force: Boolean
): Boolean {
    if (force || current == null) return false
    if (current.playlistUrl != playlistUrl || current.targetPlatformKey != targetPlatformKey) return false
    if (isJobActive || current.state is PlaylistConversionState.Loading || current.state is PlaylistConversionState.Converting) {
        return true
    }
    if (current.state is PlaylistConversionState.Success) {
        val successData = (current.state as PlaylistConversionState.Success).result
        if (!isPro || !successData.isLimited) {
            return true
        }
    }
    return false
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val _uiState = MutableStateFlow(
        MainUiState(
            targetPlatform = settingsRepository.targetPlatform,
            appLanguage = settingsRepository.appLanguage,
            themeMode = settingsRepository.themeMode,
            isCurrentlyPaused = PauseHelper.isCurrentlyPaused(application),
            pausedUntilTimestamp = application.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L),
            domainStatus = DomainVerificationUtils.getDomainStatus(application),
            linksActive = DomainVerificationUtils.checkLinksEnabled(application) ?: false
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _activePlaylistConversion = MutableStateFlow<ActivePlaylistConversion?>(null)
    val activePlaylistConversion: StateFlow<ActivePlaylistConversion?> = _activePlaylistConversion.asStateFlow()
    private var activeConversionJob: kotlinx.coroutines.Job? = null

    init {
        refreshStatus()
        viewModelScope.launch {
            ProManager.proState.collect { proState ->
                if (proState.isPro) {
                    val current = _activePlaylistConversion.value
                    if (current != null && current.state is PlaylistConversionState.Success) {
                        val res = (current.state as PlaylistConversionState.Success).result
                        if (res.isLimited) {
                            startPlaylistConversion(
                                playlistUrl = current.playlistUrl,
                                targetPlatformKey = current.targetPlatformKey,
                                isPro = true,
                                force = true
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val currentPlatform = _uiState.value.targetPlatform
            val newDomainStatus = DomainVerificationUtils.getDomainStatus(app)
            val newLinksActive = DomainVerificationUtils.checkLinksEnabled(app) ?: false
            val newIsPaused = PauseHelper.isCurrentlyPaused(app)
            val newPausedUntil = app.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
            val diagnosis = LinkDiagnosisManager.runDiagnosis(app, currentPlatform)

            _uiState.update { current ->
                current.copy(
                    domainStatus = newDomainStatus,
                    linksActive = newLinksActive,
                    isCurrentlyPaused = newIsPaused,
                    pausedUntilTimestamp = newPausedUntil,
                    diagnosisSummary = diagnosis
                )
            }
        }
    }

    fun setTargetPlatform(newPlatform: String) {
        settingsRepository.targetPlatform = newPlatform
        _uiState.update { it.copy(targetPlatform = newPlatform) }
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val diagnosis = LinkDiagnosisManager.runDiagnosis(app, newPlatform)
            _uiState.update { it.copy(diagnosisSummary = diagnosis) }
        }
    }

    fun setAppLanguage(newLanguage: String) {
        settingsRepository.appLanguage = newLanguage
        _uiState.update { it.copy(appLanguage = newLanguage) }
    }

    fun setThemeMode(newTheme: String) {
        settingsRepository.themeMode = newTheme
        _uiState.update { it.copy(themeMode = newTheme) }
    }

    fun setPause(durationMillis: Long) {
        val app = getApplication<Application>()
        PauseHelper.setPause(app, durationMillis)
        refreshStatus()
    }

    fun cancelPause() {
        val app = getApplication<Application>()
        PauseHelper.resume(app)
        refreshStatus()
    }

    fun startPlaylistConversion(
        playlistUrl: String,
        targetPlatformKey: String,
        isPro: Boolean,
        force: Boolean = false
    ) {
        val detectedPlatform = UrlUtils.detectPlatform(playlistUrl)
        val isFileOrSongFlipUrl = isFileOrSongFlipPlaylistUrl(playlistUrl)
        if (!isFileOrSongFlipUrl && detectedPlatform != null && detectedPlatform.key == targetPlatformKey) {
            _activePlaylistConversion.value = ActivePlaylistConversion(
                playlistUrl = playlistUrl,
                targetPlatformKey = targetPlatformKey,
                state = PlaylistConversionState.SamePlatform
            )
            return
        }
        val sourceTelemetryKey = resolvePlaylistSourceTelemetryKey(playlistUrl)

        val current = _activePlaylistConversion.value
        if (shouldSkipDuplicateConversion(
                current = current,
                isJobActive = activeConversionJob?.isActive == true,
                playlistUrl = playlistUrl,
                targetPlatformKey = targetPlatformKey,
                isPro = isPro,
                force = force
            )
        ) {
            return
        }

        activeConversionJob?.cancel()
        _activePlaylistConversion.value = ActivePlaylistConversion(
            playlistUrl = playlistUrl,
            targetPlatformKey = targetPlatformKey,
            state = PlaylistConversionState.Loading()
        )

        activeConversionJob = viewModelScope.launch(Dispatchers.IO) {
            val maxTracksToConvert = if (isPro) {
                de.goork.songflip.core.engine.ProLimits.MAX_PRO_PLAYLIST_TRACKS
            } else {
                de.goork.songflip.core.engine.ProLimits.MAX_FREE_PLAYLIST_TRACKS
            }
            AptabaseClient.shared.trackPlaylistConversionStarted(
                sourcePlatform = sourceTelemetryKey,
                targetPlatform = targetPlatformKey,
                trackCount = maxTracksToConvert
            )

            val result = PlaylistConverterEngine.shared.convertPlaylist(
                url = playlistUrl,
                targetPlatformKey = targetPlatformKey,
                isPro = isPro,
                maxTracks = maxTracksToConvert,
                authToken = ProManager.getAuthToken(),
                forceRefresh = force
            )

            if (result.isSuccess) {
                val data = result.getOrThrow()
                _activePlaylistConversion.value = ActivePlaylistConversion(
                    playlistUrl = playlistUrl,
                    targetPlatformKey = targetPlatformKey,
                    state = PlaylistConversionState.Success(data)
                )

                // Save playlist entry into local history
                val targetUrl = data.zeroOAuthUrl ?: data.webShareUrl ?: playlistUrl
                val displayTotal = maxOf(data.totalTracks, data.originalTotalTracks)
                SongLinkEngine.shared.cache.put(
                    canonicalUrl = playlistUrl,
                    targetPlatformKey = targetPlatformKey,
                    result = ResolutionResult.Success(
                        targetUrl = targetUrl,
                        platform = "${targetPlatformKey}_playlist",
                        title = data.title.ifBlank { "Playlist" },
                        artist = getApplication<Application>().getString(
                            de.goork.songflip.R.string.history_playlist_tracks_summary,
                            data.matchedCount,
                            displayTotal
                        ),
                        isAlbum = false,
                        thumbnailUrl = data.thumbnailUrl
                    ),
                    isHistory = true
                )

                AptabaseClient.shared.trackPlaylistConversionCompleted(
                    sourcePlatform = sourceTelemetryKey,
                    targetPlatform = targetPlatformKey,
                    totalTracks = data.totalTracks,
                    resolvedTracks = data.matchedCount,
                    failedTracks = (data.totalTracks - data.matchedCount).coerceAtLeast(0),
                    isBatch = data.totalTracks > 50
                )

                AptabaseClient.shared.trackLinkFlipped(
                    target = targetPlatformKey,
                    isAlbum = false,
                    isSearch = false,
                    source = sourceTelemetryKey
                )
            } else {
                val exception = result.exceptionOrNull()
                val playlistEx = exception as? PlaylistConversionException
                val errorCode = playlistEx?.errorCode ?: PlaylistErrorCode.UNKNOWN_ERROR
                val reason = playlistEx?.reason ?: errorCode.name.lowercase()
                val errorMsg = playlistEx?.message ?: exception?.message ?: "Unknown error"

                _activePlaylistConversion.value = ActivePlaylistConversion(
                    playlistUrl = playlistUrl,
                    targetPlatformKey = targetPlatformKey,
                    state = PlaylistConversionState.Error(
                        message = errorMsg,
                        errorCode = errorCode,
                        reason = reason
                    )
                )

                AptabaseClient.shared.trackPlaylistConversionFailed(
                    sourcePlatform = sourceTelemetryKey,
                    targetPlatform = targetPlatformKey,
                    reason = reason,
                    errorCode = errorCode.name.lowercase()
                )
            }
        }
    }

    fun dismissActivePlaylistConversion() {
        _activePlaylistConversion.value = null
    }
}
