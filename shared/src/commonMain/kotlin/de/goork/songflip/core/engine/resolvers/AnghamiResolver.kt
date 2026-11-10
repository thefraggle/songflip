package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class AnghamiResolver(
    private val client: HttpClient
) {

    /**
     * Extracts title and artist from an incoming Anghami song or album URL.
     */
    suspend fun extractMetadata(url: String): String? {
        return try {
            val resp = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9,ar;q=0.8")
            }

            if (resp.status.isSuccess()) {
                val html = resp.bodyAsText()
                val ogTitleMatch = Regex("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+property=[\"']og:title[\"']", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)

                if (ogTitleMatch != null) {
                    val raw = ogTitleMatch.groupValues[1]
                        .replace("&amp;", "&")
                        .replace("&#39;", "'")
                        .replace("&quot;", "\"")

                    val cleaned = raw.substringBefore(" on Anghami")
                        .substringBefore(" | Anghami")
                        .substringBefore(" - Anghami")
                        .trim()

                    if (cleaned.isNotBlank() && cleaned.lowercase() != "anghami") {
                        return cleaned
                    }
                }
            }

            // Fallback: Check if URL contains an informative slug
            val slugMatch = Regex("anghami\\.com/(?:song|album|playlist)/([a-zA-Z0-9_-]+)").find(url)
            if (slugMatch != null) {
                val slug = slugMatch.groupValues[1].replace("-", " ").replace("_", " ").trim()
                if (slug.isNotBlank() && !slug.all { it.isDigit() }) {
                    return slug
                }
            }

            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val slugMatch = Regex("anghami\\.com/(?:song|album|playlist)/([a-zA-Z0-9_-]+)").find(url)
            if (slugMatch != null) {
                val slug = slugMatch.groupValues[1].replace("-", " ").replace("_", " ").trim()
                if (slug.isNotBlank() && !slug.all { it.isDigit() }) {
                    return slug
                }
            }
            null
        }
    }
}
