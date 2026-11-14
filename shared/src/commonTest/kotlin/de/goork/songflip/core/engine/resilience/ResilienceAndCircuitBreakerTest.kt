package de.goork.songflip.core.engine.resilience

import de.goork.songflip.core.engine.resolvers.DeezerResolver
import de.goork.songflip.core.engine.resolvers.TidalResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResilienceAndCircuitBreakerTest {

    @Test
    fun testPlatformCircuitBreakerLifecycle() {
        var currentTime = 1000000L
        val cb = PlatformCircuitBreaker(clock = { currentTime }, defaultCooldownMs = 300_000L)

        // 1. Initial state: host is allowed
        assertTrue(cb.canExecute("api.deezer.com"))
        assertFalse(cb.isHostOpen("api.deezer.com"))
        assertEquals(0L, cb.getCooldownRemainingSeconds("api.deezer.com"))

        // 2. Record rate limit (e.g. 60 seconds)
        cb.recordRateLimit("api.deezer.com", retryAfterSeconds = 60L)
        assertFalse(cb.canExecute("api.deezer.com"))
        assertTrue(cb.isHostOpen("api.deezer.com"))
        assertEquals(60L, cb.getCooldownRemainingSeconds("api.deezer.com"))

        // 3. Advance time by 30 seconds -> still open
        currentTime += 30_000L
        assertFalse(cb.canExecute("api.deezer.com"))
        assertEquals(30L, cb.getCooldownRemainingSeconds("api.deezer.com"))

        // 4. Advance time past cooldown (e.g. +31 seconds -> total 61s elapsed) -> half-open probe allowed
        currentTime += 31_000L
        assertTrue(cb.canExecute("api.deezer.com"))

        // 5. Successful response closes the circuit
        cb.recordSuccess("api.deezer.com")
        assertTrue(cb.canExecute("api.deezer.com"))
        assertFalse(cb.isHostOpen("api.deezer.com"))
    }

    @Test
    fun testDynamicTokenRegistry() {
        DynamicTokenRegistry.reset()
        assertEquals("CzET4vdadNUFQ5JU", DynamicTokenRegistry.getTidalToken())
        assertEquals("712109809", DynamicTokenRegistry.getQobuzAppId())
        assertTrue(DynamicTokenRegistry.DEFAULT_QOBUZ_APP_ID.isNotBlank(), "DEFAULT_QOBUZ_APP_ID must never be blank (causes Qobuz HTTP 500)")
        assertTrue(DynamicTokenRegistry.DEFAULT_TIDAL_TOKEN.isNotBlank(), "DEFAULT_TIDAL_TOKEN must never be blank (causes Tidal HTTP 400/401)")

        DynamicTokenRegistry.setToken(DynamicTokenRegistry.KEY_TIDAL_TOKEN, "new-tidal-token-123")
        DynamicTokenRegistry.setToken(DynamicTokenRegistry.KEY_QOBUZ_APP_ID, "new-qobuz-app-456")
        assertEquals("new-tidal-token-123", DynamicTokenRegistry.getTidalToken())
        assertEquals("new-qobuz-app-456", DynamicTokenRegistry.getQobuzAppId())

        DynamicTokenRegistry.reset()
        assertEquals("CzET4vdadNUFQ5JU", DynamicTokenRegistry.getTidalToken())
        assertEquals("712109809", DynamicTokenRegistry.getQobuzAppId())
    }

    @Test
    fun testDeezerResolverAbortsVariantCascadeOnQuotaError() = runTest {
        var callCount = 0
        val cb = PlatformCircuitBreaker(clock = { 1000000L }, defaultCooldownMs = 300_000L)
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = """{"error":{"type":"OAuthException","message":"Quota limit exceeded","code":4}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val resolver = DeezerResolver(client = HttpClient(mockEngine), circuitBreaker = cb)

        val outcome = resolver.resolveTrackDetailed("Daft Punk - Get Lucky (Radio Edit) feat. Pharrell Williams")
        assertTrue(outcome.isRateLimited)
        assertTrue(outcome is ResolutionOutcome.RateLimited)
        // CRITICAL INVARIANT (#198): Only 1 network request must be dispatched.
        // Stripped, noParens, and collaboration query variations must NOT be attempted!
        assertEquals(1, callCount)
        assertTrue(cb.isHostOpen("api.deezer.com"))
    }

    @Test
    fun testDeezerResolverAbortsVariantCascadeOnHttp429() = runTest {
        var callCount = 0
        val cb = PlatformCircuitBreaker(clock = { 1000000L }, defaultCooldownMs = 300_000L)
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = "Too Many Requests",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf("Retry-After", "120")
            )
        }
        val resolver = DeezerResolver(client = HttpClient(mockEngine), circuitBreaker = cb)

        val outcome = resolver.resolveTrackDetailed("Queen - Bohemian Rhapsody (2011 Remaster)")
        assertTrue(outcome.isRateLimited)
        assertEquals(1, callCount)
        assertTrue(cb.isHostOpen("api.deezer.com"))
        assertEquals(120L, cb.getCooldownRemainingSeconds("api.deezer.com"))
    }

    @Test
    fun testTidalResolverAbortsVariantCascadeOnHttp429() = runTest {
        var callCount = 0
        val cb = PlatformCircuitBreaker(clock = { 1000000L }, defaultCooldownMs = 300_000L)
        val mockEngine = MockEngine { request ->
            callCount++
            respond(
                content = "Too Many Requests",
                status = HttpStatusCode.TooManyRequests,
                headers = headersOf("Retry-After", "90")
            )
        }
        val resolver = TidalResolver(client = HttpClient(mockEngine), circuitBreaker = cb)

        val outcome = resolver.resolveTrackDetailed("Radiohead - Creep (Remastered)")
        assertTrue(outcome.isRateLimited)
        // Both api.tidal.com and listen.tidal.com get tried once on the primary variant, but
        // variant 2 (stripped), variant 3 (noParens), variant 4 (collaboration) MUST NOT run!
        assertTrue(callCount <= 2, "Expected at most 2 calls across baseUrls, got $callCount")
    }
}
