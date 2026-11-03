package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class AudiomackResolver(
    private val client: HttpClient
) {

    /**
     * Extracts title and artist from an incoming Audiomack track or album URL.
     */
    suspend fun extractMetadata(url: String): String? {
        return try {
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9")
            }

            if (resp.status.isSuccess()) {
                val html = resp.bodyAsText()
                // Audiomack title tags: "<title>Title by Artist: Listen on Audiomack</title>"
                val titleMatch = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+property=[\"']og:title[\"']", RegexOption.IGNORE_CASE).find(html)

                if (titleMatch != null) {
                    val raw = titleMatch.groupValues[1]
                        .replace("&amp;", "&")
                        .replace("&#39;", "'")
                        .replace("&quot;", "\"")

                    var cleaned = raw.substringBefore(": Listen on Audiomack")
                        .substringBefore(" | Audiomack")
                        .substringBefore(" on Audiomack")
                        .trim()

                    if (cleaned.contains(" by ")) {
                        val titlePart = cleaned.substringBefore(" by ").trim()
                        val artistPart = cleaned.substringAfter(" by ").trim()
                        if (titlePart.isNotBlank() && artistPart.isNotBlank()) {
                            cleaned = "$artistPart - $titlePart"
                        }
                    }

                    if (cleaned.isNotBlank() && cleaned != "Audiomack") {
                        return cleaned
                    }
                }
            }

            // Fallback: Extract from URL slug e.g. audiomack.com/artist-name/song/song-title
            val slugMatch = Regex("audiomack\\.com/([a-zA-Z0-9_-]+)/(?:song|album)/([a-zA-Z0-9_-]+)").find(url)
            if (slugMatch != null) {
                val artist = slugMatch.groupValues[1].replace("-", " ").replace("_", " ").trim()
                val title = slugMatch.groupValues[2].replace("-", " ").replace("_", " ").trim()
                val combined = "$artist $title".trim()
                if (combined.isNotBlank()) {
                    return combined
                }
            }

            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val slugMatch = Regex("audiomack\\.com/([a-zA-Z0-9_-]+)/(?:song|album)/([a-zA-Z0-9_-]+)").find(url)
            if (slugMatch != null) {
                val artist = slugMatch.groupValues[1].replace("-", " ").replace("_", " ").trim()
                val title = slugMatch.groupValues[2].replace("-", " ").replace("_", " ").trim()
                "$artist $title".trim().takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }
}
