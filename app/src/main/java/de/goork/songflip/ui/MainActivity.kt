package de.goork.songflip.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.goork.songflip.R
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.components.*
import de.goork.songflip.ui.theme.*

class MainActivity : AppCompatActivity() {

    private var initialShowPauseSheet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LinkCacheManager.init(this)
        initialShowPauseSheet = intent?.getBooleanExtra("show_pause_sheet", false) == true

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
                        onThemeModeSelected = { newMode -> currentThemeMode = newMode }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
                            onThemeModeSelected = { newMode -> currentThemeMode = newMode }
                        )
                    }
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
    onThemeModeSelected: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val repository = remember { OdesliRepository() }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var selectedLanguage by remember { mutableStateOf(settingsRepository.appLanguage) }

    // Bottom Sheets State
    var showPauseBottomSheet by remember { mutableStateOf(initialShowPause) }
    var showSettingsBottomSheet by remember { mutableStateOf(false) }
    var showTestStudioBottomSheet by remember { mutableStateOf(false) }

    // Pause State
    var isCurrentlyPaused by remember { mutableStateOf(PauseHelper.isCurrentlyPaused(context)) }
    val prefs = remember { context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE) }
    var pausedUntilTimestamp by remember {
        mutableStateOf(prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L))
    }

    var domainStatus by remember { mutableStateOf(DomainVerificationUtils.getDomainStatus(context)) }
    var linksActive by remember { mutableStateOf<Boolean?>(DomainVerificationUtils.checkLinksEnabled(context)) }

    // Update state when resuming from system settings or external changes
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                domainStatus = DomainVerificationUtils.getDomainStatus(context)
                linksActive = DomainVerificationUtils.checkLinksEnabled(context)
                isCurrentlyPaused = PauseHelper.isCurrentlyPaused(context)
                pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
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
                } else {
                    PauseHelper.setPause(context, durationMs)
                    isCurrentlyPaused = true
                    pausedUntilTimestamp = if (durationMs > 0) prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L) else 0L
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
            odesliRepository = repository
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
            onThemeModeSelected = onThemeModeSelected
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
                onOpenTestStudio = { showTestStudioBottomSheet = true },
                onOpenSettings = { showSettingsBottomSheet = true }
            )

            // 2. Live Status & Quick Pause Card
            LiveStatusBanner(
                isCurrentlyPaused = isCurrentlyPaused,
                pausedUntilTimestamp = pausedUntilTimestamp,
                onResumeClick = {
                    PauseHelper.resume(context)
                    isCurrentlyPaused = false
                    pausedUntilTimestamp = 0L
                },
                onPauseClick = {
                    showPauseBottomSheet = true
                }
            )

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
                }
            )

            // 5. App Footer Section
            FooterSection()
        }
    }
}
