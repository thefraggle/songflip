package de.goork.songflip.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import de.goork.songflip.R
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.data.NetworkUtils
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.components.OfflineWaitingBottomSheet
import de.goork.songflip.ui.components.PreReleaseBottomSheet
import de.goork.songflip.ui.components.QuickTargetPickerBottomSheet
import de.goork.songflip.ui.theme.SongFlipTheme
import de.goork.songflip.ui.viewmodel.RedirectUiState
import de.goork.songflip.ui.viewmodel.RedirectViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Transparent activity that silently intercepts incoming music links (or shared URLs) in the background,
 * resolves them via RedirectViewModel and launches the target player.
 */
class RedirectActivity : ComponentActivity() {

    companion object {
        const val EXTRA_FORWARDED_FROM_SONGFLIP = "de.goork.songflip.FORWARDED_FROM_SONGFLIP"
    }

    private val viewModel by viewModels<RedirectViewModel>()
    private var networkRetryJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // 0. Loop Break: If this intent was already forwarded by SongFlip, never re-intercept
        if (intent?.getBooleanExtra(EXTRA_FORWARDED_FROM_SONGFLIP, false) == true) {
            finish()
            suppressTransitionAnimation()
            return
        }

        var fallbackIncomingUri: Uri? = null
        try {
            AndroidSharedPreferencesCacheStorage.init(this)
            ProManager.init(this)
            val settingsRepository = SettingsRepository(this)

            // Extract raw input text from EXTRA_TEXT, ClipData, or Data URI
            val rawInput = when {
                Intent.ACTION_SEND == intent?.action -> {
                    intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                        ?: intent.dataString
                        ?: intent.data?.toString()
                        ?: ""
                }
                else -> intent?.dataString ?: intent?.data?.toString() ?: ""
            }

            val incomingUrl = UrlUtils.extractCleanUrl(rawInput) ?: rawInput
            if (incomingUrl.isBlank() || (!incomingUrl.startsWith("http://") && !incomingUrl.startsWith("https://"))) {
                finish()
                suppressTransitionAnimation()
                return
            }

            // 0a. Check if incoming link is SongFlip's own domain (defensive loop & invalid share guard)
            if (UrlUtils.isSongFlipUrl(incomingUrl)) {
                Toast.makeText(this, getString(R.string.share_self_link_notice), Toast.LENGTH_LONG).show()
                finish()
                suppressTransitionAnimation()
                return
            }

            val incomingUri = Uri.parse(incomingUrl)
            fallbackIncomingUri = incomingUri

            // 1. Check if SongFlip is currently paused
            if (PauseHelper.isCurrentlyPaused(this)) {
                forwardOriginalUrl(incomingUri)
                finish()
                suppressTransitionAnimation()
                return
            }

            // 1a. Check if incoming link is a playlist -> Route to interactive Playlist Converter in MainActivity
            if (UrlUtils.isPlaylistUrl(incomingUrl)) {
                val mainIntent = Intent(this, MainActivity::class.java).apply {
                    putExtra("open_playlist_url", incomingUrl)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(mainIntent)
                finish()
                suppressTransitionAnimation()
                return
            }

            val isShareAction = Intent.ACTION_SEND == intent?.action
            val hasNetwork = NetworkUtils.isNetworkAvailable(this)
            val isPro = ProManager.proState.value.isPro
            val authToken = ProManager.getAuthToken()

            // Initialize processing in ViewModel
            viewModel.processIncomingUrl(
                incomingUrl = incomingUrl,
                targetPlatform = settingsRepository.targetPlatform,
                askEveryTime = settingsRepository.askEveryTime,
                hasNetwork = hasNetwork,
                isShare = isShareAction,
                isPro = isPro,
                authToken = authToken,
                customApiUrl = settingsRepository.customApiUrl,
                customApiToken = settingsRepository.customApiToken,
                settingsRepository = settingsRepository
            )

            setContent {
                val isDark = when (settingsRepository.themeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                SongFlipTheme(darkTheme = isDark) {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    when (val state = uiState) {
                        is RedirectUiState.Idle -> {
                            // Waiting for resolution, transparent background
                        }
                        is RedirectUiState.QuickPicker -> {
                            QuickTargetPickerBottomSheet(
                                trackTitle = state.trackTitle,
                                artistName = state.artistName,
                                isResolving = state.isResolving,
                                selectedPlatformKey = state.selectedPlatformKey,
                                onPlatformSelected = { key ->
                                    viewModel.onQuickPickerPlatformSelected(
                                        platformKey = key,
                                        hasNetwork = NetworkUtils.isNetworkAvailable(this@RedirectActivity),
                                        isPro = ProManager.proState.value.isPro,
                                        authToken = ProManager.getAuthToken(),
                                        settingsRepository = settingsRepository
                                    )
                                },
                                onDismissRequest = {
                                    forwardOriginalUrl(incomingUri)
                                    finish()
                                    suppressTransitionAnimation()
                                }
                            )
                        }
                        is RedirectUiState.OfflineWaiting -> {
                            // Listen for network reconnect
                            LaunchedEffect(state.incomingUrl) {
                                networkRetryJob?.cancel()
                                networkRetryJob = lifecycleScope.launch {
                                    NetworkUtils.observeNetworkAvailability(this@RedirectActivity)
                                        .collect { isAvailable ->
                                            if (isAvailable) {
                                                networkRetryJob?.cancel()
                                                viewModel.retryOffline(
                                                    isPro = ProManager.proState.value.isPro,
                                                    authToken = ProManager.getAuthToken(),
                                                    settingsRepository = settingsRepository
                                                )
                                            }
                                        }
                                }
                            }

                            OfflineWaitingBottomSheet(
                                isRetrying = state.isRetrying,
                                onRetry = {
                                    networkRetryJob?.cancel()
                                    viewModel.retryOffline(
                                        isPro = ProManager.proState.value.isPro,
                                        authToken = ProManager.getAuthToken(),
                                        settingsRepository = settingsRepository
                                    )
                                },
                                onDismissRequest = {
                                    networkRetryJob?.cancel()
                                    forwardOriginalUrl(incomingUri)
                                    finish()
                                    suppressTransitionAnimation()
                                }
                            )
                        }
                        is RedirectUiState.PreReleaseDetected -> {
                            PreReleaseBottomSheet(
                                title = state.title,
                                artist = state.artist,
                                targetPlatformKey = state.targetPlatform,
                                onPreSaveSpotify = {
                                    val spotifyUri = state.nativeSpotifyUri ?: state.originalUrl
                                    openTargetUrl(spotifyUri, "spotify")
                                    finish()
                                    suppressTransitionAnimation()
                                },
                                onSearchTarget = {
                                    openTargetUrl(state.targetSearchUrl, state.targetPlatform)
                                    finish()
                                    suppressTransitionAnimation()
                                },
                                onDismiss = {
                                    forwardOriginalUrl(incomingUri)
                                    finish()
                                    suppressTransitionAnimation()
                                }
                            )
                        }
                        is RedirectUiState.LaunchTarget -> {
                            LaunchedEffect(state) {
                                state.feedbackText?.let {
                                    Toast.makeText(applicationContext, it, Toast.LENGTH_SHORT).show()
                                }
                                openTargetUrl(state.targetUrl, state.platform)
                                finish()
                                suppressTransitionAnimation()
                            }
                        }
                        is RedirectUiState.OpenPlaylistInMain -> {
                            LaunchedEffect(state) {
                                val mainIntent = Intent(this@RedirectActivity, MainActivity::class.java).apply {
                                    putExtra("open_playlist_url", state.playlistUrl)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                startActivity(mainIntent)
                                finish()
                                suppressTransitionAnimation()
                            }
                        }
                        is RedirectUiState.DirectShareUniversal -> {
                            LaunchedEffect(state) {
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("SongFlip Universal Link", state.shareUrl)
                                clipboard?.setPrimaryClip(clip)

                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackSharePageGenerated(target = "share_sheet_same_platform")

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, state.shareUrl)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                val chooser = Intent.createChooser(shareIntent, getString(R.string.share_universal_link)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(chooser)
                                Toast.makeText(applicationContext, getString(R.string.share_universal_link_copied), Toast.LENGTH_SHORT).show()
                                finish()
                                suppressTransitionAnimation()
                            }
                        }
                        is RedirectUiState.ForwardOriginal -> {
                            LaunchedEffect(state) {
                                if (state.showErrorToast) {
                                    val errorMsg = if (state.isPlaylistNotSupported) {
                                        getString(R.string.playlist_not_supported_toast)
                                    } else {
                                        getString(R.string.redirect_error_toast)
                                    }
                                    Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
                                }
                                forwardOriginalUrl(state.uri)
                                finish()
                                suppressTransitionAnimation()
                            }
                        }
                        is RedirectUiState.Dismiss -> {
                            LaunchedEffect(Unit) {
                                finish()
                                suppressTransitionAnimation()
                            }
                        }
                    }
                }
            }

        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            try {
                fallbackIncomingUri?.let { forwardOriginalUrl(it) }
            } catch (forwardError: Throwable) {
                // Last resort already failed; log so it shows up instead of vanishing silently.
                android.util.Log.w("RedirectActivity", "Forwarding original URL after error failed", forwardError)
            }
            finish()
            suppressTransitionAnimation()
        }
    }

    /**
     * Opens target music link directly in target player app if installed (explicit package launch),
     * completely eliminating intent disambiguation dialogs and infinite intercept loops.
     * Uses native URI schemas for Spotify, Deezer, Tidal for zero latency.
     */
    private fun openTargetUrl(rawUrl: String, targetPlatformKey: String) {
        val url = if (rawUrl.contains("music.music.youtube.com")) {
            rawUrl.replace("music.music.youtube.com", "music.youtube.com")
        } else rawUrl

        de.goork.songflip.data.ShortcutHelper.updateShortcuts(this)
        val targetPackage = PackageUtils.getInstalledPackage(this, targetPlatformKey)

        // Stage 1: Explicit target app launch (fastest, zero intent disambiguation)
        if (targetPackage != null) {
            try {
                val targetUri = Uri.parse(PackageUtils.toNativeAppUri(url, targetPlatformKey))

                val appIntent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                    setPackage(targetPackage)
                    putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(appIntent)
                return
            } catch (e: Exception) {
                // Proceed to Stage 2
            }
        }

        // When target app is missing (e.g. YouTube Music app not installed), adapt URL for web / generic fallback
        val effectiveUrl = PackageUtils.toWebFallbackUrl(url, targetPlatformKey)

        // Stage 2: Implicit player intent (dispatch to discovered music player handler excluding SongFlip & browsers)
        try {
            val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val handlers = packageManager.queryIntentActivities(genericIntent, 0)
                .map { it.activityInfo.packageName }
                .filter { it != packageName && !PackageUtils.isGenericBrowser(it) }

            if (handlers.isNotEmpty()) {
                val bestHandler = if (targetPackage != null && handlers.contains(targetPackage)) {
                    targetPackage
                } else {
                    handlers.first()
                }
                val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(effectiveUrl)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(bestHandler)
                    putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(launchIntent)
                return
            }
        } catch (ignored: Exception) {}

        // Stage 3: Safe web browser fallback
        openInBrowser(Uri.parse(effectiveUrl))
    }

    /**
     * Forwards original music URL to web browser when redirect is disabled, paused, or resolution fails.
     */
    private fun forwardOriginalUrl(uri: Uri) {
        openInBrowser(uri)
    }

    private fun openInBrowser(uri: Uri) {
        try {
            val genericWebIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }

            val browserPackage = packageManager.queryIntentActivities(genericWebIntent, 0)
                .map { it.activityInfo.packageName }
                .firstOrNull { it != packageName }

            if (browserPackage != null) {
                val targetIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(browserPackage)
                    putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                    putExtra(Browser.EXTRA_APPLICATION_ID, browserPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(targetIntent)
                return
            }

            val selectorIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_BROWSER)
            }
            val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                selector = selectorIntent
                putExtra(EXTRA_FORWARDED_FROM_SONGFLIP, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
        } catch (ignored: Exception) {}
    }

    private fun suppressTransitionAnimation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkRetryJob?.cancel()
    }
}
