package de.goork.songflip.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.ui.components.*
import de.goork.songflip.ui.theme.*

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            viewModel.checkClipboardOnResume()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AndroidSharedPreferencesCacheStorage.init(this)

        if (intent?.getBooleanExtra("show_pause_sheet", false) == true) {
            viewModel.openPauseSheet()
        }
        val initialPlaylist = intent?.getStringExtra("open_playlist_url")
        if (!initialPlaylist.isNullOrBlank()) {
            viewModel.openPlaylistConvert(initialPlaylist)
        }
        handleShortcutIntent(intent)

        val shared = intent?.takeIf { it.action == Intent.ACTION_SEND }?.let {
            it.getStringExtra(Intent.EXTRA_TEXT) ?: it.clipData?.takeIf { cd -> cd.itemCount > 0 }?.getItemAt(0)?.text?.toString()
        }?.let { UrlUtils.extractCleanUrl(it) ?: it }

        if (shared != null && isSupportedMusicUrl(shared)) {
            viewModel.onIncomingSharedUrl(shared)
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
            val uiState by viewModel.uiState.collectAsState()

            val darkTheme = when (uiState.currentThemeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            SongFlipTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
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
            viewModel.onIncomingSharedUrl(shared)
        } else if (playlistUrl != null) {
            viewModel.openPlaylistConvert(playlistUrl)
        }

        if (intent.getBooleanExtra("show_pause_sheet", false)) {
            viewModel.openPauseSheet()
        }
    }

    private fun handleShortcutIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            de.goork.songflip.data.ShortcutHelper.ACTION_PAUSE_1H -> {
                PauseHelper.setPause(this, 60 * 60 * 1000L)
                viewModel.refreshStatusAndPauseState()
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
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { SongLinkEngine.shared }
    val settingsRepository = remember { SettingsRepository(context) }

    val uiState by viewModel.uiState.collectAsState()
    val proState by ProManager.proState.collectAsState()

    // Update state when resuming from system settings or external changes
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStatusAndPauseState()
                viewModel.refreshDiagnosis()
                (context as? Activity)?.let { act ->
                    de.goork.songflip.data.ReviewHelper.maybeRequestReview(act, settingsRepository)
                }
                de.goork.songflip.data.ShortcutHelper.updateShortcuts(context)
                viewModel.checkClipboardOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
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
    if (uiState.showPauseBottomSheet) {
        PauseBottomSheet(
            onDismissRequest = { viewModel.closePauseSheet() },
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
                viewModel.refreshStatusAndPauseState()
                viewModel.closePauseSheet()
            }
        )
    }

    if (uiState.showSettingsBottomSheet) {
        SettingsBottomSheet(
            onDismissRequest = { viewModel.closeSettings() },
            settingsRepository = settingsRepository,
            supportedLanguages = supportedLanguages,
            currentLanguageCode = uiState.selectedLanguage,
            onLanguageSelected = { newLang ->
                viewModel.setAppLanguage(newLang)
            },
            currentThemeMode = uiState.currentThemeMode,
            onThemeModeSelected = { newMode ->
                viewModel.setThemeMode(newMode)
            },
            isPro = proState.isPro,
            onOpenProPaywall = {
                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("settings_sheet")
                viewModel.openProPaywall()
            },
            onOpenSetupGuide = {
                viewModel.closeSettings()
                viewModel.openAppLinksSetup()
            }
        )
    }

    if (uiState.showAppLinksSetupBottomSheet) {
        AppLinksSetupBottomSheet(
            onDismissRequest = { viewModel.closeAppLinksSetup() },
            targetPlatformKey = uiState.selectedTargetKey,
            onOpenSystemSettings = {
                viewModel.closeAppLinksSetup()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    } catch (_: Exception) {
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
    }

    if (uiState.showProPaywall) {
        ProPaywallBottomSheet(
            onDismissRequest = {
                viewModel.closeProPaywall()
            },
            initialShowPromo = uiState.initialShowPromoInPaywall
        )
    }

    uiState.showPlaylistConvertSheet?.let { playlistUrl ->
        PlaylistConvertBottomSheet(
            playlistUrl = playlistUrl,
            targetPlatformKey = uiState.selectedTargetKey,
            onDismiss = { viewModel.closePlaylistConvert() },
            onOpenPaywall = {
                viewModel.closePlaylistConvert()
                viewModel.openProPaywall()
            }
        )
    }

    uiState.showPodcastNoticeSheet?.let { podcastUrl ->
        val platformKey = UrlUtils.detectPlatform(podcastUrl)?.key ?: "unknown"
        val isAudiobook = UrlUtils.isAudiobookUrl(podcastUrl)
        PodcastNoticeBottomSheet(
            url = podcastUrl,
            platformKey = platformKey,
            isAudiobook = isAudiobook,
            onDismiss = { viewModel.closePodcastNotice() }
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
                    viewModel.openSettings()
                },
                isPro = proState.isPro
            )

            val isSetupRequired = !uiState.isCurrentlyPaused && ((uiState.domainStatus?.enabledHosts == 0) || (uiState.linksActive == false))

            // 2. Live Status & Quick Pause Card
            LiveStatusBanner(
                isCurrentlyPaused = uiState.isCurrentlyPaused,
                pausedUntilTimestamp = uiState.pausedUntilTimestamp,
                isSetupRequired = isSetupRequired,
                onResumeClick = {
                    PauseHelper.resume(context)
                    viewModel.refreshStatusAndPauseState()
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPauseStateChanged("unpaused")
                },
                onPauseClick = {
                    viewModel.openPauseSheet()
                },
                onSetupClick = {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                    viewModel.openAppLinksSetup()
                }
            )

            // 2.5 Clipboard Smart-Banner (when music link is copied in clipboard)
            AnimatedVisibility(
                visible = uiState.detectedClipboardUrl != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                uiState.detectedClipboardUrl?.let { clipUrl ->
                    val isPlaylist = UrlUtils.isPlaylistUrl(clipUrl)
                    val isPodcastOrAudiobook = UrlUtils.isPodcastOrAudiobookUrl(clipUrl)
                    val isAudiobook = UrlUtils.isAudiobookUrl(clipUrl)
                    val targetService = targetServices.find { it.key == uiState.selectedTargetKey }
                    val targetServiceName: String = targetService?.let { stringResource(it.nameResId) } ?: "Player"
                    ClipboardSmartBanner(
                        musicUrl = clipUrl,
                        targetPlatformName = targetServiceName,
                        isPro = proState.isPro,
                        isPlaylist = isPlaylist,
                        isPodcastOrAudiobook = isPodcastOrAudiobook,
                        isAudiobook = isAudiobook,
                        onOpenPlaylist = { url ->
                            viewModel.openPlaylistConvert(url)
                        },
                        onOpenPodcast = { url ->
                            viewModel.openPodcastNotice(url)
                        },
                        onOpenInTarget = { urlToOpen ->
                            if (isPlaylist) {
                                viewModel.openPlaylistConvert(urlToOpen)
                            } else if (isPodcastOrAudiobook) {
                                viewModel.openPodcastNotice(urlToOpen)
                            } else {
                                viewModel.dismissClipboardBanner(urlToOpen)
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
                                viewModel.openProPaywall()
                            }
                        },
                        onDismiss = {
                            viewModel.dismissClipboardBanner(clipUrl)
                        }
                    )
                }
            }

            // 2.6 PRO-Upgrade Milestone Nudge
            AnimatedVisibility(
                visible = !proState.isPro && uiState.activeMilestone > 0,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                ProNudgeBanner(
                    milestone = uiState.activeMilestone,
                    onRedeemPromo = {
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("milestone_${uiState.activeMilestone}_promo")
                        viewModel.openProPaywall(showPromo = true)
                    },
                    onLearnMore = {
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("milestone_${uiState.activeMilestone}_learn_more")
                        viewModel.openProPaywall(showPromo = false)
                    },
                    onDismiss = {
                        viewModel.dismissMilestone(uiState.activeMilestone)
                    }
                )
            }

            // 3. Domain Verification Setup Card
            SetupCard(
                domainStatus = uiState.domainStatus,
                linksActive = uiState.linksActive,
                diagnosisSummary = uiState.diagnosisSummary,
                onOpenSetupGuide = {
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                    viewModel.openAppLinksSetup()
                }
            )

            // 4. Preferred Target Music Player Card
            TargetSelectorCard(
                targetServices = targetServices,
                selectedTargetKey = uiState.selectedTargetKey,
                onTargetSelected = { key ->
                    viewModel.setTargetPlatform(key)
                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackTargetPlatformChanged(key)
                }
            )

            // 5. App Footer Section
            FooterSection()
        }
    }
}
