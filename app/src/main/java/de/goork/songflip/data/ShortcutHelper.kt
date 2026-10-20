package de.goork.songflip.data

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import de.goork.songflip.R
import de.goork.songflip.core.cache.AndroidSharedPreferencesCacheStorage
import de.goork.songflip.core.engine.SongLinkEngine
import de.goork.songflip.ui.ListenAndFlipActivity
import de.goork.songflip.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ShortcutHelper {
    const val ACTION_LISTEN_AND_FLIP = "de.goork.songflip.ACTION_LISTEN_AND_FLIP"
    const val ACTION_PLAY_LAST_SONG = "de.goork.songflip.ACTION_PLAY_LAST_SONG"
    const val ACTION_PAUSE_1H = "de.goork.songflip.ACTION_PAUSE_1H"
    const val EXTRA_TARGET_URL = "extra_target_url"

    private const val ID_LISTEN_AND_FLIP = "shortcut_listen_and_flip"
    private const val ID_LAST_SONG = "shortcut_last_song"
    private const val ID_PAUSE_1H = "shortcut_pause_1h"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateShortcuts(context: Context) {
        scope.launch {
            try {
                AndroidSharedPreferencesCacheStorage.init(context)
                val shortcuts = mutableListOf<ShortcutInfoCompat>()

                // 1. Shortcut: Listen & Flip (Rank 0 - top priority on App Icon long-press)
                val listenIntent = Intent(context, ListenAndFlipActivity::class.java).apply {
                    action = ACTION_LISTEN_AND_FLIP
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val listenShortcut = ShortcutInfoCompat.Builder(context, ID_LISTEN_AND_FLIP)
                    .setShortLabel(context.getString(R.string.listen_and_flip_title))
                    .setLongLabel(context.getString(R.string.listen_and_flip_subtitle))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_qs_listen))
                    .setIntent(listenIntent)
                    .setRank(0)
                    .build()
                shortcuts.add(listenShortcut)

                // 2. Shortcut: 1h pausieren
                val pauseIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_PAUSE_1H
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pauseShortcut = ShortcutInfoCompat.Builder(context, ID_PAUSE_1H)
                    .setShortLabel(context.getString(R.string.shortcut_pause_1h_short))
                    .setLongLabel(context.getString(R.string.shortcut_pause_1h_long))
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_pause))
                    .setIntent(pauseIntent)
                    .setRank(2)
                    .build()
                shortcuts.add(pauseShortcut)

                // 3. Shortcut: Letzten Song öffnen (nur wenn ein Song im Verlauf vorhanden ist)
                val lastSong = SongLinkEngine.shared.cache.getHistoryEntries(1).firstOrNull()
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
                        .setRank(1)
                        .build()
                    shortcuts.add(playShortcut)
                }

                ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
            } catch (_: Exception) {
                // Graceful fallback if shortcuts are unsupported or launcher throws
            }
        }
    }
}
