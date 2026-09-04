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
     * Handles /intl-<lang>/ prefixes reliably.
     */
    fun toNativeSpotifyUri(url: String): String {
        if (url.startsWith("spotify:")) return url
        val clean = url.trim().substringBefore("?")
        val match = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/(track|album|artist|playlist)/([a-zA-Z0-9]+)").find(clean)
        if (match != null) {
            val type = match.groupValues[1]
            val id = match.groupValues[2]
            return "spotify:$type:$id"
        }
        if (clean.contains("open.spotify.com/search/")) {
            val query = clean.substringAfter("open.spotify.com/search/").substringBefore("?").trim()
            if (query.isNotEmpty()) return "spotify:search:$query"
        }
        return url
    }

    /**
     * Converts Deezer web URLs into native deezer:// URIs
     * Handles regional language prefixes (/de/, /fr/, etc.) reliably.
     */
    fun toNativeDeezerUri(url: String): String {
        if (url.startsWith("deezer://")) return url
        val clean = url.trim().substringBefore("?")
        if (!clean.contains("deezer.com")) return url
        val match = Regex("deezer\\.com(?:/[a-zA-Z-]+)?/(track|album|artist)/(\\d+)").find(clean)
        return if (match != null) {
            val type = match.groupValues[1]
            val id = match.groupValues[2]
            "deezer://www.deezer.com/$type/$id"
        } else {
            url
        }
    }

    /**
     * Converts Tidal web URLs into native tidal:// URIs
     * Handles /browse/ and localized paths reliably.
     */
    fun toNativeTidalUri(url: String): String {
        if (url.startsWith("tidal://")) return url
        val clean = url.trim().substringBefore("?")
        if (!clean.contains("tidal.com")) return url
        val match = Regex("tidal\\.com(?:/[a-zA-Z-]+)?(?:/browse)?/(track|album|artist)/([0-9a-zA-Z-]+)").find(clean)
        return if (match != null) {
            val type = match.groupValues[1]
            val id = match.groupValues[2]
            "tidal://$type/$id"
        } else {
            url
        }
    }

    fun getPlatformDisplayName(platformKey: String): String {
        val cleanKey = platformKey.substringBefore("_")
        return when (cleanKey) {
            "youtubeMusic" -> "YouTube Music"
            "appleMusic" -> "Apple Music"
            "spotify" -> "Spotify"
            "tidal" -> "Tidal"
            "deezer" -> "Deezer"
            "amazonMusic" -> "Amazon Music"
            "universal" -> "Universal Link"
            else -> cleanKey.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
