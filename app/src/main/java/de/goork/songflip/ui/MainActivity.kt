package de.goork.songflip.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.goork.songflip.R
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.ui.components.*
import de.goork.songflip.ui.theme.*

class MainActivity : AppCompatActivity() {

    private var initialShowPauseSheet = false
    private val windowFocusState = mutableStateOf(false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocusState.value = hasFocus
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LinkCacheManager.init(this)
        initialShowPauseSheet = intent?.getBooleanExtra("show_pause_sheet", false) == true
        handleShortcutIntent(intent)

        if (savedInstanceState == null) {
            val settingsRepo = SettingsRepository(this)
            de.goork.songflip.core.analytics.AptabaseClient.shared.trackAppLaunched(
                platform = "Android",
                language = settingsRepo.appLanguage
            )
        }

        setContent {
            val settingsRepository = remember { SettingsRepository(this) }
            var currentThemeMode by remember { mutableStateOf(settingsRepository.themeMode) }

            val darkTheme = when (currentThemeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            SongFlipTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        initialShowPause = initialShowPauseSheet,
                        currentThemeMode = currentThemeMode,
                        isWindowFocused = windowFocusState.value,
                        onThemeModeSelected = { newMode -> currentThemeMode = newMode }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)
        if (intent.getBooleanExtra("show_pause_sheet", false)) {
            setContent {
                val settingsRepository = remember { SettingsRepository(this) }
                var currentThemeMode by remember { mutableStateOf(settingsRepository.themeMode) }

                val darkTheme = when (currentThemeMode) {
                    "light" -> false
                    "dark" -> true
                    else -> isSystemInDarkTheme()
                }

                SongFlipTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(
                            initialShowPause = true,
                            currentThemeMode = currentThemeMode,
                            isWindowFocused = windowFocusState.value,
                            onThemeModeSelected = { newMode -> currentThemeMode = newMode }
                        )
                    }
                }
            }
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            de.goork.songflip.data.ShortcutHelper.ACTION_PAUSE_1H -> {
                PauseHelper.setPause(this, 60 * 60 * 1000L)
                Toast.makeText(this, getString(R.string.shortcut_pause_1h_toast), Toast.LENGTH_SHORT).show()
            }
            de.goork.songflip.data.ShortcutHelper.ACTION_PLAY_LAST_SONG -> {
                val targetUrl = intent.getStringExtra(de.goork.songflip.data.ShortcutHelper.EXTRA_TARGET_URL)
                if (!targetUrl.isNullOrBlank()) {
                    try {
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(playIntent)
                    } catch (_: Exception) {
                        Toast.makeText(this, getString(R.string.shortcut_no_last_song), Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.shortcut_no_last_song), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

data class ServiceInfo(
    val key: String,
    val nameResId: Int,
    val brandColor: Color,
    val iconResId: Int
)

data class LanguageItem(
    val code: String,
    val nativeName: String,
    val flag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    initialShowPause: Boolean = false,
    currentThemeMode: String = "system",
    isWindowFocused: Boolean = false,
    onThemeModeSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { OdesliRepository() }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var selectedLanguage by remember { mutableStateOf(settingsRepository.appLanguage) }

    // Bottom Sheets State
    var showPauseBottomSheet by remember { mutableStateOf(initialShowPause) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showTestStudioBottomSheet by remember { mutableStateOf(false) }
    var showProPaywall by remember { mutableStateOf(false) }

    val proState by ProManager.proState.collectAsState()

    // Pause State
    var isCurrentlyPaused by remember { mutableStateOf(PauseHelper.isCurrentlyPaused(context)) }
    val prefs = remember { context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE) }
    var pausedUntilTimestamp by remember {
        mutableStateOf(prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L))
    }

    var domainStatus by remember { mutableStateOf(DomainVerificationUtils.getDomainStatus(context)) }
    var linksActive by remember { mutableStateOf<Boolean?>(DomainVerificationUtils.checkLinksEnabled(context)) }

    // Clipboard Smart-Banner State
    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var dismissedClipboardUrl by remember { mutableStateOf<String?>(null) }

    val checkClipboard = rememberUpdatedState {
        if (!settingsRepository.autoClipboardDetect) {
            detectedClipboardUrl = null
            return@rememberUpdatedState
        }
        try {
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val rawClipText = clipManager?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim() ?: ""
            if (rawClipText.isNotEmpty()) {
                val cleanUrl = UrlUtils.extractCleanUrl(rawClipText) ?: rawClipText
                if (isSupportedMusicUrl(cleanUrl)) {
                    if (cleanUrl != dismissedClipboardUrl) {
                        detectedClipboardUrl = cleanUrl
                        // Predictive prefetching (Idee 1): silently warm L1 cache in background for 0ms launch
                        coroutineScope.launch {
                            repository.prefetch(cleanUrl, selectedTargetKey)
                        }
                    }
                } else {
                    // Clipboard contains non-music text -> clear banner
                    detectedClipboardUrl = null
                }
            }
        } catch (e: Exception) {
            // Focus not yet granted or security restriction
        }
    }

    LaunchedEffect(isWindowFocused) {
        if (isWindowFocused) {
            checkClipboard.value()
        }
    }

    // Update state when resuming from system settings or external changes
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                domainStatus = DomainVerificationUtils.getDomainStatus(context)
                linksActive = DomainVerificationUtils.checkLinksEnabled(context)
                isCurrentlyPaused = PauseHelper.isCurrentlyPaused(context)
                pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
                (context as? Activity)?.let { act ->
                    de.goork.songflip.data.ReviewHelper.maybeRequestReview(act, settingsRepository)
                }
                de.goork.songflip.data.ShortcutHelper.updateShortcuts(context)

                // Check clipboard on resume (with decorView.post fallback to ensure window focus)
                checkClipboard.value()
                (context as? Activity)?.window?.decorView?.post {
                    checkClipboard.value()
                }
                coroutineScope.launch {
                    kotlinx.coroutines.delay(150)
                    checkClipboard.value()
                }
            }
        }
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PauseHelper.PREFS_KEY_PAUSED || key == PauseHelper.PREFS_KEY_PAUSED_UNTIL) {
                isCurrentlyPaused = PauseHelper.isCurrentlyPaused(context)
                pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        }
    }

    val scrollState = rememberScrollState()

    val supportedLanguages = remember {
        listOf(
            LanguageItem("de", "Deutsch", "🇩🇪"),
            LanguageItem("en", "English", "🇬🇧"),
            LanguageItem("da", "Dansk", "🇩🇰"),
            LanguageItem("nb", "Norsk", "🇳🇴"),
            LanguageItem("sv", "Svenska", "🇸🇪"),
            LanguageItem("nl", "Nederlands", "🇳🇱"),
            LanguageItem("fr", "Français", "🇫🇷"),
            LanguageItem("es", "Español", "🇪🇸"),
            LanguageItem("it", "Italiano", "🇮🇹"),
            LanguageItem("pt", "Português", "🇵🇹"),
            LanguageItem("pl", "Polski", "🇵🇱"),
            LanguageItem("ru", "Русский", "🇷🇺"),
            LanguageItem("tr", "Türkçe", "🇹🇷"),
            LanguageItem("uk", "Українська", "🇺🇦"),
            LanguageItem("ja", "日本語", "🇯🇵"),
            LanguageItem("ko", "한국어", "🇰🇷"),
            LanguageItem("zh", "简体中文", "🇨🇳"),
            LanguageItem("in", "Bahasa Indonesia", "🇮🇩"),
            LanguageItem("vi", "Tiếng Việt", "🇻🇳"),
            LanguageItem("bn", "বাংলা", "🇧🇩"),
            LanguageItem("hi", "हिन्दी", "🇮🇳"),
            LanguageItem("mr", "मराठी", "🇮🇳")
        )
    }

    val targetServices = remember {
        listOf(
            ServiceInfo("youtubeMusic", R.string.target_youtube_music, BrandYouTubeMusic, R.drawable.ic_youtube_music),
            ServiceInfo("appleMusic", R.string.target_apple_music, BrandAppleMusic, R.drawable.ic_apple_music),
            ServiceInfo("spotify", R.string.target_spotify, BrandSpotify, R.drawable.ic_spotify),
            ServiceInfo("tidal", R.string.target_tidal, BrandTidal, R.drawable.ic_tidal),
            ServiceInfo("deezer", R.string.target_deezer, BrandDeezer, R.drawable.ic_deezer),
            ServiceInfo("amazonMusic", R.string.target_amazon_music, BrandAmazonMusic, R.drawable.ic_amazon_music)
        )
    }

    // Bottom Sheets
    if (showPauseBottomSheet) {
        PauseBottomSheet(
            onDismissRequest = { showPauseBottomSheet = false },
            onPauseOptionSelected = { durationMs, isUntilTomorrow ->
                if (isUntilTomorrow) {
                    val tomorrowTimestamp = PauseHelper.getTomorrowMorningTimestamp()
                    PauseHelper.setPauseUntil(context, tomorrowTimestamp)
                    isCurrentlyPaused = true
                    pausedUntilTimestamp = tomorrowTimestamp
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("paused_tomorrow")
                } else if (durationMs == 0L) {
                    PauseHelper.setPause(context, 0L)
                    isCurrentlyPaused = true
                    pausedUntilTimestamp = 0L
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("paused_indefinitely")
                } else {
                    PauseHelper.setPause(context, durationMs)
                    isCurrentlyPaused = true
                    pausedUntilTimestamp = if (durationMs > 0) prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L) else 0L
                    val durationStr = if (durationMs == 15 * 60 * 1000L) "paused_15m" else "paused_1h"
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged(durationStr)
                }
                showPauseBottomSheet = false
            }
        )
    }

    if (showTestStudioBottomSheet) {
        TestStudioBottomSheet(
            onDismissRequest = { showTestStudioBottomSheet = false },
            selectedTargetKey = selectedTargetKey,
            settingsRepository = settingsRepository,
            odesliRepository = repository,
            isPro = proState.isPro,
            onOpenProPaywall = {
                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed()
                showProPaywall = true
            }
        )
    }

    if (showSettingsBottomSheet) {
        SettingsBottomSheet(
            onDismissRequest = { showSettingsBottomSheet = false },
            settingsRepository = settingsRepository,
            supportedLanguages = supportedLanguages,
            currentLanguageCode = selectedLanguage,
            onLanguageSelected = { newLang ->
                selectedLanguage = newLang
            },
            currentThemeMode = currentThemeMode,
            onThemeModeSelected = onThemeModeSelected,
            isPro = proState.isPro,
            onOpenProPaywall = {
                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed()
                showProPaywall = true
            },
            onOpenTestStudio = { showTestStudioBottomSheet = true }
        )
    }

    if (showProPaywall) {
        ProPaywallBottomSheet(
            onDismissRequest = { showProPaywall = false }
        )
    }

    // Main UI Layout
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWideScreen = maxWidth >= 600.dp
        val contentModifier = if (isWideScreen) {
            Modifier
                .width(560.dp)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        }

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Header Banner
            HeaderBanner(
                onOpenSettings = {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackSettingsOpened()
                    showSettingsBottomSheet = true
                },
                isPro = proState.isPro
            )

            val isSetupRequired = !isCurrentlyPaused && (domainStatus?.let { it.enabledHosts == 0 } ?: (linksActive == false))

            // 2. Live Status & Quick Pause Card
            LiveStatusBanner(
                isCurrentlyPaused = isCurrentlyPaused,
                pausedUntilTimestamp = pausedUntilTimestamp,
                isSetupRequired = isSetupRequired,
                onResumeClick = {
                    PauseHelper.resume(context)
                    isCurrentlyPaused = false
                    pausedUntilTimestamp = 0L
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("unpaused")
                },
                onPauseClick = {
                    showPauseBottomSheet = true
                },
                onSetupClick = {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (e: Exception) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    } else {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    }
                }
            )

            // 2.5 Clipboard Smart-Banner (when music link is copied in clipboard)
            AnimatedVisibility(
                visible = detectedClipboardUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                detectedClipboardUrl?.let { clipUrl ->
                    val targetService = targetServices.find { it.key == selectedTargetKey }
                    val targetServiceName: String = targetService?.let { stringResource(it.nameResId) } ?: "Player"
                    ClipboardSmartBanner(
                        musicUrl = clipUrl,
                        targetPlatformName = targetServiceName,
                        onOpenInTarget = { urlToOpen ->
                            val redirectIntent = Intent(context, RedirectActivity::class.java).apply {
                                data = Uri.parse(urlToOpen)
                                putExtra("from_clipboard_banner", true)
                            }
                            context.startActivity(redirectIntent)
                        },
                        onShareUniversalLink = { urlToShare ->
                            val universalUrl = ProManager.getUniversalWebShareUrl(urlToShare)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("SongFlip Universal Link", universalUrl)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, context.getString(R.string.share_universal_link_copied), Toast.LENGTH_SHORT).show()
                            ProManager.warmupUniversalShare(urlToShare)
                        },
                        onDismiss = {
                            dismissedClipboardUrl = clipUrl
                            detectedClipboardUrl = null
                        }
                    )
                }
            }

            // 3. Domain Verification Setup Card
            SetupCard(
                domainStatus = domainStatus,
                linksActive = linksActive
            )

            // 4. Preferred Target Music Player Card
            TargetSelectorCard(
                targetServices = targetServices,
                selectedTargetKey = selectedTargetKey,
                onTargetSelected = { key ->
                    selectedTargetKey = key
                    settingsRepository.targetPlatform = key
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackTargetPlatformChanged(key)
                }
            )

            // 5. App Footer Section
            FooterSection()
        }
    }
}
