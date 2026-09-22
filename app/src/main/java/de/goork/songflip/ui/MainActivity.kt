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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.ui.components.*
import de.goork.songflip.ui.theme.*

class MainActivity : AppCompatActivity() {

    private var showPauseSheetState = mutableStateOf(false)
    private var openPlaylistUrlState = mutableStateOf<String?>(null)
    private val windowFocusState = mutableStateOf(true)
    private val incomingSharedUrlState = mutableStateOf<String?>(null)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        windowFocusState.value = hasFocus
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSharedPreferencesCacheStorage.init(this)
        showPauseSheetState.value = intent?.getBooleanExtra("show_pause_sheet", false) == true
        openPlaylistUrlState.value = intent?.getStringExtra("open_playlist_url")
        handleShortcutIntent(intent)

        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }?.let {
            it.getStringExtra(Intent.EXTRA_TEXT) ?: it.clipData?.takeIf { cd -> cd.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }?.let { UrlUtils.extractCleanUrl(it) ?: it }
        if (shared != null && isSupportedMusicUrl(shared)) {
            if (UrlUtils.isPlaylistUrl(shared)) {
                openPlaylistUrlState.value = shared
            } else {
                incomingSharedUrlState.value = shared
            }
        }

        val settingsRepo = SettingsRepository(this)
        val savedLang = settingsRepo.appLanguage
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty || currentLocales[0]?.language != savedLang) {
            val localeList = androidx.core.os.LocaleListCompat.forLanguageTags(savedLang)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
        }

        if (savedInstanceState == null) {
            de.goork.songflip.core.analytics.AptabaseClient.shared.trackAppLaunched(
                platform = "Android",
                language = savedLang
            )
        }

        setContent {
            val themeMode = remember { mutableStateOf(settingsRepo.themeMode) }

            val darkTheme = when (themeMode.value) {
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
                        showPauseSheetOnStart = showPauseSheetState.value,
                        onPauseSheetDismissed = { showPauseSheetState.value = false },
                        initialPlaylistUrl = openPlaylistUrlState.value,
                        onPlaylistSheetDismissed = { openPlaylistUrlState.value = null },
                        windowFocused = windowFocusState.value,
                        incomingSharedUrl = incomingSharedUrlState.value,
                        onIncomingSharedUrlHandled = { incomingSharedUrlState.value = null },
                        onThemeModeChanged = { newMode -> themeMode.value = newMode }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcutIntent(intent)

        val playlistUrl = intent.getStringExtra("open_playlist_url")
        val shared = intent.takeIf { it.action == Intent.ACTION_SEND }?.let {
            it.getStringExtra(Intent.EXTRA_TEXT) ?: it.clipData?.takeIf { cd -> cd.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }?.let { UrlUtils.extractCleanUrl(it) ?: it }

        if (shared != null && isSupportedMusicUrl(shared)) {
            if (UrlUtils.isPlaylistUrl(shared)) {
                openPlaylistUrlState.value = shared
            } else {
                incomingSharedUrlState.value = shared
            }
        } else if (playlistUrl != null) {
            openPlaylistUrlState.value = playlistUrl
        }

        if (intent.getBooleanExtra("show_pause_sheet", false)) {
            showPauseSheetState.value = true
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
    showPauseSheetOnStart: Boolean = false,
    onPauseSheetDismissed: () -> Unit = {},
    initialPlaylistUrl: String? = null,
    onPlaylistSheetDismissed: () -> Unit = {},
    windowFocused: Boolean = true,
    incomingSharedUrl: String? = null,
    onIncomingSharedUrlHandled: () -> Unit = {},
    onThemeModeChanged: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { SongLinkEngine.shared }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var selectedLanguage by remember { mutableStateOf(settingsRepository.appLanguage) }
    var currentThemeMode by remember { mutableStateOf(settingsRepository.themeMode) }

    var isCurrentlyPaused by remember { mutableStateOf(PauseHelper.isCurrentlyPaused(context)) }
    var pausedUntilTimestamp by remember {
        mutableStateOf(
            context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
        )
    }

    var domainStatus by remember { mutableStateOf(DomainVerificationUtils.getDomainStatus(context)) }
    var linksActive by remember { mutableStateOf(DomainVerificationUtils.checkLinksEnabled(context)) }
    var diagnosisSummary by remember { mutableStateOf<de.goork.songflip.data.DiagnosisSummary?>(null) }

    fun refreshStatus() {
        isCurrentlyPaused = PauseHelper.isCurrentlyPaused(context)
        pausedUntilTimestamp = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
        domainStatus = DomainVerificationUtils.getDomainStatus(context)
        linksActive = DomainVerificationUtils.checkLinksEnabled(context)
    }

    LaunchedEffect(selectedTargetKey) {
        withContext(Dispatchers.IO) {
            diagnosisSummary = de.goork.songflip.data.LinkDiagnosisManager.runDiagnosis(context, selectedTargetKey)
        }
    }

    val proState by ProManager.proState.collectAsState()

    var showPauseBottomSheet by remember { mutableStateOf(showPauseSheetOnStart) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showAppLinksSetupBottomSheet by remember { mutableStateOf(false) }
    var showProPaywall by remember { mutableStateOf(false) }
    var initialShowPromoInPaywall by remember { mutableStateOf(false) }
    var showPlaylistConvertSheet by remember { mutableStateOf(initialPlaylistUrl) }
    var showPodcastNoticeSheet by remember { mutableStateOf<String?>(null) }

    // Clipboard Smart-Banner State
    var detectedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var dismissedClipboardUrl by remember { mutableStateOf<String?>(null) }
    var lastClipboardCheckTimestamp by remember { mutableStateOf(0L) }

    // Milestone Promo Nudge State
    var activeMilestone by remember { mutableStateOf(settingsRepository.getActiveProNudgeMilestone()) }

    fun checkClipboard() {
        val now = System.currentTimeMillis()
        if (now - lastClipboardCheckTimestamp < 800L) {
            return
        }
        lastClipboardCheckTimestamp = now

        if (!settingsRepository.autoClipboardDetect) {
            detectedClipboardUrl = null
            return
        }

        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                val description = clipboard.primaryClipDescription
                if (description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
                    description?.hasMimeType(android.content.ClipDescription.MIMETYPE_TEXT_HTML) == true
                ) {
                    val item = clipboard.primaryClip?.getItemAt(0)
                    val text = item?.text?.toString() ?: item?.uri?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = UrlUtils.extractCleanUrl(text)
                        if (clean != null && isSupportedMusicUrl(clean)) {
                            if (clean != dismissedClipboardUrl) {
                                detectedClipboardUrl = clean
                                coroutineScope.launch(Dispatchers.IO) {
                                    repository.prefetch(clean, selectedTargetKey)
                                }
                            }
                            return
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        detectedClipboardUrl = null
    }

    LaunchedEffect(showPauseSheetOnStart) {
        if (showPauseSheetOnStart) {
            showPauseBottomSheet = true
            onPauseSheetDismissed()
        }
    }

    LaunchedEffect(initialPlaylistUrl) {
        if (!initialPlaylistUrl.isNullOrBlank()) {
            showPlaylistConvertSheet = initialPlaylistUrl
            onPlaylistSheetDismissed()
        }
    }

    LaunchedEffect(incomingSharedUrl) {
        if (!incomingSharedUrl.isNullOrBlank()) {
            if (UrlUtils.isPlaylistUrl(incomingSharedUrl)) {
                showPlaylistConvertSheet = incomingSharedUrl
            } else {
                detectedClipboardUrl = incomingSharedUrl
                coroutineScope.launch(Dispatchers.IO) {
                    repository.prefetch(incomingSharedUrl, selectedTargetKey)
                }
            }
            onIncomingSharedUrlHandled()
        }
    }

    // Update state when resuming from system settings
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshStatus()
                activeMilestone = settingsRepository.getActiveProNudgeMilestone()
                (context as? Activity)?.let { act ->
                    de.goork.songflip.data.ReviewHelper.maybeRequestReview(act, settingsRepository)
                }
                de.goork.songflip.data.ShortcutHelper.updateShortcuts(context)
                checkClipboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(windowFocused) {
        if (windowFocused) {
            checkClipboard()
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
            LanguageItem("zh-TW", "繁體中文", "🇹🇼"),
            LanguageItem("cs", "Čeština", "🇨🇿"),
            LanguageItem("hu", "Magyar", "🇭🇺"),
            LanguageItem("fi", "Suomi", "🇫🇮"),
            LanguageItem("el", "Ελληνικά", "🇬🇷"),
            LanguageItem("ro", "Română", "🇷🇴"),
            LanguageItem("th", "ไทย", "🇹🇭"),
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
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("paused_tomorrow")
                } else if (durationMs == 0L) {
                    PauseHelper.setPause(context, 0L)
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("paused_indefinitely")
                } else {
                    PauseHelper.setPause(context, durationMs)
                    val durationStr = if (durationMs == 15 * 60 * 1000L) "paused_15m" else "paused_1h"
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged(durationStr)
                }
                refreshStatus()
                showPauseBottomSheet = false
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
            onThemeModeSelected = { newMode ->
                currentThemeMode = newMode
                onThemeModeChanged(newMode)
            },
            isPro = proState.isPro,
            onOpenProPaywall = {
                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("settings_sheet")
                showProPaywall = true
            },
            onOpenSetupGuide = {
                showSettingsBottomSheet = false
                showAppLinksSetupBottomSheet = true
            }
        )
    }

    if (showAppLinksSetupBottomSheet) {
        AppLinksSetupBottomSheet(
            onDismissRequest = {
                showAppLinksSetupBottomSheet = false
                refreshStatus()
            },
            targetPlatformKey = selectedTargetKey,
            onOpenSystemSettings = {
                val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(fallbackIntent)
                }
            }
        )
    }

    if (showProPaywall) {
        ProPaywallBottomSheet(
            onDismissRequest = {
                showProPaywall = false
                initialShowPromoInPaywall = false
            },
            initialShowPromo = initialShowPromoInPaywall
        )
    }

    showPlaylistConvertSheet?.let { playlistUrl ->
        PlaylistConvertBottomSheet(
            playlistUrl = playlistUrl,
            targetPlatformKey = selectedTargetKey,
            onDismiss = { showPlaylistConvertSheet = null },
            onOpenPaywall = {
                showProPaywall = true
            }
        )
    }

    showPodcastNoticeSheet?.let { podcastUrl ->
        val isAudiobook = UrlUtils.isAudiobookUrl(podcastUrl)
        val platformKey = UrlUtils.detectPlatform(podcastUrl)?.key ?: "spotify"
        PodcastNoticeBottomSheet(
            url = podcastUrl,
            platformKey = platformKey,
            isAudiobook = isAudiobook,
            onDismiss = { showPodcastNoticeSheet = null }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    showAppLinksSetupBottomSheet = true
                }
            )

            // 2.5 Clipboard Smart-Banner (when music link is copied in clipboard)
            AnimatedVisibility(
                visible = detectedClipboardUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                detectedClipboardUrl?.let { clipUrl ->
                    val isPlaylist = UrlUtils.isPlaylistUrl(clipUrl)
                    val isPodcastOrAudiobook = UrlUtils.isPodcastOrAudiobookUrl(clipUrl)
                    val isAudiobook = UrlUtils.isAudiobookUrl(clipUrl)
                    val targetService = targetServices.find { it.key == selectedTargetKey }
                    val targetServiceName: String = targetService?.let { stringResource(it.nameResId) } ?: "Player"
                    ClipboardSmartBanner(
                        musicUrl = clipUrl,
                        targetPlatformName = targetServiceName,
                        isPro = proState.isPro,
                        isPlaylist = isPlaylist,
                        isPodcastOrAudiobook = isPodcastOrAudiobook,
                        isAudiobook = isAudiobook,
                        onOpenPlaylist = { url ->
                            showPlaylistConvertSheet = url
                        },
                        onOpenPodcast = { url ->
                            showPodcastNoticeSheet = url
                        },
                        onOpenInTarget = { urlToOpen ->
                            if (isPlaylist) {
                                showPlaylistConvertSheet = urlToOpen
                            } else if (isPodcastOrAudiobook) {
                                showPodcastNoticeSheet = urlToOpen
                            } else {
                                dismissedClipboardUrl = urlToOpen
                                detectedClipboardUrl = null
                                val redirectIntent = Intent(context, RedirectActivity::class.java).apply {
                                    data = Uri.parse(urlToOpen)
                                    putExtra("from_clipboard_banner", true)
                                }
                                context.startActivity(redirectIntent)
                            }
                        },
                        onShareUniversalLink = { urlToShare ->
                            if (proState.isPro) {
                                coroutineScope.launch {
                                    val canonical = if (UrlUtils.isShortLinkDomain(urlToShare)) {
                                        repository.resolveCanonicalUrl(urlToShare)
                                    } else {
                                        urlToShare
                                    }
                                    val universalUrl = ProManager.getUniversalWebShareUrl(canonical)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                    val clip = android.content.ClipData.newPlainText("SongFlip Universal Link", universalUrl)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, context.getString(R.string.share_universal_link_copied), Toast.LENGTH_SHORT).show()
                                    ProManager.warmupUniversalShare(canonical)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, universalUrl)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_universal_link)))
                                }
                            } else {
                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("clipboard_banner_universal_share")
                                showProPaywall = true
                            }
                        },
                        onDismiss = {
                            dismissedClipboardUrl = clipUrl
                            detectedClipboardUrl = null
                        }
                    )
                }
            }

            // 2.6 PRO-Upgrade Milestone Nudge
            AnimatedVisibility(
                visible = !proState.isPro && activeMilestone > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ProNudgeBanner(
                    milestone = activeMilestone,
                    onRedeemPromo = {
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("milestone_${activeMilestone}_promo")
                        initialShowPromoInPaywall = true
                        showProPaywall = true
                    },
                    onLearnMore = {
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("milestone_${activeMilestone}_learn_more")
                        initialShowPromoInPaywall = false
                        showProPaywall = true
                    },
                    onDismiss = {
                        settingsRepository.dismissProNudgeMilestone(activeMilestone)
                        activeMilestone = 0
                    }
                )
            }

            // 3. Domain Verification Setup Card
            SetupCard(
                domainStatus = domainStatus,
                linksActive = linksActive,
                diagnosisSummary = diagnosisSummary,
                onOpenSetupGuide = {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                    showAppLinksSetupBottomSheet = true
                }
            )

            // 4. Preferred Target Music Player Card
            TargetSelectorCard(
                targetServices = targetServices,
                selectedTargetKey = selectedTargetKey,
                onTargetSelected = { key ->
                    selectedTargetKey = key
                    settingsRepository.targetPlatform = key
                    coroutineScope.launch(Dispatchers.IO) {
                        diagnosisSummary = de.goork.songflip.data.LinkDiagnosisManager.runDiagnosis(context, key)
                    }
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackTargetPlatformChanged(key)
                }
            )

            // 5. App Footer Section
            FooterSection()
        }
    }
}
