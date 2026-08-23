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

    /**
     * Converts standard Spotify web URLs into native Spotify URIs (spotify:track:ID, spotify:album:ID, spotify:search:query)
     * for instant playback without browser intent filtering.
     */
    fun toNativeSpotifyUri(url: String): String {
        if (url.startsWith("spotify:")) return url
        val clean = url.trim().substringBefore("?")
        return when {
            clean.contains("open.spotify.com/track/") -> {
                val id = clean.substringAfter("open.spotify.com/track/").substringBefore("/").trim()
                if (id.isNotEmpty()) "spotify:track:$id" else url
            }
            clean.contains("open.spotify.com/album/") -> {
                val id = clean.substringAfter("open.spotify.com/album/").substringBefore("/").trim()
                if (id.isNotEmpty()) "spotify:album:$id" else url
            }
            clean.contains("open.spotify.com/artist/") -> {
                val id = clean.substringAfter("open.spotify.com/artist/").substringBefore("/").trim()
                if (id.isNotEmpty()) "spotify:artist:$id" else url
            }
            clean.contains("open.spotify.com/playlist/") -> {
                val id = clean.substringAfter("open.spotify.com/playlist/").substringBefore("/").trim()
                if (id.isNotEmpty()) "spotify:playlist:$id" else url
            }
            url.contains("open.spotify.com/search/") -> {
                val query = url.substringAfter("open.spotify.com/search/").substringBefore("?").trim()
                if (query.isNotEmpty()) "spotify:search:$query" else url
            }
            else -> url
        }
    }

    fun getPlatformDisplayName(platformKey: String): String {
        return when (platformKey) {
            "youtubeMusic" -> "YouTube Music"
            "appleMusic" -> "Apple Music"
            "spotify" -> "Spotify"
            "tidal" -> "Tidal"
            "deezer" -> "Deezer"
            "amazonMusic" -> "Amazon Music"
            else -> platformKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
}
