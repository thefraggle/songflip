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
    @SerialName("title_version")
    val titleVersion: String? = null,
    val artist: DeezerArtist? = null,
    val album: DeezerAlbumRef? = null,
    @SerialName("nb_fan")
    val nbFan: Int? = null
)

@Serializable
internal data class DeezerAlbumRef(
    val title: String? = null
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

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        return searchDeezer(query, isAlbum = false, audiophileMode = audiophileMode)
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        return searchDeezer(query, isAlbum = true, audiophileMode = audiophileMode)
    }

    private suspend fun searchDeezer(query: String, isAlbum: Boolean = false, audiophileMode: Boolean = false): String? {
        val primary = executeDeezerSearch(query, isAlbum, audiophileMode)
        if (primary != null) return primary
        val stripped = ResolverUtils.stripEditionSuffix(query)
        if (stripped != query && stripped.isNotBlank()) {
            return executeDeezerSearch(stripped, isAlbum, audiophileMode)
        }
        return null
    }

    private suspend fun executeDeezerSearch(query: String, isAlbum: Boolean = false, audiophileMode: Boolean = false): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val endpoint = if (isAlbum) "search/album" else "search"
            val limit = if (audiophileMode) 10 else 5
            val resp = client.get("https://api.deezer.com/$endpoint?q=$encoded&limit=$limit")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<DeezerSearchResponse>(resp.bodyAsText())
                val match = if (audiophileMode) {
                    parsed.data
                        .map { item ->
                            val candidateTitle = item.title ?: item.titleShort ?: item.name
                            val candidateArtist = item.artist?.name
                            val score = ResolverUtils.scoreAudiophileCandidate(
                                query = query,
                                candidateArtist = candidateArtist,
                                candidateTitle = candidateTitle,
                                candidateVersion = item.titleVersion,
                                albumTitle = item.album?.title,
                                isLossless = true
                            )
                            item to score
                        }
                        .filter { it.second > Int.MIN_VALUE }
                        .maxByOrNull { it.second }
                        ?.first
                } else {
                    parsed.data.firstOrNull { item ->
                        val candidateTitle = item.title ?: item.titleShort ?: item.name
                        val candidateArtist = item.artist?.name
                        ResolverUtils.isMatch(query, candidateArtist, candidateTitle)
                    }
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
