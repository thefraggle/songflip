package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SpotifyResolver(
    private val client: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.SPOTIFY

    override suspend fun resolveTrack(query: String): String? {
        val encoded = query.encodeURLParameter()
        return "https://open.spotify.com/search/$encoded"
    }

    override suspend fun resolveAlbum(query: String): String? {
        val encoded = query.encodeURLParameter()
        return "https://open.spotify.com/search/$encoded"
    }

    suspend fun extractMetadata(url: String): String? {
        return try {
            val encoded = url.encodeURLParameter()
            val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
            if (resp.status.isSuccess()) {
                val root = json.parseToJsonElement(resp.bodyAsText()).jsonObject
                val title = root["title"]?.jsonPrimitive?.content
                if (!title.isNullOrEmpty()) return title
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
