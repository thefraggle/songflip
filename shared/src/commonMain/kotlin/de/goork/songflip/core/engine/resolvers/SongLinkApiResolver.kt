package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SongLinkApiResponse(
    val entityUniqueId: String? = null,
    val userCountry: String? = null,
    val linksByPlatform: Map<String, SongLinkPlatformLink> = emptyMap(),
    val entitiesByUniqueId: Map<String, SongLinkEntity> = emptyMap()
)

@Serializable
internal data class SongLinkPlatformLink(
    val country: String? = null,
    val url: String? = null,
    val nativeAppUriMobile: String? = null,
    val nativeAppUriDesktop: String? = null,
    val entityUniqueId: String? = null
)

@Serializable
internal data class SongLinkEntity(
    val id: String? = null,
    val type: String? = null,
    val title: String? = null,
    val artistName: String? = null,
    val thumbnailUrl: String? = null,
    val thumbnailWidth: Int? = null,
    val thumbnailHeight: Int? = null,
    val apiProvider: String? = null,
    val platforms: List<String> = emptyList(),
    val isAlbum: Boolean? = null
)

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
                val parsed = json.decodeFromString<SongLinkApiResponse>(resp.bodyAsText())
                val targetObj = parsed.linksByPlatform[targetPlatformKey]
                    ?: if (targetPlatformKey == "youtubeMusic") parsed.linksByPlatform["youtube"] else null

                val targetPageUrl = targetObj?.url
                val targetEntityId = targetObj?.entityUniqueId

                var title: String? = null
                var artist: String? = null
                var isAlbum = false
                var thumbnailUrl: String? = null

                if (targetEntityId != null) {
                    val entity = parsed.entitiesByUniqueId[targetEntityId]
                    title = entity?.title
                    artist = entity?.artistName
                    isAlbum = entity?.isAlbum ?: false
                    thumbnailUrl = entity?.thumbnailUrl
                }

                if (title == null && parsed.entityUniqueId != null) {
                    val entity = parsed.entitiesByUniqueId[parsed.entityUniqueId]
                    title = entity?.title
                    artist = entity?.artistName
                    isAlbum = entity?.isAlbum ?: false
                    if (thumbnailUrl == null) {
                        thumbnailUrl = entity?.thumbnailUrl
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
                        nativeAppUri = nativeUri,
                        thumbnailUrl = thumbnailUrl
                    )
                }

                return ResolutionResult.Error("Target platform URL not found in API response", isUnsupported = false, errorReason = "target_not_found")
            } else {
                ResolutionResult.Error("API returned ${resp.status.value}", isUnsupported = false, errorReason = "upstream_${resp.status.value}")
            }
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                throw e
            }
            val errorReason = if (e is io.ktor.client.plugins.HttpRequestTimeoutException || e is kotlinx.coroutines.TimeoutCancellationException) {
                "timeout"
            } else {
                "network_error"
            }
            ResolutionResult.Error(e.message ?: "Network error", isUnsupported = false, errorReason = errorReason)
        }
    }
}
