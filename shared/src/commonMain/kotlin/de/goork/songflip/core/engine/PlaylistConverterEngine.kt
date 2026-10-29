package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistConversionException
import de.goork.songflip.core.model.PlaylistConversionResult
import de.goork.songflip.core.model.PlaylistErrorCode
import de.goork.songflip.core.model.PlaylistErrorPayload
import de.goork.songflip.core.model.PlaylistTrackItem
import de.goork.songflip.core.model.deduplicated
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.plugin
import io.ktor.client.plugins.timeout
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

    private val supportsHttpTimeout: Boolean by lazy {
        try {
            client.plugin(HttpTimeout)
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Throws(Throwable::class)
    suspend fun convertPlaylistOrThrow(
        url: String,
        targetPlatformKey: String,
        isPro: Boolean = false,
        maxTracks: Int = 200,
        authToken: String? = null
    ): PlaylistConversionResult {
        return convertPlaylist(url, targetPlatformKey, isPro, maxTracks, authToken, false).getOrThrow()
    }

    @Throws(Throwable::class)
    suspend fun convertPlaylistOrThrow(
        url: String,
        targetPlatformKey: String,
        isPro: Boolean,
        maxTracks: Int,
        authToken: String?,
        forceRefresh: Boolean
    ): PlaylistConversionResult {
        return convertPlaylist(url, targetPlatformKey, isPro, maxTracks, authToken, forceRefresh).getOrThrow()
    }

    suspend fun convertPlaylist(
        url: String,
        targetPlatformKey: String,
        isPro: Boolean,
        maxTracks: Int,
        authToken: String?
    ): Result<PlaylistConversionResult> {
        return convertPlaylist(url, targetPlatformKey, isPro, maxTracks, authToken, false)
    }

    suspend fun convertPlaylist(
        url: String,
        targetPlatformKey: String,
        isPro: Boolean = false,
        maxTracks: Int = 200,
        authToken: String? = null,
        forceRefresh: Boolean = false
    ): Result<PlaylistConversionResult> {
        val payload = buildJsonObject {
            put("url", url)
            put("targetPlatform", targetPlatformKey)
            put("maxTracks", maxTracks)
            if (forceRefresh) {
                put("forceRefresh", true)
            }
        }.toString()

        var lastException: Throwable? = null
        val baseTimeout = if (isPro || maxTracks > 20) 45000L else 25000L

        for ((index, endpoint) in ENDPOINTS.withIndex()) {
            try {
                val timeoutMs = if (index == 0 && ENDPOINTS.size > 1) baseTimeout else (baseTimeout + 10000L)
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    kotlinx.coroutines.withTimeout(timeoutMs) {
                        client.post(endpoint) {
                            if (supportsHttpTimeout) {
                                timeout {
                                    requestTimeoutMillis = timeoutMs
                                    socketTimeoutMillis = timeoutMs
                                }
                            }
                            contentType(ContentType.Application.Json)
                            header("x-web-client", "songflip-app")
                            if (!authToken.isNullOrBlank()) {
                                header("Authorization", "Bearer $authToken")
                            }
                            setBody(payload)
                        }
                    }
                }

                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val result = json.decodeFromString<PlaylistConversionResult>(body).deduplicated()
                    return Result.success(result)
                } else {
                    val errorBody = response.bodyAsText()
                    val parsedException = parseErrorPayload(errorBody, response.status.value)
                    // Fast-fail on 4xx client errors (e.g. private playlist, bad request, rate limit)
                    // to prevent duplicate failed calls against fallback endpoints (#172).
                    if (response.status.value in 400..499) {
                        return Result.failure(parsedException)
                    }
                    lastException = parsedException
                }
            } catch (t: Throwable) {
                if (t is HttpRequestTimeoutException || t is kotlinx.coroutines.TimeoutCancellationException) {
                    lastException = PlaylistConversionException(
                        errorCode = PlaylistErrorCode.UPSTREAM_TIMEOUT,
                        message = "Playlist conversion timed out.",
                        reason = "timeout"
                    )
                    continue
                }
                if (t is kotlinx.coroutines.CancellationException) throw t
                lastException = t
            }
        }

        if (lastException is kotlinx.coroutines.TimeoutCancellationException) {
            return Result.failure(PlaylistConversionException(
                errorCode = PlaylistErrorCode.UPSTREAM_TIMEOUT,
                message = "Playlist conversion timed out.",
                reason = "timeout"
            ))
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
                val spotifyIdRegex = Regex("^[a-zA-Z0-9]+$")
                val trackIds = matched.mapNotNull { track ->
                    track.targetId?.takeIf { spotifyIdRegex.matches(it) }
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
