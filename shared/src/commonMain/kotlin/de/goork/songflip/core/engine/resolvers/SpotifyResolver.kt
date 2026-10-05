package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class SpotifyOembedResponse(
    val title: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null
)

data class SpotifyPreReleaseInfo(
    val title: String?,
    val artist: String?
)

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
                val parsed = json.decodeFromString<SpotifyOembedResponse>(resp.bodyAsText())
                val title = parsed.title
                if (!title.isNullOrEmpty()) return title
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    suspend fun extractPreReleaseInfo(url: String): SpotifyPreReleaseInfo {
        var title: String? = null
        var artist: String? = null

        // 1. Scrape pre-release page HTML for initialState JSON
        try {
            val resp = client.get(url)
            if (resp.status.isSuccess()) {
                val html = resp.bodyAsText()
                val match = Regex("""<script\s+id="initialState"[^>]*>([^<]+)</script>""").find(html)
                if (match != null) {
                    val rawB64 = match.groupValues[1].trim()
                    val decoded = try {
                        rawB64.decodeBase64String()
                    } catch (_: Throwable) {
                        ""
                    }
                    if (decoded.isNotEmpty()) {
                        val titleMatch = Regex(""""preReleaseContent"\s*:\s*\{[^}]*"name"\s*:\s*"([^"]+)"""").find(decoded)
                        if (titleMatch != null) {
                            title = titleMatch.groupValues[1]
                        }
                        val artistMatch = Regex(""""profile"\s*:\s*\{\s*"name"\s*:\s*"([^"]+)"""").find(decoded)
                        if (artistMatch != null) {
                            artist = artistMatch.groupValues[1]
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
        }

        // 2. Fallback / supplement via Spotify oEmbed if title is missing
        if (title.isNullOrBlank()) {
            val oembedTitle = extractMetadata(url)
            if (!oembedTitle.isNullOrBlank()) {
                title = oembedTitle
            }
        }

        return SpotifyPreReleaseInfo(title = title, artist = artist)
    }
}
