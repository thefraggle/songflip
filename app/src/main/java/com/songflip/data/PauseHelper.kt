package com.songflip.data

import android.content.Context
import java.util.Calendar

object PauseHelper {
    const val PREFS_KEY_PAUSED = "is_paused"
    const val PREFS_KEY_PAUSED_UNTIL = "paused_until"

    /**
     * Checks if SongFlip is currently paused, automatically resetting expired timed pauses.
     */
    fun isCurrentlyPaused(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val isPaused = prefs.getBoolean(PREFS_KEY_PAUSED, false)
        if (!isPaused) return false

        val pausedUntil = prefs.getLong(PREFS_KEY_PAUSED_UNTIL, 0L)
        if (pausedUntil > 0L) {
            val now = System.currentTimeMillis()
            if (now >= pausedUntil) {
                // Timer expired -> auto resume
                prefs.edit()
                    .putBoolean(PREFS_KEY_PAUSED, false)
                    .putLong(PREFS_KEY_PAUSED_UNTIL, 0L)
                    .apply()
                return false
            }
        }
        return true
    }

    /**
     * Set a pause duration in milliseconds or indefinite (0L).
     */
    fun setPause(context: Context, durationMs: Long) {
        val prefs = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        val pausedUntil = if (durationMs > 0L) System.currentTimeMillis() + durationMs else 0L
        prefs.edit()
            .putBoolean(PREFS_KEY_PAUSED, true)
            .putLong(PREFS_KEY_PAUSED_UNTIL, pausedUntil)
            .apply()
    }

    /**
     * Pause until a specific epoch timestamp (e.g. tomorrow morning).
     */
    fun setPauseUntil(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREFS_KEY_PAUSED, true)
            .putLong(PREFS_KEY_PAUSED_UNTIL, timestamp)
            .apply()
    }

    /**
     * Immediately resume SongFlip.
     */
    fun resume(context: Context) {
        val prefs = context.getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(PREFS_KEY_PAUSED, false)
            .putLong(PREFS_KEY_PAUSED_UNTIL, 0L)
            .apply()
    }

    /**
     * Helper to compute next morning 6:00 AM timestamp.
     */
    fun getTomorrowMorningTimestamp(): Long {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }
}
