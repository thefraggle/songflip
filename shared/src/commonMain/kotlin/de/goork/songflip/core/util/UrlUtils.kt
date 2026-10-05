package de.goork.songflip.core.util

import de.goork.songflip.core.model.MusicEntityType
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
        val spotifyPrereleaseMatch = Regex("open\\.spotify\\.com(?:/intl-[a-zA-Z-]+)?/prerelease/([a-zA-Z0-9]+)").find(clean)
        if (spotifyPrereleaseMatch != null) {
            val id = spotifyPrereleaseMatch.groupValues[1]
            return "https://open.spotify.com/prerelease/$id"
        }

        val host = extractDomain(clean)

        // 2. Apple Music: Preserve base URL + exact track id parameter (?i=...)
        if (isHostOrSubdomain(host, "apple.com", "music.apple.com") && clean.contains("i=")) {
            val base = clean.substringBefore("?")
            val trackId = clean.substringAfter("i=").substringBefore("&").substringBefore("?").trim()
            if (trackId.isNotEmpty()) {
                return "$base?i=$trackId"
            }
        } else if (isHostOrSubdomain(host, "apple.com", "music.apple.com") && (clean.contains("/album/") || clean.contains("/song/"))) {
            clean = clean.substringBefore("?")
        }

        // 3. Deezer: deezer.com/track/{id}, album/{id}, artist/{id}
        val deezerMatch = Regex("deezer\\.com(?:/[a-zA-Z-]+)?/(track|album|artist)/(\\d+)").find(clean)
        if (deezerMatch != null) {
            val type = deezerMatch.groupValues[1]
            val id = deezerMatch.groupValues[2]
            return "https://www.deezer.com/$type/$id"
        }

        // 4. YouTube & YouTube Music: watch?v={id}, youtu.be/{id}, or /shorts/{id}
        if (host == "youtu.be") {
            val id = clean.substringAfter("youtu.be/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) {
                return "https://youtu.be/$id"
            }
        }
        if (isHostOrSubdomain(host, "youtube.com") && clean.contains("/shorts/")) {
            val id = clean.substringAfter("/shorts/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) {
                return "https://www.youtube.com/watch?v=$id"
            }
        }
        if (isHostOrSubdomain(host, "youtube.com") && clean.contains("watch") && clean.contains("v=")) {
            val isYtMusic = host == "music.youtube.com"
            val schemeHost = if (isYtMusic) "https://music.youtube.com" else "https://www.youtube.com"
            val id = clean.substringAfter("v=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) {
                return "$schemeHost/watch?v=$id"
            }
        }

        // 5. Amazon Music: preserve trackAsin if present, otherwise strip tracking
        if ((host.startsWith("music.amazon.") || host.contains(".amazon.")) && clean.contains("trackAsin=")) {
            val base = clean.substringBefore("?")
            val asin = clean.substringAfter("trackAsin=").substringBefore("&").substringBefore("?").trim()
            if (asin.isNotEmpty()) {
                return "$base?trackAsin=$asin"
            }
        } else if (host.startsWith("music.amazon.") || host.contains(".amazon.")) {
            clean = clean.substringBefore("?")
        }

        // 6. Tidal: track/{id} or album/{id}
        val tidalMatch = Regex("(?:(?:www\\.|listen\\.)?tidal\\.com)/(?:browse/)?(track|album)/([0-9a-zA-Z-]+)").find(clean)
        if (tidalMatch != null) {
            val type = tidalMatch.groupValues[1]
            val id = tidalMatch.groupValues[2]
            return "https://tidal.com/browse/$type/$id"
        }

        // 7. SoundCloud: soundcloud.com/{artist}/{track}
        val soundCloudMatch = Regex("(?:https?://)?(?:www\\.|m\\.)?soundcloud\\.com/([a-zA-Z0-9_-]+)/([a-zA-Z0-9_-]+)").find(clean)
        if (soundCloudMatch != null && soundCloudMatch.groupValues[1].lowercase() !in listOf("search", "discover", "upload", "you", "stream", "settings")) {
            val user = soundCloudMatch.groupValues[1]
            val track = soundCloudMatch.groupValues[2]
            return "https://soundcloud.com/$user/$track"
        }

        // 8. Bandcamp: {subdomain}.bandcamp.com/(track|album)/{slug}
        val bandcampMatch = Regex("(?:https?://)?([a-zA-Z0-9_-]+)\\.bandcamp\\.com/(track|album)/([a-zA-Z0-9_-]+)").find(clean)
        if (bandcampMatch != null) {
            val subdomain = bandcampMatch.groupValues[1]
            val type = bandcampMatch.groupValues[2]
            val slug = bandcampMatch.groupValues[3]
            return "https://$subdomain.bandcamp.com/$type/$slug"
        }

        // 9. Generic Query Parameter Stripping (si, context, rowId, utm_*, ad-tracking)
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

        // Strip YouTube Topic Channel suffix
        cleaned = cleaned.replace(Regex("(?i)\\s*[-–—]\\s*topic$"), "")

        // Replace disruptive punctuation & symbols with spaces (e.g. +, &, #, |, :, quotes)
        cleaned = cleaned.replace(Regex("""[+&/\\|•~^*#:_"'`]"""), " ")

        // Remove emojis, variation selectors, and miscellaneous decorative symbols
        cleaned = cleaned.replace(Regex("""[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF\uFE00-\uFE0F]"""), " ")

        // Clean extra internal spaces
        cleaned = cleaned.replace(Regex("\\s{2,}"), " ").trim()

        return if (cleaned.isNotBlank()) cleaned else query.trim()
    }

    fun isShortLinkDomain(url: String): Boolean {
        val host = extractDomain(url)
        return isHostOrSubdomain(
            host,
            "spotify.link",
            "spotify.app.link",
            "spoti.fi",
            "deezer.page.link",
            "link.deezer.com",
            "tidal.link",
            "youtu.be",
            "on.soundcloud.com",
            "t.co",
            "bit.ly",
            "amzn.to",
            "amzn.eu",
            "amzn.asia",
            "a.co",
            "apple.co",
            "shazam.com"
        )
    }

    private val albumPathRegex = Regex("""(?:/album/|/albums/|album\.link/|\.bandcamp\.com/album/)""", RegexOption.IGNORE_CASE)

    fun isAlbumUrl(url: String): Boolean {
        if (url.contains("i=")) return false
        if (url.contains("trackAsin=")) return false
        if (url.contains("/track/")) return false
        if (url.contains("/song/")) return false
        val clean = url.substringBefore("?")
        return albumPathRegex.containsMatchIn(clean) || clean.endsWith("/album") || clean.endsWith("/albums")
    }

    fun isPlaylistUrl(url: String): Boolean {
        if (url.contains("i=") || url.contains("trackAsin=") || url.contains("/track/") || url.contains("/song/")) return false
        val clean = url.lowercase()
        // YouTube URLs with watch?v= or youtu.be/ are individual songs, even if they have a &list= parameter
        if (clean.contains("watch?v=") || clean.contains("youtu.be/")) return false

        val host = extractDomain(clean)
        val path = clean.substringAfter(host, "")

        return (isHostOrSubdomain(host, "spotify.com") && (path.contains("/playlist/") || path.contains("/playlists/"))) ||
                (isHostOrSubdomain(host, "apple.com") && (path.contains("/playlist/") || path.contains("/playlists/"))) ||
                (isHostOrSubdomain(host, "youtube.com") && (path.contains("/playlist") || path.contains("list="))) ||
                (isHostOrSubdomain(host, "deezer.com", "link.deezer.com") && (path.contains("/playlist/") || host == "link.deezer.com")) ||
                (isHostOrSubdomain(host, "tidal.com") && (path.contains("/playlist/") || path.contains("/playlists/"))) ||
                (isHostOrSubdomain(host, "soundcloud.com") && (path.contains("/sets/") || path.contains("/playlists/"))) ||
                ((host.startsWith("music.amazon.") || host.contains(".amazon.")) && path.contains("/playlists/")) ||
                clean.startsWith("spotify:playlist:")
    }

    fun isPodcastUrl(url: String): Boolean {
        val clean = url.lowercase()
        val host = extractDomain(clean)
        val path = clean.substringAfter(host, "")

        return isHostOrSubdomain(host, "podcasts.apple.com", "podcasts.google.com", "pocketcasts.com", "pca.st", "castbox.fm", "overcast.fm") ||
                ((isHostOrSubdomain(host, "spotify.com", "deezer.com", "tidal.com", "youtube.com", "apple.com") || host.startsWith("music.amazon.") || host.contains(".amazon.")) &&
                        (path.contains("/show/") || path.contains("/shows/") || path.contains("/episode/") || path.contains("/episodes/") || path.contains("/podcast/") || path.contains("/podcasts/"))) ||
                clean.startsWith("spotify:episode:") ||
                clean.startsWith("spotify:show:")
    }

    fun isAudiobookUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("/audiobook/") ||
                clean.contains("/audiobooks/") ||
                clean.contains("books.apple.com") ||
                clean.contains("audiobooks.apple.com") ||
                clean.contains("audible.com") ||
                clean.contains("audible.de") ||
                clean.contains("audible.co.uk") ||
                clean.contains("audible.fr") ||
                clean.contains("audible.it") ||
                clean.contains("audible.es") ||
                clean.contains("audible.ca") ||
                clean.contains("audible.in") ||
                clean.contains("audible.com.au") ||
                clean.startsWith("spotify:audiobook:")
    }

    fun isPodcastOrAudiobookUrl(url: String): Boolean {
        return isPodcastUrl(url) || isAudiobookUrl(url)
    }

    fun isSocialOrSessionUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("spotify.com/blend/") ||
                clean.startsWith("spotify:blend:") ||
                clean.contains("spotify.com/jam/") ||
                clean.startsWith("spotify:jam:") ||
                clean.contains("spotify.com/live/") ||
                clean.startsWith("spotify:live:") ||
                clean.contains("spotify.com/user/") ||
                clean.startsWith("spotify:user:") ||
                clean.contains("spotify.com/collection")
    }

    fun isPreReleaseUrl(url: String): Boolean {
        val clean = url.lowercase()
        return clean.contains("spotify.com/prerelease/") ||
                clean.startsWith("spotify:prerelease:")
    }

    fun detectEntityType(url: String): MusicEntityType {
        val clean = url.lowercase()
        return when {
            isPodcastUrl(url) -> MusicEntityType.PODCAST
            isAudiobookUrl(url) -> MusicEntityType.AUDIOBOOK
            isSocialOrSessionUrl(url) -> MusicEntityType.SOCIAL_SESSION
            isPreReleaseUrl(url) -> MusicEntityType.PRE_RELEASE
            isSearchUrl(url) -> MusicEntityType.SEARCH
            isPlaylistUrl(url) -> MusicEntityType.PLAYLIST
            isAlbumUrl(url) -> MusicEntityType.ALBUM
            clean.contains("/artist/") || clean.contains("/channel/") -> MusicEntityType.ARTIST
            clean.contains("/track/") || clean.contains("/song/") || clean.contains("youtu.be/") || clean.contains("watch?v=") || clean.contains("/shorts/") || clean.contains("i=") || clean.contains("trackasin=") -> MusicEntityType.TRACK
            else -> MusicEntityType.UNKNOWN
        }
    }

    fun extractDomain(url: String): String {
        val clean = url.trim()
        val afterScheme = when {
            clean.startsWith("https://", ignoreCase = true) -> clean.substring(8)
            clean.startsWith("http://", ignoreCase = true) -> clean.substring(7)
            else -> clean
        }
        val host = afterScheme.substringBefore("/").substringBefore("?").substringBefore("#").substringBefore(":").trim().lowercase()
        return host.ifBlank { "unknown" }
    }

    private fun isHostOrSubdomain(host: String, vararg domains: String): Boolean {
        return domains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    fun detectPlatform(url: String): MusicPlatform? {
        val lower = url.trim().lowercase()
        val host = extractDomain(lower)
        return when {
            isHostOrSubdomain(host, "spotify.com", "spotify.link", "spotify.app.link", "spoti.fi") || lower.startsWith("spotify:") -> MusicPlatform.SPOTIFY
            isHostOrSubdomain(host, "apple.com", "apple.co", "itun.es") -> MusicPlatform.APPLE_MUSIC
            host == "music.youtube.com" -> MusicPlatform.YOUTUBE_MUSIC
            isHostOrSubdomain(host, "youtube.com", "youtu.be") -> MusicPlatform.YOUTUBE_MUSIC
            isHostOrSubdomain(host, "deezer.com", "deezer.page.link") -> MusicPlatform.DEEZER
            isHostOrSubdomain(host, "tidal.com", "tidal.link") -> MusicPlatform.TIDAL
            isHostOrSubdomain(host, "amzn.to", "amzn.eu", "amzn.asia", "a.co") || host.startsWith("music.amazon.") || host.startsWith("amazon.") || host.contains(".amazon.") -> MusicPlatform.AMAZON_MUSIC
            isHostOrSubdomain(host, "soundcloud.com") -> MusicPlatform.SOUNDCLOUD
            isHostOrSubdomain(host, "bandcamp.com") -> MusicPlatform.BANDCAMP
            else -> null
        }
    }

    fun isSearchUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        val host = extractDomain(clean)
        val path = clean.substringAfter(host, "")
        return (isHostOrSubdomain(host, "spotify.com") && path.contains("/search")) ||
                (isHostOrSubdomain(host, "apple.com") && path.contains("/search")) ||
                (isHostOrSubdomain(host, "youtube.com") && (path.contains("/search") || path.contains("/results"))) ||
                (isHostOrSubdomain(host, "deezer.com") && path.contains("/search")) ||
                (isHostOrSubdomain(host, "tidal.com") && path.contains("/search")) ||
                ((host.contains("amazon.") || isHostOrSubdomain(host, "amzn.to")) && path.contains("/search")) ||
                (isHostOrSubdomain(host, "soundcloud.com") && path.contains("/search")) ||
                (isHostOrSubdomain(host, "bandcamp.com") && path.contains("/search"))
    }

    fun extractSearchQuery(url: String): String? {
        val clean = url.trim()
        val lower = clean.lowercase()
        val host = extractDomain(lower)

        val rawQuery: String? = when {
            // Spotify: open.spotify.com/search/Farin%20Urlaub%20Kein%20Pardon or /intl-de/search/...
            isHostOrSubdomain(host, "spotify.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&").substringBefore("?")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Apple Music: music.apple.com/de/search?term=Farin%20Urlaub
            isHostOrSubdomain(host, "apple.com") && lower.contains("/search") -> {
                if (clean.contains("term=")) {
                    clean.substringAfter("term=").substringBefore("&")
                } else if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // YouTube Music: music.youtube.com/search?q=Farin+Urlaub
            host == "music.youtube.com" && lower.contains("/search") -> {
                clean.substringAfter("q=").substringBefore("&")
            }
            // YouTube: youtube.com/results?search_query=Farin+Urlaub
            isHostOrSubdomain(host, "youtube.com") && lower.contains("/results") -> {
                clean.substringAfter("search_query=").substringBefore("&")
            }
            // Deezer: deezer.com/search/Farin%20Urlaub or deezer.com/de/search/Farin%20Urlaub
            isHostOrSubdomain(host, "deezer.com") && lower.contains("/search") -> {
                val afterSearch = clean.substringAfter("/search/").substringAfter("/search?")
                if (afterSearch.startsWith("q=")) {
                    afterSearch.substringAfter("q=").substringBefore("&")
                } else {
                    afterSearch.substringBefore("?").substringBefore("&")
                }
            }
            // Tidal: tidal.com/search?q=Farin%20Urlaub or listen.tidal.com/search?q=...
            isHostOrSubdomain(host, "tidal.com") && lower.contains("/search") -> {
                if (clean.contains("q=")) {
                    clean.substringAfter("q=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // Amazon Music: music.amazon.com/search/Farin%20Urlaub or ?k=...
            (host.contains("amazon.") || isHostOrSubdomain(host, "amzn.to")) && lower.contains("/search") -> {
                if (clean.contains("k=")) {
                    clean.substringAfter("k=").substringBefore("&")
                } else if (clean.contains("keywords=")) {
                    clean.substringAfter("keywords=").substringBefore("&")
                } else {
                    clean.substringAfter("/search/").substringBefore("?").substringBefore("&")
                }
            }
            // SoundCloud: soundcloud.com/search?q=...
            isHostOrSubdomain(host, "soundcloud.com") && lower.contains("/search") -> {
                clean.substringAfter("q=").substringBefore("&")
            }
            // Bandcamp: bandcamp.com/search?q=...
            isHostOrSubdomain(host, "bandcamp.com") && lower.contains("/search") -> {
                clean.substringAfter("q=").substringBefore("&")
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
        return try {
            val bytes = mutableListOf<Byte>()
            var i = 0
            val s = this.replace("+", " ")
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val hex = s.substring(i + 1, i + 3)
                    val byteVal = hex.toIntOrNull(16)
                    if (byteVal != null) {
                        bytes.add(byteVal.toByte())
                        i += 3
                        continue
                    }
                }
                val charBytes = c.toString().encodeToByteArray()
                for (b in charBytes) {
                    bytes.add(b)
                }
                i++
            }
            bytes.toByteArray().decodeToString()
        } catch (_: Throwable) {
            this.replace("+", " ").replace("%20", " ")
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
        if (clean.contains("youtube.com/shorts/")) {
            val id = clean.substringAfter("youtube.com/shorts/").substringBefore("/").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }
        if (url.contains("youtube.com/watch") && url.contains("v=")) {
            val id = url.substringAfter("v=").substringBefore("&").substringBefore("?").trim()
            if (id.isNotEmpty()) return "https://song.link/y/$id"
        }

        val regionalCleaned = url
            .replace(Regex("""music\.amazon\.[a-z.]+"""), "music.amazon.com")
            .replace("geo.music.apple.com", "music.apple.com")

        val isAlbum = (clean.contains("/album/") || clean.contains("/albums/")) && !url.contains("trackAsin=")
        return if (isAlbum) "https://album.link/$regionalCleaned" else "https://song.link/$regionalCleaned"
    }

    fun buildSearchUrl(queryText: String, targetPlatformKey: String): String {
        val clean = cleanSearchQuery(queryText)
        val query = clean.encodeURLParameter()
        return when (targetPlatformKey) {
            "youtubeMusic" -> "https://music.youtube.com/search?q=$query"
            "appleMusic" -> "https://music.apple.com/search?term=$query"
            "spotify" -> "https://open.spotify.com/search/$query"
            "tidal" -> "https://listen.tidal.com/search?q=$query"
            "deezer" -> "https://www.deezer.com/search/$query"
            "amazonMusic" -> "https://music.amazon.com/search/$query"
            "soundcloud" -> "https://soundcloud.com/search?q=$query"
            "bandcamp" -> "https://bandcamp.com/search?q=$query"
            else -> "https://music.youtube.com/search?q=$query"
        }
    }

    fun buildPodcastSearchUrl(queryText: String, targetPlatformKey: String): String {
        val clean = cleanSearchQuery(queryText)
        val query = clean.encodeURLParameter()
        return when (targetPlatformKey) {
            "spotify" -> "https://open.spotify.com/search/$query"
            "appleMusic", "applePodcasts" -> "https://podcasts.apple.com/search?term=$query"
            "youtubeMusic" -> "https://music.youtube.com/search?q=$query"
            "pocketCasts" -> "https://play.pocketcasts.com/podcasts/search?q=$query"
            "amazonMusic" -> "https://music.amazon.com/search/$query"
            "deezer" -> "https://www.deezer.com/search/$query"
            else -> "https://open.spotify.com/search/$query"
        }
    }

    fun toPodcastNativeAppUri(targetPlatformKey: String, queryText: String): String? {
        val clean = cleanSearchQuery(queryText)
        val query = clean.encodeURLParameter()
        return when (targetPlatformKey) {
            "spotify" -> if (query.isNotBlank()) "spotify:search:$query" else "spotify:"
            "appleMusic", "applePodcasts" -> if (query.isNotBlank()) "podcast://podcasts.apple.com/search?term=$query" else "podcast://"
            "pocketCasts" -> if (query.isNotBlank()) "pocketcasts://search?q=$query" else "pocketcasts://"
            "youtubeMusic" -> if (query.isNotBlank()) "https://music.youtube.com/search?q=$query" else null
            else -> null
        }
    }

    fun formatTargetUrl(rawUrl: String, targetPlatformKey: String): String {
        var url = rawUrl
        // Self-healing: fix corrupted double-domain if present from legacy cache
        if (url.contains("music.music.youtube.com")) {
            url = url.replace("music.music.youtube.com", "music.youtube.com")
        }
        if (targetPlatformKey == "youtubeMusic") {
            if (url.contains("music.youtube.com")) return url
            if (url.contains("youtu.be/")) {
                val videoId = url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                if (videoId.isNotEmpty()) {
                    return "https://music.youtube.com/watch?v=$videoId"
                }
            }
            val ytDomainRegex = Regex("^https?://(?:www\\.|m\\.)?youtube\\.com")
            if (ytDomainRegex.containsMatchIn(url)) {
                return url.replaceFirst(ytDomainRegex, "https://music.youtube.com")
            }
        }
        return url
    }

    /**
     * Converts native/specialized player URLs into universal, non-paywalled web URLs
     * when the native target app is missing on the device.
     * E.g. converts music.youtube.com URLs to youtube.com URLs so mobile web users
     * land directly on the playable song rather than the "Music Premium" subscription paywall.
     */
    fun toWebFallbackUrl(rawUrl: String, targetPlatformKey: String): String {
        var url = rawUrl
        if (url.contains("music.music.youtube.com")) {
            url = url.replace("music.music.youtube.com", "music.youtube.com")
        }
        val cleanKey = targetPlatformKey.substringBefore("_")
        if (cleanKey == "youtubeMusic") {
            if (url.startsWith("youtubemusic://")) {
                url = url.replace("youtubemusic://", "https://")
            }
            if (url.startsWith("vnd.youtube.music://")) {
                url = url.replace("vnd.youtube.music://", "https://www.youtube.com/")
            }
            if (url.contains("music.youtube.com/search") && url.contains("q=")) {
                val query = url.substringAfter("q=").substringBefore("&").substringBefore("#")
                return "https://www.youtube.com/results?search_query=$query"
            }
            val musicYtRegex = Regex("^https?://music\\.youtube\\.com")
            if (musicYtRegex.containsMatchIn(url)) {
                return url.replaceFirst(musicYtRegex, "https://www.youtube.com")
            }
        }
        return url
    }

    fun toNativeAppUri(url: String, platformKey: String): String {
        return when (platformKey) {
            "spotify" -> toNativeSpotifyUri(url)
            "deezer" -> toNativeDeezerUri(url)
            "tidal" -> toNativeTidalUri(url)
            "appleMusic" -> toNativeAppleMusicUri(url)
            "youtubeMusic" -> toNativeYouTubeMusicUri(url)
            "amazonMusic" -> toNativeAmazonMusicUri(url)
            "soundcloud" -> toNativeSoundCloudUri(url)
            "bandcamp" -> toNativeBandcampUri(url)
            else -> url
        }
    }

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

    fun toNativeAppleMusicUri(url: String): String {
        if (url.startsWith("music://") || url.startsWith("musics://")) return url
        val clean = url.replace("geo.music.apple.com", "music.apple.com")
        return if (clean.contains("music.apple.com")) {
            clean.replace("https://music.apple.com", "music://music.apple.com")
                 .replace("http://music.apple.com", "music://music.apple.com")
        } else {
            clean
        }
    }

    fun toNativeYouTubeMusicUri(url: String): String {
        if (url.startsWith("youtubemusic://")) return url
        var clean = if (url.contains("music.music.youtube.com")) {
            url.replace("music.music.youtube.com", "music.youtube.com")
        } else url
        val ytRegex = Regex("^https?://(?:www\\.|m\\.)?youtube\\.com")
        if (ytRegex.containsMatchIn(clean)) {
            clean = clean.replaceFirst(ytRegex, "https://music.youtube.com")
        }
        return if (clean.contains("music.youtube.com")) {
            clean.replace("https://music.youtube.com", "youtubemusic://music.youtube.com")
                 .replace("http://music.youtube.com", "youtubemusic://music.youtube.com")
        } else {
            clean
        }
    }

    fun toNativeAmazonMusicUri(url: String): String {
        if (url.startsWith("amznmp3://")) return url
        val amazonDomainRegex = Regex("^https?://music\\.amazon\\.[a-z.]+")
        return if (amazonDomainRegex.containsMatchIn(url)) {
            url.replaceFirst(amazonDomainRegex, "amznmp3://music.amazon.com")
        } else {
            url
        }
    }

    fun toNativeSoundCloudUri(url: String): String {
        if (url.startsWith("soundcloud:")) return url
        val clean = url.trim().substringBefore("?")
        val match = Regex("(?:https?://)?(?:www\\.|m\\.)?soundcloud\\.com/([a-zA-Z0-9_-]+)/([a-zA-Z0-9_-]+)").find(clean)
        return if (match != null && match.groupValues[1].lowercase() !in listOf("search", "discover", "upload", "you", "stream")) {
            "soundcloud://tracks/${match.groupValues[1]}/${match.groupValues[2]}"
        } else {
            url
        }
    }

    fun toNativeBandcampUri(url: String): String {
        return url
    }

    /**
     * Checks if a URL belongs to SongFlip's own domains (e.g. download.songflip.link, share.songflip.link).
     * Used as a defensive guard to prevent self-referential flips and guide users to share authentic music links.
     */
    fun isSongFlipUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        if (clean.isBlank()) return false
        val host = clean
            .substringAfter("://")
            .substringBefore("/")
            .substringBefore(":")
            .substringBefore("?")
            .trim()
        if (host.isEmpty()) return false
        return host == "songflip.link" || host.endsWith(".songflip.link") ||
               host == "songflip.de" || host.endsWith(".songflip.de") ||
               host == "songflip.app" || host.endsWith(".songflip.app") ||
               host == "songflip-web.web.app" ||
               host == "songflip-web.firebaseapp.com"
    }
}

