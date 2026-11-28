package de.goork.songflip.core.engine.resolvers

import de.goork.songflip.core.engine.resilience.PlatformCircuitBreaker
import de.goork.songflip.core.model.MusicPlatform
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

class SoundCloudResolver(
    private val client: HttpClient,
    private val circuitBreaker: PlatformCircuitBreaker = PlatformCircuitBreaker.shared
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
        val host = "soundcloud.com"
        if (!circuitBreaker.canExecute(host)) return null
        return try {
            val encoded = query.encodeURLParameter()
            val resp = client.get("https://soundcloud.com/search/sounds?q=$encoded") {
                header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header("Accept-Language", "en-US,en;q=0.9")
            }
            if (resp.status.value in setOf(403, 406, 429)) {
                circuitBreaker.recordRateLimit(host, retryAfterSeconds = 900L)
                return null
            }
            if (resp.status.isSuccess()) {
                circuitBreaker.recordSuccess(host)
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
                        val trackSlug = candidatePath.substringAfter("/")
                        if (isSlugMatch(query, prefix, trackSlug)) {
                            return "https://soundcloud.com/$candidatePath"
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            de.goork.songflip.core.util.SongFlipLogger.d("SoundCloudResolver", "Search scrape failed: ${e.message}")
            null
        }
    }

    private fun isSlugMatch(query: String, uploaderSlug: String, trackSlug: String): Boolean {
        val uploaderTokens = ResolverUtils.normalizeTokens(uploaderSlug.replace("-", " ").replace("_", " "))
        val trackTokens = ResolverUtils.normalizeTokens(trackSlug.replace("-", " ").replace("_", " "))
        if (trackTokens.isEmpty()) return false
        val allTokens = (uploaderTokens + trackTokens).toSet()

        fun tokensMatch(a: String, b: String): Boolean =
            a == b || (a.length >= 4 && (b.startsWith(a) || a.startsWith(b)))

        fun countMatches(needles: List<String>, haystack: Collection<String>): Int =
            needles.count { n -> haystack.any { h -> tokensMatch(n, h) } }

        fun requiredCount(total: Int): Int =
            if (total <= 3) total else kotlin.math.ceil(total * 0.75).toInt()

        val separatorMatch = Regex("""\s+[-–—]\s+""").find(query)
        if (separatorMatch != null) {
            val artistPart = query.substring(0, separatorMatch.range.first)
            val titlePart = query.substring(separatorMatch.range.last + 1)
            val artistTokens = ResolverUtils.normalizeTokens(artistPart)
            val titleTokens = ResolverUtils.normalizeTokens(titlePart)

            if (titleTokens.isNotEmpty() && countMatches(titleTokens, trackTokens) < requiredCount(titleTokens.size)) {
                return false
            }
            if (artistTokens.isNotEmpty() && countMatches(artistTokens, allTokens) == 0) {
                return false
            }
            return titleTokens.isNotEmpty() || artistTokens.isNotEmpty()
        }

        val queryTokens = ResolverUtils.normalizeTokens(query)
        if (queryTokens.isEmpty()) return true
        if (countMatches(queryTokens, allTokens) < requiredCount(queryTokens.size)) {
            return false
        }

        val nonUploaderTokens = queryTokens.filter { qTok -> uploaderTokens.none { uTok -> tokensMatch(qTok, uTok) } }
        val targetTokens = nonUploaderTokens.ifEmpty { queryTokens }
        return countMatches(targetTokens, trackTokens) >= requiredCount(targetTokens.size)
    }
}
