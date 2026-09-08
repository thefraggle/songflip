package de.goork.songflip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.goork.songflip.R
import de.goork.songflip.data.DomainStatusInfo
import de.goork.songflip.ui.theme.*

enum class StatusType { ACTIVE, WARNING, ERROR }

@Composable
fun SetupCard(
    domainStatus: DomainStatusInfo?,
    linksActive: Boolean?,
    onOpenSetupGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isConfigured = domainStatus?.let { it.enabledHosts > 0 } ?: (linksActive == true)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (isConfigured) {
                // Active / Success State (at least 1 music domain enabled or links active)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = StateActiveGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(R.string.setup_card_active_title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.setup_card_active_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (domainStatus != null) {
                    if (domainStatus.isFullyEnabled) {
                        StatusBadge(
                            text = stringResource(R.string.status_all_active, domainStatus.totalHosts),
                            statusType = StatusType.ACTIVE
                        )
                    } else {
                        // Partially enabled (e.g. 2 of 22 or 21 of 22 where official YouTube Music claims its link)
                        // Green & configured with exact count and tap to manage
                        StatusBadge(
                            text = stringResource(R.string.status_partial_active, domainStatus.enabledHosts, domainStatus.totalHosts),
                            statusType = StatusType.ACTIVE,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                                onOpenSetupGuide()
                            }
                        )
                    }
                } else {
                    StatusBadge(
                        text = stringResource(R.string.status_active),
                        statusType = StatusType.ACTIVE
                    )
                }
            } else {
                // Setup Needed State (0 domains enabled)
                Text(
                    text = stringResource(R.string.setup_card_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = stringResource(R.string.setup_card_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Primary CTA button to open the 3-step guide sheet
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackDomainSetupClicked()
                        onOpenSetupGuide()
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
                        text = stringResource(R.string.btn_start_setup),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Inactive Badge
                StatusBadge(
                    text = stringResource(R.string.status_inactive),
                    statusType = StatusType.ERROR
                )

                // Subtle note about zero-setup alternatives (Share Sheet & Clipboard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.setup_card_tip),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(
    text: String,
    statusType: StatusType,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val (bgColor, textColor, icon: ImageVector) = when (statusType) {
        StatusType.ACTIVE -> Triple(StateActiveGreen.copy(alpha = 0.15f), StateActiveGreen, Icons.Default.CheckCircle)
        StatusType.WARNING -> Triple(StatePausedAmber.copy(alpha = 0.15f), StatePausedAmber, Icons.Default.Warning)
        StatusType.ERROR -> Triple(StateErrorRed.copy(alpha = 0.15f), StateErrorRed, Icons.Default.Error)
    }

    val clickableModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
    } else {
        modifier.clip(RoundedCornerShape(12.dp))
    }

    Row(
        modifier = clickableModifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
