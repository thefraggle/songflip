package de.goork.songflip.ui.components

import de.goork.songflip.ui.components.history.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.goork.songflip.R
import de.goork.songflip.core.cache.LinkHistoryItem
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.data.PackageUtils

private val attemptedHydrationKeys = mutableSetOf<String>()
private var isHydratingThumbnails = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    onDismissRequest: () -> Unit,
    isPro: Boolean = false,
    showProTeaser: Boolean = false,
    onOpenProPaywall: (String) -> Unit = {},
    targetPlatformKey: String = "",
    onOpenPlaylist: ((playlistUrl: String, targetPlatformKey: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val settingsRepo = remember(context) { de.goork.songflip.data.SettingsRepository(context) }
    val effectiveTargetKey = targetPlatformKey.ifBlank { settingsRepo.targetPlatform }

    val historyLimit = if (isPro) 100 else 10
    var isLoadingHistory by remember { mutableStateOf(true) }
    var historyItems by remember { mutableStateOf<List<LinkHistoryItem>>(emptyList()) }
    var historyCount by remember { mutableStateOf(0) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }
    var refreshingKeys by remember { mutableStateOf(setOf<String>()) }
    var qrCodeItem by remember { mutableStateOf<LinkHistoryItem?>(null) }
    var disambiguateItem by remember { mutableStateOf<LinkHistoryItem?>(null) }
    var showOnlyFavorites by remember { mutableStateOf(false) }

    fun refreshHistory() {
        coroutineScope.launch {
            val items = SongLinkEngine.shared.cache.getHistoryEntries(limit = historyLimit)
            val count = SongLinkEngine.shared.cache.getHistoryCount()
            historyItems = items
            historyCount = count
            isLoadingHistory = false

            // Auto-hydrate cover artwork for older history entries in background (capped to 5 per run, cached attempted keys)
            val missingCoverItems = items.filter {
                it.thumbnailUrl.isNullOrBlank() &&
                    !it.targetPlatformKey.contains("_playlist") &&
                    !attemptedHydrationKeys.contains("${it.canonicalUrl}|${it.targetPlatformKey}")
            }
            if (missingCoverItems.isNotEmpty() && !isHydratingThumbnails) {
                isHydratingThumbnails = true
                val batchToHydrate = missingCoverItems.take(5)
                for (item in batchToHydrate) {
                    attemptedHydrationKeys.add("${item.canonicalUrl}|${item.targetPlatformKey}")
                }
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        var hasUpdates = false
                        for (missing in batchToHydrate) {
                            try {
                                val res = SongLinkEngine.shared.forceRefreshTargetUrl(
                                    inputUrl = missing.canonicalUrl,
                                    targetPlatformKey = missing.targetPlatformKey,
                                    isPro = isPro,
                                    authToken = de.goork.songflip.data.ProManager.getAuthToken()
                                )
                                if (res is de.goork.songflip.core.model.ResolutionResult.Success && !res.thumbnailUrl.isNullOrBlank()) {
                                    hasUpdates = true
                                }
                            } catch (t: Throwable) {
                                if (t is kotlinx.coroutines.CancellationException) throw t
                                // Thumbnail backfill is best-effort; one failing entry must not stop the rest.
                                android.util.Log.w("HistoryBottomSheet", "Thumbnail refresh failed: ${t.message}")
                            }
                        }
                        if (hasUpdates) {
                            val updated = SongLinkEngine.shared.cache.getHistoryEntries(limit = historyLimit)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                historyItems = updated
                            }
                        }
                    } finally {
                        isHydratingThumbnails = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
    }

    if (showClearConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmationDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_title),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        coroutineScope.launch {
                            SongLinkEngine.shared.cache.clearHistoryAndCache()
                            refreshHistory()
                        }
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackHistoryCleared()
                        showClearConfirmationDialog = false
                        Toast.makeText(context, context.getString(R.string.history_all_cleared), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmationDialog = false }) {
                    Text(stringResource(R.string.pause_cancel))
                }
            }
        )
    }

    if (isPro) {
        qrCodeItem?.let { item ->
            val isPl = item.platform.contains("_playlist")
            val shareUrl = if (isPl) {
                val targetKey = item.targetPlatformKey.ifBlank { item.platform.removeSuffix("_playlist") }
                de.goork.songflip.data.ProManager.getPlaylistWebShareUrl(item.canonicalUrl, targetKey)
            } else {
                de.goork.songflip.data.ProManager.getUniversalWebShareUrl(item.canonicalUrl)
            }
            QRCodeDialog(
                url = shareUrl,
                title = item.title,
                artist = item.artist,
                onDismissRequest = { qrCodeItem = null }
            )
        }
    }

    disambiguateItem?.let { item ->
        DisambiguationBottomSheet(
            cacheKey = item.cacheKey,
            title = item.title ?: "",
            artist = item.artist ?: "",
            targetPlatformKey = item.targetPlatformKey,
            currentMatchedUrl = item.targetUrl,
            onDismissRequest = { disambiguateItem = null },
            onMatchUpdated = {
                refreshHistory()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .closeOnEsc(onDismissRequest)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.history_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (historyCount > 0 && historyItems.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "${historyItems.size}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    if (historyItems.isNotEmpty()) {
                        Text(
                            text = if (isPro) {
                                pluralStringResource(R.plurals.history_capacity_pro, historyItems.size, historyItems.size, historyLimit)
                            } else {
                                pluralStringResource(R.plurals.history_capacity_free, historyItems.size, historyItems.size, historyLimit)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!isPro) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (!isPro) {
                                Modifier.clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenProPaywall("history_header")
                                }
                            } else Modifier
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (historyItems.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showClearConfirmationDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.history_clear_all),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.pause_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isLoadingHistory) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (historyItems.isEmpty()) {
                // Modern Empty State with Decorative Badge + Title + Description + Helpful Tip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp, vertical = 24.dp)
                    ) {
                        // Decorative Icon Badge with Layered Depth
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Title & Description
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.history_empty_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.history_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.widthIn(max = 290.dp)
                            )
                        }

                        // Helpful Tip Banner
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.widthIn(max = 340.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "💡",
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_hint),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            } else {
                val favoriteCount = remember(historyItems) { historyItems.count { it.isFavorite } }
                val identifiedCount = remember(historyItems) { historyItems.count { it.isAcoustic } }
                var filterMode by remember { mutableStateOf("all") }
                val displayedItems = when (filterMode) {
                    "favorites" -> historyItems.filter { it.isFavorite }
                    "identified" -> historyItems.filter { it.isAcoustic }
                    else -> historyItems
                }

                if (favoriteCount > 0 || identifiedCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterMode == "all",
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                filterMode = "all"
                            },
                            label = { Text(stringResource(R.string.history_filter_all)) }
                        )
                        if (favoriteCount > 0) {
                            FilterChip(
                                selected = filterMode == "favorites",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    filterMode = "favorites"
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color(0xFFFFB800)
                                    )
                                },
                                label = { Text("${stringResource(R.string.history_filter_favorites)} ($favoriteCount)") }
                            )
                        }
                        if (identifiedCount > 0) {
                            FilterChip(
                                selected = filterMode == "identified",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    filterMode = "identified"
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                label = { Text("${stringResource(R.string.badge_identified)} ($identifiedCount)") }
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = displayedItems,
                        key = { it.cacheKey }
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            isPro = isPro,
                            isRefreshing = refreshingKeys.contains(item.cacheKey),
                            onToggleFavorite = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    val result = SongLinkEngine.shared.cache.toggleFavoriteWithLimit(
                                        cacheKey = item.cacheKey,
                                        isPro = isPro,
                                        maxFreeFavorites = de.goork.songflip.core.engine.ProLimits.MAX_FREE_FAVORITES
                                    )
                                    if (result == de.goork.songflip.core.cache.FavoriteToggleResult.LIMIT_REACHED) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.favorite_limit_reached),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        onOpenProPaywall("favorite_limit_reached")
                                    } else {
                                        refreshHistory()
                                    }
                                }
                            },
                            onShowQrCode = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                qrCodeItem = item
                            },
                            onDisambiguate = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isPro) {
                                    disambiguateItem = item
                                } else {
                                    onOpenProPaywall("history_disambiguate_pro")
                                }
                            },
                            onPlay = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackHistoryItemClicked(item.platform)
                                openTargetUrl(context, item.targetUrl, item.platform)
                            },
                            onOpenPlaylist = onOpenPlaylist?.let { cb ->
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val targetKey = item.targetPlatformKey.ifBlank { item.platform.removeSuffix("_playlist") }
                                    cb(item.canonicalUrl, targetKey)
                                }
                            },
                            onReconvertPlaylist = onOpenPlaylist?.let { cb ->
                                {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    cb(item.canonicalUrl, effectiveTargetKey)
                                }
                            },
                            onCopyTarget = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                copyToClipboard(context, item.targetUrl)
                            },
                            onCopySource = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                copyToClipboard(context, item.canonicalUrl)
                            },
                            onRefresh = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    refreshingKeys = refreshingKeys + item.cacheKey
                                    Toast.makeText(context, context.getString(R.string.history_link_refreshing), Toast.LENGTH_SHORT).show()
                                    val isPl = item.platform.contains("_playlist") || de.goork.songflip.core.util.UrlUtils.isPlaylistUrl(item.canonicalUrl)
                                    val refreshedOk = if (isPl) {
                                        val targetKey = item.targetPlatformKey.ifBlank { item.platform.removeSuffix("_playlist") }
                                        val maxTracks = if (isPro) {
                                            de.goork.songflip.core.engine.ProLimits.MAX_PRO_PLAYLIST_TRACKS
                                        } else {
                                            de.goork.songflip.core.engine.ProLimits.MAX_FREE_PLAYLIST_TRACKS
                                        }
                                        val plRes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            de.goork.songflip.core.engine.PlaylistConverterEngine.shared.convertPlaylist(
                                                url = item.canonicalUrl,
                                                targetPlatformKey = targetKey,
                                                isPro = isPro,
                                                maxTracks = maxTracks,
                                                authToken = de.goork.songflip.data.ProManager.getAuthToken(),
                                                forceRefresh = true
                                            )
                                        }
                                        if (plRes.isSuccess) {
                                            val data = plRes.getOrThrow()
                                            val targetUrl = data.zeroOAuthUrl ?: data.webShareUrl ?: item.canonicalUrl
                                            val displayTotal = maxOf(data.totalTracks, data.originalTotalTracks)
                                            SongLinkEngine.shared.cache.put(
                                                canonicalUrl = item.canonicalUrl,
                                                targetPlatformKey = targetKey,
                                                result = de.goork.songflip.core.model.ResolutionResult.Success(
                                                    targetUrl = targetUrl,
                                                    platform = "${targetKey}_playlist",
                                                    title = data.title.ifBlank { item.title ?: "Playlist" },
                                                    artist = context.getString(R.string.history_playlist_tracks_summary, data.matchedCount, displayTotal),
                                                    isAlbum = false,
                                                    thumbnailUrl = data.thumbnailUrl ?: item.thumbnailUrl
                                                ),
                                                isHistory = true
                                            )
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        val settingsRepo = de.goork.songflip.data.SettingsRepository(context)
                                        val result = try {
                                            SongLinkEngine.shared.forceRefreshTargetUrl(
                                                inputUrl = item.canonicalUrl,
                                                targetPlatformKey = item.targetPlatformKey,
                                                isPro = isPro,
                                                authToken = de.goork.songflip.data.ProManager.getAuthToken(),
                                                audiophileMode = isPro && settingsRepo.audiophileMode
                                            )
                                        } catch (_: Exception) {
                                            null
                                        }
                                        result is de.goork.songflip.core.model.ResolutionResult.Success
                                    }
                                    refreshHistory()
                                    refreshingKeys = refreshingKeys - item.cacheKey
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (refreshedOk) {
                                        Toast.makeText(context, context.getString(R.string.history_link_refreshed), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.pro_coupon_network_error), Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onShareUniversal = {
                                if (isPro) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val isPl = item.platform.contains("_playlist")
                                    val shareUrl = if (isPl) {
                                        val targetKey = item.targetPlatformKey.ifBlank { item.platform.removeSuffix("_playlist") }
                                        de.goork.songflip.data.ProManager.getPlaylistWebShareUrl(item.canonicalUrl, targetKey)
                                    } else {
                                        de.goork.songflip.data.ProManager.getUniversalWebShareUrl(item.canonicalUrl)
                                    }
                                    if (!isPl) {
                                        de.goork.songflip.data.ProManager.warmupUniversalShare(item.canonicalUrl)
                                    }
                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackSharePageGenerated(target = if (isPl) "history_playlist" else "history")
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        putExtra(Intent.EXTRA_TITLE, item.title ?: "SongFlip Link")
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_universal_link)))
                                    } catch (e: Exception) {
                                        de.goork.songflip.core.util.SongFlipLogger.w("HistoryBottomSheet", "Failed to launch share chooser: ${e.message}", e)
                                        Toast.makeText(context, context.getString(R.string.redirect_error_toast), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenProPaywall("history_universal_share")
                                }
                            },
                            onDelete = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                coroutineScope.launch {
                                    SongLinkEngine.shared.cache.removeByCacheKey(item.cacheKey)
                                    refreshHistory()
                                }
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.history_item_deleted),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }

                    // Pro Teaser Card if there are more than 10 items in history
                    if (showProTeaser && !isPro && historyCount > 10) {
                        item {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onOpenProPaywall("history_teaser_bottom")
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "💎 " + stringResource(R.string.history_pro_teaser_title),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        val hiddenCount = historyCount - 10
                                        Text(
                                            text = pluralStringResource(
                                                R.plurals.history_pro_teaser_hidden_desc,
                                                hiddenCount,
                                                hiddenCount,
                                                historyCount
                                            ),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onOpenProPaywall("history_teaser_bottom")
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.pro_title), fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

