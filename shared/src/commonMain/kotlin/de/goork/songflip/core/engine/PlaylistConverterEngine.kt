package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PlaylistConversionResult
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
        maxTracks: Int = 100
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
                    setBody(payload)
                }

                if (response.status.isSuccess()) {
                    val body = response.bodyAsText()
                    val result = json.decodeFromString<PlaylistConversionResult>(body)
                    return Result.success(result)
                } else {
                    val errorBody = response.bodyAsText()
                    lastException = Exception("HTTP ${response.status.value}: $errorBody")
                }
            } catch (t: Throwable) {
                lastException = t
            }
        }

        return Result.failure(lastException ?: Exception("Failed to convert playlist"))
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
                    "https://music.youtube.com/watch_videos?video_ids=${videoIds.joinToString(",")}"
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
