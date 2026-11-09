package de.goork.songflip.core.engine

import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.SongFlipLogger
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Handles communication with the SongFlip L2 server-side edge cache (/resolve & /ingest).
 */
class L2CacheClient(
    private val client: HttpClient,
    private val json: Json,
    private val backgroundScope: CoroutineScope
) {
    fun pingCacheIngestAsync(
        originalUrl: String,
        targetUrl: String,
        targetPlatform: String,
        title: String?,
        artist: String?,
        isAlbum: Boolean,
        links: Map<String, String>? = null
    ) {
        if (originalUrl.isBlank() || targetUrl.isBlank()) return
        // Only ingest verified direct platform mappings with complete metadata and at least 3 platform links.
        if (targetPlatform.endsWith("_search") || targetPlatform.endsWith("_artist") || targetPlatform.endsWith("_fallback")) return
        if (UrlUtils.isSearchUrl(targetUrl) || UrlUtils.isSearchUrl(originalUrl)) return
        if (title.isNullOrBlank() || artist.isNullOrBlank()) return
        if (links == null || links.size < 3) return

        backgroundScope.launch {
            try {
                val jsonPayload = buildJsonObject {
                    put("originalUrl", originalUrl)
                    put("targetUrl", targetUrl)
                    put("platform", targetPlatform)
                    put("title", title.trim())
                    put("artist", artist.trim())
                    put("isAlbum", isAlbum)
                    if (!links.isNullOrEmpty()) {
                        putJsonObject("links") {
                            for ((k, v) in links) {
                                if (k.isNotBlank() && v.isNotBlank() && !UrlUtils.isSearchUrl(v)) {
                                    put(k, v)
                                }
                            }
                        }
                    }
                }.toString()

                client.post("https://cache.songflip.link/ingest") {
                    header("Content-Type", "application/json")
                    header("x-web-client", "songflip-app")
                    setBody(jsonPayload)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                // Background ingestion is non-blocking and best-effort, but log for diagnostics
                SongFlipLogger.w("L2CacheClient", "Background cache ingest failed for $originalUrl: ${t.message}", t)
            }
        }
    }

    suspend fun queryL2ServerCache(
        canonicalUrl: String,
        targetPlatformKey: String,
        authToken: String,
        forceRefresh: Boolean = false,
        resolveDirectPlatformUrl: suspend (query: String, platform: String, isAlbum: Boolean) -> String?
    ): ResolutionResult.Success? {
        if (authToken.isBlank()) return null
        val encodedUrl = canonicalUrl.encodeURLParameter()
        val refreshParam = if (forceRefresh) "&force_refresh=true" else ""
        val endpoints = listOf(
            "https://cache.songflip.link/resolve?url=$encodedUrl$refreshParam",
            "https://songflip-web.web.app/resolve?url=$encodedUrl$refreshParam"
        )

        for (endpoint in endpoints) {
            try {
                val resp = client.get(endpoint) {
                    header("Authorization", "Bearer $authToken")
                    header("Accept", "application/json")
                }
                if (resp.status.isSuccess()) {
                    val body = resp.bodyAsText()
                    val root = json.parseToJsonElement(body).jsonObject
                    if (root["status"]?.jsonPrimitive?.content == "success") {
                        val item = root["item"]?.jsonObject
                        if (item != null) {
                            val title = item["title"]?.jsonPrimitive?.content?.ifBlank { null }
                            val artist = item["artist"]?.jsonPrimitive?.content?.ifBlank { null }
                            val isAlbum = item["isAlbum"]?.jsonPrimitive?.booleanOrNull ?: false
                            val links = item["links"]?.jsonObject
                            val thumbnailUrl = item["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }
                                ?: root["thumbnailUrl"]?.jsonPrimitive?.content?.ifBlank { null }

                            val rawTarget = when (targetPlatformKey) {
                                "spotify" -> links?.get("spotify")?.jsonPrimitive?.content
                                "appleMusic" -> links?.get("appleMusic")?.jsonPrimitive?.content
                                "youtubeMusic" -> links?.get("youtubeMusic")?.jsonPrimitive?.content ?: links?.get("youtube")?.jsonPrimitive?.content
                                "deezer" -> links?.get("deezer")?.jsonPrimitive?.content
                                "tidal" -> links?.get("tidal")?.jsonPrimitive?.content
                                "amazonMusic" -> links?.get("amazonMusic")?.jsonPrimitive?.content
                                else -> links?.get(targetPlatformKey)?.jsonPrimitive?.content
                            }

                            if (!rawTarget.isNullOrBlank()) {
                                val searchFallbackQuery = UrlUtils.extractSearchQuery(rawTarget)
                                val upgradedDirectUrl = if (searchFallbackQuery != null) {
                                    val candidateQuery = if (!title.isNullOrBlank()) {
                                        if (!artist.isNullOrBlank() && !title.contains(artist, ignoreCase = true)) {
                                            "$artist $title"
                                        } else {
                                            title
                                        }
                                    } else {
                                        searchFallbackQuery
                                    }
                                    val cleanQuery = UrlUtils.cleanSearchQuery(candidateQuery)
                                    resolveDirectPlatformUrl(
                                        cleanQuery,
                                        targetPlatformKey,
                                        isAlbum
                                    )
                                } else null

                                val effectiveRawTarget = upgradedDirectUrl ?: rawTarget
                                val isStillSearchFallback = upgradedDirectUrl == null && searchFallbackQuery != null
                                val formatted = UrlUtils.formatTargetUrl(effectiveRawTarget, targetPlatformKey)
                                if (!UrlUtils.isSafeTargetUrl(formatted)) {
                                    SongFlipLogger.w("L2CacheClient", "Rejected unsafe targetUrl from backend cache: $formatted")
                                    continue
                                }
                                val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                                val safeNativeUri = if (nativeUri.isNotBlank() && UrlUtils.isSafeNativeAppUri(nativeUri)) nativeUri else null
                                return ResolutionResult.Success(
                                    targetUrl = formatted,
                                    platform = if (isStillSearchFallback) "${targetPlatformKey}_search" else targetPlatformKey,
                                    title = title,
                                    artist = artist,
                                    isAlbum = isAlbum,
                                    nativeAppUri = safeNativeUri,
                                    thumbnailUrl = thumbnailUrl
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Endpoint unreachable or timed out; continue to next fallback
            }
        }
        return null
    }
}
