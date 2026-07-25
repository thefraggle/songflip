package com.songflip.ui

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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.songflip.R
import com.songflip.data.DomainVerificationUtils
import com.songflip.data.OdesliRepository
import com.songflip.data.OdesliResult
import com.songflip.data.PackageUtils
import com.songflip.data.SettingsRepository
import com.songflip.ui.theme.SongFlipTheme
import kotlinx.coroutines.launch

private const val URL_FAMWAKE = "https://play.google.com/store/apps/details?id=de.familienwecker.famwake"
private const val URL_NOTTHOFF = "https://notthoff.org"

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SongFlipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
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
fun MainScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { OdesliRepository() }
    val settingsRepository = remember { SettingsRepository(context) }

    var selectedTargetKey by remember { mutableStateOf(settingsRepository.targetPlatform) }
    var selectedLanguage by remember { mutableStateOf(settingsRepository.appLanguage) }
    var showLanguageBottomSheet by remember { mutableStateOf(false) }

    var linksActive by remember { mutableStateOf<Boolean?>(DomainVerificationUtils.checkLinksEnabled(context)) }

    // Live update when returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                linksActive = DomainVerificationUtils.checkLinksEnabled(context)
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
            ServiceInfo("youtubeMusic", R.string.target_youtube_music, Color(0xFFFF0000)),
            ServiceInfo("appleMusic", R.string.target_apple_music, Color(0xFFFA243C)),
            ServiceInfo("spotify", R.string.target_spotify, Color(0xFF1DB954)),
            ServiceInfo("tidal", R.string.target_tidal, Color(0xFF00F3FF)),
            ServiceInfo("deezer", R.string.target_deezer, Color(0xFFA238FF))
        )
    }

    // Language Selector BottomSheet
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
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))

                supportedLanguages.forEach { lang ->
                    val isSelected = selectedLanguage == lang.code
                    Surface(
                        onClick = {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // App Header Banner with Compact Top-Right Language Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF1DB954).copy(alpha = 0.5f),
                            Color(0xFFFF0000).copy(alpha = 0.5f)
                        )
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF1DB954), Color(0xFFFF0000))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Compact Subtle Language Switcher Button (Analog MapFlip 🌐)
                IconButton(
                    onClick = { showLanguageBottomSheet = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF334155).copy(alpha = 0.5f))
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = stringResource(R.string.select_language_title),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Setup Card ("So geht's / How it works" - MapFlip Style)
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
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.btn_open_settings),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.Black
                    )
                }

                // Dynamic Status Badge (Exact MapFlip System Domain Status Verification)
                when (linksActive) {
                    true -> StatusBadge(text = stringResource(R.string.status_active), active = true)
                    false -> StatusBadge(text = stringResource(R.string.status_inactive), active = false)
                    null -> Text(
                        text = stringResource(R.string.status_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        // Intercept Source Links Configuration Card
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
                        color = Color(0xFF94A3B8)
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
                            .padding(horizontal = 14.dp, vertical = 8.dp),
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

        // Target Service Selector Card with App Installation Detection
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
                        color = Color(0xFF94A3B8)
                    )
                }

                targetServices.forEach { service ->
                    val isSelected = selectedTargetKey == service.key
                    val isInstalled = remember(service.key) {
                        PackageUtils.isAppInstalled(context, service.key)
                    }

                    val backgroundColor = if (isSelected) {
                        service.brandColor.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.background
                    }
                    val borderColor = if (isSelected) service.brandColor else Color.Transparent

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(backgroundColor)
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .clickable {
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
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(service.brandColor)
                            )
                            Column {
                                Text(
                                    text = stringResource(service.nameResId),
                                    style = MaterialTheme.typography.bodyLarge.copy(
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
                                            tint = Color(0xFF1DB954),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.status_installed),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF1DB954)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Language,
                                            contentDescription = null,
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.status_browser),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = service.brandColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // Test Link Converter Card
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
                        color = Color(0xFF94A3B8)
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
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        } else {
                            IconButton(onClick = { testInputUrl = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                )

                Button(
                    onClick = {
                        if (testInputUrl.isNotBlank()) {
                            isLoading = true
                            conversionResult = null
                            isError = false
                            scope.launch {
                                val res = repository.resolveTargetUrl(
                                    testInputUrl.trim(),
                                    selectedTargetKey
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.test_converting))
                    } else {
                        Text(
                            text = stringResource(R.string.test_button),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                AnimatedVisibility(
                    visible = conversionResult != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    conversionResult?.let { result ->
                        val textColor = if (isError) MaterialTheme.colorScheme.error else Color(0xFF1DB954)
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

        // FamWake Promo Card (MapFlip Style)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.famwake_promo),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF94A3B8)
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
                    color = Color(0xFF94A3B8)
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

        // Copyright Footer (MapFlip Style: © 2026 Daniel Notthoff • notthoff.org)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.copyright_text),
                style = MaterialTheme.typography.bodySmall.copy(
                    letterSpacing = 0.3.sp
                ),
                color = Color(0xFF64748B),
                modifier = Modifier.clickable {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(URL_NOTTHOFF))
                    context.startActivity(intent)
                }
            )
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
            modifier = Modifier.size(30.dp)
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    val targetBg = if (active) Color(0xFF1DB954).copy(alpha = 0.12f) else Color(0xFFFF0000).copy(alpha = 0.12f)
    val contentColor = if (active) Color(0xFF1DB954) else Color(0xFFFF0000)

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
            if (active) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1DB954).copy(alpha = pulseAlpha))
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp)
                )
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
