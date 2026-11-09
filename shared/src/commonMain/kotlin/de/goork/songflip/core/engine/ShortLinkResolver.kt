package de.goork.songflip.core.engine

import de.goork.songflip.core.util.PlatformLock
import de.goork.songflip.core.util.UrlUtils
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.statement.request
import kotlinx.coroutines.CancellationException

/**
 * Resolves short links, tracking redirects, and canonical URLs across streaming services.
 */
class ShortLinkResolver(
    private val client: HttpClient,
    private val resolveShazam: suspend (String) -> String?
) {
    private val shortLinkResolutionCache = mutableMapOf<String, String>()
    private val shortLinkLock = PlatformLock()

    fun cacheResolvedShortLink(sourceUrl: String, resolvedUrl: String): String {
        shortLinkLock.withLock {
            if (shortLinkResolutionCache.size > 500) {
                shortLinkResolutionCache.clear()
            }
            shortLinkResolutionCache[sourceUrl] = resolvedUrl
        }
        return resolvedUrl
    }

    suspend fun resolveCanonicalUrl(url: String): String {
        val cached = shortLinkLock.withLock { shortLinkResolutionCache[url] }
        if (cached != null) return cached

        if (url.contains("shazam.com")) {
            val appleUrl = resolveShazam(url)
            if (appleUrl != null) return cacheResolvedShortLink(url, appleUrl)
        }

        var currentUrl = url
        for (hop in 0 until 5) {
            val normalized = UrlUtils.normalizeUrl(currentUrl)
            if (normalized != currentUrl && !UrlUtils.isShortLinkDomain(normalized)) {
                return cacheResolvedShortLink(url, normalized)
            }

            try {
                val resp = client.head(currentUrl) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                }
                val rawLocation = resp.headers["Location"]
                if (!rawLocation.isNullOrBlank()) {
                    val nextUrl = resolveRelativeUrl(currentUrl, rawLocation)
                    currentUrl = nextUrl
                    val nextNormalized = UrlUtils.normalizeUrl(nextUrl)
                    if (nextNormalized != nextUrl && !UrlUtils.isShortLinkDomain(nextNormalized)) {
                        return cacheResolvedShortLink(url, nextNormalized)
                    }
                    if (!UrlUtils.isShortLinkDomain(nextUrl)) {
                        return cacheResolvedShortLink(url, nextUrl)
                    }
                } else {
                    val getResp = client.get(currentUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                    val target = UrlUtils.normalizeUrl(getResp.request.url.toString())
                    return cacheResolvedShortLink(url, target)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                try {
                    val getResp = client.get(currentUrl) {
                        header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    }
                    val target = UrlUtils.normalizeUrl(getResp.request.url.toString())
                    return cacheResolvedShortLink(url, target)
                } catch (inner: Exception) {
                    if (inner is CancellationException) throw inner
                    val target = UrlUtils.normalizeUrl(currentUrl)
                    return cacheResolvedShortLink(url, target)
                }
            }
        }
        val target = UrlUtils.normalizeUrl(currentUrl)
        return cacheResolvedShortLink(url, target)
    }

    fun resolveRelativeUrl(base: String, location: String): String {
        return when {
            location.startsWith("http://") || location.startsWith("https://") -> location
            location.startsWith("//") -> "https:$location"
            location.startsWith("/") -> {
                val scheme = if (base.startsWith("http://")) "http://" else "https://"
                val domain = UrlUtils.extractDomain(base)
                "$scheme$domain$location"
            }
            else -> location
        }
    }
}
