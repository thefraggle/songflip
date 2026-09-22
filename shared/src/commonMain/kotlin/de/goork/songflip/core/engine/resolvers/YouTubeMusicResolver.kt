package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess

class YouTubeMusicResolver(
    private val client: HttpClient
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.YOUTUBE_MUSIC

    private val ytVideoRendererRegex = Regex("\"videoRenderer\":\\{\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytVideoIdJsonRegex = Regex("\"videoId\":\"([a-zA-Z0-9_-]{11})\"")
    private val ytWatchRegex = Regex("/watch\\?v=([a-zA-Z0-9_-]{11})")
    private val ytAlbumPlaylistRegex = Regex("\"playlistId\":\"(OLAK5uy_[a-zA-Z0-9_-]+)\"")
    private val ytAlbumBrowseRegex = Regex("\"browseId\":\"(MPREb_[a-zA-Z0-9_-]+)\"")
    private val ytGenericPlaylistRegex = Regex("\"playlistId\":\"([a-zA-Z0-9_-]{18,})\"")

    override suspend fun resolveTrack(query: String): String? {
        // Strategy 1: YouTube Music Innertube Web Client Search (Primary)
        val innertubeVideoId = searchYouTubeMusicInnertube(query)
        if (innertubeVideoId != null) {
            return "https://music.youtube.com/watch?v=$innertubeVideoId"
        }

        // Strategy 2: HTML Scraping via Desktop Web Search (Fallback)
        val scrapedVideoId = searchYouTubeHtmlScraping(query)
        if (scrapedVideoId != null) {
            return "https://music.youtube.com/watch?v=$scrapedVideoId"
        }

        return null
    }

    override suspend fun resolveAlbum(query: String): String? {
        val innertubeAlbum = searchYouTubeMusicAlbumInnertube(query)
        if (innertubeAlbum != null) return innertubeAlbum

        val scrapedAlbum = searchYouTubeAlbumHtmlScraping(query)
        if (scrapedAlbum != null) return scrapedAlbum

        return null
    }

    private suspend fun searchYouTubeMusicInnertube(query: String): String? {
        return try {
            val payload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240101.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "${query.replace("\"", "\\\"")}",
                    "params": "Eg-KAQwIARAAGAAgACgAMABqChAMEAMQBBAFEAo%3D"
                }
            """.trimIndent()

            val resp = client.post("https://music.youtube.com/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Origin", "https://music.youtube.com")
                header("Referer", "https://music.youtube.com/")
                setBody(payload)
            }

            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val match = ytVideoIdJsonRegex.find(body)
                if (match != null) {
                    return match.groupValues[1]
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun searchYouTubeHtmlScraping(query: String): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9")
            }

            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rendererMatch = ytVideoRendererRegex.find(body)
                if (rendererMatch != null) {
                    return rendererMatch.groupValues[1]
                }
                val watchMatch = ytWatchRegex.find(body)
                if (watchMatch != null) {
                    return watchMatch.groupValues[1]
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun searchYouTubeMusicAlbumInnertube(query: String): String? {
        return try {
            val payload = """
                {
                    "context": {
                        "client": {
                            "clientName": "WEB_REMIX",
                            "clientVersion": "1.20240101.01.00",
                            "hl": "en",
                            "gl": "US"
                        }
                    },
                    "query": "${query.replace("\"", "\\\"")}",
                    "params": "Eg-KAQwIABAAGAAgACgAMABqChAMEAMQBBAFEAo%3D"
                }
            """.trimIndent()

            val resp = client.post("https://music.youtube.com/youtubei/v1/search") {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                setBody(payload)
            }

            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val albumMatch = ytAlbumPlaylistRegex.find(body)
                if (albumMatch != null) {
                    return "https://music.youtube.com/playlist?list=${albumMatch.groupValues[1]}"
                }
                val browseMatch = ytAlbumBrowseRegex.find(body)
                if (browseMatch != null) {
                    return "https://music.youtube.com/browse/${browseMatch.groupValues[1]}"
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun searchYouTubeAlbumHtmlScraping(query: String): String? {
        return try {
            val encoded = "$query album".encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAw%253D%253D") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }

            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val albumMatch = ytAlbumPlaylistRegex.find(body) ?: ytGenericPlaylistRegex.find(body)
                if (albumMatch != null) {
                    return "https://music.youtube.com/playlist?list=${albumMatch.groupValues[1]}"
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun resolveArtist(artistName: String): String? {
        return try {
            val encoded = "$artistName Topic".encodeURLParameter()
            val resp = client.get("https://www.youtube.com/results?search_query=$encoded&sp=EgIQAg%253D%253D") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "de,en;q=0.9")
            }
            if (!resp.status.isSuccess()) return null
            val html = resp.bodyAsText()
            val channelRendererRegex = "\"channelRenderer\":\\{\"channelId\":\"(UC[a-zA-Z0-9_-]{22})\",.*?\"title\":\\{\"simpleText\":\"(.*?)\"\\}".toRegex()
            val match = channelRendererRegex.find(html)
            if (match != null) {
                val channelId = match.groupValues[1]
                val channelTitle = match.groupValues[2]
                if (channelId.isNotEmpty() && channelTitle.isNotEmpty() && isArtistNameMatch(channelTitle, artistName)) {
                    return "https://music.youtube.com/channel/$channelId"
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun isArtistNameMatch(candidate: String, target: String): Boolean {
        fun normalize(str: String): String {
            return str.lowercase().trim()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .replace("[^a-z0-9]".toRegex(), "")
        }
        val normCand = normalize(candidate)
        val normTarget = normalize(target)
        if (normCand.isEmpty() || normTarget.isEmpty()) return false
        if (normCand == normTarget) return true
        if (normCand == normTarget + "thema" || normCand == normTarget + "topic") return true
        return false
    }
}
