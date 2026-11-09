package de.goork.songflip.core.engine

import de.goork.songflip.core.cache.LinkCache
import de.goork.songflip.core.model.MusicPlatform
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.SongFlipLogger
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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class SpotifyPodcastOEmbedDto(
    val title: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null
)

/**
 * Handles podcast episode and show resolution across supported podcast ecosystems.
 */
class PodcastResolver(
    private val client: HttpClient,
    private val json: Json,
    private val cache: LinkCache
) {
    suspend fun resolvePodcast(
        canonicalUrl: String,
        targetPlatformKey: String,
        isPrefetch: Boolean = false,
        forceRefresh: Boolean = false
    ): ResolutionResult.Podcast {
        val now = getCurrentTimeMillis()
        if (!forceRefresh) {
            val cached = cache.get(canonicalUrl, targetPlatformKey, now)
            if (cached != null) {
                if (!isPrefetch) {
                    cache.markAsHistory(canonicalUrl, targetPlatformKey, now)
                }
                return ResolutionResult.Podcast(
                    originalUrl = canonicalUrl,
                    targetUrl = cached.targetUrl,
                    platform = cached.platform,
                    showTitle = cached.artist ?: cached.title ?: "Podcast",
                    episodeTitle = if (cached.artist != null) cached.title else null,
                    nativeAppUri = cached.nativeAppUri,
                    thumbnailUrl = cached.thumbnailUrl,
                    isEpisode = cached.artist != null,
                    isDeepSearch = true
                )
            }
        }

        // 1. Try Backend /resolve for high-accuracy metadata & direct links
        val encodedUrl = canonicalUrl.encodeURLParameter()
        val refreshParam = if (forceRefresh) "&force_refresh=true" else ""
        val endpoints = listOf(
            "https://cache.songflip.link/resolve?url=$encodedUrl&platform=$targetPlatformKey$refreshParam",
            "https://songflip-web.web.app/resolve?url=$encodedUrl&platform=$targetPlatformKey$refreshParam"
        )

        for (endpoint in endpoints) {
            try {
                val resp = client.get(endpoint) {
                    header("Accept", "application/json")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    val root = json.parseToJsonElement(body).jsonObject
                    if (root["status"]?.jsonPrimitive?.content == "success" && (root["isPodcast"]?.jsonPrimitive?.booleanOrNull == true || root["entityType"]?.jsonPrimitive?.content == "podcast")) {
                        val item = root["item"]?.jsonObject
                        if (item != null) {
                            val showTitle = item["showTitle"]?.jsonPrimitive?.content?.ifBlank { null } ?: "Podcast"
                            val episodeTitle = item["episodeTitle"]?.jsonPrimitive?.content?.ifBlank { null }
                            val thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }
                            val isEpisode = item["isEpisode"]?.jsonPrimitive?.booleanOrNull ?: (episodeTitle != null)
                            val queryText = if (episodeTitle != null) {
                                if (showTitle.equals("Podcast", ignoreCase = true) || episodeTitle.contains(showTitle, ignoreCase = true)) {
                                    episodeTitle
                                } else {
                                    "$showTitle $episodeTitle"
                                }
                            } else {
                                showTitle
                            }
                            val rawTargetUrl = item["targetUrl"]?.jsonPrimitive?.content
                            val targetUrl = if (rawTargetUrl != null && UrlUtils.isSafeTargetUrl(rawTargetUrl)) {
                                rawTargetUrl
                            } else {
                                UrlUtils.buildPodcastSearchUrl(queryText, targetPlatformKey)
                            }
                            val rawNativeAppUri = item["nativeAppUri"]?.jsonPrimitive?.content
                            val nativeAppUri = if (rawNativeAppUri != null && UrlUtils.isSafeNativeAppUri(rawNativeAppUri)) {
                                rawNativeAppUri
                            } else {
                                UrlUtils.toPodcastNativeAppUri(targetPlatformKey, queryText)
                            }

                            val result = ResolutionResult.Podcast(
                                originalUrl = canonicalUrl,
                                targetUrl = targetUrl,
                                platform = targetPlatformKey,
                                showTitle = showTitle,
                                episodeTitle = episodeTitle,
                                nativeAppUri = nativeAppUri,
                                thumbnailUrl = thumbnailUrl,
                                isEpisode = isEpisode,
                                isDeepSearch = true
                            )
                            cache.putPodcast(canonicalUrl, targetPlatformKey, result, now, isHistory = !isPrefetch)
                            return result
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // 2. Direct Spotify Resolution via Official oEmbed API
        if (UrlUtils.detectPlatform(canonicalUrl) == MusicPlatform.SPOTIFY) {
            try {
                val oembedUrl = "https://open.spotify.com/oembed?url=${canonicalUrl.encodeURLParameter()}"
                val oResp = client.get(oembedUrl)
                if (oResp.status.isSuccess()) {
                    val oembed = json.decodeFromString<SpotifyPodcastOEmbedDto>(oResp.bodyAsText())
                    val title = oembed.title?.ifBlank { null }
                    val thumb = oembed.thumbnailUrl?.ifBlank { null }
                    if (title != null) {
                        val isEp = canonicalUrl.contains("/episode/") || canonicalUrl.startsWith("spotify:episode:")
                        val targetUrl = UrlUtils.buildPodcastSearchUrl(title, targetPlatformKey)
                        val nativeUri = UrlUtils.toPodcastNativeAppUri(targetPlatformKey, title)
                        val oResult = ResolutionResult.Podcast(
                            originalUrl = canonicalUrl,
                            targetUrl = targetUrl,
                            platform = targetPlatformKey,
                            showTitle = title,
                            episodeTitle = if (isEp) title else null,
                            nativeAppUri = nativeUri,
                            thumbnailUrl = thumb,
                            isEpisode = isEp,
                            isDeepSearch = true
                        )
                        cache.putPodcast(canonicalUrl, targetPlatformKey, oResult, now, isHistory = !isPrefetch)
                        return oResult
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }

        // 3. Offline Fallback: Extract Show / Episode slug and generate Deep-Search Intent
        val fallbackQuery = extractPodcastFallbackQuery(canonicalUrl)
        val targetUrl = UrlUtils.buildPodcastSearchUrl(fallbackQuery, targetPlatformKey)
        val nativeUri = UrlUtils.toPodcastNativeAppUri(targetPlatformKey, fallbackQuery)
        val fallbackResult = ResolutionResult.Podcast(
            originalUrl = canonicalUrl,
            targetUrl = targetUrl,
            platform = targetPlatformKey,
            showTitle = fallbackQuery,
            episodeTitle = null,
            nativeAppUri = nativeUri,
            thumbnailUrl = null,
            isEpisode = false,
            isDeepSearch = true
        )
        cache.putPodcast(canonicalUrl, targetPlatformKey, fallbackResult, now, isHistory = !isPrefetch)
        return fallbackResult
    }

    private fun extractPodcastFallbackQuery(url: String): String {
        return try {
            val clean = url.substringBefore("?").substringBefore("#").trimEnd('/')
            val rawSlug = clean.substringAfterLast("/")
            if (rawSlug.length in 18..30 && rawSlug.matches(Regex("""^[a-zA-Z0-9]+$"""))) {
                return "Podcast"
            }
            val slug = rawSlug
                .replace("-", " ")
                .replace("_", " ")
                .replace(Regex("""\bid\d+\b""", RegexOption.IGNORE_CASE), "")
                .trim()
            if (slug.isNotBlank() && slug.length > 2 && !slug.all { it.isDigit() }) {
                slug
            } else {
                "Podcast"
            }
        } catch (t: Throwable) {
            SongFlipLogger.d("PodcastResolver", "Failed to extract podcast fallback query: ${t.message}")
            "Podcast"
        }
    }
}
