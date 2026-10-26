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
    val title: String? = null,
    val name: String? = null,
    val version: String? = null,
    val audioQuality: String? = null,
    val audioModes: List<String> = emptyList(),
    val mediaMetadata: TidalMediaMetadata? = null,
    val album: TidalAlbumRef? = null,
    val artist: TidalArtist? = null,
    val artists: List<TidalArtist> = emptyList(),
    val popularity: Int? = null
)

@Serializable
internal data class TidalMediaMetadata(
    val tags: List<String> = emptyList()
)

@Serializable
internal data class TidalAlbumRef(
    val id: String? = null,
    val title: String? = null
)

@Serializable
internal data class TidalArtist(
    val name: String? = null
)

class TidalResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val tidalAppToken: String = "CzET4vdadNUFQ5JU",
    private val countryCodeProvider: () -> String = { "DE" }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.TIDAL

    private val searchBaseUrls = listOf(
        "https://api.tidal.com/v1/search",
        "https://listen.tidal.com/v1/search"
    )

    private fun getCountryCode(): String {
        val raw = countryCodeProvider().trim().uppercase()
        return if (raw.length == 2 && raw.all { it.isLetter() }) raw else "DE"
    }

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        return searchTidal(query, isAlbum = false, audiophileMode = audiophileMode)
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        return searchTidal(query, isAlbum = true, audiophileMode = audiophileMode)
    }

    private suspend fun searchTidal(query: String, isAlbum: Boolean = false, audiophileMode: Boolean = false): String? {
        val primary = executeTidalSearch(query, isAlbum, audiophileMode)
        if (primary != null) return primary
        val stripped = ResolverUtils.stripEditionSuffix(query)
        if (stripped != query && stripped.isNotBlank()) {
            return executeTidalSearch(stripped, isAlbum, audiophileMode)
        }
        return null
    }

    private suspend fun executeTidalSearch(query: String, isAlbum: Boolean = false, audiophileMode: Boolean = false): String? {
        val encoded = query.encodeURLParameter()
        val limit = if (audiophileMode) 10 else 5
        val countryCode = getCountryCode()

        for (baseUrl in searchBaseUrls) {
            try {
                val resp = client.get("$baseUrl?query=$encoded&limit=$limit&countryCode=$countryCode") {
                    header("x-tidal-token", tidalAppToken)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    if (body.isBlank()) continue
                    val parsed = json.decodeFromString<TidalSearchResponse>(body)
                    val container = if (isAlbum) parsed.albums else parsed.tracks
                    val items = container?.items ?: emptyList()
                    if (items.isEmpty()) continue

                    val validItem = if (audiophileMode) {
                        items
                            .map { item ->
                                val candidateTitle = item.title ?: item.name
                                val candidateArtist = item.artist?.name ?: item.artists.firstOrNull()?.name
                                val tags = item.mediaMetadata?.tags ?: emptyList()
                                val qualityUpper = (item.audioQuality ?: "").uppercase()
                                val isHiRes = tags.any { it.contains("HIRES", ignoreCase = true) || it.equals("MQA", ignoreCase = true) } ||
                                    qualityUpper.contains("HI_RES")
                                val isLossless = isHiRes || tags.any { it.contains("LOSSLESS", ignoreCase = true) } ||
                                    qualityUpper == "LOSSLESS"
                                val isSpatial = item.audioModes.any { it.contains("ATMOS", ignoreCase = true) || it.contains("360", ignoreCase = true) } ||
                                    tags.any { it.contains("ATMOS", ignoreCase = true) || it.contains("360", ignoreCase = true) }

                                val baseScore = ResolverUtils.scoreAudiophileCandidate(
                                    query = query,
                                    candidateArtist = candidateArtist,
                                    candidateTitle = candidateTitle,
                                    candidateVersion = item.version,
                                    albumTitle = item.album?.title,
                                    isHiRes = isHiRes,
                                    isLossless = isLossless,
                                    isSpatialOrAtmos = isSpatial
                                )
                                val popBonus = ((item.popularity ?: 0).coerceIn(0, 100)) / 10
                                item to (if (baseScore == Int.MIN_VALUE) Int.MIN_VALUE else baseScore + popBonus)
                            }
                            .filter { it.second > Int.MIN_VALUE }
                            .maxByOrNull { it.second }
                            ?.first
                    } else {
                        items.firstOrNull { item ->
                            val candidateTitle = item.title ?: item.name
                            val candidateArtist = item.artist?.name ?: item.artists.firstOrNull()?.name
                            ResolverUtils.isMatch(query, candidateArtist, candidateTitle)
                        }
                    }

                    val id = validItem?.id
                    if (!id.isNullOrEmpty()) {
                        val path = if (isAlbum) "album" else "track"
                        return "https://tidal.com/browse/$path/$id"
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }
        return null
    }

    override suspend fun resolveArtist(artistName: String): String? {
        val encoded = artistName.encodeURLParameter()
        val countryCode = getCountryCode()
        for (baseUrl in searchBaseUrls) {
            try {
                val resp = client.get("$baseUrl?query=$encoded&limit=10&countryCode=$countryCode") {
                    header("x-tidal-token", tidalAppToken)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    if (body.isBlank()) continue
                    val parsed = json.decodeFromString<TidalSearchResponse>(body)
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
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }
        return null
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
