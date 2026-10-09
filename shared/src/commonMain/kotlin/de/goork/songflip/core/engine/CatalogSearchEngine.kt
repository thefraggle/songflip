package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SearchTrackResult(
    val id: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val coverUrl: String? = null,
    val previewUrl: String? = null,
    val durationMs: Long? = null,
    val sourcePlatform: String = "appleMusic",
    val sourceUrl: String? = null
)

@Serializable
internal data class ITunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ITunesSearchItem> = emptyList()
)

@Serializable
internal data class ITunesSearchItem(
    val trackId: Long? = null,
    val artistName: String? = null,
    val trackName: String? = null,
    val collectionName: String? = null,
    val trackViewUrl: String? = null,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackTimeMillis: Long? = null
)

@Serializable
internal data class DeezerSearchResponse(
    val data: List<DeezerSearchItem> = emptyList()
)

@Serializable
internal data class DeezerSearchItem(
    val id: Long? = null,
    val title: String? = null,
    val link: String? = null,
    val preview: String? = null,
    val duration: Long? = null,
    val artist: DeezerSearchArtist? = null,
    val album: DeezerSearchAlbum? = null
)

@Serializable
internal data class DeezerSearchArtist(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class DeezerSearchAlbum(
    val id: Long? = null,
    val title: String? = null,
    val cover_medium: String? = null
)

class CatalogSearchEngine(
    private val client: HttpClient = createPlatformHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    companion object {
        val shared: CatalogSearchEngine by lazy { CatalogSearchEngine() }
    }

    suspend fun search(query: String, limit: Int = 15): Result<List<SearchTrackResult>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Result.success(emptyList())

        // 1. Primary: iTunes Search API (fast, metadata-rich, reliable)
        try {
            val encoded = trimmed.encodeURLParameter()
            val resp = client.get("https://itunes.apple.com/search?term=$encoded&entity=song&limit=$limit")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<ITunesSearchResponse>(resp.bodyAsText())
                val tracks = parsed.results.mapNotNull { item ->
                    val title = item.trackName?.trim()
                    val artist = item.artistName?.trim()
                    if (title.isNullOrEmpty() || artist.isNullOrEmpty()) null
                    else {
                        val highResCover = item.artworkUrl100?.replace("100x100bb", "600x600bb")
                        SearchTrackResult(
                            id = item.trackId?.toString() ?: "${artist}_$title",
                            title = title,
                            artist = artist,
                            album = item.collectionName,
                            coverUrl = highResCover ?: item.artworkUrl100,
                            previewUrl = item.previewUrl,
                            durationMs = item.trackTimeMillis,
                            sourcePlatform = "appleMusic",
                            sourceUrl = item.trackViewUrl
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    return Result.success(tracks)
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            // Fall through to Deezer
        }

        // 2. Fallback: Deezer Search API
        return try {
            val encoded = trimmed.encodeURLParameter()
            val resp = client.get("https://api.deezer.com/search?q=$encoded&limit=$limit")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                val tracks = parsed.data.mapNotNull { item ->
                    val title = item.title?.trim()
                    val artist = item.artist?.name?.trim()
                    if (title.isNullOrEmpty() || artist.isNullOrEmpty()) null
                    else {
                        SearchTrackResult(
                            id = item.id?.toString() ?: "${artist}_$title",
                            title = title,
                            artist = artist,
                            album = item.album?.title,
                            coverUrl = item.album?.cover_medium,
                            previewUrl = item.preview,
                            durationMs = item.duration?.let { it * 1000L },
                            sourcePlatform = "deezer",
                            sourceUrl = item.link
                        )
                    }
                }
                Result.success(tracks)
            } else {
                Result.success(emptyList())
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
