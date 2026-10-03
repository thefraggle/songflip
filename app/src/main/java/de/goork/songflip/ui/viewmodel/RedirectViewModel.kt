package de.goork.songflip.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.goork.songflip.core.analytics.FlipErrorClassifier
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

sealed interface RedirectUiState {
    object Idle : RedirectUiState

    data class QuickPicker(
        val incomingUrl: String,
        val trackTitle: String? = null,
        val artistName: String? = null,
        val isResolving: Boolean = true,
        val selectedPlatformKey: String? = null
    ) : RedirectUiState

    data class OfflineWaiting(
        val incomingUrl: String,
        val targetPlatform: String,
        val isRetrying: Boolean = false
    ) : RedirectUiState

    data class LaunchTarget(
        val targetUrl: String,
        val platform: String,
        val isAlbum: Boolean = false,
        val nativeAppUri: String? = null,
        val feedbackText: String? = null
    ) : RedirectUiState

    data class OpenPlaylistInMain(
        val playlistUrl: String
    ) : RedirectUiState

    data class DirectShareUniversal(
        val shareUrl: String
    ) : RedirectUiState

    data class ForwardOriginal(
        val uri: Uri,
        val showErrorToast: Boolean = false,
        val isPlaylistNotSupported: Boolean = false
    ) : RedirectUiState

    object Dismiss : RedirectUiState
}

class RedirectViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<RedirectUiState>(RedirectUiState.Idle)
    val uiState: StateFlow<RedirectUiState> = _uiState.asStateFlow()

    private var currentIncomingUrl: String = ""
    private var currentTargetPlatform: String = ""
    private var currentCustomApiUrl: String = ""
    private var currentCustomApiToken: String = ""
    private var isShareAction: Boolean = false

    fun processIncomingUrl(
        incomingUrl: String,
        targetPlatform: String,
        askEveryTime: Boolean,
        hasNetwork: Boolean,
        isShare: Boolean,
        isPro: Boolean,
        authToken: String = "",
        customApiUrl: String = "",
        customApiToken: String = "",
        settingsRepository: SettingsRepository? = null
    ) {
        currentIncomingUrl = incomingUrl
        currentTargetPlatform = targetPlatform
        currentCustomApiUrl = customApiUrl
        currentCustomApiToken = customApiToken
        isShareAction = isShare

        if (askEveryTime) {
            _uiState.value = RedirectUiState.QuickPicker(
                incomingUrl = incomingUrl,
                isResolving = true
            )
            loadQuickPickerMetadata(incomingUrl)
            return
        }

        executeRedirectCheck(
            incomingUrl = incomingUrl,
            targetPlatform = targetPlatform,
            hasNetwork = hasNetwork,
            isShare = isShare,
            isPro = isPro,
            authToken = authToken,
            settingsRepository = settingsRepository
        )
    }

    private fun loadQuickPickerMetadata(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val info = SongLinkEngine.shared.extractTrackInfo(url)
                withContext(Dispatchers.Main) {
                    if (_uiState.value is RedirectUiState.QuickPicker) {
                        var title: String? = null
                        var artist: String? = null
                        if (info != null) {
                            val parts = info.split(" - ", limit = 2)
                            if (parts.size == 2) {
                                artist = parts[0].trim()
                                title = parts[1].trim()
                            } else {
                                title = info
                            }
                        }
                        _uiState.update { current ->
                            if (current is RedirectUiState.QuickPicker) {
                                current.copy(
                                    trackTitle = title,
                                    artistName = artist,
                                    isResolving = false
                                )
                            } else current
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                withContext(Dispatchers.Main) {
                    _uiState.update { current ->
                        if (current is RedirectUiState.QuickPicker) {
                            current.copy(isResolving = false)
                        } else current
                    }
                }
            }
        }
    }

    fun onQuickPickerPlatformSelected(
        platformKey: String,
        hasNetwork: Boolean,
        isPro: Boolean,
        authToken: String = "",
        settingsRepository: SettingsRepository? = null
    ) {
        currentTargetPlatform = platformKey
        _uiState.update { current ->
            if (current is RedirectUiState.QuickPicker) {
                current.copy(selectedPlatformKey = platformKey)
            } else current
        }

        executeRedirectCheck(
            incomingUrl = currentIncomingUrl,
            targetPlatform = platformKey,
            hasNetwork = hasNetwork,
            isShare = isShareAction,
            isPro = isPro,
            authToken = authToken,
            settingsRepository = settingsRepository
        )
    }

    private fun executeRedirectCheck(
        incomingUrl: String,
        targetPlatform: String,
        hasNetwork: Boolean,
        isShare: Boolean,
        isPro: Boolean,
        authToken: String,
        settingsRepository: SettingsRepository?
    ) {
        val incomingPlatform = UrlUtils.detectPlatform(incomingUrl)
        val isSamePlatform = when (targetPlatform) {
            "spotify" -> incomingPlatform == MusicPlatform.SPOTIFY
            "appleMusic" -> incomingPlatform == MusicPlatform.APPLE_MUSIC
            "youtubeMusic" -> incomingPlatform == MusicPlatform.YOUTUBE_MUSIC
            "deezer" -> incomingPlatform == MusicPlatform.DEEZER
            "tidal" -> incomingPlatform == MusicPlatform.TIDAL
            "amazonMusic" -> incomingPlatform == MusicPlatform.AMAZON_MUSIC
            "soundcloud" -> incomingPlatform == MusicPlatform.SOUNDCLOUD
            "bandcamp" -> incomingPlatform == MusicPlatform.BANDCAMP
            else -> false
        }

        if (isSamePlatform) {
            if (isShare) {
                if (isPro) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val canonical = if (UrlUtils.isShortLinkDomain(incomingUrl)) {
                            SongLinkEngine.shared.resolveCanonicalUrl(incomingUrl)
                        } else {
                            incomingUrl
                        }
                        val shareUrl = ProManager.getUniversalWebShareUrl(canonical)
                        ProManager.warmupUniversalShare(canonical)
                        _uiState.value = RedirectUiState.DirectShareUniversal(shareUrl)
                    }
                    return
                } else {
                    _uiState.value = RedirectUiState.ForwardOriginal(Uri.parse(incomingUrl))
                    return
                }
            }

            val targetDisplayName = PackageUtils.getPlatformDisplayName(targetPlatform)
            _uiState.value = RedirectUiState.LaunchTarget(
                targetUrl = incomingUrl,
                platform = targetPlatform,
                feedbackText = "🎵 ➔ $targetDisplayName"
            )
            return
        }

        // Asynchronous cache lookup
        viewModelScope.launch(Dispatchers.IO) {
            val isCached = SongLinkEngine.shared.cache.get(incomingUrl, targetPlatform) != null
            if (!hasNetwork && !isCached) {
                withContext(Dispatchers.Main) {
                    _uiState.value = RedirectUiState.OfflineWaiting(
                        incomingUrl = incomingUrl,
                        targetPlatform = targetPlatform,
                        isRetrying = false
                    )
                }
            } else {
                performResolution(incomingUrl, targetPlatform, isPro, authToken, settingsRepository)
            }
        }
    }

    fun retryOffline(isPro: Boolean, authToken: String = "", settingsRepository: SettingsRepository? = null) {
        _uiState.update { current ->
            if (current is RedirectUiState.OfflineWaiting) {
                current.copy(isRetrying = true)
            } else current
        }

        viewModelScope.launch(Dispatchers.IO) {
            performResolution(currentIncomingUrl, currentTargetPlatform, isPro, authToken, settingsRepository)
        }
    }

    private suspend fun performResolution(
        incomingUrl: String,
        targetPlatform: String,
        isPro: Boolean,
        authToken: String,
        settingsRepository: SettingsRepository?
    ) {
        val targetDisplayName = PackageUtils.getPlatformDisplayName(targetPlatform)
        try {
            val result = withTimeoutOrNull(12000L) {
                SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl = incomingUrl,
                    targetPlatformKey = targetPlatform,
                    customApiUrl = currentCustomApiUrl,
                    customApiToken = currentCustomApiToken,
                    isPro = isPro,
                    authToken = authToken
                )
            }

            withContext(Dispatchers.Main) {
                when (result) {
                    is ResolutionResult.Success -> {
                        val feedback = when {
                            !result.artist.isNullOrBlank() && !result.title.isNullOrBlank() -> {
                                "🎵 ${result.artist} – ${result.title} ➔ $targetDisplayName"
                            }
                            !result.title.isNullOrBlank() -> {
                                "🎵 ${result.title} ➔ $targetDisplayName"
                            }
                            else -> "🎵 ➔ $targetDisplayName"
                        }
                        settingsRepository?.incrementSuccessfulFlips()
                        SongLinkEngine.shared.cache.markAsHistory(incomingUrl, targetPlatform)
                        UrlUtils.extractCleanUrl(incomingUrl)?.let { clean ->
                            SongLinkEngine.shared.cache.markAsHistory(UrlUtils.normalizeUrl(clean), targetPlatform)
                        }

                        val sourcePlatformKey = UrlUtils.detectPlatform(incomingUrl)?.key ?: "unknown"
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipped(
                            target = targetPlatform,
                            isAlbum = result.isAlbum,
                            isSearch = result.isSearchFallback,
                            source = sourcePlatformKey
                        )

                        _uiState.value = RedirectUiState.LaunchTarget(
                            targetUrl = result.targetUrl,
                            platform = result.platform,
                            isAlbum = result.isAlbum,
                            nativeAppUri = result.nativeAppUri,
                            feedbackText = feedback
                        )
                    }
                    is ResolutionResult.Podcast -> {
                        val feedback = when {
                            !result.showTitle.isNullOrBlank() && !result.episodeTitle.isNullOrBlank() -> {
                                "🎙️ ${result.showTitle} – ${result.episodeTitle} ➔ $targetDisplayName"
                            }
                            !result.showTitle.isNullOrBlank() -> {
                                "🎙️ ${result.showTitle} ➔ $targetDisplayName"
                            }
                            else -> "🎙️ ➔ $targetDisplayName"
                        }
                        settingsRepository?.incrementSuccessfulFlips()
                        SongLinkEngine.shared.cache.markAsHistory(incomingUrl, targetPlatform)

                        val sourcePlatformKey = UrlUtils.detectPlatform(incomingUrl)?.key ?: "unknown"
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPodcastFlipped(
                            source = sourcePlatformKey,
                            target = targetPlatform,
                            isDeepSearch = result.isDeepSearch
                        )

                        _uiState.value = RedirectUiState.LaunchTarget(
                            targetUrl = result.targetUrl,
                            platform = result.platform,
                            isAlbum = false,
                            nativeAppUri = result.nativeAppUri,
                            feedbackText = feedback
                        )
                    }
                    is ResolutionResult.Playlist -> {
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPlaylistRouted(target = targetPlatform)
                        _uiState.value = RedirectUiState.OpenPlaylistInMain(incomingUrl)
                    }
                    is ResolutionResult.PodcastOrAudiobook -> {
                        if (result.isAudiobook) {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackAudiobookIntercepted(targetPlatform)
                        } else {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackPodcastIntercepted(targetPlatform)
                        }
                        _uiState.value = RedirectUiState.ForwardOriginal(Uri.parse(incomingUrl))
                    }
                    else -> {
                        val sourceDomain = UrlUtils.extractDomain(incomingUrl)
                        val sourcePlatform = UrlUtils.detectPlatform(incomingUrl)?.key ?: "unknown"
                        val (reason, errorReason) = when {
                            result is ResolutionResult.Error -> result.message to (result.errorReason ?: result.message)
                            result == null -> "timeout" to "timeout"
                            else -> "not_found" to "not_found"
                        }
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipFailed(
                            target = targetPlatform,
                            reason = reason,
                            sourceDomain = sourceDomain,
                            sourcePlatform = sourcePlatform,
                            errorReason = errorReason
                        )
                        _uiState.value = RedirectUiState.ForwardOriginal(
                            uri = Uri.parse(incomingUrl),
                            showErrorToast = true,
                            isPlaylistNotSupported = (result is ResolutionResult.Error && result.message == "PLAYLIST_NOT_SUPPORTED")
                        )
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val sourceDomain = UrlUtils.extractDomain(incomingUrl)
            val sourcePlatform = UrlUtils.detectPlatform(incomingUrl)?.key ?: "unknown"
            // No t::class.simpleName here: R8 renames classes in release builds ("f0", #59).
            val errorReason = when (t) {
                is java.net.SocketTimeoutException, is java.net.ConnectException -> "socket_timeout"
                is java.net.UnknownHostException -> "unknown_host"
                is javax.net.ssl.SSLException -> "ssl_error"
                else -> FlipErrorClassifier.classify(t)
            }

            de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipFailed(
                target = targetPlatform,
                reason = errorReason,
                sourceDomain = sourceDomain,
                sourcePlatform = sourcePlatform,
                errorReason = errorReason,
                errorDetail = FlipErrorClassifier.detail(t)
            )
            withContext(Dispatchers.Main) {
                _uiState.value = RedirectUiState.ForwardOriginal(
                    uri = Uri.parse(incomingUrl),
                    showErrorToast = true
                )
            }
        }
    }

    fun onDismiss() {
        _uiState.value = RedirectUiState.Dismiss
    }
}
