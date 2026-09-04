package de.goork.songflip.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import de.goork.songflip.R
import de.goork.songflip.ui.MainActivity

object ShortcutHelper {
    const val ACTION_PLAY_LAST_SONG = "de.goork.songflip.ACTION_PLAY_LAST_SONG"
    const val ACTION_PAUSE_1H = "de.goork.songflip.ACTION_PAUSE_1H"
    const val EXTRA_TARGET_URL = "extra_target_url"

    private const val ID_LAST_SONG = "shortcut_last_song"
    private const val ID_PAUSE_1H = "shortcut_pause_1h"

    fun updateShortcuts(context: Context) {
        try {
            LinkCacheManager.init(context)
            val shortcuts = mutableListOf<ShortcutInfoCompat>()

            // 1. Shortcut: 1h pausieren
            val pauseIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_PAUSE_1H
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pauseShortcut = ShortcutInfoCompat.Builder(context, ID_PAUSE_1H)
                .setShortLabel(context.getString(R.string.shortcut_pause_1h_short))
                .setLongLabel(context.getString(R.string.shortcut_pause_1h_long))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_pause))
                .setIntent(pauseIntent)
                .setRank(1)
                .build()
            shortcuts.add(pauseShortcut)

            // 2. Shortcut: Letzten Song öffnen (nur wenn ein Song im Verlauf vorhanden ist)
            val lastSong = LinkCacheManager.getHistoryEntries(1).firstOrNull()
            if (lastSong != null && lastSong.targetUrl.isNotBlank()) {
                val songTitle = lastSong.title ?: "Track"
                val playIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_PLAY_LAST_SONG
                    putExtra(EXTRA_TARGET_URL, lastSong.targetUrl)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val playShortcut = ShortcutInfoCompat.Builder(context, ID_LAST_SONG)
                    .setShortLabel(context.getString(R.string.shortcut_last_song_short))
                    .setLongLabel(context.getString(R.string.shortcut_last_song_long, songTitle))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_play))
                    .setIntent(playIntent)
                    .setRank(0)
                    .build()
                shortcuts.add(playShortcut)
            }

            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (_: Exception) {
            // Graceful fallback if shortcuts are unsupported or launcher throws
        }
    }
}
