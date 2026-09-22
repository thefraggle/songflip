package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SongLinkApiResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {

    suspend fun resolve(
        canonicalUrl: String,
        targetPlatformKey: String,
        customApiUrl: String = "",
        customApiToken: String = ""
    ): ResolutionResult {
        return try {
            val encoded = canonicalUrl.encodeURLParameter()
            val base = if (customApiUrl.isNotBlank()) {
                customApiUrl.trimEnd('/')
            } else {
                "https://api.song.link/v1-alpha.1"
            }
            val targetUrl = "$base/links?url=$encoded"

            val resp = client.get(targetUrl) {
                if (customApiToken.isNotBlank()) {
                    header("Authorization", "Bearer $customApiToken")
                }
                header("User-Agent", "SongFlip-Mobile-KMP/1.4.10")
            }

            if (resp.status.isSuccess()) {
                val body = resp.bodyAsText()
                val rootObj = json.parseToJsonElement(body).jsonObject

                val linksByPlatform = rootObj["linksByPlatform"]?.jsonObject
                val targetObj = linksByPlatform?.get(targetPlatformKey)?.jsonObject
                    ?: if (targetPlatformKey == "youtubeMusic") linksByPlatform?.get("youtube")?.jsonObject else null

                val targetPageUrl = targetObj?.get("url")?.jsonPrimitive?.content
                val targetEntityId = targetObj?.get("entityUniqueId")?.jsonPrimitive?.content

                var title: String? = null
                var artist: String? = null
                var isAlbum = false

                if (targetEntityId != null) {
                    val entitiesByUniqueId = rootObj["entitiesByUniqueId"]?.jsonObject
                    val entity = entitiesByUniqueId?.get(targetEntityId)?.jsonObject
                    title = entity?.get("title")?.jsonPrimitive?.content
                    artist = entity?.get("artistName")?.jsonPrimitive?.content
                    isAlbum = entity?.get("isAlbum")?.jsonPrimitive?.booleanOrNull ?: false
                }

                if (title == null && rootObj.containsKey("entityUniqueId")) {
                    val fallbackId = rootObj["entityUniqueId"]?.jsonPrimitive?.content
                    if (fallbackId != null) {
                        val entity = rootObj["entitiesByUniqueId"]?.jsonObject?.get(fallbackId)?.jsonObject
                        title = entity?.get("title")?.jsonPrimitive?.content
                        artist = entity?.get("artistName")?.jsonPrimitive?.content
                        isAlbum = entity?.get("isAlbum")?.jsonPrimitive?.booleanOrNull ?: false
                    }
                }

                if (!targetPageUrl.isNullOrEmpty()) {
                    val formatted = UrlUtils.formatTargetUrl(targetPageUrl, targetPlatformKey)
                    val nativeUri = UrlUtils.toNativeAppUri(formatted, targetPlatformKey)
                    return ResolutionResult.Success(
                        targetUrl = formatted,
                        platform = targetPlatformKey,
                        title = title,
                        artist = artist,
                        isAlbum = isAlbum,
                        nativeAppUri = nativeUri
                    )
                }

                return ResolutionResult.Error("Target platform URL not found in API response", isUnsupported = false)
            } else {
                ResolutionResult.Error("API returned ${resp.status.value}", isUnsupported = false)
            }
        } catch (e: Throwable) {
            ResolutionResult.Error(e.message ?: "Network error", isUnsupported = false)
        }
    }
}
