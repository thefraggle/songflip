package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class QobuzSearchResponse(
    val tracks: QobuzTrackContainer? = null,
    val albums: QobuzAlbumContainer? = null,
    val artists: QobuzArtistContainer? = null
)

@Serializable
internal data class QobuzTrackContainer(
    val items: List<QobuzTrackItem> = emptyList()
)

@Serializable
internal data class QobuzAlbumContainer(
    val items: List<QobuzAlbumItem> = emptyList()
)

@Serializable
internal data class QobuzArtistContainer(
    val items: List<QobuzArtistItem> = emptyList()
)

@Serializable
internal data class QobuzTrackItem(
    val id: Long? = null,
    val title: String? = null,
    val performer: QobuzPerformer? = null,
    val album: QobuzAlbumRef? = null
)

@Serializable
internal data class QobuzAlbumItem(
    val id: String? = null,
    val title: String? = null,
    val artist: QobuzPerformer? = null
)

@Serializable
internal data class QobuzArtistItem(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class QobuzPerformer(
    val name: String? = null
)

@Serializable
internal data class QobuzAlbumRef(
    val id: String? = null,
    val title: String? = null
)

class QobuzResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val qobuzAppId: String = "712109809"
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.QOBUZ

    override suspend fun resolveTrack(query: String): String? {
        return searchQobuz(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchQobuz(query, isAlbum = true)
    }

    override suspend fun resolveArtist(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val url = "https://www.qobuz.com/api.json/0.2/catalog/search?query=$encoded&type=artists&limit=3&app_id=$qobuzAppId"
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/1.9.1")
            }
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<QobuzSearchResponse>(resp.bodyAsText())
                val artistId = parsed.artists?.items?.firstOrNull()?.id
                if (artistId != null) {
                    return "https://open.qobuz.com/artist/$artistId"
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private suspend fun searchQobuz(query: String, isAlbum: Boolean): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val typeParam = if (isAlbum) "albums" else "tracks"
            val url = "https://www.qobuz.com/api.json/0.2/catalog/search?query=$encoded&type=$typeParam&limit=5&app_id=$qobuzAppId"
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/1.9.1")
            }
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<QobuzSearchResponse>(resp.bodyAsText())
                if (isAlbum) {
                    val album = parsed.albums?.items?.firstOrNull()
                    if (album?.id != null) {
                        return "https://open.qobuz.com/album/${album.id}"
                    }
                } else {
                    val track = parsed.tracks?.items?.firstOrNull()
                    if (track?.id != null) {
                        return "https://open.qobuz.com/track/${track.id}"
                    }
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Extracts title and artist from an incoming Qobuz URL for conversion to other platforms.
     */
    suspend fun extractMetadata(url: String): String? {
        return try {
            val trackMatch = Regex("(?:track)/([0-9]+)").find(url)
            if (trackMatch != null) {
                val trackId = trackMatch.groupValues[1]
                val apiUrl = "https://www.qobuz.com/api.json/0.2/track/get?track_id=$trackId&app_id=$qobuzAppId"
                val resp = client.get(apiUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/1.9.1")
                }
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content?.trim()
                    val performer = root["performer"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.trim()
                    if (!title.isNullOrBlank()) {
                        return if (!performer.isNullOrBlank() && !title.contains(performer, ignoreCase = true)) {
                            "$performer $title"
                        } else {
                            title
                        }
                    }
                }
            }

            val albumMatch = Regex("(?:album)/(?:[a-zA-Z0-9_-]+/)?([0-9a-zA-Z]+)").find(url)
            if (albumMatch != null) {
                val albumId = albumMatch.groupValues[1]
                val apiUrl = "https://www.qobuz.com/api.json/0.2/album/get?album_id=$albumId&app_id=$qobuzAppId"
                val resp = client.get(apiUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/1.9.1")
                }
                if (resp.status.isSuccess()) {
                    val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                    val title = root["title"]?.jsonPrimitive?.content?.trim()
                    val artist = root["artist"]?.jsonObject?.get("name")?.jsonPrimitive?.content?.trim()
                    if (!title.isNullOrBlank()) {
                        return if (!artist.isNullOrBlank() && !title.contains(artist, ignoreCase = true)) {
                            "$artist $title"
                        } else {
                            title
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
}
