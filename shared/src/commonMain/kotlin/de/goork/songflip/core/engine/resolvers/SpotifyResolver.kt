package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.util.decodeBase64String
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
internal data class SpotifyOembedResponse(
    val title: String? = null,
    @SerialName("thumbnail_url")
    val thumbnailUrl: String? = null
)

data class SpotifyScrapedTrack(
    val title: String,
    val artist: String?,
    val isAlbum: Boolean = false,
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

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        val encoded = query.encodeURLParameter()
        return "https://open.spotify.com/search/$encoded"
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        val encoded = query.encodeURLParameter()
        return "https://open.spotify.com/search/$encoded"
    }

    suspend fun extractEmbedMetadata(url: String): SpotifyScrapedTrack? {
        val entityMatch = Regex("""(?:open\.spotify\.com|spotify:)(?:/intl-[a-z]{2})?/(track|album|episode)/([a-zA-Z0-9]+)""").find(url)
            ?: Regex("""spotify:(track|album|episode):([a-zA-Z0-9]+)""").find(url)

        if (entityMatch != null) {
            val entityType = entityMatch.groupValues[1]
            val entityId = entityMatch.groupValues[2]
            val embedUrl = "https://open.spotify.com/embed/$entityType/$entityId"

            try {
                val resp = client.get(embedUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    header("Accept-Language", "en-US,en;q=0.9")
                }
                if (resp.status.isSuccess()) {
                    val html = resp.bodyAsText()
                    // 1. Next.js __NEXT_DATA__
                    val nextDataMatch = Regex("""<script\s+id="__NEXT_DATA__"[^>]*>([^<]+)</script>""").find(html)
                    if (nextDataMatch != null) {
                        try {
                            val jsonStr = nextDataMatch.groupValues[1]
                            val root = json.parseToJsonElement(jsonStr).jsonObject
                            val pageProps = root["props"]?.jsonObject?.get("pageProps")?.jsonObject
                            val state = pageProps?.get("state")?.jsonObject
                            val data = state?.get("data")?.jsonObject
                            val entity = data?.get("entity")?.jsonObject

                            if (entity != null) {
                                val name = entity["name"]?.jsonPrimitive?.content
                                    ?: entity["title"]?.jsonPrimitive?.content
                                val artists = entity["artists"]?.jsonArray?.mapNotNull {
                                    it.jsonObject["name"]?.jsonPrimitive?.content
                                }?.joinToString(", ")?.ifBlank { null }
                                val type = entity["type"]?.jsonPrimitive?.content
                                val images = entity["visualIdentity"]?.jsonObject?.get("image")?.jsonArray
                                val thumb = images?.firstOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.content

                                if (!name.isNullOrBlank()) {
                                    return SpotifyScrapedTrack(
                                        title = name,
                                        artist = artists,
                                        isAlbum = (type == "album" || entityType == "album"),
                                        thumbnailUrl = thumb
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            de.goork.songflip.core.util.SongFlipLogger.d("SpotifyResolver", "Failed to parse initial_state JSON: ${t.message}")
                        }
                    }

                    // 2. OpenGraph Fallback from Embed HTML
                    val ogTitleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""").find(html)
                        ?: Regex("""data-testid="entity-title"[^>]*>.*?<a[^>]*>([^<]+)</a>""").find(html)
                    val ogDescMatch = Regex("""<meta\s+property="og:description"\s+content="([^"]+)"""").find(html)
                    val ogArtistMatch = Regex("""data-testid="subtitle"[^>]*>.*?<a[^>]*>([^<]+)</a>""").find(html)
                    val ogImageMatch = Regex("""<meta\s+property="og:image"\s+content="([^"]+)"""").find(html)

                    if (ogTitleMatch != null) {
                        val title = ogTitleMatch.groupValues[1].trim()
                        val artist = ogArtistMatch?.groupValues?.get(1)?.trim()
                            ?: ogDescMatch?.groupValues?.get(1)?.substringBefore("·")?.trim()
                        return SpotifyScrapedTrack(
                            title = title,
                            artist = artist?.ifBlank { null },
                            isAlbum = (entityType == "album"),
                            thumbnailUrl = ogImageMatch?.groupValues?.get(1)?.trim()
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
            }
        }

        // 3. Fallback to Spotify oEmbed
        return try {
            val encoded = url.encodeURLParameter()
            val resp = client.get("https://open.spotify.com/oembed?url=$encoded")
            if (resp.status.isSuccess()) {
                val parsed = json.decodeFromString<SpotifyOembedResponse>(resp.bodyAsText())
                val title = parsed.title
                if (!title.isNullOrEmpty()) {
                    SpotifyScrapedTrack(
                        title = title,
                        artist = null,
                        isAlbum = url.contains("/album/"),
                        thumbnailUrl = parsed.thumbnailUrl
                    )
                } else null
            } else null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }

    suspend fun extractMetadata(url: String): String? {
        val scraped = extractEmbedMetadata(url)
        if (scraped != null) {
            val artist = scraped.artist
            val title = scraped.title
            return if (!artist.isNullOrBlank() && !title.contains(artist, ignoreCase = true)) {
                "$artist $title"
            } else {
                title
            }
        }
        return null
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
