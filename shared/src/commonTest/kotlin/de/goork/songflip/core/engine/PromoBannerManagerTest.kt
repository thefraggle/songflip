package de.goork.songflip.core.engine

import de.goork.songflip.core.model.PromoBannerConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PromoBannerManagerTest {

    @BeforeTest
    fun setup() {
        PromoBannerManager.setConfigForTesting(PromoBannerConfig())
        PromoBannerManager.resetDismissed()
    }

    @Test
    fun testPromoBannerLocalization() {
        val config = PromoBannerConfig(
            enabled = true,
            endTimestampMs = 1727827199000L,
            badge = mapOf("de" to "50% RABATT", "en" to "50% OFF", "fr" to "50% DE RÉDUCTION"),
            title = mapOf("de" to "Weltmusiktag Flash Sale", "en" to "World Music Day Flash Sale"),
            subtitle = mapOf("de" to "Lifetime zum halben Preis", "en" to "Lifetime at half price"),
            buttonText = mapOf("de" to "Angebot ansehen", "en" to "View Offer")
        )

        // Exact match
        assertEquals("50% RABATT", config.getLocalizedBadge("de"))
        assertEquals("Weltmusiktag Flash Sale", config.getLocalizedTitle("de-DE"))
        assertEquals("50% DE RÉDUCTION", config.getLocalizedBadge("fr-FR"))

        // Fallback to English for missing language
        assertEquals("50% OFF", config.getLocalizedBadge("ko-KR"))
        assertEquals("World Music Day Flash Sale", config.getLocalizedTitle("ja"))
        assertEquals("Lifetime at half price", config.getLocalizedSubtitle("es"))
        assertEquals("View Offer", config.getLocalizedButtonText("it"))
    }

    @Test
    fun testPromoBannerValidationAndExpiration() {
        val validConfig = PromoBannerConfig(
            enabled = true,
            endTimestampMs = 2000L
        )

        // Before expiry
        assertTrue(validConfig.isValid(1000L))
        // At or after expiry
        assertFalse(validConfig.isValid(2000L))
        assertFalse(validConfig.isValid(3000L))

        // Disabled config
        val disabledConfig = PromoBannerConfig(enabled = false, endTimestampMs = 2000L)
        assertFalse(disabledConfig.isValid(1000L))

        // Unlimited duration (endTimestampMs = 0)
        val unlimitedConfig = PromoBannerConfig(enabled = true, endTimestampMs = 0L)
        assertTrue(unlimitedConfig.isValid(99999999999L))
    }

    @Test
    fun testIsBannerVisibleRules() {
        val config = PromoBannerConfig(
            enabled = true,
            endTimestampMs = 5000L,
            title = mapOf("en" to "Sale")
        )
        PromoBannerManager.setConfigForTesting(config)

        // Visible for free user before expiry
        assertTrue(PromoBannerManager.isBannerVisible(isPro = false, currentTimeMs = 1000L))

        // Strictly hidden for PRO users
        assertFalse(PromoBannerManager.isBannerVisible(isPro = true, currentTimeMs = 1000L))

        // Hidden when expired
        assertFalse(PromoBannerManager.isBannerVisible(isPro = false, currentTimeMs = 6000L))

        // Hidden when user dismisses
        PromoBannerManager.dismiss()
        assertFalse(PromoBannerManager.isBannerVisible(isPro = false, currentTimeMs = 1000L))

        // Visible again after reset
        PromoBannerManager.resetDismissed()
        assertTrue(PromoBannerManager.isBannerVisible(isPro = false, currentTimeMs = 1000L))
    }

    @Test
    fun testParsePromoBannerJson() {
        val sampleJson = """
            {
              "enabled": true,
              "end_timestamp": 1727827199000,
              "badge": {
                "de": "50% RABATT",
                "en": "50% OFF"
              },
              "title": {
                "de": "Weltmusiktag Flash Sale",
                "en": "World Music Day Flash Sale"
              },
              "subtitle": {
                "de": "SongFlip Lifetime zum halben Preis",
                "en": "SongFlip Lifetime at half price"
              },
              "button_text": {
                "de": "Angebot ansehen",
                "en": "View Offer"
              }
            }
        """.trimIndent()

        val parsed = PromoBannerManager.parsePromoBannerJson(sampleJson)
        assertNotNull(parsed)
        assertTrue(parsed.enabled)
        assertEquals(1727827199000L, parsed.endTimestampMs)
        assertEquals("50% RABATT", parsed.getLocalizedBadge("de"))
        assertEquals("World Music Day Flash Sale", parsed.getLocalizedTitle("en"))
        assertEquals("SongFlip Lifetime at half price", parsed.getLocalizedSubtitle("en"))
        assertEquals("Angebot ansehen", parsed.getLocalizedButtonText("de"))
    }

    @Test
    fun testFetchPromoBannerWithMockEngine() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = """{"enabled": true, "end_timestamp": 9999999, "title": {"en": "Promo"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        PromoBannerManager.setHttpClientForTesting(HttpClient(mockEngine))
        val result = PromoBannerManager.fetchPromoBannerDirect("https://mock.songflip.link/api/promo-banner", currentTimeMs = 100L, forceRefresh = true)

        // Verify config updated
        assertNotNull(result)
        assertEquals(true, result.enabled)
        assertEquals(true, PromoBannerManager.config.value.enabled)
        assertEquals("Promo", PromoBannerManager.config.value.getLocalizedTitle("en"))
    }
}
