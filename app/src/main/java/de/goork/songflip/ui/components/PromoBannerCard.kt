package de.goork.songflip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.goork.songflip.core.model.PromoBannerConfig
import java.util.Locale

private val EmeraldAccent = Color(0xFF10B981)
private val EmeraldAccentDark = Color(0xFF059669)
private val BannerCardBackground = Color(0xFF0D1B17)
private val BannerBorderColor = Color(0x3310B981)

@Composable
fun PromoBannerCard(
    config: PromoBannerConfig,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val currentLocale = remember {
        val conf = context.resources.configuration
        val loc = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            conf.locales.get(0) ?: Locale.getDefault()
        } else {
            @Suppress("DEPRECATION")
            conf.locale ?: Locale.getDefault()
        }
        loc.toLanguageTag()
    }

    val badgeText = config.getLocalizedBadge(currentLocale)
    val titleText = config.getLocalizedTitle(currentLocale)
    val subtitleText = config.getLocalizedSubtitle(currentLocale)
    val buttonText = config.getLocalizedButtonText(currentLocale)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isPressed -> EmeraldAccent
            isHovered -> EmeraldAccent.copy(alpha = 0.8f)
            else -> BannerBorderColor
        },
        label = "promoBannerBorderColor"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = BannerCardBackground
        ),
        border = BorderStroke(1.dp, animatedBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Badge Pill + Icon + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (badgeText.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(EmeraldAccent, EmeraldAccentDark)
                                    )
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Outlined.LocalOffer,
                        contentDescription = null,
                        tint = EmeraldAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Title
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )

            // Subtitle Description
            if (subtitleText.isNotBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }

            // Primary Action Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldAccent,
                    contentColor = Color.Black
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
