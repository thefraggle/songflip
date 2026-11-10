package de.goork.songflip.ui.components.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.goork.songflip.R
import de.goork.songflip.data.ProState
import java.text.DateFormat
import java.util.Date

/**
 * Settings Card showing Pro status or upgrade teaser (#241).
 */
@Composable
fun SettingsProCard(
    isPro: Boolean,
    proState: ProState,
    onOpenProPaywall: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPro) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isPro) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onOpenProPaywall("settings_sheet")
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("💎", fontSize = 20.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPro) stringResource(R.string.pro_active_status) else stringResource(R.string.pro_title),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val proSubText = if (isPro) {
                        when (proState.proType) {
                            "lifetime_coupon", "revenuecat_lifetime" -> stringResource(R.string.pro_active_lifetime)
                            "1year_coupon", "annual_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_annual, dateStr)
                            }
                            "3months_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_3months, dateStr)
                            }
                            "1month_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_1month, dateStr)
                            }
                            "revenuecat_subscription", "revenuecat" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                }
                                if (!dateStr.isNullOrBlank()) {
                                    stringResource(R.string.pro_active_annual, dateStr)
                                } else {
                                    stringResource(R.string.pro_active_lifetime)
                                }
                            }
                            else -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                }
                                if (!dateStr.isNullOrBlank()) {
                                    stringResource(R.string.pro_active_annual, dateStr)
                                } else {
                                    stringResource(R.string.pro_active_lifetime)
                                }
                            }
                        }
                    } else {
                        stringResource(R.string.pro_subtitle)
                    }
                    Text(
                        text = proSubText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isPro) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
