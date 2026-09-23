package de.goork.songflip

import de.goork.songflip.data.ShortcutHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutHelperTest {

    @Test
    fun testShortcutConstants() {
        assertEquals("de.goork.songflip.ACTION_PLAY_LAST_SONG", ShortcutHelper.ACTION_PLAY_LAST_SONG)
        assertEquals("de.goork.songflip.ACTION_PAUSE_1H", ShortcutHelper.ACTION_PAUSE_1H)
        assertEquals("extra_target_url", ShortcutHelper.EXTRA_TARGET_URL)
    }
}
