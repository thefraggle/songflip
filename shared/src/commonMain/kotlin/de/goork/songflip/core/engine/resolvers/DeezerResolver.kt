package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DeezerSearchResponse(
    val data: List<DeezerItem> = emptyList()
)

@Serializable
internal data class DeezerItem(
    val link: String? = null,
    val name: String? = null,
    val title: String? = null,
    @SerialName("title_short")
    val titleShort: String? = null,
    val artist: DeezerArtist? = null,
    @SerialName("nb_fan")
    val nbFan: Int? = null
)

@Serializable
internal data class DeezerArtist(
    val name: String? = null
)

class DeezerResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.DEEZER

    override suspend fun resolveTrack(query: String): String? {
        return searchDeezer(query, isAlbum = false)
    }

    override suspend fun resolveAlbum(query: String): String? {
        return searchDeezer(query, isAlbum = true)
    }

    private suspend fun searchDeezer(query: String, isAlbum: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val endpoint = if (isAlbum) "search/album" else "search"
            val resp = client.get("https://api.deezer.com/$endpoint?q=$encoded&limit=5")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                val match = parsed.data.firstOrNull { item ->
                    val candidateTitle = item.title ?: item.titleShort ?: item.name
                    val candidateArtist = item.artist?.name
                    ResolverUtils.isMatch(query, candidateArtist, candidateTitle)
                }
                val link = match?.link
                if (!link.isNullOrEmpty()) {
                    return link
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
            val resp = client.get("https://api.deezer.com/search/artist?q=$encoded&limit=10")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                var bestLink: String? = null
                var maxFans = -1
                for (item in parsed.data) {
                    val name = item.name ?: ""
                    if (isArtistNameMatch(name, artistName)) {
                        val fans = item.nbFan ?: 0
                        val link = item.link
                        if (fans > maxFans && !link.isNullOrEmpty()) {
                            maxFans = fans
                            bestLink = link
                        }
                    }
                }
                if (bestLink != null) return bestLink
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
