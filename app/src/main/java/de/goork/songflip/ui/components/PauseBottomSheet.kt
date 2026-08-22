package de.goork.songflip.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PauseCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.goork.songflip.R
import de.goork.songflip.ui.theme.StatePausedAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseBottomSheet(
    onDismissRequest: () -> Unit,
    onPauseOptionSelected: (durationMs: Long, isUntilTomorrow: Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.pause_dialog_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.pause_dialog_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            // Option 1: 15 Minutes
            PauseOptionCard(
                title = stringResource(R.string.pause_15m),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPauseOptionSelected(15 * 60 * 1000L, false)
                }
            )

            // Option 2: 1 Hour
            PauseOptionCard(
                title = stringResource(R.string.pause_1h),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPauseOptionSelected(60 * 60 * 1000L, false)
                }
            )

            // Option 3: Until Tomorrow Morning (6:00 AM)
            PauseOptionCard(
                title = stringResource(R.string.pause_tomorrow),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPauseOptionSelected(0L, true)
                }
            )

            // Option 4: Indefinite
            PauseOptionCard(
                title = stringResource(R.string.pause_indefinitely),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPauseOptionSelected(0L, false)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PauseOptionCard(
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Outlined.PauseCircle,
                contentDescription = null,
                tint = StatePausedAmber,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
