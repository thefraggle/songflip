package com.songflip.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.songflip.R
import com.songflip.data.PauseHelper
import com.songflip.data.SettingsRepository
import com.songflip.ui.MainActivity

@RequiresApi(Build.VERSION_CODES.N)
class SongFlipTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val currentlyPaused = PauseHelper.isCurrentlyPaused(this)
        if (currentlyPaused) {
            PauseHelper.resume(this)
            updateTileState()
        } else {
            // Open MainActivity with the pause bottom sheet requested
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("show_pause_sheet", true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val currentlyPaused = PauseHelper.isCurrentlyPaused(this)
        val prefs = getSharedPreferences(SettingsRepository.PREFS_NAME, Context.MODE_PRIVATE)

        // Set clean monochrome music note vector icon
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_tile)
        tile.label = getString(R.string.app_name)

        if (currentlyPaused) {
            tile.state = Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val pausedUntil = prefs.getLong(PauseHelper.PREFS_KEY_PAUSED_UNTIL, 0L)
                if (pausedUntil > 0L) {
                    val timeStr = android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(pausedUntil))
                    tile.subtitle = "${getString(R.string.tile_paused)} ($timeStr)"
                } else {
                    tile.subtitle = getString(R.string.tile_paused)
                }
            }
        } else {
            tile.state = Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = getString(R.string.tile_active)
            }
        }
        tile.updateTile()
    }
}
