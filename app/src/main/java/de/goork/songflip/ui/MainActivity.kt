package de.goork.songflip.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.goork.songflip.R
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.OdesliResult
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.theme.*
import kotlinx.coroutines.launch

private const val URL_FAMWAKE = "https://play.google.com/store/apps/details?id=de.familienwecker.famwake"
private const val URL_NOTTHOFF = "https://notthoff.org"

class MainActivity : AppCompatActivity() {

    private var initialShowPauseSheet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialShowPauseSheet = intent?.getBooleanExtra("show_pause_sheet", false) == true

        setContent {
            SongFlipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(initialShowPause = initialShowPauseSheet)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("show_pause_sheet", false)) {
            // Re-trigger pause sheet if invoked again via TileService
            setContent {
                SongFlipTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainScreen(initialShowPause = true)
                    }
                }
            }
        }
    }
}

data class ServiceInfo(
    val key: String,
    val nameResId: Int,
    val brandColor: Color
)

data class LanguageItem(
    val code: String,
    val nativeName: String,
    val flag: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(initialShowPause: Boolean = false) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val repository = remember { OdesliRepository() }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var selectedLanguage by remember { mutableStateOf(settingsRepository.appLanguage) }

    // Dialog and Sheet States
    var showLanguageBottomSheet by remember { mutableStateOf(false) }
    var showPauseBottomSheet by remember { mutableStateOf(initialShowPause) }
    var showAdvancedSettingsDialog by remember { mutableStateOf(false) }

    // Pause State
    var isCurrentlyPaused by remember { mutableStateOf(PauseHelper.isCurrentlyPaused(context)) }
    val prefs = remember { context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE) }
    var pausedUntilTimestamp by remember {
        mutableStateOf(prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L))
    }

    var domainStatus by remember { mutableStateOf(DomainVerificationUtils.getDomainStatus(context)) }
    var linksActive by remember { mutableStateOf<Boolean?>(DomainVerificationUtils.checkLinksEnabled(context)) }

    // Live update when returning from system settings or shared preferences changes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                domainStatus = DomainVerificationUtils.getDomainStatus(context)
                linksActive = DomainVerificationUtils.checkLinksEnabled(context)
                isCurrentlyPaused = PauseHelper.isCurrentlyPaused(context)
                pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var testInputUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var conversionResult by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val supportedLanguages = remember {
        listOf(
            LanguageItem("de", "Deutsch", "🇩🇪"),
            LanguageItem("en", "English", "🇬🇧")
        )
    }

    val targetServices = remember {
        listOf(
            ServiceInfo("youtubeMusic", R.string.target_youtube_music, BrandYouTubeMusic),
            ServiceInfo("appleMusic", R.string.target_apple_music, BrandAppleMusic),
            ServiceInfo("spotify", R.string.target_spotify, BrandSpotify),
            ServiceInfo("tidal", R.string.target_tidal, BrandTidal),
            ServiceInfo("deezer", R.string.target_deezer, BrandDeezer),
            ServiceInfo("amazonMusic", R.string.target_amazon_music, BrandAmazonMusic)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Language Bottom Sheet
    // ─────────────────────────────────────────────────────────────────────────
    if (showLanguageBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showLanguageBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_language_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                supportedLanguages.forEach { lang ->
                    val isSelected = selectedLanguage == lang.code
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedLanguage = lang.code
                            settingsRepository.appLanguage = lang.code
                            val localeList = LocaleListCompat.forLanguageTags(lang.code)
                            AppCompatDelegate.setApplicationLocales(localeList)
                            showLanguageBottomSheet = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lang.flag, fontSize = 22.sp)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = lang.nativeName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pause Bottom Sheet
    // ─────────────────────────────────────────────────────────────────────────
    if (showPauseBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPauseBottomSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.pause_dialog_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.pause_dialog_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Option 1: 15 Minutes
                PauseOptionItem(
                    title = stringResource(R.string.pause_15m),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        PauseHelper.setPause(context, 15 * 60 * 1000L)
                        isCurrentlyPaused = true
                        pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
                        showPauseBottomSheet = false
                    }
                )

                // Option 2: 1 Hour
                PauseOptionItem(
                    title = stringResource(R.string.pause_1h),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        PauseHelper.setPause(context, 60 * 60 * 1000L)
                        isCurrentlyPaused = true
                        pausedUntilTimestamp = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
                        showPauseBottomSheet = false
                    }
                )

                // Option 3: Until Tomorrow Morning (6:00 AM)
                PauseOptionItem(
                    title = stringResource(R.string.pause_tomorrow),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val tomorrowTimestamp = PauseHelper.getTomorrowMorningTimestamp()
                        PauseHelper.setPauseUntil(context, tomorrowTimestamp)
                        isCurrentlyPaused = true
                        pausedUntilTimestamp = tomorrowTimestamp
                        showPauseBottomSheet = false
                    }
                )

                // Option 4: Indefinite
                PauseOptionItem(
                    title = stringResource(R.string.pause_indefinitely),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        PauseHelper.setPause(context, 0L)
                        isCurrentlyPaused = true
                        pausedUntilTimestamp = 0L
                        showPauseBottomSheet = false
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Advanced Settings Dialog (Custom AI / Webhook API)
    // ─────────────────────────────────────────────────────────────────────────
    if (showAdvancedSettingsDialog) {
        var tempUrl by remember { mutableStateOf(settingsRepository.customApiUrl) }
        var tempToken by remember { mutableStateOf(settingsRepository.customApiToken) }

        AlertDialog(
            onDismissRequest = { showAdvancedSettingsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.advanced_settings_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.advanced_settings_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text(stringResource(R.string.custom_api_url_label)) },
                        placeholder = { Text(stringResource(R.string.custom_api_url_hint), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempToken,
                        onValueChange = { tempToken = it },
                        label = { Text(stringResource(R.string.custom_api_token_label)) },
                        placeholder = { Text(stringResource(R.string.custom_api_token_hint), fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = stringResource(R.string.custom_api_url_helper),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        settingsRepository.customApiUrl = tempUrl.trim()
                        settingsRepository.customApiToken = tempToken.trim()
                        showAdvancedSettingsDialog = false
                        Toast.makeText(context, context.getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdvancedSettingsDialog = false }) {
                    Text(stringResource(R.string.pause_cancel))
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main UI Layout (Responsive Centered Container for Tablet / Foldables)
    // ─────────────────────────────────────────────────────────────────────────
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val isWideScreen = maxWidth >= 600.dp
        val contentModifier = if (isWideScreen) {
            Modifier
                .width(560.dp)
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        } else {
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        }

        Column(
            modifier = contentModifier,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // App Header Banner with Quick Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NightSlate900,
                                NightSlate800
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            colors = listOf(
                                BrandSpotify.copy(alpha = 0.4f),
                                BrandAppleMusic.copy(alpha = 0.4f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(NightSlate950)
                                .border(1.dp, NightSlate700, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 26.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.app_tagline),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = NightSlate400
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Advanced Settings Action Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showAdvancedSettingsDialog = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(NightSlate700.copy(alpha = 0.5f))
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = stringResource(R.string.advanced_settings_title),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Subtle Language Switcher Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showLanguageBottomSheet = true
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(NightSlate700.copy(alpha = 0.5f))
                                .size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = stringResource(R.string.select_language_title),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Live Status & Quick Pause Banner Card
            // ─────────────────────────────────────────────────────────────────
            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val activeColor = if (isDarkTheme) StateActiveGreen else StateActiveGreenLight
            val pausedColor = if (isDarkTheme) StatePausedAmber else StatePausedAmberLight

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentlyPaused) {
                        pausedColor.copy(alpha = 0.12f)
                    } else {
                        activeColor.copy(alpha = 0.12f)
                    }
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isCurrentlyPaused) pausedColor.copy(alpha = 0.4f) else activeColor.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isCurrentlyPaused) {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = null,
                                tint = pausedColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.status_paused),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = pausedColor
                                )
                                if (pausedUntilTimestamp > 0L) {
                                    val timeStr = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(pausedUntilTimestamp))
                                    Text(
                                        text = stringResource(R.string.status_paused_until, timeStr),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulseAlpha"
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(activeColor.copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = stringResource(R.string.status_active),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = activeColor
                            )
                        }
                    }

                    if (isCurrentlyPaused) {
                        FilledTonalButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                PauseHelper.resume(context)
                                isCurrentlyPaused = false
                                pausedUntilTimestamp = 0L
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_resume), style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showPauseBottomSheet = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PauseCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.btn_pause), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Setup Card ("How it works" - System Domain Verification)
            // ─────────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setup_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    SetupStepItem(number = 1, text = stringResource(R.string.step1))
                    SetupStepItem(number = 2, text = stringResource(R.string.step2))
                    SetupStepItem(number = 3, text = stringResource(R.string.step3))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    context.startActivity(Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                } catch (e: Exception) {
                                    context.startActivity(Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                }
                            } else {
                                context.startActivity(Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                ))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_open_settings),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // System Domain Status Verification Badge
                    if (domainStatus != null) {
                        val status = domainStatus!!
                        if (status.isFullyEnabled) {
                            StatusBadge(
                                text = stringResource(R.string.status_all_active, status.totalHosts),
                                statusType = StatusType.ACTIVE
                            )
                        } else if (status.isPartiallyEnabled) {
                            val missingSample = status.unverifiedHosts.take(3).joinToString(", ")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                StatusBadge(
                                    text = stringResource(R.string.status_partial_active, status.enabledHosts, status.totalHosts),
                                    statusType = StatusType.WARNING
                                )
                                Text(
                                    text = stringResource(R.string.status_missing_hint, missingSample),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            StatusBadge(
                                text = stringResource(R.string.status_inactive),
                                statusType = StatusType.ERROR
                            )
                        }
                    } else {
                        when (linksActive) {
                            true -> StatusBadge(text = stringResource(R.string.status_active), statusType = StatusType.ACTIVE)
                            false -> StatusBadge(text = stringResource(R.string.status_inactive), statusType = StatusType.ERROR)
                            null -> Text(
                                text = stringResource(R.string.status_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Preferred Target Player Card (With Installed App Detection)
            // ─────────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.target_service_label),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.target_service_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = NightSlate400
                        )
                    }

                    targetServices.forEach { service ->
                        val isSelected = selectedTargetKey == service.key
                        val isInstalled = remember(service.key) {
                            PackageUtils.isAppInstalled(context, service.key)
                        }

                        val backgroundColor = if (isSelected) {
                            service.brandColor.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.background
                        }
                        val borderColor = if (isSelected) service.brandColor.copy(alpha = 0.6f) else Color.Transparent

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(backgroundColor)
                                .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTargetKey = service.key
                                    settingsRepository.targetPlatform = service.key
                                }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(service.brandColor)
                                )
                                Column {
                                    Text(
                                        text = stringResource(service.nameResId),
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (isInstalled) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = StateActiveGreen,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.status_installed),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StateActiveGreen
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Language,
                                                contentDescription = null,
                                                tint = NightSlate400,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.status_browser),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NightSlate400
                                            )
                                        }
                                    }
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = service.brandColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Intercept Source Links Configuration Card
            // ─────────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.input_sources_label),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.input_sources_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = NightSlate400
                        )
                    }

                    targetServices.forEach { service ->
                        var isEnabled by remember {
                            mutableStateOf(settingsRepository.isInputPlatformEnabled(service.key))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(service.brandColor)
                                )
                                Text(
                                    text = stringResource(service.nameResId),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { checked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isEnabled = checked
                                    settingsRepository.setInputPlatformEnabled(service.key, checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = service.brandColor
                                )
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Test Link Converter Studio Card
            // ─────────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.test_section_title),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.test_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = NightSlate400
                        )
                    }

                    OutlinedTextField(
                        value = testInputUrl,
                        onValueChange = { testInputUrl = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.test_placeholder),
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            if (testInputUrl.isEmpty()) {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clipData = clipboard.primaryClip
                                    if (clipData != null && clipData.itemCount > 0) {
                                        val text = clipData.getItemAt(0).text.toString()
                                        if (text.startsWith("http://") || text.startsWith("https://")) {
                                            testInputUrl = text
                                        }
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = stringResource(R.string.action_paste),
                                        tint = NightSlate400
                                    )
                                }
                            } else {
                                IconButton(onClick = { testInputUrl = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        tint = NightSlate400
                                    )
                                }
                            }
                        }
                    )

                    Button(
                        onClick = {
                            if (testInputUrl.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLoading = true
                                conversionResult = null
                                isError = false
                                scope.launch {
                                    val res = repository.resolveTargetUrl(
                                        inputUrl = testInputUrl.trim(),
                                        targetPlatformKey = selectedTargetKey,
                                        customApiUrl = settingsRepository.customApiUrl,
                                        customApiToken = settingsRepository.customApiToken
                                    )
                                    isLoading = false
                                    when (res) {
                                        is OdesliResult.Success -> {
                                            conversionResult = res.targetUrl
                                        }
                                        is OdesliResult.Error -> {
                                            isError = true
                                            conversionResult = res.message
                                        }
                                    }
                                }
                            }
                        },
                        enabled = testInputUrl.isNotBlank() && !isLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.test_converting), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text(
                                text = stringResource(R.string.test_button),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = conversionResult != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        conversionResult?.let { result ->
                            val textColor = if (isError) StateErrorRed else StateActiveGreen
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, textColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = if (isError) {
                                        stringResource(R.string.test_error, result)
                                    } else {
                                        stringResource(R.string.test_result, result)
                                    },
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (!isError) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                val clip = ClipData.newPlainText("SongFlip Link", result)
                                                clipboard.setPrimaryClip(clip)
                                                Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_copy), fontSize = 12.sp)
                                        }

                                        Button(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result))
                                                context.startActivity(viewIntent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(stringResource(R.string.action_open), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // FamWake Promo Card (MapFlip Style)
            // ─────────────────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.famwake_promo),
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = NightSlate400
                    )
                    Text(
                        text = stringResource(R.string.famwake_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.famwake_desc),
                        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                        textAlign = TextAlign.Center,
                        color = NightSlate400
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_FAMWAKE))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.famwake_button),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Copyright & Developer Footer (MapFlip Style)
            // ─────────────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.copyright_text),
                    style = MaterialTheme.typography.bodySmall.copy(
                        letterSpacing = 0.3.sp
                    ),
                    color = NightSlate400,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_NOTTHOFF))
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun SetupStepItem(number: Int, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

enum class StatusType {
    ACTIVE,
    WARNING,
    ERROR
}

@Composable
private fun StatusBadge(text: String, statusType: StatusType) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val activeColor = if (isDark) StateActiveGreen else StateActiveGreenLight
    val warningColor = if (isDark) StatePausedAmber else StatePausedAmberLight
    val errorColor = if (isDark) StateErrorRed else StateErrorRedLight

    val contentColor = when (statusType) {
        StatusType.ACTIVE -> activeColor
        StatusType.WARNING -> warningColor
        StatusType.ERROR -> errorColor
    }
    val targetBg = contentColor.copy(alpha = 0.12f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        color = targetBg,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (statusType) {
                StatusType.ACTIVE -> {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(activeColor.copy(alpha = pulseAlpha))
                    )
                }
                StatusType.WARNING -> {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = warningColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                StatusType.ERROR -> {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = contentColor
            )
        }
    }
}

@Composable
private fun PauseOptionItem(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Outlined.PauseCircle,
                contentDescription = null,
                tint = StatePausedAmber,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
