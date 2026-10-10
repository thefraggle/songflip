package de.goork.songflip.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.goork.songflip.R
import de.goork.songflip.core.analytics.AptabaseClient
import de.goork.songflip.core.engine.CatalogSearchEngine
import de.goork.songflip.core.engine.SearchTrackResult
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.ProManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBottomSheet(
    targetPlatformKey: String,
    onDismissRequest: () -> Unit,
    onOpenProPaywall: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val proState by ProManager.proState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchTrackResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }

    var qrCodeTrack by remember { mutableStateOf<SearchTrackResult?>(null) }
    var pickerTrack by remember { mutableStateOf<SearchTrackResult?>(null) }

    val listState = rememberLazyListState()

    // Dismiss keyboard automatically when user scrolls the results list
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            keyboardController?.hide()
        }
    }

    // Debounced search trigger
    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }
        delay(350L)
        isLoading = true
        errorMessage = null
        val result = withContext(Dispatchers.IO) {
            CatalogSearchEngine.shared.search(trimmed, limit = 20)
        }
        isLoading = false
        result.onSuccess { list ->
            searchResults = list
            if (list.isEmpty()) {
                errorMessage = context.getString(R.string.search_empty)
            }
        }.onFailure { err ->
            errorMessage = err.message ?: context.getString(R.string.search_error)
        }
    }

    val targetDisplayName = remember(targetPlatformKey) {
        PackageUtils.getPlatformDisplayName(targetPlatformKey)
    }

    fun flipTrack(track: SearchTrackResult) {
        val sourceUrl = track.sourceUrl
        if (sourceUrl.isNullOrBlank() || resolvingTrackId != null) return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        resolvingTrackId = track.id
        coroutineScope.launch {
            val res = withContext(Dispatchers.IO) {
                SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl = sourceUrl,
                    targetPlatformKey = targetPlatformKey,
                    isPro = proState.isPro
                )
            }
            resolvingTrackId = null
            when (res) {
                is ResolutionResult.Success -> {
                    SongLinkEngine.shared.cache.markAsHistory(sourceUrl, targetPlatformKey)
                    AptabaseClient.shared.trackLinkFlipped(
                        target = targetPlatformKey,
                        isAlbum = res.isAlbum,
                        isSearch = res.isSearchFallback,
                        source = "in_app_search"
                    )
                    try {
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(res.targetUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(playIntent)
                        onDismissRequest()
                    } catch (_: Exception) {
                        Toast.makeText(context, context.getString(R.string.redirect_error_toast), Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    Toast.makeText(context, context.getString(R.string.redirect_error_toast), Toast.LENGTH_SHORT).show()
                }
            }
        }
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
                .closeOnEsc(onDismissRequest)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.search_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.pause_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (!errorMessage.isNullOrBlank() && searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (searchResults.isEmpty() && searchQuery.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.search_initial_prompt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(searchResults, key = { it.id }) { track ->
                        val isResolving = resolvingTrackId == track.id
                        var showMenu by remember { mutableStateOf(false) }

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isResolving) {
                                    flipTrack(track)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cover Art mit Play-Overlay
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
                                        .clickable(enabled = !isResolving) {
                                            flipTrack(track)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!track.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(track.coverUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Outlined.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    // Subtle Play Indicator Badge (unten rechts auf dem Cover)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PlayArrow,
                                            contentDescription = stringResource(R.string.action_open),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }

                                    if (isResolving) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(22.dp),
                                                strokeWidth = 2.dp,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Metadata
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val albumOrPlatform = track.album?.takeIf { it.isNotBlank() } ?: targetDisplayName
                                    Text(
                                        text = albumOrPlatform,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Option A: Aufgeräumtes 3-Punkte-Aktionsmenü (⋮)
                                Box {
                                    IconButton(
                                        onClick = { showMenu = true },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        // 1. Teilen / FlipPage (mit 💎 bei Free)
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(stringResource(R.string.share_universal_link))
                                                    if (!proState.isPro) {
                                                        Text("💎", fontSize = 12.sp)
                                                    }
                                                }
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Share,
                                                    contentDescription = null,
                                                    tint = if (proState.isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                val sUrl = track.sourceUrl
                                                if (sUrl.isNullOrBlank()) return@DropdownMenuItem
                                                if (proState.isPro) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val shareUrl = ProManager.getUniversalWebShareUrl(sUrl)
                                                    ProManager.warmupUniversalShare(sUrl)
                                                    AptabaseClient.shared.trackSharePageGenerated(target = "search")
                                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, shareUrl)
                                                        putExtra(Intent.EXTRA_TITLE, track.title)
                                                    }
                                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_universal_link)))
                                                } else {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onOpenProPaywall()
                                                }
                                            }
                                        )

                                        // 2. QR-Code zur FlipPage (NUR bei Pro sichtbar!)
                                        if (proState.isPro) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.history_qr_action)) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.QrCode,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showMenu = false
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    qrCodeTrack = track
                                                }
                                            )
                                        }

                                        // 3. In anderem Player öffnen
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.quick_picker_title)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.Tune,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                pickerTrack = track
                                            }
                                        )

                                        // 4. Link kopieren
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_copy)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.ContentCopy,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                val sUrl = track.sourceUrl
                                                if (!sUrl.isNullOrBlank()) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(ClipData.newPlainText("SongFlip Link", sUrl))
                                                    Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )

                                        // 5. Zu Favoriten hinzufügen
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.favorite_add)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Outlined.StarBorder,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFB800),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                showMenu = false
                                                val sUrl = track.sourceUrl
                                                if (!sUrl.isNullOrBlank()) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    coroutineScope.launch {
                                                        val res = withContext(Dispatchers.IO) {
                                                            SongLinkEngine.shared.resolveTargetUrl(
                                                                inputUrl = sUrl,
                                                                targetPlatformKey = targetPlatformKey,
                                                                isPro = proState.isPro
                                                            )
                                                        }
                                                        if (res is ResolutionResult.Success) {
                                                            SongLinkEngine.shared.cache.markAsHistory(sUrl, targetPlatformKey)
                                                            SongLinkEngine.shared.cache.setFavoriteForUrl(sUrl, targetPlatformKey, true)
                                                            Toast.makeText(context, context.getString(R.string.favorite_add), Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // QR-Code Dialog (nur Pro)
    qrCodeTrack?.let { tr ->
        val sUrl = tr.sourceUrl
        if (!sUrl.isNullOrBlank()) {
            val shareUrl = ProManager.getUniversalWebShareUrl(sUrl)
            QRCodeDialog(
                url = shareUrl,
                title = tr.title,
                artist = tr.artist,
                onDismissRequest = { qrCodeTrack = null }
            )
        }
    }

    // Quick Target Picker Sheet
    pickerTrack?.let { tr ->
        val sUrl = tr.sourceUrl
        QuickTargetPickerBottomSheet(
            trackTitle = tr.title,
            artistName = tr.artist,
            isResolving = resolvingTrackId == tr.id,
            selectedPlatformKey = targetPlatformKey,
            onDismissRequest = { pickerTrack = null },
            onPlatformSelected = { pickedTarget ->
                pickerTrack = null
                if (!sUrl.isNullOrBlank()) {
                    resolvingTrackId = tr.id
                    coroutineScope.launch {
                        val res = withContext(Dispatchers.IO) {
                            SongLinkEngine.shared.resolveTargetUrl(
                                inputUrl = sUrl,
                                targetPlatformKey = pickedTarget,
                                isPro = proState.isPro
                            )
                        }
                        resolvingTrackId = null
                        if (res is ResolutionResult.Success) {
                            SongLinkEngine.shared.cache.markAsHistory(sUrl, pickedTarget)
                            try {
                                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse(res.targetUrl)).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(playIntent)
                                onDismissRequest()
                            } catch (_: Exception) {
                                Toast.makeText(context, context.getString(R.string.redirect_error_toast), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        )
    }
}
