package de.goork.songflip.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import de.goork.songflip.R
import de.goork.songflip.data.DomainVerificationUtils
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.PauseHelper
import de.goork.songflip.data.SettingsRepository
import de.goork.songflip.ui.components.PauseBottomSheet
import de.goork.songflip.ui.components.SettingsBottomSheet
import de.goork.songflip.ui.components.TestStudioBottomSheet
import de.goork.songflip.ui.theme.*

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
            ServiceInfo("youtubeMusic", R.string.target_youtube_music, BrandYouTubeMusic),
            ServiceInfo("appleMusic", R.string.target_apple_music, BrandAppleMusic),
            ServiceInfo("spotify", R.string.target_spotify, BrandSpotify),
            ServiceInfo("tidal", R.string.target_tidal, BrandTidal),
            ServiceInfo("deezer", R.string.target_deezer, BrandDeezer),
            ServiceInfo("amazonMusic", R.string.target_amazon_music, BrandAmazonMusic)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pause Bottom Sheet
    // ─────────────────────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────────────────────
    // Test Studio Bottom Sheet
    // ─────────────────────────────────────────────────────────────────────────
    if (showTestStudioBottomSheet) {
        TestStudioBottomSheet(
            onDismissRequest = { showTestStudioBottomSheet = false },
            selectedTargetKey = selectedTargetKey,
            settingsRepository = settingsRepository,
            odesliRepository = repository
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings Bottom Sheet
    // ─────────────────────────────────────────────────────────────────────────
    if (showSettingsBottomSheet) {
        SettingsBottomSheet(
            onDismissRequest = { showSettingsBottomSheet = false },
            settingsRepository = settingsRepository,
            targetServices = targetServices,
            supportedLanguages = supportedLanguages,
            currentLanguageCode = selectedLanguage,
            onLanguageSelected = { newLang ->
                selectedLanguage = newLang
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main UI Layout
    // ─────────────────────────────────────────────────────────────────────────
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
            // App Header Banner with Quick Actions (Accessible Touch Targets >= 48dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(NightSlate900, NightSlate800)
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
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Test Studio Action Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showTestStudioBottomSheet = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NightSlate700.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Science,
                                contentDescription = stringResource(R.string.nav_test_studio),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Settings Action Button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showSettingsBottomSheet = true
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NightSlate700.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.nav_settings),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // Live Status & Quick Pause Banner Card (Active vs. Paused only)
            // ─────────────────────────────────────────────────────────────────
            val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
            val activeColor = if (isDarkTheme) StateActiveGreen else StateActiveGreenLight
            val pausedColor = if (isDarkTheme) StatePausedAmber else StatePausedAmberLight

            val (bannerBgColor, bannerBorderColor) = if (isCurrentlyPaused) {
                pausedColor.copy(alpha = 0.12f) to pausedColor.copy(alpha = 0.4f)
            } else {
                activeColor.copy(alpha = 0.12f) to activeColor.copy(alpha = 0.4f)
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = bannerBgColor),
                border = androidx.compose.foundation.BorderStroke(1.dp, bannerBorderColor)
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
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
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
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            MaterialTheme.colorScheme.surfaceVariant
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
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
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
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.status_browser),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            val appVersion = remember {
                try {
                    "v" + context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) {
                    "v1.0.0"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "SongFlip $appVersion",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "© 2026 Daniel Notthoff",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://notthoff.org")))
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.legal_privacy),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://songflip.link/privacy-policy.html")))
                        }
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.legal_imprint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://songflip.link/imprint.html")))
                        }
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = stringResource(R.string.legal_terms),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://songflip.link/terms.html")))
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
