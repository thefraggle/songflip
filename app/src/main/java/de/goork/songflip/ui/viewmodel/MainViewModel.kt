package de.goork.songflip.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.goork.songflip.data.DomainStatusInfo
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.DiagnosisSummary
import de.goork.songflip.data.LinkDiagnosisManager
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        refreshStatus()
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
}
