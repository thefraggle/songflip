package de.goork.songflip.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.goork.songflip.R
import de.goork.songflip.core.cache.FavoriteToggleResult
import de.goork.songflip.core.engine.AcousticFlipResult
import de.goork.songflip.core.engine.AcousticQuotaManager
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.data.AcousticRecognitionState
import de.goork.songflip.data.AcousticRecognizer
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.ProManager
import de.goork.songflip.ui.theme.BrandSpotify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenAndFlipBottomSheet(
    targetPlatformKey: String,
    isPro: Boolean,
    onDismissRequest: () -> Unit,
    onOpenProPaywall: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepository = remember(context) { de.goork.songflip.data.SettingsRepository(context) }

    val recognitionState by AcousticRecognizer.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }
    var isFavorited by remember { mutableStateOf(false) }
    var showQuickPicker by remember { mutableStateOf(false) }
    var quickPickerSourceUrl by remember { mutableStateOf<String?>(null) }
    var isResolvingAlternative by remember { mutableStateOf(false) }
    var remainingFreeScans by remember(isPro) {
        mutableStateOf(AcousticQuotaManager.shared.getRemainingFreeScans(isPro = isPro))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            AcousticRecognizer.startListening(
                context = context,
                targetPlatformKey = targetPlatformKey,
                isPro = isPro
            )
        } else {
            permissionDenied = true
        }
    }

    fun triggerScan() {
        remainingFreeScans = AcousticQuotaManager.shared.getRemainingFreeScans(isPro = isPro)
        if (!AcousticQuotaManager.shared.canStartScan(isPro = isPro)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("acoustic_quota_reached")
            onOpenProPaywall()
            return
        }

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudioPermission) {
            permissionDenied = false
            isFavorited = false
            AcousticRecognizer.startListening(
                context = context,
                targetPlatformKey = targetPlatformKey,
                isPro = isPro
            )
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Start listening automatically on sheet open if quota permits
    LaunchedEffect(Unit) {
        if (AcousticQuotaManager.shared.canStartScan(isPro = isPro)) {
            triggerScan()
        }
    }

    // Update remaining free scans and haptic feedback when a match occurs
    LaunchedEffect(recognitionState) {
        val current = recognitionState
        if (current is AcousticRecognitionState.Matched) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            remainingFreeScans = current.result.remainingFreeScans
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            AcousticRecognizer.resetState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            AcousticRecognizer.resetState()
            onDismissRequest()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .closeOnEsc {
                    AcousticRecognizer.resetState()
                    onDismissRequest()
                }
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = BrandSpotify,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.listen_and_flip_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        AcousticRecognizer.resetState()
                        onDismissRequest()
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.pause_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Free Quota Pill (for Free users)
            if (!isPro) {
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("acoustic_quota_badge")
                        onOpenProPaywall()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (remainingFreeScans > 0) {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (remainingFreeScans > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(
                                R.string.listen_free_scans_remaining,
                                remainingFreeScans,
                                AcousticQuotaManager.MAX_FREE_SCANS_PER_MONTH
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (remainingFreeScans > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            when {
                permissionDenied -> {
                    PermissionDeniedContent(
                        onRetryPermission = { triggerScan() }
                    )
                }
                !isPro && remainingFreeScans <= 0 && recognitionState !is AcousticRecognitionState.Matched -> {
                    QuotaExhaustedContent(
                        onUnlockPro = {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("acoustic_quota_exhausted")
                            onOpenProPaywall()
                        }
                    )
                }
                else -> {
                    when (val st = recognitionState) {
                        is AcousticRecognitionState.Idle,
                        is AcousticRecognitionState.Listening -> {
                            val listeningState = st as? AcousticRecognitionState.Listening
                            val amp = listeningState?.amplitude ?: 0.1f
                            val elapsed = listeningState?.elapsedSeconds ?: 0
                            ListeningPulseContent(
                                amplitude = amp,
                                elapsedSeconds = elapsed,
                                isListening = listeningState != null,
                                onToggleScan = {
                                    if (listeningState != null) {
                                        AcousticRecognizer.resetState()
                                    } else {
                                        triggerScan()
                                    }
                                }
                            )
                        }
                        is AcousticRecognitionState.Resolving -> {
                            ResolvingMatchContent(
                                title = st.title,
                                artist = st.artist
                            )
                        }
                        is AcousticRecognitionState.Matched -> {
                            MatchedResultContent(
                                result = st.result,
                                isPro = isPro,
                                isFavorited = isFavorited,
                                onOpenInPlayer = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    openTargetInPlayer(context, st.result.targetUrl, targetPlatformKey)
                                },
                                onChangeTarget = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    quickPickerSourceUrl = st.result.sourceUrl
                                    showQuickPicker = true
                                },
                                onToggleFavorite = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        val toggleRes = SongLinkEngine.shared.cache.toggleFavoriteWithLimit(
                                            cacheKey = st.result.cacheKey,
                                            isPro = isPro,
                                            maxFreeFavorites = 10
                                        )
                                        when (toggleRes) {
                                            FavoriteToggleResult.ADDED -> isFavorited = true
                                            FavoriteToggleResult.REMOVED -> isFavorited = false
                                            FavoriteToggleResult.LIMIT_REACHED -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.favorite_limit_reached),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                onOpenProPaywall()
                                            }
                                            else -> {}
                                        }
                                    }
                                },
                                onShare = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val urlToShare = if (isPro) {
                                        ProManager.warmupUniversalShare(st.result.sourceUrl)
                                        ProManager.getUniversalWebShareUrl(st.result.sourceUrl)
                                    } else {
                                        st.result.targetUrl
                                    }
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, urlToShare)
                                        putExtra(Intent.EXTRA_TITLE, "${st.result.title} – ${st.result.artist}")
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(shareIntent, st.result.title))
                                    } catch (_: Exception) {}
                                },
                                onScanAnother = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    triggerScan()
                                }
                            )
                        }
                        is AcousticRecognitionState.OfflineQueued -> {
                            OfflineQueuedContent(
                                pendingCount = st.pendingCount,
                                onDone = {
                                    AcousticRecognizer.resetState()
                                    onDismissRequest()
                                }
                            )
                        }
                        is AcousticRecognitionState.NoMatch -> {
                            NoMatchContent(
                                onRetry = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    triggerScan()
                                }
                            )
                        }
                        is AcousticRecognitionState.Error -> {
                            val errMsg = when (st.messageKey) {
                                "listen_error_offline" -> stringResource(R.string.listen_error_offline)
                                "listen_error_mic" -> stringResource(R.string.listen_permission_desc)
                                else -> stringResource(R.string.listen_no_match_subtitle)
                            }
                            ErrorContent(
                                message = errMsg,
                                isOfflineProGate = st.messageKey == "listen_error_offline" && !isPro,
                                onUnlockPro = {
                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed("listen_offline_club_mode")
                                    onOpenProPaywall()
                                },
                                onRetry = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    triggerScan()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    if (showQuickPicker && quickPickerSourceUrl != null) {
        val sUrl = quickPickerSourceUrl!!
        val currentMatch = recognitionState as? AcousticRecognitionState.Matched
        val matchTitle = currentMatch?.result?.title ?: ""
        val matchArtist = currentMatch?.result?.artist ?: ""

        QuickTargetPickerBottomSheet(
            trackTitle = matchTitle,
            artistName = matchArtist,
            isResolving = isResolvingAlternative,
            selectedPlatformKey = targetPlatformKey,
            onDismissRequest = { showQuickPicker = false },
            onPlatformSelected = { pickedTarget ->
                showQuickPicker = false
                isResolvingAlternative = true
                coroutineScope.launch {
                    val res = withContext(Dispatchers.IO) {
                        SongLinkEngine.shared.resolveTargetUrl(
                            inputUrl = sUrl,
                            targetPlatformKey = pickedTarget,
                            isPro = isPro,
                            audiophileMode = isPro && settingsRepository.audiophileMode
                        )
                    }
                    isResolvingAlternative = false
                    if (res is ResolutionResult.Success) {
                        openTargetInPlayer(context, res.targetUrl, pickedTarget)
                    } else {
                        Toast.makeText(context, context.getString(R.string.redirect_error_toast), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun ListeningPulseContent(
    amplitude: Float,
    elapsedSeconds: Int,
    isListening: Boolean,
    onToggleScan: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "listen_pulse")
    val outerScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isListening) (1.28f + amplitude * 0.25f) else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "outer_pulse"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(148.dp)
        ) {
            if (isListening) {
                Box(
                    modifier = Modifier
                        .size(124.dp)
                        .scale(outerScale)
                        .clip(CircleShape)
                        .background(BrandSpotify.copy(alpha = 0.14f))
                )
                Box(
                    modifier = Modifier
                        .size(102.dp)
                        .scale(1.0f + amplitude * 0.18f)
                        .clip(CircleShape)
                        .background(BrandSpotify.copy(alpha = 0.24f))
                )
            }

            Surface(
                onClick = onToggleScan,
                shape = CircleShape,
                color = BrandSpotify,
                shadowElevation = 8.dp,
                modifier = Modifier.size(84.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isListening) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.listen_and_flip_title),
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        val showQuietHint = isListening && elapsedSeconds >= 4 && amplitude < 0.15f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isListening) stringResource(R.string.listen_status_listening) else stringResource(R.string.listen_and_flip_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(if (showQuietHint) R.string.listen_status_hint else R.string.listen_and_flip_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResolvingMatchContent(
    title: String,
    artist: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
    ) {
        CircularProgressIndicator(
            color = BrandSpotify,
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.5.dp
        )
        Text(
            text = "$title – $artist",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.listen_status_resolving),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MatchedResultContent(
    result: AcousticFlipResult,
    isPro: Boolean,
    isFavorited: Boolean,
    onOpenInPlayer: () -> Unit,
    onChangeTarget: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: () -> Unit,
    onScanAnother: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Saved to History Confirmation Pill
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = BrandSpotify.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BrandSpotify.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = BrandSpotify,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = stringResource(R.string.listen_saved_to_history),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = BrandSpotify
                )
            }
        }

        // Track Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!result.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(result.artworkUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = result.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BrandSpotify.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = BrandSpotify,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = stringResource(R.string.badge_identified),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = BrandSpotify
                            )
                        }
                    }

                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (result.artist.isNotBlank()) {
                        Text(
                            text = result.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Primary CTA: Open in Preferred Player
        Button(
            onClick = onOpenInPlayer,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrandSpotify,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.listen_open_in_player, result.platformName),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }

        // Quick Target Changer
        TextButton(
            onClick = onChangeTarget,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.SwapHoriz,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.listen_change_target),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Secondary Actions Row: Favorite, Share, Scan Another
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .weight(1.4f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isFavorited) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (isFavorited) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(if (isFavorited) R.string.favorite_remove else R.string.favorite_add),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }

            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(0.9f)
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.listen_share_action),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp
                )
            }
        }

        TextButton(
            onClick = onScanAnother,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.listen_scan_again),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun OfflineQueuedContent(
    pendingCount: Int,
    onDone: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(BrandSpotify.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudQueue,
                contentDescription = null,
                tint = BrandSpotify,
                modifier = Modifier.size(34.dp)
            )
        }
        Text(
            text = stringResource(R.string.listen_offline_saved_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.listen_offline_saved_desc, pendingCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.action_close), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NoMatchContent(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = stringResource(R.string.listen_no_match_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.listen_no_match_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandSpotify, contentColor = Color.White)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.listen_try_again), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    isOfflineProGate: Boolean = false,
    onUnlockPro: () -> Unit = {},
    onRetry: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(
                if (isOfflineProGate) R.string.listen_offline_saved_title else R.string.listen_no_match_title
            ),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (isOfflineProGate) {
            Button(
                onClick = onUnlockPro,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandSpotify, contentColor = Color.White)
            ) {
                Text("💎 ${stringResource(R.string.listen_unlock_pro)}", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.listen_try_again), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(onRetryPermission: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MicOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = stringResource(R.string.listen_permission_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.listen_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onRetryPermission,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(stringResource(R.string.listen_permission_grant), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun QuotaExhaustedContent(onUnlockPro: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.listen_quota_reached_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.listen_quota_reached_desc, AcousticQuotaManager.MAX_FREE_SCANS_PER_MONTH),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onUnlockPro,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandSpotify, contentColor = Color.White)
        ) {
            Text(stringResource(R.string.listen_unlock_pro), fontWeight = FontWeight.Bold)
        }
    }
}

private fun openTargetInPlayer(context: Context, rawTargetUrl: String, platformKey: String) {
    val targetUrl = rawTargetUrl.replace("music.music.youtube.com", "music.youtube.com")
    val (targetPackage, nativeUri) = PackageUtils.resolveEffectiveLaunchTarget(context, targetUrl, platformKey)
    if (targetPackage != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(nativeUri)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                setPackage(targetPackage)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    val fallbackUrl = PackageUtils.toWebFallbackUrl(targetUrl, platformKey)
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
