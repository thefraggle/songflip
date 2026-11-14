package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.engine.EngineConfig
import de.goork.songflip.core.engine.resilience.DynamicTokenRegistry
import de.goork.songflip.core.engine.resilience.PlatformCircuitBreaker
import de.goork.songflip.core.engine.resilience.ResolutionOutcome
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
    private val tidalAppToken: String = DynamicTokenRegistry.DEFAULT_TIDAL_TOKEN,
    private val countryCodeProvider: () -> String = { "DE" },
    private val circuitBreaker: PlatformCircuitBreaker = PlatformCircuitBreaker.shared,
    private val tokenProvider: () -> String = { DynamicTokenRegistry.getTidalToken() }
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
        return resolveTrackDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        return resolveAlbumDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveTrackDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchTidalDetailed(query, isAlbum = false, audiophileMode = audiophileMode)
    }

    override suspend fun resolveAlbumDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchTidalDetailed(query, isAlbum = true, audiophileMode = audiophileMode)
    }

    private suspend fun searchTidalDetailed(
        query: String,
        isAlbum: Boolean = false,
        audiophileMode: Boolean = false
    ): ResolutionOutcome<String> {
        val primary = executeTidalSearch(query, isAlbum, audiophileMode)
        if (primary is ResolutionOutcome.Success) return primary
        if (primary is ResolutionOutcome.RateLimited) return primary // Immediate cascade abort

        val stripped = ResolverUtils.stripEditionSuffix(query)
        if (stripped != query && stripped.isNotBlank()) {
            val second = executeTidalSearch(stripped, isAlbum, audiophileMode)
            if (second is ResolutionOutcome.Success) return second
            if (second is ResolutionOutcome.RateLimited) return second
        }
        val noParens = query.replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("\\s*\\[[^\\]]*\\]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (noParens != query && noParens != stripped && noParens.isNotBlank()) {
            val third = executeTidalSearch(noParens, isAlbum, audiophileMode)
            if (third is ResolutionOutcome.Success) return third
            if (third is ResolutionOutcome.RateLimited) return third
        }
        val simplifiedQuery = query
            .replace(Regex("(?i)\\s+(?:feat\\.?|ft\\.?)\\s+[^\\-–(]+"), "")
            .replace(Regex(",\\s*[^,\\-–]+(?=\\s*[\\-–])"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (simplifiedQuery.isNotBlank() && simplifiedQuery != query && simplifiedQuery != stripped && simplifiedQuery != noParens) {
            val fourth = executeTidalSearch(simplifiedQuery, isAlbum, audiophileMode)
            if (fourth is ResolutionOutcome.Success) return fourth
            if (fourth is ResolutionOutcome.RateLimited) return fourth
        }
        return ResolutionOutcome.NotFound
    }

    private suspend fun executeTidalSearch(
        query: String,
        isAlbum: Boolean = false,
        audiophileMode: Boolean = false
    ): ResolutionOutcome<String> {
        val encoded = query.encodeURLParameter()
        val limit = if (audiophileMode) 20 else 15
        val countryCode = getCountryCode()
        val effectiveToken = if (tidalAppToken != DynamicTokenRegistry.DEFAULT_TIDAL_TOKEN) {
            tidalAppToken
        } else {
            tokenProvider()
        }

        var anyRateLimited = false
        var lastRateLimitRetryAfter: Long? = null

        for (baseUrl in searchBaseUrls) {
            val host = if (baseUrl.contains("listen.tidal.com")) "listen.tidal.com" else "api.tidal.com"
            if (!circuitBreaker.canExecute(host)) {
                anyRateLimited = true
                lastRateLimitRetryAfter = circuitBreaker.getCooldownRemainingSeconds(host)
                continue
            }

            try {
                val resp = client.get("$baseUrl?query=$encoded&limit=$limit&countryCode=$countryCode") {
                    header("x-tidal-token", effectiveToken)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
                }
                if (resp.status.value == 429) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                    circuitBreaker.recordRateLimit(host, retryAfter)
                    anyRateLimited = true
                    lastRateLimitRetryAfter = retryAfter
                    continue
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    if (body.isBlank()) continue
                    circuitBreaker.recordSuccess(host)
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
                        ResolverUtils.findBestCandidate(
                            candidates = items,
                            query = query,
                            isAlbum = isAlbum,
                            getArtist = { it.artist?.name ?: it.artists.firstOrNull()?.name },
                            getTitle = { it.title ?: it.name },
                            getVersion = { it.version },
                            getAlbumTitle = { it.album?.title }
                        )
                    }

                    if (validItem != null) {
                        val itemId = validItem.id
                        if (!itemId.isNullOrEmpty()) {
                            val directUrl = if (isAlbum) "https://tidal.com/browse/album/$itemId" else "https://tidal.com/browse/track/$itemId"
                            return ResolutionOutcome.Success(directUrl)
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }

        if (anyRateLimited) {
            return ResolutionOutcome.RateLimited(host = "tidal", retryAfterSeconds = lastRateLimitRetryAfter ?: 300L)
        }

        return ResolutionOutcome.NotFound
    }

    override suspend fun resolveArtist(artistName: String): String? {
        val encoded = artistName.encodeURLParameter()
        val countryCode = getCountryCode()
        val effectiveToken = if (tidalAppToken != DynamicTokenRegistry.DEFAULT_TIDAL_TOKEN) {
            tidalAppToken
        } else {
            tokenProvider()
        }

        for (baseUrl in searchBaseUrls) {
            val host = if (baseUrl.contains("listen.tidal.com")) "listen.tidal.com" else "api.tidal.com"
            if (!circuitBreaker.canExecute(host)) continue

            try {
                val resp = client.get("$baseUrl?query=$encoded&limit=10&countryCode=$countryCode") {
                    header("x-tidal-token", effectiveToken)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
                }
                if (resp.status.value == 429) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                    circuitBreaker.recordRateLimit(host, retryAfter)
                    continue
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    if (body.isBlank()) continue
                    circuitBreaker.recordSuccess(host)
                    val parsed = json.decodeFromString<TidalSearchResponse>(body)
                    val items = parsed.artists?.items
                    if (!items.isNullOrEmpty()) {
                        var bestId: String? = null
                        var maxPop = -1
                        for (item in items) {
                            val name = item.name ?: ""
                            if (ResolverUtils.isArtistNameMatch(name, artistName)) {
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
}
