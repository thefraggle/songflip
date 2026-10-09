package de.goork.songflip.ui.components

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.goork.songflip.R
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
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val proState by ProManager.proState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<SearchTrackResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }

    // Audio preview state
    var playingPreviewUrl by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            } catch (_: Exception) {}
            mediaPlayer = null
        }
    }

    fun togglePreview(url: String?) {
        if (url.isNullOrBlank()) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        try {
            if (playingPreviewUrl == url) {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
                playingPreviewUrl = null
            } else {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(url)
                    setOnCompletionListener {
                        playingPreviewUrl = null
                    }
                    setOnErrorListener { _, _, _ ->
                        playingPreviewUrl = null
                        true
                    }
                    prepareAsync()
                    setOnPreparedListener { start() }
                }
                mediaPlayer = player
                playingPreviewUrl = url
            }
        } catch (_: Exception) {
            playingPreviewUrl = null
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(searchResults, key = { it.id }) { track ->
                        val isResolving = resolvingTrackId == track.id
                        val isPlayingThis = playingPreviewUrl == track.previewUrl && !track.previewUrl.isNullOrBlank()

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isResolving) {
                                    val sourceUrl = track.sourceUrl
                                    if (!sourceUrl.isNullOrBlank()) {
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
                                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipped(
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
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Cover Art
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
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

                                // Preview or Action Button
                                if (!track.previewUrl.isNullOrBlank()) {
                                    IconButton(
                                        onClick = { togglePreview(track.previewUrl) },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) Icons.Outlined.PauseCircle else Icons.Outlined.PlayCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Outlined.OpenInNew,
                                    contentDescription = targetDisplayName,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
