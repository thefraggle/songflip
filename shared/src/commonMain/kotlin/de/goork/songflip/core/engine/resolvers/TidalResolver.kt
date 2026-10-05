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

@Serializable
internal data class TidalSearchResponse(
    val tracks: TidalItemsContainer? = null,
    val albums: TidalItemsContainer? = null,
    val artists: TidalItemsContainer? = null
)

@Serializable
internal data class TidalItemsContainer(
    val items: List<TidalItem> = emptyList()
)

@Serializable
internal data class TidalItem(
    val id: String? = null,
    val name: String? = null,
    val popularity: Int? = null
)

class TidalResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val tidalAppToken: String = "CzET4vdadNUFQ5JU"
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.TIDAL

    override suspend fun resolveTrack(query: String): String? {
        return searchTidal(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchTidal(query, isAlbum = true)
    }

    private suspend fun searchTidal(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://listen.tidal.com/v1/search?query=$encoded&limit=5&countryCode=DE") {
                header("x-tidal-token", tidalAppToken)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<TidalSearchResponse>(resp.bodyAsText())
                val container = if (isAlbum) parsed.albums else parsed.tracks
                val id = container?.items?.firstOrNull()?.id
                if (!id.isNullOrEmpty()) {
                    val path = if (isAlbum) "album" else "track"
                    return "https://tidal.com/browse/$path/$id"
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    override suspend fun resolveArtist(artistName: String): String? {
        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://listen.tidal.com/v1/search?query=$encoded&limit=10&countryCode=DE") {
                header("x-tidal-token", tidalAppToken)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            }
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<TidalSearchResponse>(resp.bodyAsText())
                val items = parsed.artists?.items
                if (!items.isNullOrEmpty()) {
                    var bestId: String? = null
                    var maxPop = -1
                    for (item in items) {
                        val name = item.name ?: ""
                        if (isArtistNameMatch(name, artistName)) {
                            val pop = item.popularity ?: 0
                            if (pop > maxPop) {
                                maxPop = pop
                                bestId = item.id
                            }
                        }
                    }
                    if (!bestId.isNullOrEmpty()) {
                        return "https://tidal.com/artist/$bestId"
                    }
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
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
