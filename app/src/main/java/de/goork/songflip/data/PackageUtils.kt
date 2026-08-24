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
     * Converts standard web URLs into native app URIs (Spotify, Deezer, Tidal)
     * for instant direct launch without browser intent filtering.
     */
    fun toNativeAppUri(url: String, platformKey: String): String {
        return when (platformKey) {
            "spotify" -> toNativeSpotifyUri(url)
            "deezer" -> toNativeDeezerUri(url)
            "tidal" -> toNativeTidalUri(url)
            else -> url
        }
    }

    /**
     * Converts standard Spotify web URLs into native Spotify URIs (spotify:track:ID, spotify:album:ID, spotify:search:query)
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

    /**
     * Converts Deezer web URLs into native deezer:// URIs
     */
    fun toNativeDeezerUri(url: String): String {
        if (url.startsWith("deezer://")) return url
        val clean = url.trim().substringBefore("?")
        return when {
            clean.contains("deezer.com/track/") -> {
                val id = clean.substringAfter("/track/").substringBefore("/").trim()
                if (id.isNotEmpty()) "deezer://www.deezer.com/track/$id" else url
            }
            clean.contains("deezer.com/album/") -> {
                val id = clean.substringAfter("/album/").substringBefore("/").trim()
                if (id.isNotEmpty()) "deezer://www.deezer.com/album/$id" else url
            }
            clean.contains("deezer.com/artist/") -> {
                val id = clean.substringAfter("/artist/").substringBefore("/").trim()
                if (id.isNotEmpty()) "deezer://www.deezer.com/artist/$id" else url
            }
            else -> url
        }
    }

    /**
     * Converts Tidal web URLs into native tidal:// URIs
     */
    fun toNativeTidalUri(url: String): String {
        if (url.startsWith("tidal://")) return url
        val clean = url.trim().substringBefore("?")
        return when {
            clean.contains("tidal.com/track/") || clean.contains("tidal.com/browse/track/") -> {
                val id = clean.substringAfter("/track/").substringBefore("/").trim()
                if (id.isNotEmpty()) "tidal://track/$id" else url
            }
            clean.contains("tidal.com/album/") || clean.contains("tidal.com/browse/album/") -> {
                val id = clean.substringAfter("/album/").substringBefore("/").trim()
                if (id.isNotEmpty()) "tidal://album/$id" else url
            }
            clean.contains("tidal.com/artist/") || clean.contains("tidal.com/browse/artist/") -> {
                val id = clean.substringAfter("/artist/").substringBefore("/").trim()
                if (id.isNotEmpty()) "tidal://artist/$id" else url
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

    fun detectPlatformFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("spotify.com") || lower.startsWith("spotify:") -> "spotify"
            lower.contains("apple.com") || lower.contains("itunes.apple.com") -> "appleMusic"
            lower.contains("music.youtube.com") -> "youtubeMusic"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtubeMusic"
            lower.contains("tidal.com") || lower.startsWith("tidal:") -> "tidal"
            lower.contains("deezer.com") || lower.startsWith("deezer:") -> "deezer"
            lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co") -> "amazonMusic"
            else -> ""
        }
    }
}
