package de.goork.songflip.ui.components.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import de.goork.songflip.R
import de.goork.songflip.core.cache.LinkHistoryItem
import de.goork.songflip.data.PackageUtils

@Composable
fun HistoryItemCard(
    item: LinkHistoryItem,
    isPro: Boolean = false,
    isRefreshing: Boolean = false,
    onPlay: () -> Unit,
    onOpenPlaylist: (() -> Unit)? = null,
    onReconvertPlaylist: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit = {},
    onShowQrCode: () -> Unit = {},
    onDisambiguate: () -> Unit = {},
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
    val unknownSourceLabel = stringResource(R.string.platform_label_unknown)
    val sourceDisplayName = remember(sourcePlatformKey, unknownSourceLabel) {
        if (sourcePlatformKey.isNotEmpty()) PackageUtils.getPlatformDisplayName(sourcePlatformKey) else unknownSourceLabel
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
            .clickable {
                if (isPlaylist && onOpenPlaylist != null) {
                    onOpenPlaylist()
                } else {
                    onPlay()
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

                // Subtiles Play-Indicator Overlay unten rechts auf dem Cover (1-Tap-Schnellzugriff auf Playback)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f))
                        .clickable { onPlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_open),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(13.dp)
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
                    if (item.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.favorite_remove),
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(13.dp)
                        )
                    }
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
                    item.isAcoustic -> stringResource(R.string.badge_identified)
                    else -> null
                }
                val isAcousticBadge = !isPlaylist && !item.isAlbum && !isArtist && item.isAcoustic
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
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                if (isAcousticBadge) {
                                    Icon(
                                        imageVector = Icons.Outlined.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                                Text(
                                    text = typeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
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

            // 3. Right Action: Overflow Menu (⋮)
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(38.dp)
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
                    // 1. Favorite toggle
                    DropdownMenuItem(
                        text = { Text(stringResource(if (item.isFavorite) R.string.favorite_remove else R.string.favorite_add)) },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                contentDescription = null,
                                tint = if (item.isFavorite) Color(0xFFFFB800) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onToggleFavorite()
                        }
                    )

                    // 2. Share / FlipPage (mit 💎 bei Free)
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(stringResource(R.string.share_universal_link))
                                if (!isPro) {
                                    Text("💎", fontSize = 12.sp)
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = null,
                                tint = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showMenu = false
                            onShareUniversal()
                        }
                    )
                    if (isPlaylist) {
                        if (onOpenPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_open_playlist_details)) },
                                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.QueueMusic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    onOpenPlaylist()
                                }
                            )
                        }
                        if (onReconvertPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.playlist_reconvert)) },
                                leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showMenu = false
                                    onReconvertPlaylist()
                                }
                            )
                        }
                    }
                    if (isPro) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.history_qr_action)) },
                            leadingIcon = { Icon(Icons.Outlined.QrCode, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                showMenu = false
                                onShowQrCode()
                            }
                        )
                    }
                    if (!isPlaylist) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(stringResource(R.string.disambiguate_title))
                                    if (!isPro) {
                                        Text("💎", fontSize = 12.sp)
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Tune,
                                    contentDescription = null,
                                    tint = if (isPro) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                onDisambiguate()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history_copy_target, targetDisplayName)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showMenu = false
                            onCopyTarget()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            // "Copy original Music link link" reads badly, so unknown sources get their own phrase.
                            Text(
                                if (sourcePlatformKey.isNotEmpty()) stringResource(R.string.history_copy_source, sourceDisplayName)
                                else stringResource(R.string.history_copy_source_generic)
                            )
                        },
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

fun formatRelativeTime(timestamp: Long, context: Context): String {
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

fun openTargetUrl(context: Context, rawTargetUrl: String, platformKey: String) {
    val targetUrl = if (rawTargetUrl.contains("music.music.youtube.com")) {
        rawTargetUrl.replace("music.music.youtube.com", "music.youtube.com")
    } else rawTargetUrl
    PackageUtils.launchTargetOrFallback(context, targetUrl, platformKey)
}

fun copyToClipboard(context: Context, rawText: String) {
    val text = if (rawText.contains("music.music.youtube.com")) {
        rawText.replace("music.music.youtube.com", "music.youtube.com")
    } else rawText
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Target Music Link", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
}
