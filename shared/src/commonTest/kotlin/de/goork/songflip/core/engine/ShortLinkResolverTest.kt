package de.goork.songflip.core.engine

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShortLinkResolverTest {

    @Test
    fun testSuccessfulShortLinkResolutionCachesResult() = runTest {
        val mockEngine = MockEngine { request ->
            if (request.url.toString().contains("spotify.link")) {
                respond(
                    content = "",
                    status = HttpStatusCode.Found,
                    headers = headersOf("Location", listOf("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT"))
                )
            } else {
                respond(
                    content = "OK",
                    status = HttpStatusCode.OK
                )
            }
        }
        val client = HttpClient(mockEngine) {
            followRedirects = false
        }
        val resolver = ShortLinkResolver(client) { null }

        val shortUrl = "https://spotify.link/AbCdEf"
        val resolved = resolver.resolveCanonicalUrl(shortUrl)

        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", resolved)
        assertEquals("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", resolver.getCachedUrl(shortUrl))
    }

    @Test
    fun testFailedResolutionDoesNotPoisonCache() = runTest {
        val mockEngine = MockEngine { _ ->
            throw RuntimeException("Network connection failed / offline")
        }
        val client = HttpClient(mockEngine)
        val resolver = ShortLinkResolver(client) { null }

        val shortUrl = "https://spotify.link/FailedNetwork123"
        val fallback = resolver.resolveCanonicalUrl(shortUrl)

        // Must normalize and return the URL, but must NOT poison cache (#D8)
        assertEquals("https://spotify.link/FailedNetwork123", fallback)
        assertNull(resolver.getCachedUrl(shortUrl), "Unresolved shortlink must not be stored in cache")
    }

    @Test
    fun testShortLinkToShortLinkLoopDoesNotCacheUnresolvedDomain() = runTest {
        val mockEngine = MockEngine { request ->
            // Simulates redirecting to another short domain without reaching a real target
            respond(
                content = "",
                status = HttpStatusCode.Found,
                headers = headersOf("Location", listOf("https://spoti.fi/AnotherShortLink"))
            )
        }
        val client = HttpClient(mockEngine) {
            followRedirects = false
        }
        val resolver = ShortLinkResolver(client) { null }

        val initial = "https://spotify.link/StartShortLink"
        val result = resolver.resolveCanonicalUrl(initial)

        assertEquals("https://spoti.fi/AnotherShortLink", result)
        assertNull(resolver.getCachedUrl(initial), "Shortlink destination should not be cached as final target")
    }
}
