package de.goork.songflip

import de.goork.songflip.data.ShortcutHelper
import de.goork.songflip.service.FlipClipboardTileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FlipClipboardTileServiceTest {

    @Test
    fun testFlipClipboardShortcutActionConstant() {
        assertEquals("de.goork.songflip.ACTION_FLIP_CLIPBOARD", ShortcutHelper.ACTION_FLIP_CLIPBOARD)
    }

    @Test
    fun testFlipClipboardTileServiceInstantiable() {
        val service = FlipClipboardTileService()
        assertNotNull(service)
    }
}
