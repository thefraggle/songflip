package de.goork.songflip.data

import android.content.Context
import android.content.pm.PackageManager

object PackageUtils {

    val packageMap = mapOf(
        "youtubeMusic" to "com.google.android.apps.youtube.music",
        "appleMusic" to "com.apple.android.music",
        "spotify" to "com.spotify.music",
        "tidal" to "com.aspiro.tidal",
        "deezer" to "deezer.android.app",
        "amazonMusic" to "com.amazon.mp3"
    )

    fun isAppInstalled(context: Context, platformKey: String): Boolean {
        val packageName = packageMap[platformKey] ?: return false
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
