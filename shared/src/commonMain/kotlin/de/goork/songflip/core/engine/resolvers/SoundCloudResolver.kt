package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class SoundCloudResolver(
    private val client: HttpClient
) : PlatformResolver {

    override val platform: MusicPlatform = MusicPlatform.SOUNDCLOUD

    override suspend fun resolveTrack(query: String, audiophileMode: Boolean): String? {
        return searchSoundCloud(query)
    }

    override suspend fun resolveAlbum(query: String, audiophileMode: Boolean): String? {
        val encoded = query.encodeURLParameter()
        return "https://soundcloud.com/search/sets?q=$encoded"
    }

    private suspend fun searchSoundCloud(query: String): String? {
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://soundcloud.com/search/sounds?q=$encoded") {
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
            }
            if (resp.status.isSuccess()) {
                val html = resp.bodyAsText()
                val noscriptMatch = Regex("<noscript>(.*?)</noscript>", RegexOption.DOT_MATCHES_ALL).find(html)
                val searchBody = noscriptMatch?.groupValues?.get(1) ?: html

                val invalidPrefixes = setOf(
                    "search", "pages", "terms", "popular", "upload", "charts", "imprint",
                    "you", "tags", "jobs", "mobile", "contact", "discover", "settings", "stream"
                )

                val linkRegex = Regex("""href="/([a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+)"""")
                val matches = linkRegex.findAll(searchBody)
                for (m in matches) {
                    val candidatePath = m.groupValues[1]
                    val prefix = candidatePath.substringBefore("/").lowercase()
                    if (!invalidPrefixes.contains(prefix)) {
                        return "https://soundcloud.com/$candidatePath"
                    }
                }
            }
            null
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        }
    }
}
