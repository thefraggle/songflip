package de.goork.songflip.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.goork.songflip.ui.components.closeOnEsc
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModalEscHelperTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun closeOnEsc_triggersDismiss_onEscapeKey() {
        var dismissed = false
        val focusRequester = FocusRequester()

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .focusRequester(focusRequester)
                    .focusTarget()
                    .closeOnEsc {
                        dismissed = true
                    }
            )
        }

        composeTestRule.runOnIdle {
            focusRequester.requestFocus()
        }

        composeTestRule.onRoot().performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(
                NativeKeyEvent(
                    android.view.KeyEvent.ACTION_UP,
                    android.view.KeyEvent.KEYCODE_ESCAPE
                )
            )
        )

        assertTrue("Modal should be dismissed when Escape key is pressed", dismissed)
    }
}
