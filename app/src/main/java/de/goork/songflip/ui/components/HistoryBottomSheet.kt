package de.goork.songflip.ui.components

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryBottomSheet(
    onDismissRequest: () -> Unit,
    isPro: Boolean = false,
    showProTeaser: Boolean = false,
    onOpenProPaywall: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val historyLimit = if (isPro) 100 else 10
    var isLoadingHistory by remember { mutableStateOf(true) }
    var historyItems by remember { mutableStateOf<List<LinkHistoryItem>>(emptyList()) }
    var historyCount by remember { mutableStateOf(0) }
    var showClearConfirmationDialog by remember { mutableStateOf(false) }
    var refreshingKeys by remember { mutableStateOf(setOf<String>()) }

    fun refreshHistory() {
        coroutineScope.launch {
            val items = SongLinkEngine.shared.cache.getHistoryEntries(limit = historyLimit)
            val count = SongLinkEngine.shared.cache.getHistoryCount()
            historyItems = items
            historyCount = count
            isLoadingHistory = false

            // Auto-hydrate cover artwork for older history entries in background
            val missingCoverItems = items.filter { it.thumbnailUrl.isNullOrBlank() }
            if (missingCoverItems.isNotEmpty()) {
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    var hasUpdates = false
                    for (missing in missingCoverItems) {
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
                        } catch (_: Throwable) {}
                    }
                    if (hasUpdates) {
                        val updated = SongLinkEngine.shared.cache.getHistoryEntries(limit = historyLimit)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            historyItems = updated
                        }
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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
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
                                    onOpenProPaywall()
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
                    IconButton(onClick = onDismissRequest) {
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
                // Empty State
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = stringResource(R.string.history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(
                        items = historyItems,
                        key = { it.cacheKey }
                    ) { item ->
                        HistoryItemCard(
                            item = item,
                            isPro = isPro,
                            isRefreshing = refreshingKeys.contains(item.cacheKey),
                            onPlay = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackHistoryItemClicked(item.platform)
                                openTargetUrl(context, item.targetUrl, item.platform)
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
                                    SongLinkEngine.shared.forceRefreshTargetUrl(
                                        inputUrl = item.canonicalUrl,
                                        targetPlatformKey = item.targetPlatformKey,
                                        isPro = isPro,
                                        authToken = de.goork.songflip.data.ProManager.getAuthToken()
                                    )
                                    refreshHistory()
                                    refreshingKeys = refreshingKeys - item.cacheKey
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, context.getString(R.string.history_link_refreshed), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShareUniversal = {
                                if (isPro) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val shareUrl = de.goork.songflip.data.ProManager.getUniversalWebShareUrl(item.canonicalUrl)
                                    de.goork.songflip.data.ProManager.warmupUniversalShare(item.canonicalUrl)
                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackSharePageGenerated(target = "history")
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                        putExtra(Intent.EXTRA_TITLE, item.title ?: "SongFlip Link")
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_universal_link)))
                                } else {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenProPaywall()
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
                                    .clickable { onOpenProPaywall() }
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
                                        Text(
                                            text = stringResource(R.string.history_pro_teaser_desc),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = onOpenProPaywall,
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

@Composable
fun HistoryItemCard(
    item: LinkHistoryItem,
    isPro: Boolean = false,
    isRefreshing: Boolean = false,
    onPlay: () -> Unit,
    onCopyTarget: () -> Unit,
    onCopySource: () -> Unit,
    onRefresh: () -> Unit,
    onShareUniversal: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val sourcePlatformKey = remember(item.canonicalUrl) {
        PackageUtils.detectPlatformFromUrl(item.canonicalUrl)
    }
    val sourceDisplayName = remember(sourcePlatformKey) {
        if (sourcePlatformKey.isNotEmpty()) PackageUtils.getPlatformDisplayName(sourcePlatformKey) else "Music Link"
    }
    val targetDisplayName = remember(item.platform) {
        PackageUtils.getPlatformDisplayName(item.platform)
    }
    val relativeTime = remember(item.timestamp) {
        formatRelativeTime(item.timestamp, context)
    }

    val isArtist = remember(item.platform) { item.platform.contains("_artist") }
    val isPlaylist = remember(item.platform) { item.platform.contains("_playlist") }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Cover Artwork (Left: 52x52dp with Play Overlay)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!item.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.thumbnailUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val fallbackIcon = when {
                        isArtist -> Icons.Outlined.Person
                        isPlaylist -> Icons.AutoMirrored.Outlined.QueueMusic
                        item.isAlbum -> Icons.Outlined.Album
                        else -> Icons.Outlined.MusicNote
                    }
                    Icon(
                        imageVector = fallbackIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Media type icon badge (Playlist / Album / Artist) top-start
                if (isPlaylist || item.isAlbum || isArtist) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isArtist -> Icons.Outlined.Person
                                isPlaylist -> Icons.AutoMirrored.Outlined.QueueMusic
                                else -> Icons.Outlined.Album
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }

                // Subtiles Play-Indicator Overlay unten rechts auf dem Cover
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_open),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // 2. Middle Column: Platform Tag + Title + Artist / Metadata
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Platform Transition Tag Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PlatformTag(name = sourceDisplayName, isSource = true)
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PlatformTag(name = targetDisplayName, isSource = false)
                }

                // Title
                Text(
                    text = item.title ?: item.targetUrl,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Subtitle: Artist/Track count • Type • Relative Time
                val typeLabel = when {
                    isPlaylist -> stringResource(R.string.badge_playlist)
                    item.isAlbum -> stringResource(R.string.badge_album)
                    isArtist -> stringResource(R.string.badge_artist)
                    else -> null
                }
                val subtitlePrefix = item.artist?.takeIf { it.isNotBlank() }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (subtitlePrefix != null) {
                        Text(
                            text = subtitlePrefix,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    if (typeLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }

            // 3. Right Actions: Share (FlipPage) + Overflow Menu (⋮)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(
                    onClick = onShareUniversal,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = stringResource(if (isPro) R.string.share_universal_link else R.string.share_universal_link_pro),
                            tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(19.dp)
                        )
                        if (!isPro) {
                            Text(
                                text = "💎",
                                fontSize = 8.sp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                            )
                        }
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_copy_target, targetDisplayName)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onCopyTarget()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_copy_source, sourceDisplayName)) },
                            leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onCopySource()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_refresh_link)) },
                            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onRefresh()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformTag(name: String, isSource: Boolean) {
    val bgColor = if (isSource) {
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val textColor = if (isSource) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

private fun formatRelativeTime(timestamp: Long, context: Context): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> context.getString(R.string.time_just_now)
        minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes)
        hours < 24 -> context.getString(R.string.time_hours_ago, hours)
        else -> context.getString(R.string.time_days_ago, days)
    }
}

private fun openTargetUrl(context: Context, rawTargetUrl: String, platformKey: String) {
    val targetUrl = if (rawTargetUrl.contains("music.music.youtube.com")) {
        rawTargetUrl.replace("music.music.youtube.com", "music.youtube.com")
    } else rawTargetUrl
    try {
        val nativeUriString = PackageUtils.toNativeAppUri(targetUrl, platformKey)
        val uri = Uri.parse(nativeUriString)
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val targetPackage = PackageUtils.getInstalledPackage(context, platformKey)
        if (targetPackage != null) {
            intent.setPackage(targetPackage)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        } catch (ignored: Exception) {}
    }
}

private fun copyToClipboard(context: Context, rawText: String) {
    val text = if (rawText.contains("music.music.youtube.com")) {
        rawText.replace("music.music.youtube.com", "music.youtube.com")
    } else rawText
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Target Music Link", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
}
