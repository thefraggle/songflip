package de.goork.songflip.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Ensures hardware ESC key presses immediately dismiss bottom sheets and modals,
 * satisfying WCAG / keyboard navigation accessibility and desktop/tablet UX (Issue #57 M5).
 */
fun Modifier.closeOnEsc(onDismissRequest: () -> Unit): Modifier =
    this.onPreviewKeyEvent { event ->
        if (event.key == Key.Escape && event.type == KeyEventType.KeyUp) {
            onDismissRequest()
            true
        } else {
            false
        }
    }
