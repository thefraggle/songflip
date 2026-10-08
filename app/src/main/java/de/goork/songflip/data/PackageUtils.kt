package de.goork.songflip.data

import android.content.Context
import android.content.pm.PackageManager

data class AppPlayerOption(
    val packageName: String,
    val appName: String,
    val isOfficial: Boolean
)

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
            "app.morphe.android.apps.youtube.music",
            "app.morphe.android.youtube.music",
            "app.morphe.youtube.music",
            "app.morphe.music",
            "com.morphe.android.apps.youtube.music",
            "com.morphe.android.youtube.music",
            "com.morphe.youtube.music",
            "app.revanced.android.apps.youtube.music",
            "app.revanced.android.youtube.music",
            "app.rvx.android.apps.youtube.music",
            "app.rvx.android.youtube.music",
            "anddea.youtube.music",
            "app.anddea.youtube.music",
            "app.anddea.android.youtube.music",
            "com.anddea.youtube.music",
            "com.vanced.android.apps.youtube.music",
            "com.inotia00.youtube.music",
            "it.fast4x.rimusic",
            "it.vfsfitvnm.vimusic",
            "vimusic.app",
            "com.zionhuang.music",
            "com.metareal.innertune",
            "com.kashike.simpmusic",
            "com.maxrave.simpmusic",
            "com.github.brahmkshatriya.echo",
            "io.github.aleixrodriala.arc",
            "com.aleixrodriala.newtube",
            "com.craftworks.music",
            "com.google.android.apps.youtube.music"
        )
    )

    val genericBrowserPackages = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.touch",
        "com.opera.gx",
        "com.brave.browser",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "org.torproject.torbrowser",
        "com.android.browser",
        "mark.via.gp",
        "com.kiwibrowser.browser",
        "com.mi.globalbrowser",
        "com.huawei.browser",
        "com.google.android.webview",
        "com.android.webview",
        "xyz.pavelpopov.interceptor",
        "fe.linksheet",
        "com.trianguloy.urlcheck"
    )

    fun isGenericBrowser(packageName: String): Boolean {
        val lower = packageName.lowercase()
        return genericBrowserPackages.contains(lower) ||
                lower.contains("browser") ||
                lower.contains("webview") ||
                lower.contains("interceptor")
    }

    private const val NOT_INSTALLED_SENTINEL = "__SF_NOT_INSTALLED__"
    private val packageCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun invalidateCache() {
        packageCache.clear()
    }

    fun getInstalledPackage(context: Context, platformKey: String): String? {
        val cleanKey = platformKey.substringBefore("_")
        val cached = packageCache[cleanKey]
        if (cached != null) {
            return if (cached == NOT_INSTALLED_SENTINEL) null else cached
        }

        val resolved = resolveInstalledPackageInternal(context, cleanKey)
        packageCache[cleanKey] = resolved ?: NOT_INSTALLED_SENTINEL
        return resolved
    }

    fun getInstalledPlayersForPlatform(context: Context, platformKey: String): List<AppPlayerOption> {
        val cleanKey = platformKey.substringBefore("_")
        val candidates = fallbackPackages[cleanKey] ?: listOfNotNull(packageMap[cleanKey])
        val result = mutableListOf<AppPlayerOption>()
        val pm = context.packageManager
        val seen = mutableSetOf<String>()

        for (pkg in candidates) {
            if (!seen.add(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val isOfficial = (pkg == packageMap[cleanKey])
                result.add(AppPlayerOption(packageName = pkg, appName = label, isOfficial = isOfficial))
            } catch (_: PackageManager.NameNotFoundException) {}
        }
        return result
    }

    private fun resolveInstalledPackageInternal(context: Context, cleanKey: String): String? {
        if (cleanKey == "youtubeMusic") {
            val preferred = SettingsRepository(context).preferredYouTubePlayerPackage
            if (!preferred.isNullOrBlank()) {
                try {
                    context.packageManager.getPackageInfo(preferred, 0)
                    return preferred
                } catch (_: PackageManager.NameNotFoundException) {}
            }
        }

        val candidates = fallbackPackages[cleanKey] ?: listOfNotNull(packageMap[cleanKey])
        for (pkg in candidates) {
            try {
                context.packageManager.getPackageInfo(pkg, 0)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {}
        }

        // Dynamic player detection for custom/modded installations
        try {
            val probeUrls = when (cleanKey) {
                "youtubeMusic" -> listOf(
                    "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
                    "vnd.youtube.music://watch?v=dQw4w9WgXcQ"
                )
                "spotify" -> listOf(
                    "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
                    "spotify:track:4u7EnebtmKWzUH433cf5Qv"
                )
                "appleMusic" -> listOf("https://music.apple.com/song/1761770183")
                "deezer" -> listOf(
                    "https://www.deezer.com/track/12345",
                    "deezer://www.deezer.com/track/12345"
                )
                "tidal" -> listOf(
                    "https://tidal.com/browse/track/12345",
                    "tidal://track/12345"
                )
                "amazonMusic" -> listOf("https://music.amazon.com/albums/B000000000")
                "soundcloud" -> listOf("https://soundcloud.com/artist/track")
                "bandcamp" -> listOf("https://artist.bandcamp.com/track/song")
                else -> emptyList()
            }
            for (probeUrl in probeUrls) {
                val browsableIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(probeUrl)).apply {
                    addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                }
                val resolveInfos = context.packageManager.queryIntentActivities(browsableIntent, 0)
                for (info in resolveInfos) {
                    val pkg = info.activityInfo.packageName
                    if (pkg != context.packageName && !isGenericBrowser(pkg)) {
                        return pkg
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    fun isAppInstalled(context: Context, platformKey: String): Boolean {
        return getInstalledPackage(context, platformKey) != null
    }

    /**
     * Converts a platform URL into a safe web/universal fallback URL when the native app is not installed.
     * E.g. converts music.youtube.com URLs to youtube.com URLs so non-Premium mobile web users
     * land directly on the playable song rather than the YouTube Music subscription paywall.
     */
    fun toWebFallbackUrl(url: String, platformKey: String): String {
        return de.goork.songflip.core.util.UrlUtils.toWebFallbackUrl(url, platformKey)
    }

    /**
     * Converts standard web URLs into native app URIs (Spotify, Deezer, Tidal)
     * for instant direct launch without browser intent filtering.
     */
    fun toNativeAppUri(url: String, platformKey: String): String {
        val cleanKey = platformKey.substringBefore("_")
        return when (cleanKey) {
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
        val match = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/(track|album|artist|playlist|prerelease)/([a-zA-Z0-9]+)").find(clean)
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
            lower.contains("spotify.com") || lower.contains("spotify.link") || lower.contains("spotify.app.link") || lower.contains("spoti.fi") || lower.startsWith("spotify:") -> "spotify"
            lower.contains("apple.com") || lower.contains("itunes.apple.com") || lower.contains("apple.co") || lower.contains("itun.es") -> "appleMusic"
            lower.contains("music.youtube.com") -> "youtubeMusic"
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "youtubeMusic"
            lower.contains("tidal.com") || lower.contains("tidal.link") || lower.startsWith("tidal:") -> "tidal"
            lower.contains("deezer.com") || lower.contains("deezer.page.link") || lower.contains("link.deezer.com") || lower.startsWith("deezer:") -> "deezer"
            lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("amzn.eu") || lower.contains("amzn.asia") || lower.contains("a.co") -> "amazonMusic"
            lower.contains("soundcloud.com") || lower.contains("on.soundcloud.com") || lower.startsWith("soundcloud:") -> "soundcloud"
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
