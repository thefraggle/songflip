package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.engine.EngineConfig
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class DeezerError(
    val type: String? = null,
    val message: String? = null,
    val code: Int? = null
)

@Serializable
internal data class DeezerSearchResponse(
    val data: List<DeezerItem> = emptyList(),
    val error: DeezerError? = null
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
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val circuitBreaker: PlatformCircuitBreaker = PlatformCircuitBreaker.shared
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.DEEZER

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        return resolveTrackDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        return resolveAlbumDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveTrackDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchDeezerDetailed(query, isAlbum = false, audiophileMode = audiophileMode)
    }

    override suspend fun resolveAlbumDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchDeezerDetailed(query, isAlbum = true, audiophileMode = audiophileMode)
    }

    private suspend fun searchDeezerDetailed(
        query: String,
        isAlbum: Boolean = false,
        audiophileMode: Boolean = false
    ): ResolutionOutcome<String> {
        val primary = executeDeezerSearch(query, isAlbum, audiophileMode)
        if (primary is ResolutionOutcome.Success) return primary
        if (primary is ResolutionOutcome.RateLimited) return primary // Immediate cascade abort

        val stripped = ResolverUtils.stripEditionSuffix(query)
        if (stripped != query && stripped.isNotBlank()) {
            val second = executeDeezerSearch(stripped, isAlbum, audiophileMode)
            if (second is ResolutionOutcome.Success) return second
            if (second is ResolutionOutcome.RateLimited) return second
        }
        val noParens = query.replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("\\s*\\[[^\\]]*\\]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (noParens != query && noParens != stripped && noParens.isNotBlank()) {
            val third = executeDeezerSearch(noParens, isAlbum, audiophileMode)
            if (third is ResolutionOutcome.Success) return third
            if (third is ResolutionOutcome.RateLimited) return third
        }
        if (query.contains(",") || query.contains(" feat", ignoreCase = true) || query.contains(" ft", ignoreCase = true)) {
            val artistPart = query.substringBefore(",").substringBefore(" feat").substringBefore(" ft").trim()
            val remainingPart = query.substringAfterLast(",").substringAfter("feat.").substringAfter("ft.").trim()
            val simplifiedQuery = "$artistPart $remainingPart".trim()
            if (simplifiedQuery.isNotBlank() && simplifiedQuery != query) {
                val fourth = executeDeezerSearch(simplifiedQuery, isAlbum, audiophileMode)
                if (fourth is ResolutionOutcome.Success) return fourth
                if (fourth is ResolutionOutcome.RateLimited) return fourth
            }
        }
        return ResolutionOutcome.NotFound
    }

    private suspend fun executeDeezerSearch(
        query: String,
        isAlbum: Boolean = false,
        audiophileMode: Boolean = false
    ): ResolutionOutcome<String> {
        val host = "api.deezer.com"
        if (!circuitBreaker.canExecute(host)) {
            val remaining = circuitBreaker.getCooldownRemainingSeconds(host)
            return ResolutionOutcome.RateLimited(host = host, retryAfterSeconds = remaining)
        }

        return try {
            val encoded = query.encodeURLParameter()
            val endpoint = if (isAlbum) "search/album" else "search"
            val limit = if (audiophileMode) 20 else 15
            val resp = client.get("https://$host/$endpoint?q=$encoded&limit=$limit") {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
            }
            if (resp.status.value == 429) {
                val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                circuitBreaker.recordRateLimit(host, retryAfter)
                return ResolutionOutcome.RateLimited(host = host, retryAfterSeconds = retryAfter)
            }
            if (resp.status.isSuccess()) {
                val bodyText = resp.bodyAsText()
                val parsed = json.decodeFromString<DeezerSearchResponse>(bodyText)
                if (parsed.error != null && (parsed.error.code == 4 || parsed.error.code == 429)) {
                    circuitBreaker.recordRateLimit(host, 300L)
                    return ResolutionOutcome.RateLimited(host = host, retryAfterSeconds = 300L)
                }
                circuitBreaker.recordSuccess(host)
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
                    return ResolutionOutcome.Success(link)
                }
            }
            ResolutionOutcome.NotFound
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            ResolutionOutcome.Error("Deezer search failed: ${t.message}", t)
        }
    }

    override suspend fun resolveArtist(artistName: String): String? {
        val host = "api.deezer.com"
        if (!circuitBreaker.canExecute(host)) return null

        return try {
            val encoded = artistName.encodeURLParameter()
            val resp = client.get("https://$host/search/artist?q=$encoded&limit=10")
            if (resp.status.value == 429) {
                val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                circuitBreaker.recordRateLimit(host, retryAfter)
                return null
            }
            if (resp.status.isSuccess()) {
                val bodyText = resp.bodyAsText()
                val parsed = json.decodeFromString<DeezerSearchResponse>(bodyText)
                if (parsed.error != null && (parsed.error.code == 4 || parsed.error.code == 429)) {
                    circuitBreaker.recordRateLimit(host, 300L)
                    return null
                }
                circuitBreaker.recordSuccess(host)
                var bestLink: String? = null
                var maxFans = -1
                for (item in parsed.data) {
                    val name = item.name ?: ""
                    if (ResolverUtils.isArtistNameMatch(name, artistName)) {
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
}
