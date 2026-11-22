package de.goork.songflip

import de.goork.songflip.data.PauseHelper
import de.goork.songflip.service.SongFlipTileService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongFlipTileServiceTest {

    @Test
    fun testPauseHelperStateTransitions() {
        val now = System.currentTimeMillis()
        val inMemoryPrefs = InMemorySharedPreferences()

        // 1. Initially not paused
        val pausedUntilInit = inMemoryPrefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
        assertFalse(pausedUntilInit > now)

        // 2. Pause for 1 hour
        val pausedUntil = now + (60 * 60 * 1000L)
        inMemoryPrefs.edit().putLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, pausedUntil).apply()

        val isPaused = inMemoryPrefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L) > now
        assertTrue(isPaused)

        // 3. Unpause / Resume
        inMemoryPrefs.edit().putLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L).apply()
        val isPausedAfterResume = inMemoryPrefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L) > now
        assertFalse(isPausedAfterResume)
    }

    @Test
    fun testHandleResumeClickExecutesImmediatelyWhenUnlocked() {
        var unlockCalled = false
        var resumedCount = 0

        SongFlipTileService.handleResumeClick(
            isLocked = false,
            unlockAndRun = { unlockCalled = true },
            onResumeUnlocked = { resumedCount++ }
        )

        assertFalse(unlockCalled)
        assertEquals(1, resumedCount)
    }

    @Test
    fun testHandleResumeClickDefersUntilUnlockWhenLocked() {
        var pendingAction: Runnable? = null
        var resumedCount = 0

        SongFlipTileService.handleResumeClick(
            isLocked = true,
            unlockAndRun = { runnable -> pendingAction = runnable },
            onResumeUnlocked = { resumedCount++ }
        )

        assertEquals(0, resumedCount)
        pendingAction!!.run()
        assertEquals(1, resumedCount)
    }
}
