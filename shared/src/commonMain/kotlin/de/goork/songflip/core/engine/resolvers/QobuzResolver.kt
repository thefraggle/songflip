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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class QobuzSearchResponse(
    val tracks: QobuzItemsContainer<QobuzTrackItem>? = null,
    val albums: QobuzItemsContainer<QobuzAlbumItem>? = null,
    val artists: QobuzItemsContainer<QobuzArtistItem>? = null
)

@Serializable
internal data class QobuzItemsContainer<T>(
    val items: List<T> = emptyList(),
    val total: Int? = null
)

@Serializable
internal data class QobuzArtistItem(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class QobuzAlbumRef(
    val id: String? = null,
    val title: String? = null,
    val hires: Boolean? = null,
    @kotlinx.serialization.SerialName("tracks_count")
    val tracksCount: Int? = null
)

@Serializable
internal data class QobuzTrackItem(
    val id: Long? = null,
    val title: String? = null,
    val version: String? = null,
    val performer: QobuzPerformerRef? = null,
    val album: QobuzAlbumRef? = null,
    val hires: Boolean? = null,
    @kotlinx.serialization.SerialName("hires_streamable")
    val hiresStreamable: Boolean? = null,
    @kotlinx.serialization.SerialName("maximum_bit_depth")
    val maximumBitDepth: Int? = null,
    @kotlinx.serialization.SerialName("maximum_sampling_rate")
    val maximumSamplingRate: Double? = null
)

@Serializable
internal data class QobuzPerformerRef(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
internal data class QobuzAlbumItem(
    val id: String? = null,
    val title: String? = null,
    val version: String? = null,
    val artist: QobuzArtistRef? = null,
    val hires: Boolean? = null,
    @kotlinx.serialization.SerialName("hires_streamable")
    val hiresStreamable: Boolean? = null,
    @kotlinx.serialization.SerialName("maximum_bit_depth")
    val maximumBitDepth: Int? = null,
    @kotlinx.serialization.SerialName("maximum_sampling_rate")
    val maximumSamplingRate: Double? = null,
    @kotlinx.serialization.SerialName("tracks_count")
    val tracksCount: Int? = null
)

@Serializable
internal data class QobuzArtistRef(
    val id: Long? = null,
    val name: String? = null
)

class QobuzResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    private val qobuzAppId: String = DynamicTokenRegistry.DEFAULT_QOBUZ_APP_ID,
    private val circuitBreaker: PlatformCircuitBreaker = PlatformCircuitBreaker.shared,
    private val tokenProvider: () -> String = { DynamicTokenRegistry.getQobuzAppId() }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.QOBUZ

    private fun getEffectiveAppId(): String {
        val candidate = if (qobuzAppId != DynamicTokenRegistry.DEFAULT_QOBUZ_APP_ID && qobuzAppId.isNotBlank()) {
            qobuzAppId
        } else {
            tokenProvider()
        }
        return candidate.ifBlank { DynamicTokenRegistry.DEFAULT_QOBUZ_APP_ID }
    }

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        return resolveTrackDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        return resolveAlbumDetailed(query, audiophileMode).getOrNull()
    }

    override suspend fun resolveTrackDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchQobuzDetailed(query, isAlbum = false, audiophileMode = audiophileMode)
    }

    override suspend fun resolveAlbumDetailed(query: String, audiophileMode: Boolean): ResolutionOutcome<String> {
        return searchQobuzDetailed(query, isAlbum = true, audiophileMode = audiophileMode)
    }

    override suspend fun resolveArtist(artistName: String): String? {
        val host = "www.qobuz.com"
        if (!circuitBreaker.canExecute(host)) return null

        return try {
            val appId = getEffectiveAppId()
            val encoded = artistName.encodeURLParameter()
            val url = "https://$host/api.json/0.2/catalog/search?query=$encoded&type=artists&limit=3&app_id=$appId"
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
            }
            if (resp.status.value == 429) {
                val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                circuitBreaker.recordRateLimit(host, retryAfter)
                return null
            }
            if (resp.status.isSuccess()) {
                circuitBreaker.recordSuccess(host)
                val parsed = json.decodeFromString<QobuzSearchResponse>(resp.bodyAsText())
                val matchedArtist = parsed.artists?.items?.firstOrNull { item ->
                    ResolverUtils.isArtistNameMatch(artistName, item.name)
                }
                val artistId = matchedArtist?.id
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

    private suspend fun searchQobuzDetailed(
        query: String,
        isAlbum: Boolean,
        audiophileMode: Boolean = false
    ): ResolutionOutcome<String> {
        val primary = executeQobuzSearch(query, isAlbum, audiophileMode)
        if (primary is ResolutionOutcome.Success) return primary
        if (primary is ResolutionOutcome.RateLimited) return primary // Immediate cascade abort

        val stripped = ResolverUtils.stripEditionSuffix(query)
        if (stripped != query && stripped.isNotBlank()) {
            val second = executeQobuzSearch(stripped, isAlbum, audiophileMode)
            if (second is ResolutionOutcome.Success) return second
            if (second is ResolutionOutcome.RateLimited) return second
        }
        val noParens = query.replace(Regex("\\s*\\([^)]*\\)"), "")
            .replace(Regex("\\s*\\[[^\\]]*\\]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (noParens != query && noParens != stripped && noParens.isNotBlank()) {
            val third = executeQobuzSearch(noParens, isAlbum, audiophileMode)
            if (third is ResolutionOutcome.Success) return third
            if (third is ResolutionOutcome.RateLimited) return third
        }
        val simplifiedQuery = query
            .replace(Regex("(?i)\\s+(?:feat\\.?|ft\\.?)\\s+[^\\-–(]+"), "")
            .replace(Regex(",\\s*[^,\\-–]+(?=\\s*[\\-–])"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (simplifiedQuery.isNotBlank() && simplifiedQuery != query && simplifiedQuery != stripped && simplifiedQuery != noParens) {
            val fourth = executeQobuzSearch(simplifiedQuery, isAlbum, audiophileMode)
            if (fourth is ResolutionOutcome.Success) return fourth
            if (fourth is ResolutionOutcome.RateLimited) return fourth
        }
        return ResolutionOutcome.NotFound
    }

    private suspend fun executeQobuzSearch(
        query: String,
        isAlbum: Boolean,
        audiophileMode: Boolean
    ): ResolutionOutcome<String> {
        val host = "www.qobuz.com"
        if (!circuitBreaker.canExecute(host)) {
            val remaining = circuitBreaker.getCooldownRemainingSeconds(host)
            return ResolutionOutcome.RateLimited(host = host, retryAfterSeconds = remaining)
        }

        return try {
            val appId = getEffectiveAppId()
            val encoded = query.encodeURLParameter()
            val typeParam = if (isAlbum) "albums" else "tracks"
            val limit = if (audiophileMode) 20 else 15
            val url = "https://$host/api.json/0.2/catalog/search?query=$encoded&type=$typeParam&limit=$limit&app_id=$appId"
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
            }
            if (resp.status.value == 429) {
                val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                circuitBreaker.recordRateLimit(host, retryAfter)
                return ResolutionOutcome.RateLimited(host = host, retryAfterSeconds = retryAfter)
            }
            if (resp.status.isSuccess()) {
                circuitBreaker.recordSuccess(host)
                val parsed = json.decodeFromString<QobuzSearchResponse>(resp.bodyAsText())
                if (isAlbum) {
                    val album = if (audiophileMode) {
                        parsed.albums?.items
                            ?.map { item ->
                                val isHiRes = item.hires == true || item.hiresStreamable == true || (item.maximumBitDepth ?: 16) >= 24
                                val fullTitle = if (item.version.isNullOrBlank()) item.title else "${item.title} (${item.version})"
                                val score = ResolverUtils.scoreAudiophileCandidate(
                                    query = query,
                                    candidateArtist = item.artist?.name,
                                    candidateTitle = fullTitle,
                                    candidateVersion = item.version,
                                    albumTitle = item.title,
                                    isHiRes = isHiRes,
                                    isLossless = true,
                                    bitDepth = item.maximumBitDepth,
                                    samplingRate = item.maximumSamplingRate,
                                    albumTrackCount = item.tracksCount
                                )
                                item to score
                            }
                            ?.filter { it.second > Int.MIN_VALUE }
                            ?.maxByOrNull { it.second }
                            ?.first
                    } else {
                        ResolverUtils.findBestCandidate(
                            candidates = parsed.albums?.items ?: emptyList(),
                            query = query,
                            isAlbum = true,
                            getArtist = { it.artist?.name },
                            getTitle = { it.title },
                            getVersion = { it.version }
                        )
                    }
                    if (!album?.id.isNullOrBlank()) {
                        return ResolutionOutcome.Success("https://open.qobuz.com/album/${album?.id}")
                    }
                } else {
                    val track = if (audiophileMode) {
                        parsed.tracks?.items
                            ?.map { item ->
                                val isHiRes = item.hires == true || item.hiresStreamable == true ||
                                    item.album?.hires == true || (item.maximumBitDepth ?: 16) >= 24
                                val fullTitle = if (item.version.isNullOrBlank()) item.title else "${item.title} (${item.version})"
                                val score = ResolverUtils.scoreAudiophileCandidate(
                                    query = query,
                                    candidateArtist = item.performer?.name,
                                    candidateTitle = fullTitle,
                                    candidateVersion = item.version,
                                    albumTitle = item.album?.title,
                                    isHiRes = isHiRes,
                                    isLossless = true,
                                    bitDepth = item.maximumBitDepth,
                                    samplingRate = item.maximumSamplingRate,
                                    albumTrackCount = item.album?.tracksCount
                                )
                                item to score
                            }
                            ?.filter { it.second > Int.MIN_VALUE }
                            ?.maxByOrNull { it.second }
                            ?.first
                    } else {
                        ResolverUtils.findBestCandidate(
                            candidates = parsed.tracks?.items ?: emptyList(),
                            query = query,
                            isAlbum = false,
                            getArtist = { it.performer?.name },
                            getTitle = { it.title },
                            getVersion = { it.version }
                        )
                    }
                    if (track?.id != null) {
                        return ResolutionOutcome.Success("https://open.qobuz.com/track/${track.id}")
                    }
                }
            }
            ResolutionOutcome.NotFound
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ResolutionOutcome.Error("Qobuz search failed: ${e.message}", e)
        }
    }

    /**
     * Extracts title and artist from an incoming Qobuz URL for conversion to other platforms.
     */
    suspend fun extractMetadata(url: String): String? {
        val host = "www.qobuz.com"
        if (!circuitBreaker.canExecute(host)) return null

        return try {
            val appId = getEffectiveAppId()
            val trackMatch = Regex("(?:track)/(?:[a-zA-Z0-9_-]+/)?([0-9]+)").find(url)
            if (trackMatch != null) {
                val trackId = trackMatch.groupValues[1]
                val apiUrl = "https://$host/api.json/0.2/track/get?track_id=$trackId&app_id=$appId"
                val resp = client.get(apiUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
                }
                if (resp.status.value == 429) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                    circuitBreaker.recordRateLimit(host, retryAfter)
                    return null
                }
                if (resp.status.isSuccess()) {
                    circuitBreaker.recordSuccess(host)
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
                val apiUrl = "https://$host/api.json/0.2/album/get?album_id=$albumId&app_id=$appId"
                val resp = client.get(apiUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) SongFlip/${EngineConfig.APP_VERSION}")
                }
                if (resp.status.value == 429) {
                    val retryAfter = resp.headers["Retry-After"]?.toLongOrNull() ?: 300L
                    circuitBreaker.recordRateLimit(host, retryAfter)
                    return null
                }
                if (resp.status.isSuccess()) {
                    circuitBreaker.recordSuccess(host)
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
