package de.goork.songflip.core.util

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.http.encodeURLParameter

object UrlUtils {

    private val urlRegex = Regex("(https?://[^\\s<>'\"]+)")

    fun extractCleanUrl(rawInput: String): String? {
        val match = urlRegex.find(rawInput)
        var extracted = match?.value ?: if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) {
            rawInput.trim()
        } else {
            null
        }

        if (extracted != null) {
            extracted = extracted.trimEnd('.', ',', '!', '?', ';', ':', '>', ']', '"', '\'', '»', '”', '“')
            val openCount = extracted.count { it == '(' }
            val closeCount = extracted.count { it == ')' }
            if (closeCount > openCount && extracted.endsWith(")")) {
                extracted = extracted.substring(0, extracted.length - (closeCount - openCount))
            }
        }

        return if (extracted != null) normalizeUrl(extracted) else null
    }

    /**
     * Cleans tracking parameters, context query parameters, and regional prefixes
     * from streaming URLs across all supported platforms to guarantee maximum cache hit rates
     * and clean provider queries.
     */
    fun normalizeUrl(url: String): String {
        var clean = url.trim()
        if (clean.isBlank()) return clean

        // 1. Spotify: open.spotify.com/track/{id} or /intl-*/track/{id}
        val spotifyTrackMatch = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/track/([a-zA-Z0-9]+)").find(clean)
        if (spotifyTrackMatch != null) {
            val id = spotifyTrackMatch.groupValues[1]
            return "https://open.spotify.com/track/$id"
        }
        val spotifyAlbumMatch = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/album/([a-zA-Z0-9]+)").find(clean)
        if (spotifyAlbumMatch != null) {
            val id = spotifyAlbumMatch.groupValues[1]
            return "https://open.spotify.com/album/$id"
        }
        val spotifyArtistMatch = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/artist/([a-zA-Z0-9]+)").find(clean)
        if (spotifyArtistMatch != null) {
            val id = spotifyArtistMatch.groupValues[1]
            return "https://open.spotify.com/artist/$id"
        }

        // 2. Apple Music: Preserve base URL + exact track id parameter (?i=...)
        if (clean.contains("apple.com") && clean.contains("i=")) {
            val base = clean.substringBefore("?")
            val trackId = clean.substringAfter("i=").substringBefore("&").substringBefore("?").trim()
            if (trackId.isNotEmpty()) {
                return "$base?i=$trackId"
            }
        } else if (clean.contains("apple.com") && (clean.contains("/album/") || clean.contains("/song/"))) {
            clean = clean.substringBefore("?")
        }

        // 3. Deezer: deezer.com/track/{id}, album/{id}, artist/{id}
        val deezerMatch = Regex("deezer\\.com(?:/[a-zA-Z-]+)?/(track|album|artist)/(\\d+)").find(clean)
        if (deezerMatch != null) {
            val type = deezerMatch.groupValues[1]
            val id = deezerMatch.groupValues[2]
            return "https://www.deezer.com/$type/$id"
        }

        // 4. YouTube & YouTube Music: watch?v={id} or youtu.be/{id}
        if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) {
                return "https://youtu.be/$id"
            }
        }
        if (clean.contains("watch") && clean.contains("v=")) {
            val isYtMusic = clean.contains("music.youtube.com")
            val host = if (isYtMusic) "https://music.youtube.com" else "https://www.youtube.com"
            val id = clean.substringAfter("v=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) {
                return "$host/watch?v=$id"
            }
        }

        // 5. Amazon Music: preserve trackAsin if present, otherwise strip tracking
        if (clean.contains("music.amazon.") && clean.contains("trackAsin=")) {
            val base = clean.substringBefore("?")
            val asin = clean.substringAfter("trackAsin=").substringBefore("&").substringBefore("?").trim()
            if (asin.isNotEmpty()) {
                return "$base?trackAsin=$asin"
            }
        } else if (clean.contains("music.amazon.")) {
            clean = clean.substringBefore("?")
        }

        // 6. Tidal: track/{id} or album/{id}
        val tidalMatch = Regex("(?:listen\\.)?tidal\\.com/(?:browse/)?(track|album)/([0-9a-zA-Z-]+)").find(clean)
        if (tidalMatch != null) {
            val type = tidalMatch.groupValues[1]
            val id = tidalMatch.groupValues[2]
            return "https://tidal.com/browse/$type/$id"
        }

        // 7. Generic Query Parameter Stripping (si, context, rowId, utm_*, ad-tracking)
        if (clean.contains("?")) {
            val base = clean.substringBefore("?")
            val query = clean.substringAfter("?")
            val trackingKeys = setOf(
                "si", "context", "rowid", "feature", "src", "ref", "ref_", "tag",
                "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
                "gclid", "fbclid", "igshid", "msclkid", "uo", "at", "ct", "app", "ls"
            )
            val keptParams = query.split("&").filter { param ->
                val key = param.substringBefore("=").lowercase()
                key !in trackingKeys
            }
            clean = if (keptParams.isEmpty()) base else "$base?${keptParams.joinToString("&")}"
        }

        return clean
    }

    /**
     * Cleans metadata noise from search queries (e.g. "- 2011 Remaster", "(Radio Edit)", "[Live]")
     * to dramatically improve direct API search matching rates across Apple Music, Deezer, and YouTube Music.
     */
    fun cleanSearchQuery(query: String): String {
        var cleaned = query.trim()
        if (cleaned.isBlank()) return cleaned

        // Strip remastered suffixes
        cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*\\d{4}\\s+remaster(?:ed)?"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*[\\(\\[]\\s*\\d{4}\\s+remaster(?:ed)?\\s*[\\)\\]]"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*[\\(\\[]\\s*remaster(?:ed)?(?:\\s+\\d{4})?\\s*[\\)\\]]"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*remaster(?:ed)?"), "")

        // Strip edit and version suffixes
        cleaned = cleaned.replace(Regex("(?i)\\s*[\\(\\[]\\s*(?:radio|single|album|extended|club)\\s+edit\\s*[\\)\\]]"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*(?:radio|single|album|extended|club)\\s+edit"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*[\\(\\[]\\s*(?:radio|single|album)\\s+version\\s*[\\)\\]]"), "")

        // Strip live concert suffixes
        cleaned = cleaned.replace(Regex("(?i)\\s*[\\(\\[]\\s*live(?:\\s+at[^)\\]]+)?\\s*[\\)\\]]"), "")
        cleaned = cleaned.replace(Regex("(?i)\\s*-\\s*live(?:\\s+at[^-]+)?"), "")

        // Clean extra internal spaces
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()

        return if (cleaned.isNotBlank()) cleaned else query.trim()
    }

    fun isShortLinkDomain(url: String): Boolean {
        return url.contains("spotify.link") ||
                url.contains("deezer.page.link") ||
                url.contains("link.deezer.com") ||
                url.contains("youtu.be") ||
                url.contains("t.co/") ||
                url.contains("://t.co") ||
                url.contains("bit.ly") ||
                url.contains("amzn.to") ||
                url.contains("a.co/") ||
                url.contains("://a.co") ||
                url.contains("apple.co/") ||
                url.contains("://apple.co")
    }

    fun isAlbumUrl(url: String): Boolean {
        if (url.contains("i=")) return false
        if (url.contains("trackAsin=")) return false
        if (url.contains("/track/")) return false
        if (url.contains("/song/")) return false
        return url.contains("/album/") || url.contains("/albums/") || url.contains("/album") || url.contains("album.link")
    }

    fun isPlaylistUrl(url: String): Boolean {
        if (url.contains("i=") || url.contains("trackAsin=") || url.contains("/track/") || url.contains("/song/")) return false
        return url.contains("/playlist/") || url.contains("/playlists/") || url.contains("/playlist?") || url.contains("link.deezer.com")
    }

    fun isSearchUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("spotify.com/search/") ||
                (clean.contains("spotify.com") && clean.contains("/search")) ||
                clean.contains("music.apple.com") && clean.contains("/search") ||
                clean.contains("music.youtube.com/search") ||
                clean.contains("youtube.com/results") ||
                clean.contains("deezer.com") && clean.contains("/search") ||
                clean.contains("tidal.com") && clean.contains("/search") ||
                clean.contains("music.amazon.") && clean.contains("/search")
    }

    fun extractSearchQuery(url: String): String? {
        val clean = url.trim()
        val lower = clean.lowercase()

        val rawQuery: String? = when {
            // Spotify: open.spotify.com/search/Farin%20Urlaub%20Kein%20Pardon or /intl-de/search/...
            lower.contains("spotify.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&").substringBefore("?")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Apple Music: music.apple.com/de/search?term=Farin%20Urlaub
            lower.contains("apple.com") && lower.contains("/search") -> {
                if (clean.contains("term=")) {
                    clean.substringAfter("term=").substringBefore("&")
                } else if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // YouTube Music: music.youtube.com/search?q=Farin+Urlaub
            lower.contains("music.youtube.com/search") -> {
                clean.substringAfter("q=").substringBefore("&")
            }
            // YouTube: youtube.com/results?search_query=Farin+Urlaub
            lower.contains("youtube.com/results") -> {
                clean.substringAfter("search_query=").substringBefore("&")
            }
            // Deezer: deezer.com/search/Farin%20Urlaub or deezer.com/de/search/Farin%20Urlaub
            lower.contains("deezer.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Tidal: tidal.com/search?q=Farin%20Urlaub or listen.tidal.com/search?q=...
            lower.contains("tidal.com") && lower.contains("/search") -> {
                if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // Amazon Music: music.amazon.com/search/Farin%20Urlaub or ?k=...
            lower.contains("music.amazon.") && lower.contains("/search") -> {
                if (clean.contains("k=")) {
                    clean.substringAfter("k=").substringBefore("&")
                } else if (clean.contains("keywords=")) {
                    clean.substringAfter("keywords=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            else -> null
        }

        if (rawQuery.isNullOrBlank()) return null

        return try {
            rawQuery.decodeUrl()
        } catch (_: Exception) {
            rawQuery.replace("+", " ").replace("%20", " ")
        }
    }

    private fun String.decodeUrl(): String {
        return this.replace("+", " ")
            .let { s ->
                val result = StringBuilder()
                var i = 0
                while (i < s.length) {
                    if (s[i] == '%' && i + 2 < s.length) {
                        val hex = s.substring(i + 1, i + 3)
                        val code = hex.toIntOrNull(16)
                        if (code != null) {
                            result.append(code.toChar())
                            i += 3
                            continue
                        }
                    }
                    result.append(s[i])
                    i++
                }
                result.toString()
            }
    }


    fun normalizeToSongLinkDirectUrl(url: String): String {
        val clean = if (url.contains("?")) url.substringBefore("?") else url

        // Spotify
        if (clean.contains("open.spotify.com/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://song.link/s/$id"
        }
        if (clean.contains("open.spotify.com/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://album.link/s/$id"
        }

        // Apple Music
        if (url.contains("apple.com") && url.contains("i=")) {
            val id = url.substringAfter("i=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/i/$id"
        }
        if (clean.contains("apple.com") && clean.contains("/song/")) {
            val id = clean.substringAfter("/song/").substringAfterLast("/").substringBefore("?").trim()
            if (id.isNotEmpty() && id.all { it.isDigit() }) return "https://song.link/i/$id"
        }
        if (clean.contains("apple.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringAfterLast("/").substringBefore("?").trim()
            if (id.isNotEmpty() && id.all { it.isDigit() }) return "https://album.link/i/$id"
        }

        // Deezer
        if (clean.contains("deezer.com") && clean.contains("/track/")) {
            val id = clean.substringAfter("/track/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://song.link/d/$id"
        }
        if (clean.contains("deezer.com") && clean.contains("/album/")) {
            val id = clean.substringAfter("/album/").substringBefore("/").trim()
            if (id.isNotEmpty()) return "https://album.link/d/$id"
        }

        // YouTube
        if (clean.contains("youtu.be/")) {
            val id = clean.substringAfter("youtu.be/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }
        if (url.contains("youtube.com/watch") && url.contains("v=")) {
            val id = url.substringAfter("v=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }

        val regionalCleaned = url
            .replace("music.amazon.de", "music.amazon.com")
            .replace("music.amazon.co.uk", "music.amazon.com")
            .replace("music.amazon.fr", "music.amazon.com")
            .replace("music.amazon.it", "music.amazon.com")
            .replace("music.amazon.es", "music.amazon.com")
            .replace("music.amazon.co.jp", "music.amazon.com")
            .replace("geo.music.apple.com", "music.apple.com")

        return if (clean.contains("/album/")) "https://album.link/$regionalCleaned" else "https://song.link/$regionalCleaned"
    }

    fun buildSearchUrl(queryText: String, targetPlatformKey: String): String {
        val query = queryText.encodeURLParameter()
        return when (targetPlatformKey) {
            "youtubeMusic" -> "https://music.youtube.com/search?q=$query"
            "appleMusic" -> "https://music.apple.com/search?term=$query"
            "spotify" -> "https://open.spotify.com/search/$query"
            "tidal" -> "https://listen.tidal.com/search?q=$query"
            "deezer" -> "https://www.deezer.com/search/$query"
            "amazonMusic" -> "https://music.amazon.com/search/$query"
            else -> "https://music.youtube.com/search?q=$query"
        }
    }

    fun formatTargetUrl(rawUrl: String, targetPlatformKey: String): String {
        if (targetPlatformKey == "youtubeMusic") {
            if (rawUrl.contains("music.youtube.com")) return rawUrl
            if (rawUrl.contains("youtube.com/playlist") || rawUrl.contains("m.youtube.com/playlist")) {
                return rawUrl.replace("www.youtube.com", "music.youtube.com")
                    .replace("m.youtube.com", "music.youtube.com")
                    .replace("youtube.com", "music.youtube.com")
            }
            if (rawUrl.contains("youtube.com/watch") || rawUrl.contains("m.youtube.com/watch")) {
                return rawUrl.replace("www.youtube.com", "music.youtube.com")
                    .replace("m.youtube.com", "music.youtube.com")
                    .replace("youtube.com", "music.youtube.com")
            }
            if (rawUrl.contains("youtu.be/")) {
                val videoId = rawUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                if (videoId.isNotEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }
        }
        return rawUrl
    }

    fun toNativeAppUri(url: String, platformKey: String): String {
        return when (platformKey) {
            "spotify" -> toNativeSpotifyUri(url)
            "deezer" -> toNativeDeezerUri(url)
            "tidal" -> toNativeTidalUri(url)
            "appleMusic" -> toNativeAppleMusicUri(url)
            "youtubeMusic" -> toNativeYouTubeMusicUri(url)
            "amazonMusic" -> toNativeAmazonMusicUri(url)
            else -> url
        }
    }

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

    fun toNativeAppleMusicUri(url: String): String {
        if (url.startsWith("music://") || url.startsWith("musics://")) return url
        return if (url.contains("music.apple.com")) {
            url.replace("https://music.apple.com", "music://music.apple.com")
               .replace("http://music.apple.com", "music://music.apple.com")
        } else {
            url
        }
    }

    fun toNativeYouTubeMusicUri(url: String): String {
        if (url.startsWith("youtubemusic://")) return url
        return if (url.contains("music.youtube.com")) {
            url.replace("https://music.youtube.com", "youtubemusic://music.youtube.com")
               .replace("http://music.youtube.com", "youtubemusic://music.youtube.com")
        } else {
            url
        }
    }

    fun toNativeAmazonMusicUri(url: String): String {
        if (url.startsWith("amznmp3://")) return url
        return if (url.contains("music.amazon.com")) {
            url.replace("https://music.amazon.com", "amznmp3://music.amazon.com")
               .replace("http://music.amazon.com", "amznmp3://music.amazon.com")
        } else {
            url
        }
    }
}
