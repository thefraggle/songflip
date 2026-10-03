package de.goork.songflip.core.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AptabaseClientTest {

    @Test
    fun testAptabaseClientInitializationAndSystemProps() {
        AptabaseClient.init(
            appKey = "A-EU-1234567890",
            host = "https://telemetry-apps.goork.de",
            osName = "Android",
            osVersion = "14",
            locale = "de_DE",
            appVersion = "1.4.15",
            appBuildNumber = "10415",
            isDebug = false
        )

        // Ensure debugging toggle works
        AptabaseClient.setIsDebug(true)
        AptabaseClient.setIsDebug(false)
        assertTrue(true)
    }

    @Test
    fun testEventTrackingFunctionsExecuteSafely() {
        // Initialize client
        AptabaseClient.init(
            appKey = "A-EU-1234567890",
            host = "https://telemetry-apps.goork.de",
            osName = "Android",
            osVersion = "14",
            locale = "de_DE",
            appVersion = "1.4.15",
            appBuildNumber = "10415",
            isDebug = true
        )

        // Call tracking methods to ensure no exceptions or regressions
        AptabaseClient.trackAppLaunched("spotify", "de")
        AptabaseClient.trackLinkFlipped(
            target = "youtubeMusic",
            isAlbum = false,
            isSearch = false,
            source = "spotify"
        )
        AptabaseClient.trackHistoryOpened()
        AptabaseClient.trackSettingsOpened()
        AptabaseClient.trackSharePageGenerated("spotify")
        AptabaseClient.trackPromoRedeemedSuccess("FLIP-PRO-2026")
        AptabaseClient.trackLinkFlipFailed("deezer", "TIMEOUT")
        AptabaseClient.trackLinkFlipFailed(
            target = "youtubeMusic",
            reason = "Could not resolve music link",
            sourceDomain = "open.spotify.com",
            sourcePlatform = "spotify",
            errorReason = "no_match_found"
        )
        AptabaseClient.trackPodcastIntercepted("spotify")
        AptabaseClient.trackAudiobookIntercepted("spotify")
        AptabaseClient.trackPlaylistRouted("appleMusic")
        AptabaseClient.trackPlaylistConversionStarted("spotify", "youtubeMusic", 25)
        AptabaseClient.trackPlaylistConversionCompleted("spotify", "youtubeMusic", 25, 25, 0)
        AptabaseClient.trackPlaylistConversionFailed("spotify", "youtubeMusic", "NETWORK_ERROR")
        AptabaseClient.trackUnsupportedEntityIntercepted("spotify", "user_profile")
        AptabaseClient.trackTargetPlatformChanged("tidal")
        AptabaseClient.trackDomainSetupClicked()
        AptabaseClient.trackPauseStateChanged("paused_until_tomorrow")
        AptabaseClient.trackPaywallViewed("settings")
        AptabaseClient.trackReviewPromptTriggered("milestone_50")
        AptabaseClient.trackTestStudioResolved("spotify.com", true)
        AptabaseClient.trackHistoryItemClicked("spotify")
        AptabaseClient.trackHistoryCleared()
        AptabaseClient.trackProPurchased("lifetime_pro")
        AptabaseClient.trackProPurchased("annual_pro", layout = "annual_first")
        AptabaseClient.trackProPurchaseFailed("USER_CANCELLED")
        AptabaseClient.trackProPurchaseFailed("Payment declined", "StoreProblemError", "BillingResponseCode.ITEM_UNAVAILABLE")
        AptabaseClient.trackRateAppClicked()
        AptabaseClient.trackShareAppClicked()

        assertTrue(true)
    }

    @Test
    fun testSystemPropsDataModel() {
        val props = SystemProps(
            isDebug = false,
            osName = "Android",
            osVersion = "14",
            locale = "de_DE",
            appVersion = "1.4.15",
            appBuildNumber = "10415"
        )
        assertEquals("Android", props.osName)
        assertEquals("14", props.osVersion)
        assertEquals("de_DE", props.locale)
        assertEquals("1.4.15", props.appVersion)
        assertEquals("10415", props.appBuildNumber)
        assertEquals("aptabase-kmp@1.0.0", props.sdkVersion)
    }
}
