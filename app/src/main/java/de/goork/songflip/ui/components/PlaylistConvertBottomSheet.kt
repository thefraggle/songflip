package de.goork.songflip.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.goork.songflip.R
import de.goork.songflip.core.engine.PlaylistConverterEngine
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistConversionState
import de.goork.songflip.core.model.PlaylistTrackItem
import de.goork.songflip.data.PackageUtils
import de.goork.songflip.data.ProManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistConvertBottomSheet(
    playlistUrl: String,
    targetPlatformKey: String,
    onDismiss: () -> Unit,
    onOpenPaywall: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val proState by ProManager.proState.collectAsState()
    val isPro = proState.isPro

    val sourcePlatform = remember(playlistUrl) {
        de.goork.songflip.core.util.UrlUtils.detectPlatform(playlistUrl) ?: MusicPlatform.YOUTUBE_MUSIC
    }
    val targetPlatform = remember(targetPlatformKey) {
        MusicPlatform.fromKey(targetPlatformKey)
    }

    var conversionState by remember { mutableStateOf<PlaylistConversionState>(PlaylistConversionState.Loading()) }
    var conversionProgressStep by remember { mutableIntStateOf(0) }

    fun runConversion() {
        conversionState = PlaylistConversionState.Loading()
        conversionProgressStep = 0
        coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                PlaylistConverterEngine.shared.convertPlaylist(
                    url = playlistUrl,
                    targetPlatformKey = targetPlatformKey,
                    isPro = isPro,
                    maxTracks = if (isPro) 50 else 5
                )
            }
            if (result.isSuccess) {
                val data = result.getOrThrow()
                conversionState = PlaylistConversionState.Success(data)

                // Save playlist entry into local history
                val targetUrl = data.zeroOAuthUrl ?: data.webShareUrl ?: playlistUrl
                de.goork.songflip.data.LinkCacheManager.put(
                    canonicalUrl = playlistUrl,
                    targetPlatformKey = targetPlatformKey,
                    targetUrl = targetUrl,
                    platform = "${targetPlatformKey}_playlist",
                    title = data.title.ifBlank { "Playlist" },
                    artist = "${data.matchedCount}/${data.totalTracks} Songs",
                    isAlbum = false,
                    isHistory = true
                )

                de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipped(
                    target = targetPlatformKey,
                    isAlbum = false,
                    isSearch = false
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                conversionState = PlaylistConversionState.Error(errorMsg)
                de.goork.songflip.core.analytics.AptabaseClient.shared.trackLinkFlipFailed(
                    target = targetPlatformKey,
                    reason = "playlist_conversion_error"
                )
            }
        }
    }

    LaunchedEffect(conversionState) {
        if (conversionState is PlaylistConversionState.Loading || conversionState is PlaylistConversionState.Converting) {
            conversionProgressStep = 0
            while (true) {
                kotlinx.coroutines.delay(600)
                conversionProgressStep += 1
            }
        }
    }

    LaunchedEffect(playlistUrl, targetPlatformKey, isPro) {
        runConversion()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row with Close Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = sourcePlatform.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = targetPlatform.displayName,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.playlist_dialog_action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = conversionState) {
                is PlaylistConversionState.Loading, is PlaylistConversionState.Converting -> {
                    val progressRatio = when {
                        conversionProgressStep <= 1 -> 0.20f
                        conversionProgressStep <= 3 -> 0.45f
                        conversionProgressStep <= 6 -> 0.70f
                        conversionProgressStep <= 9 -> 0.88f
                        else -> 0.95f
                    }
                    val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = progressRatio,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                        label = "progress"
                    )
                    val statusText = when {
                        conversionProgressStep <= 1 -> stringResource(R.string.playlist_converting)
                        conversionProgressStep <= 5 -> "Suche Titel auf ${targetPlatform.displayName}…"
                        else -> "Erstelle Playlist-Queue…"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.5.dp
                            )

                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "${(animatedProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is PlaylistConversionState.Error -> {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.playlist_error_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.playlist_error_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { runConversion() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.playlist_btn_retry))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(playlistUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.playlist_btn_open_original))
                    }
                }

                is PlaylistConversionState.Success -> {
                    val res = state.result
                    val title = res.title.ifBlank { stringResource(R.string.playlist_converter_title) }

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = stringResource(R.string.playlist_matched_count, res.matchedCount, res.totalTracks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Track list card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(res.tracks) { index, track ->
                                TrackRowItem(index = index + 1, track = track)
                            }
                        }
                    }

                    // Freemium Pro Banner
                    if (res.isLimited) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.playlist_free_limit_title),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = stringResource(R.string.playlist_free_limit_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = {
                                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPaywallViewed()
                                        onOpenPaywall()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.playlist_btn_unlock_pro),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Primary CTA: Open & Save in Target Player or Web Share Link
                    val zeroOAuthUrl = res.zeroOAuthUrl
                    val webShareUrl = res.webShareUrl ?: "https://songflip.link/p/${res.playlistId}"

                    if (!zeroOAuthUrl.isNullOrBlank()) {
                        // YouTube Music & Spotify: Primary Queue Launch
                        Button(
                            onClick = {
                                val targetPkg = PackageUtils.getInstalledPackage(context, targetPlatform.key)
                                var launched = false
                                if (targetPkg != null) {
                                    try {
                                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(zeroOAuthUrl)).apply {
                                            setPackage(targetPkg)
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                        }
                                        context.startActivity(appIntent)
                                        launched = true
                                    } catch (_: Exception) {}
                                }

                                if (!launched) {
                                    try {
                                        val genericIntent = Intent(Intent.ACTION_VIEW, Uri.parse(zeroOAuthUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(genericIntent)
                                        launched = true
                                    } catch (_: Exception) {}
                                }

                                if (!launched) {
                                    try {
                                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webShareUrl)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(webIntent)
                                    } catch (_: Exception) {}
                                }
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_open_import, targetPlatform.displayName),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("SongFlip Playlist Link", webShareUrl)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playlist_share_copied),
                                    Toast.LENGTH_SHORT
                                ).show()

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, webShareUrl)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.playlist_share_link)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_share_link),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        // Apple Music, Deezer, Tidal, Amazon: Primary Share & View Web-Playlist
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("SongFlip Playlist Link", webShareUrl)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.playlist_share_copied),
                                    Toast.LENGTH_SHORT
                                ).show()

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, webShareUrl)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.playlist_share_link)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_share_link),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                try {
                                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webShareUrl)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(webIntent)
                                } catch (_: Exception) {}
                                onDismiss()
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.OpenInBrowser,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.playlist_open_in_browser),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun TrackRowItem(index: Int, track: PlaylistTrackItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (track.artist.isNotBlank()) {
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (track.matched) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = "—",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
