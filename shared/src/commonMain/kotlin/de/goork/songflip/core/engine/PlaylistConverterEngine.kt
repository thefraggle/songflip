package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistConversionException
import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistErrorCode
import de.goork.songflip.core.model.PlaylistErrorPayload
import de.goork.songflip.core.model.PlaylistTrackItem
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PlaylistConverterEngine(
    private val client: HttpClient = createPlatformHttpClient()
) {
    constructor() : this(createPlatformHttpClient())

    companion object {
        val shared: PlaylistConverterEngine by lazy { PlaylistConverterEngine() }

        private val ENDPOINTS = listOf(
            "https://songflip.link/api/playlist/convert",
            "https://songflip-web.web.app/api/playlist/convert"
        )
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun convertPlaylist(
        url: String,
        targetPlatformKey: String,
        isPro: Boolean = false,
        maxTracks: Int = 50,
        authToken: String? = null
    ): Result<PlaylistConversionResult> {
        val payload = buildJsonObject {
            put("url", url)
            put("targetPlatform", targetPlatformKey)
            put("isPro", isPro)
            put("maxTracks", maxTracks)
        }.toString()

        var lastException: Throwable? = null

        for (endpoint in ENDPOINTS) {
            try {
                val response = client.post(endpoint) {
                    contentType(ContentType.Application.Json)
                    header("x-web-client", "songflip-app")
                    if (!authToken.isNullOrBlank()) {
                        header("Authorization", "Bearer $authToken")
                        header("x-user-id", authToken)
                    }
                    setBody(payload)
                }

                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val result = json.decodeFromString<PlaylistConversionResult>(body)
                    return Result.success(result)
                } else {
                    val errorBody = response.bodyAsText()
                    val parsedException = parseErrorPayload(errorBody, response.status.value)
                    lastException = parsedException
                }
            } catch (t: Throwable) {
                lastException = t
            }
        }

        return Result.failure(lastException ?: PlaylistConversionException(
            errorCode = de.goork.songflip.core.model.PlaylistErrorCode.UNKNOWN_ERROR,
            message = "Failed to convert playlist"
        ))
    }

    internal fun parseErrorPayload(errorBody: String, statusCode: Int): Throwable {
        return try {
            val payload = json.decodeFromString<de.goork.songflip.core.model.PlaylistErrorPayload>(errorBody)
            val rawCode = payload.code ?: payload.error ?: ""
            val errorCode = when (rawCode.uppercase()) {
                "UNSUPPORTED_PLATFORM" -> de.goork.songflip.core.model.PlaylistErrorCode.UNSUPPORTED_PLATFORM
                "PRIVATE_OR_RESTRICTED" -> de.goork.songflip.core.model.PlaylistErrorCode.PRIVATE_OR_RESTRICTED
                "EMPTY_PLAYLIST" -> de.goork.songflip.core.model.PlaylistErrorCode.EMPTY_PLAYLIST
                "UPSTREAM_TIMEOUT" -> de.goork.songflip.core.model.PlaylistErrorCode.UPSTREAM_TIMEOUT
                "RATE_LIMITED" -> de.goork.songflip.core.model.PlaylistErrorCode.RATE_LIMITED
                "EXTRACTION_FAILED" -> de.goork.songflip.core.model.PlaylistErrorCode.EXTRACTION_FAILED
                else -> de.goork.songflip.core.model.PlaylistErrorCode.UNKNOWN_ERROR
            }
            val reason = payload.reason ?: errorCode.name.lowercase()
            val message = payload.message ?: "HTTP $statusCode: $rawCode"
            de.goork.songflip.core.model.PlaylistConversionException(
                errorCode = errorCode,
                message = message,
                reason = reason
            )
        } catch (_: Throwable) {
            de.goork.songflip.core.model.PlaylistConversionException(
                errorCode = de.goork.songflip.core.model.PlaylistErrorCode.UNKNOWN_ERROR,
                message = "HTTP $statusCode: $errorBody",
                reason = "http_$statusCode"
            )
        }
    }

    fun buildZeroOAuthUrl(
        title: String,
        targetPlatform: String,
        tracks: List<PlaylistTrackItem>
    ): String? {
        val matched = tracks.filter { it.matched }
        if (matched.isEmpty()) return null

        return when (targetPlatform) {
            "youtubeMusic" -> {
                val videoIds = matched.mapNotNull { track ->
                    track.targetId?.takeIf { it.length == 11 }
                }
                if (videoIds.isNotEmpty()) {
                    "https://www.youtube.com/watch_videos?video_ids=${videoIds.joinToString(",")}"
                } else null
            }
            "spotify" -> {
                val trackIds = matched.mapNotNull { track ->
                    track.targetId?.takeIf { it.isNotBlank() }
                }
                if (trackIds.isNotEmpty()) {
                    val cleanTitle = title.replace(Regex("[:/]"), " ").encodeURLParameter()
                    "spotify:trackset:$cleanTitle:${trackIds.joinToString(",")}"
                } else null
            }
            else -> null
        }
    }
}
