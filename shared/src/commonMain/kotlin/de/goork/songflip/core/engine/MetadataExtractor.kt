package de.goork.songflip.core.engine

import de.goork.songflip.core.engine.resolvers.AppleMusicResolver
import de.goork.songflip.core.engine.resolvers.AudiomackResolver
import de.goork.songflip.core.engine.resolvers.BeatportResolver
import de.goork.songflip.core.engine.resolvers.DeezerResolver
import de.goork.songflip.core.engine.resolvers.IdagioResolver
import de.goork.songflip.core.engine.resolvers.AnghamiResolver
import de.goork.songflip.core.engine.resolvers.QobuzResolver
import de.goork.songflip.core.engine.resolvers.SpotifyResolver
import de.goork.songflip.core.engine.resolvers.YouTubeMusicResolver
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class CommonOEmbedDto(
    val title: String? = null,
    @SerialName("author_name")
    val authorName: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null
)

/**
 * Extracts music metadata (track title, artist, playlist name) directly from streaming links.
 */
class MetadataExtractor(
    private val client: HttpClient,
    private val json: Json,
    private val appleMusicResolver: AppleMusicResolver,
    private val deezerResolver: DeezerResolver,
    private val youTubeMusicResolver: YouTubeMusicResolver,
    private val spotifyResolver: SpotifyResolver,
    private val qobuzResolver: QobuzResolver,
    private val beatportResolver: BeatportResolver,
    private val audiomackResolver: AudiomackResolver,
    private val idagioResolver: IdagioResolver,
    private val anghamiResolver: AnghamiResolver = AnghamiResolver(client)
) {
    suspend fun extractTrackInfo(url: String): String? {
        return try {
            when (UrlUtils.detectPlatform(url)) {
                MusicPlatform.SPOTIFY -> {
                    val scraped = spotifyResolver.extractEmbedMetadata(url)
                    if (scraped != null && scraped.title.isNotBlank()) {
                        val author = scraped.artist
                        if (!author.isNullOrBlank() && !scraped.title.contains(author, ignoreCase = true)) {
                            "$author ${scraped.title}"
                        } else {
                            scraped.title
                        }
                    } else {
                        spotifyResolver.extractMetadata(url)
                    }
                }
                MusicPlatform.APPLE_MUSIC -> appleMusicResolver.extractMetadata(url)
                MusicPlatform.YOUTUBE_MUSIC -> {
                    val details = youTubeMusicResolver.extractVideoDetails(url)
                    if (details != null && details.title.isNotBlank()) {
                        val author = details.author
                        if (!author.isNullOrBlank() && !details.title.contains(author, ignoreCase = true)) {
                            "$author ${details.title}"
                        } else {
                            details.title
                        }
                    } else {
                        val encoded = url.encodeURLParameter()
                        val resp = client.get("https://www.youtube.com/oembed?url=$encoded&format=json")
                        if (resp.status.isSuccess()) {
                            val oembed = json.decodeFromString<CommonOEmbedDto>(resp.bodyAsText())
                            val rawTitle = oembed.title ?: ""
                            val author = oembed.authorName ?: ""
                            val title = UrlUtils.cleanVideoTitle(rawTitle)
                            val cleanAuthor = author.replace(Regex("(?i)\\s*[-–—]?\\s*vevo$"), "").replace(Regex("(?i)\\s*[-–—]?\\s*topic$"), "").trim()
                            if (title.isNotEmpty()) {
                                if (cleanAuthor.isNotEmpty() && !title.contains(cleanAuthor, ignoreCase = true)) "$cleanAuthor $title" else title
                            } else null
                        } else null
                    }
                }
                MusicPlatform.DEEZER -> {
                    val encoded = url.encodeURLParameter()
                    val resp = client.get("https://api.deezer.com/oembed?url=$encoded")
                    if (resp.status.isSuccess()) {
                        val oembed = json.decodeFromString<CommonOEmbedDto>(resp.bodyAsText())
                        val title = oembed.title
                        if (!title.isNullOrEmpty()) return title
                    }
                    null
                }
                MusicPlatform.TIDAL -> {
                    val encoded = url.encodeURLParameter()
                    val resp = client.get("https://oembed.tidal.com/v1?url=$encoded")
                    if (resp.status.isSuccess()) {
                        val oembed = json.decodeFromString<CommonOEmbedDto>(resp.bodyAsText())
                        val title = oembed.title
                        val author = oembed.authorName
                        if (!title.isNullOrEmpty()) {
                            if (!author.isNullOrEmpty() && !title.contains(author, ignoreCase = true)) "$author $title" else title
                        } else null
                    } else null
                }
                MusicPlatform.SOUNDCLOUD -> {
                    val encoded = url.encodeURLParameter()
                    val resp = client.get("https://soundcloud.com/oembed?url=$encoded&format=json")
                    if (resp.status.isSuccess()) {
                        val oembed = json.decodeFromString<CommonOEmbedDto>(resp.bodyAsText())
                        val rawTitle = oembed.title
                        val author = oembed.authorName
                        if (!rawTitle.isNullOrEmpty()) {
                            val title = UrlUtils.cleanVideoTitle(rawTitle)
                            if (!author.isNullOrEmpty() && !title.contains(author, ignoreCase = true)) "$author $title" else title
                        } else null
                    } else null
                }
                MusicPlatform.AMAZON_MUSIC -> {
                    val clean = url.substringBefore("?").trimEnd('/')
                    val slug = clean.substringAfterLast("/")
                        .replace("-", " ")
                        .replace("_", " ")
                        .trim()
                    if (slug.isNotBlank() && slug.length > 2 && !slug.all { it.isDigit() }) slug else null
                }
                MusicPlatform.BANDCAMP -> {
                    val clean = url.substringBefore("?").trimEnd('/')
                    if (clean.contains("/track/") || clean.contains("/album/")) {
                        val slug = clean.substringAfterLast("/")
                            .replace("-", " ")
                            .replace("_", " ")
                            .trim()
                        if (slug.isNotBlank() && slug.length > 2) slug else null
                    } else null
                }
                MusicPlatform.QOBUZ -> qobuzResolver.extractMetadata(url)
                MusicPlatform.BEATPORT -> beatportResolver.extractMetadata(url)
                MusicPlatform.AUDIOMACK -> audiomackResolver.extractMetadata(url)
                MusicPlatform.IDAGIO -> idagioResolver.extractMetadata(url)
                MusicPlatform.ANGHAMI -> anghamiResolver.extractMetadata(url)
                else -> {
                    if (url.contains("shazam.com")) {
                        val trackMatch = Regex("shazam\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?track/([0-9]+)", RegexOption.IGNORE_CASE).find(url)
                        val trackId = trackMatch?.groupValues?.get(1) ?: url.substringAfter("/track/").substringBefore("/").substringBefore("?").trim().takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
                        if (!trackId.isNullOrEmpty()) {
                            val resp = client.get("https://amp.shazam.com/discovery/v5/en-US/US/web/-/track/$trackId") {
                                header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
                            }
                            if (resp.status.isSuccess()) {
                                val body = resp.bodyAsText()
                                val root = json.parseToJsonElement(body).jsonObject
                                val title = root["title"]?.jsonPrimitive?.content?.trim()
                                val artist = root["subtitle"]?.jsonPrimitive?.content?.trim()
                                if (!title.isNullOrEmpty() && !artist.isNullOrEmpty()) {
                                    return "$artist $title"
                                }
                            }
                        }
                        null
                    } else {
                        null
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun extractArtistInfo(url: String): String? {
        return try {
            if (url.contains("deezer.com") && url.contains("/artist/")) {
                val artistId = url.substringAfter("/artist/").substringBefore("?").substringBefore("/").trim()
                if (artistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/artist/$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val name = root["name"]?.jsonPrimitive?.content
                        if (!name.isNullOrEmpty()) return name
                    }
                }
            }
            val platform = UrlUtils.detectPlatform(url)
            val lower = url.lowercase()
            if (platform == MusicPlatform.SPOTIFY && lower.contains("/artist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            } else if (platform == MusicPlatform.APPLE_MUSIC && lower.contains("/artist/")) {
                val artistId = url.substringAfterLast("/").substringBefore("?").substringBefore("&").trim()
                if (artistId.isNotEmpty() && artistId.all { it.isDigit() }) {
                    val resp = client.get("https://itunes.apple.com/lookup?id=$artistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val results = root["results"]?.jsonArray
                        if (results != null && results.isNotEmpty()) {
                            val artistObj = results[0].jsonObject
                            val artistName = artistObj["artistName"]?.jsonPrimitive?.content
                            if (!artistName.isNullOrEmpty()) return artistName
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun extractPlaylistInfo(url: String): String? {
        return try {
            val platform = UrlUtils.detectPlatform(url)
            val lower = url.lowercase()
            if (platform == MusicPlatform.DEEZER && lower.contains("/playlist/")) {
                val playlistId = url.substringAfter("/playlist/").substringBefore("?").substringBefore("/").trim()
                if (playlistId.isNotEmpty()) {
                    val resp = client.get("https://api.deezer.com/playlist/$playlistId")
                    if (resp.status.isSuccess()) {
                        val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                        val title = root["title"]?.jsonPrimitive?.content
                        if (!title.isNullOrEmpty()) return title
                    }
                }
            } else if (platform == MusicPlatform.SPOTIFY && lower.contains("/playlist/")) {
                val encoded = url.encodeURLParameter()
                val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content
                    if (!title.isNullOrEmpty()) return title
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun resolveShazamToAppleMusic(url: String): String? {
        return try {
            val trackMatch = Regex("shazam\\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?track/([0-9]+)", RegexOption.IGNORE_CASE).find(url)
            val trackId = trackMatch?.groupValues?.get(1) ?: url.substringAfter("/track/").substringBefore("/").substringBefore("?").trim().takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
            if (trackId.isNullOrEmpty()) return null

            val resp = client.get("https://amp.shazam.com/discovery/v5/en-US/US/web/-/track/$trackId") {
                header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1")
            }
            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val root = json.parseToJsonElement(body).jsonObject
                val hub = root["hub"]?.jsonObject
                val actions = hub?.get("actions")?.jsonArray
                if (actions != null) {
                    for (actEl in actions) {
                        val act = actEl.jsonObject
                        if (act["name"]?.jsonPrimitive?.content == "apple" && act["type"]?.jsonPrimitive?.content == "applemusicplay") {
                            val appleId = act["id"]?.jsonPrimitive?.content
                            if (!appleId.isNullOrBlank()) {
                                return "https://music.apple.com/song/$appleId"
                            }
                        }
                    }
                }
                val fallbackId = root["trackadamid"]?.jsonPrimitive?.content
                if (!fallbackId.isNullOrBlank()) {
                    return "https://music.apple.com/song/$fallbackId"
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }
}
