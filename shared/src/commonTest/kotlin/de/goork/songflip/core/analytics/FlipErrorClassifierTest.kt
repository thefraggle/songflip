package de.goork.songflip.core.analytics

import de.goork.songflip.core.model.ResolutionResult
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Telemetry correctness for issue #59 (is_search always false, obfuscated "f0" error reasons). */
class FlipErrorClassifierTest {

    @Test
    fun knownExceptionsMapToStableNames() {
        assertEquals("timeout", FlipErrorClassifier.classify(HttpRequestTimeoutException("https://x.test", 1000L)))
        assertEquals("parse_error", FlipErrorClassifier.classify(SerializationException("bad json")))
        assertEquals("io_error", FlipErrorClassifier.classify(IOException("reset")))
    }

    @Test
    fun unknownExceptionsNeverLeakClassNames() {
        // In release builds the class name would be R8-obfuscated, so it must not be reported.
        class InternalFailure : RuntimeException("boom")
        assertEquals(FlipErrorClassifier.UNKNOWN, FlipErrorClassifier.classify(InternalFailure()))
        assertEquals(FlipErrorClassifier.UNKNOWN, FlipErrorClassifier.classify(IllegalStateException()))
    }

    @Test
    fun detailStripsUrlsCollapsesWhitespaceAndCapsLength() {
        val detail = FlipErrorClassifier.detail(
            RuntimeException("Failed   for https://open.spotify.com/track/abc?si=private\nretry")
        )
        assertEquals("Failed for <url> retry", detail)

        val long = FlipErrorClassifier.detail(RuntimeException("x".repeat(200)))
        assertEquals(64, long?.length)
    }

    @Test
    fun detailIsNullWithoutMessage() {
        assertNull(FlipErrorClassifier.detail(RuntimeException()))
        assertNull(FlipErrorClassifier.detail(RuntimeException("   ")))
    }

    @Test
    fun searchFallbackIsDetectedFromPlatformSuffixOrSearchUrl() {
        val direct = ResolutionResult.Success(targetUrl = "https://open.spotify.com/track/123", platform = "spotify")
        assertFalse(direct.isSearchFallback)

        val suffix = ResolutionResult.Success(targetUrl = "https://music.youtube.com/watch?v=1", platform = "youtubeMusic_fallback")
        assertTrue(suffix.isSearchFallback)

        val searchUrl = ResolutionResult.Success(targetUrl = "https://music.youtube.com/search?q=artist+title", platform = "youtubeMusic")
        assertTrue(searchUrl.isSearchFallback)
    }
}
