package de.goork.songflip.ui

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.data.DiagnosisSummary
import de.goork.songflip.data.DomainStatusInfo
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.LinkDiagnosisManager
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.components.isSupportedMusicUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val selectedTargetKey: String = SettingsRepository.DEFAULT_TARGET,
    val selectedLanguage: String = SettingsRepository.DEFAULT_LANGUAGE,
    val currentThemeMode: String = SettingsRepository.DEFAULT_THEME_MODE,
    val isCurrentlyPaused: Boolean = false,
    val pausedUntilTimestamp: Long = 0L,
    val domainStatus: DomainStatusInfo? = null,
    val linksActive: Boolean? = null,
    val diagnosisSummary: DiagnosisSummary? = null,
    val isDiagnosing: Boolean = false,
    val detectedClipboardUrl: String? = null,
    val dismissedClipboardUrl: String? = null,
    val activeMilestone: Int = 0,
    // Sheet states
    val showPauseBottomSheet: Boolean = false,
    val showSettingsBottomSheet: Boolean = false,
    val showAppLinksSetupBottomSheet: Boolean = false,
    val showProPaywall: Boolean = false,
    val initialShowPromoInPaywall: Boolean = false,
    val showPlaylistConvertSheet: String? = null,
    val showPodcastNoticeSheet: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository(application)
    private val engine = SongLinkEngine.shared

    private val _uiState = MutableStateFlow(
        MainUiState(
            selectedTargetKey = settingsRepository.targetPlatform,
            selectedLanguage = settingsRepository.appLanguage,
            currentThemeMode = settingsRepository.themeMode,
            isCurrentlyPaused = PauseHelper.isCurrentlyPaused(application),
            pausedUntilTimestamp = application.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L),
            domainStatus = DomainVerificationUtils.getDomainStatus(application),
            linksActive = DomainVerificationUtils.checkLinksEnabled(application),
            activeMilestone = settingsRepository.getActiveProNudgeMilestone()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var lastClipboardCheckTimestamp = 0L
    private var diagnosisJob: Job? = null

    init {
        refreshDiagnosis()
    }

    fun setTargetPlatform(newTarget: String) {
        settingsRepository.targetPlatform = newTarget
        _uiState.update { it.copy(selectedTargetKey = newTarget) }
        refreshDiagnosis()
    }

    fun setAppLanguage(newLang: String) {
        settingsRepository.appLanguage = newLang
        _uiState.update { it.copy(selectedLanguage = newLang) }
    }

    fun setThemeMode(newMode: String) {
        settingsRepository.themeMode = newMode
        _uiState.update { it.copy(currentThemeMode = newMode) }
    }

    fun refreshDiagnosis() {
        diagnosisJob?.cancel()
        diagnosisJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isDiagnosing = true) }
            val summary = LinkDiagnosisManager.runDiagnosis(getApplication(), _uiState.value.selectedTargetKey)
            _uiState.update { it.copy(diagnosisSummary = summary, isDiagnosing = false) }
        }
    }

    fun refreshStatusAndPauseState() {
        val context = getApplication<Application>()
        val isPaused = PauseHelper.isCurrentlyPaused(context)
        val pausedUntil = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
        val dStatus = DomainVerificationUtils.getDomainStatus(context)
        val lActive = DomainVerificationUtils.checkLinksEnabled(context)
        val milestone = settingsRepository.getActiveProNudgeMilestone()

        _uiState.update {
            it.copy(
                isCurrentlyPaused = isPaused,
                pausedUntilTimestamp = pausedUntil,
                domainStatus = dStatus,
                linksActive = lActive,
                activeMilestone = milestone
            )
        }
    }

    /**
     * Debounced clipboard check on app resume to prevent excessive privacy toasts on Android 12+.
     */
    fun checkClipboardOnResume() {
        val now = System.currentTimeMillis()
        if (now - lastClipboardCheckTimestamp < 800L) {
            return
        }
        lastClipboardCheckTimestamp = now

        if (!settingsRepository.autoClipboardDetect) {
            _uiState.update { it.copy(detectedClipboardUrl = null) }
            return
        }

        try {
            val clipboard = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val description = clipboard.primaryClipDescription
                if (description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                    description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true
                ) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString() ?: item?.uri?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = UrlUtils.extractCleanUrl(text)
                        if (clean != null && isSupportedMusicUrl(clean)) {
                            if (clean != _uiState.value.dismissedClipboardUrl) {
                                _uiState.update { it.copy(detectedClipboardUrl = clean) }
                                viewModelScope.launch(Dispatchers.IO) {
                                    engine.prefetch(clean, _uiState.value.selectedTargetKey)
                                }
                            }
                            return
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        _uiState.update { it.copy(detectedClipboardUrl = null) }
    }

    fun onIncomingSharedUrl(sharedUrl: String?) {
        if (sharedUrl != null && isSupportedMusicUrl(sharedUrl)) {
            if (UrlUtils.isPlaylistUrl(sharedUrl)) {
                openPlaylistConvert(sharedUrl)
            } else {
                _uiState.update { it.copy(detectedClipboardUrl = sharedUrl) }
                viewModelScope.launch(Dispatchers.IO) {
                    engine.prefetch(sharedUrl, _uiState.value.selectedTargetKey)
                }
            }
        }
    }

    fun dismissClipboardBanner(url: String) {
        _uiState.update { it.copy(dismissedClipboardUrl = url, detectedClipboardUrl = null) }
    }

    fun dismissMilestone(milestone: Int) {
        settingsRepository.dismissProNudgeMilestone(milestone)
        _uiState.update { it.copy(activeMilestone = 0) }
    }

    // Bottom Sheet Navigation
    fun openPauseSheet() = _uiState.update { it.copy(showPauseBottomSheet = true) }
    fun closePauseSheet() = _uiState.update { it.copy(showPauseBottomSheet = false) }

    fun openSettings() = _uiState.update { it.copy(showSettingsBottomSheet = true) }
    fun closeSettings() = _uiState.update { it.copy(showSettingsBottomSheet = false) }

    fun openAppLinksSetup() = _uiState.update { it.copy(showAppLinksSetupBottomSheet = true) }
    fun closeAppLinksSetup() = _uiState.update { it.copy(showAppLinksSetupBottomSheet = false) }

    fun openProPaywall(showPromo: Boolean = false) = _uiState.update {
        it.copy(showProPaywall = true, initialShowPromoInPaywall = showPromo)
    }
    fun closeProPaywall() = _uiState.update {
        it.copy(showProPaywall = false, initialShowPromoInPaywall = false)
    }

    fun openPlaylistConvert(url: String) = _uiState.update { it.copy(showPlaylistConvertSheet = url) }
    fun closePlaylistConvert() = _uiState.update { it.copy(showPlaylistConvertSheet = null) }

    fun openPodcastNotice(url: String) = _uiState.update { it.copy(showPodcastNoticeSheet = url) }
    fun closePodcastNotice() = _uiState.update { it.copy(showPodcastNoticeSheet = null) }
}
