package de.goork.songflip.core.util

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.http.encodeURLParameter

object UrlUtils {

    private val urlRegex = Regex("(https?://[^\\s<>'\"()]+)")

    fun extractCleanUrl(rawInput: String): String? {
        val match = urlRegex.find(rawInput)
        val extracted = match?.value ?: if (rawInput.startsWith("http://") || rawInput.startsWith("https://")) {
            rawInput.trim()
        } else {
            null
        }

        return extracted?.trimEnd('.', ',', '!', '?', ';', ':', ')', '>', ']', '"', '\'', '»', '”', '“')
    }

    fun isShortLinkDomain(url: String): Boolean {
        return url.contains("spotify.link") ||
                url.contains("deezer.page.link") ||
                url.contains("link.deezer.com") ||
                url.contains("youtu.be") ||
                url.contains("t.co") ||
                url.contains("bit.ly") ||
                url.contains("amzn.to") ||
                url.contains("a.co") ||
                url.contains("apple.co")
    }

    fun isAlbumUrl(url: String): Boolean {
        if (url.contains("i=")) return false
        if (url.contains("trackAsin=")) return false
        if (url.contains("/track/")) return false
        return url.contains("/album/") || url.contains("/albums/") || url.contains("/album") || url.contains("album.link")
    }

    fun isPlaylistUrl(url: String): Boolean {
        if (url.contains("i=") || url.contains("trackAsin=") || url.contains("/track/")) return false
        return url.contains("/playlist/") || url.contains("/playlists/") || url.contains("link.deezer.com")
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
        if (clean.contains("apple.com") && clean.contains("/album/")) {
            val id = clean.trimEnd('/').substringAfterLast("/").trim()
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
