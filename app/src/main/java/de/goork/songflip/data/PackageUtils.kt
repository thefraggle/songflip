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
        "amazonMusic" to "com.amazon.mp3",
        "soundcloud" to "com.soundcloud.android",
        "bandcamp" to "com.bandcamp.android"
    )

    val fallbackPackages = mapOf(
        "youtubeMusic" to listOf(
            "com.google.android.apps.youtube.music",
            "app.rvx.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music",
            "com.vanced.android.apps.youtube.music",
            "com.inotia00.youtube.music"
        )
    )

    fun getInstalledPackage(context: Context, platformKey: String): String? {
        val candidates = fallbackPackages[platformKey] ?: listOfNotNull(packageMap[platformKey])
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return null
    }

    fun isAppInstalled(context: Context, platformKey: String): Boolean {
        return getInstalledPackage(context, platformKey) != null
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
            "soundcloud" -> "SoundCloud"
            "bandcamp" -> "Bandcamp"
            "universal" -> "Universal Link"
            "shazam" -> "Shazam"
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
            lower.contains("soundcloud.com") || lower.startsWith("soundcloud:") -> "soundcloud"
            lower.contains("bandcamp.com") || lower.startsWith("bandcamp:") -> "bandcamp"
            lower.contains("shazam.com") -> "shazam"
            else -> ""
        }
    }

    /**
     * Opens Android System Settings directly to "Open by default" for a specific app package.
     */
    fun openAppOpenByDefaultSettings(context: Context, packageName: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (e: Exception) {}
        }
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {}
    }
}
