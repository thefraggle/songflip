package de.goork.songflip

import de.goork.songflip.data.AppCheckHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckHelperTest {

    @Test
    fun `isConfigured requires all three Firebase identifiers to be non-blank`() {
        assertFalse(AppCheckHelper.isConfigured("", "", ""))
        assertFalse(AppCheckHelper.isConfigured("songflip-web", "", "key"))
        assertFalse(AppCheckHelper.isConfigured("songflip-web", "1:123:android:abc", "   "))
        assertTrue(AppCheckHelper.isConfigured("songflip-web", "1:123:android:abc", "AIzaTestKey"))
    }

    @Test
    fun `buildShazamTokenRequest attaches X-Firebase-AppCheck only when token is non-blank`() {
        val withToken = AppCheckHelper.buildShazamTokenRequest(
            endpointUrl = "https://example.invalid/getShazamToken",
            appVersion = "1.15.9",
            appCheckToken = "appcheck-jwt-123"
        )
        assertEquals("songflip-app", withToken.header("x-web-client"))
        assertEquals("1.15.9", withToken.header("x-app-version"))
        assertEquals("android", withToken.header("x-client-platform"))
        assertEquals("appcheck-jwt-123", withToken.header("X-Firebase-AppCheck"))

        val withoutToken = AppCheckHelper.buildShazamTokenRequest(
            endpointUrl = "https://example.invalid/getShazamToken",
            appVersion = "1.15.9",
            appCheckToken = null
        )
        assertEquals("songflip-app", withoutToken.header("x-web-client"))
        assertNull(withoutToken.header("X-Firebase-AppCheck"))

        val withBlankToken = AppCheckHelper.buildShazamTokenRequest(
            endpointUrl = "https://example.invalid/getShazamToken",
            appVersion = "1.15.9",
            appCheckToken = "   "
        )
        assertNull(withBlankToken.header("X-Firebase-AppCheck"))
    }
}
