package de.goork.songflip

import android.content.SharedPreferences
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.RedeemResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProManagerTest {

    private lateinit var server: MockWebServer
    private lateinit var inMemoryPrefs: InMemorySharedPreferences

    // Valid Ed25519 tokens signed with the test environment key for installId = "install-123"
    private val validLifetimeToken =
        "sct_v2_eyJjIjoiVklQLUxJRkUiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjowLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.DEBFd4HtxJ_0VFIFgAAcwXLb0f8p2a8TnNAegvy-NnLwFD18kvKkgo91bIC0BoQbKI9qUE06Ss_3K9RcXmk-BA"
    private val validAnnualToken =
        "sct_v2_eyJjIjoiQU5OVUFMMjAyNiIsImkiOiJpbnN0YWxsLTEyMyIsImUiOjIwMDAwMDAwMDAwMDAsInQiOiJ0aW1lX2xpbWl0ZWQiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.rrEMwtIUR1auVbgNq0iXGBnqbuII_rS8L7Hm_6J9vxgJcN19ceTk9wpqsdisDtHKhqJklGgyy22uuOXc1VocAw"

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        inMemoryPrefs = InMemorySharedPreferences()
        inMemoryPrefs.edit().putString("anonymous_install_id", "install-123").apply()
        ProManager.setTestSharedPreferences(inMemoryPrefs)
        ProManager.resetProForTesting()
        inMemoryPrefs.edit().putString("anonymous_install_id", "install-123").apply()
        ProManager.testBaseUrl = server.url("").toString().removeSuffix("/")
    }

    @After
    fun teardown() {
        server.shutdown()
        ProManager.testBaseUrl = null
        ProManager.setTestStableAndroidId(null)
    }

    // Same payload as validLifetimeToken, signed with the current v3 key (#141)
    private val v3LifetimeToken =
        "sct_v2_eyJjIjoiVklQLUxJRkUiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjowLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.OaDEAjjaNP2Yk_z4WebwEO19TUpn--ce1okHsp2-7RBRDzLEMCNj0BVpqDZppHbgY76zr4b0i9zp-llUbXaYDQ"

    private fun storeLifetimeCoupon(token: String) {
        inMemoryPrefs.edit()
            .putString("pro_coupon_token", token)
            .putString("pro_coupon_type", "lifetime")
            .putString("pro_coupon_code", "VIP-LIFE")
            .apply()
        ProManager.migrationLogger = { println(it) }
        ProManager.hydrateLocalProState()
    }

    @Test
    fun testCompatTokenIsMigratedToCurrentKeyAndStaysPro() = runBlocking {
        storeLifetimeCoupon(validLifetimeToken)
        assertTrue(ProManager.isPro)
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"status":"success","type":"lifetime","token":"$v3LifetimeToken","refreshed":true}""")
        )

        ProManager.migrateStoredCouponTokenIfNeeded()

        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("\"refreshToken\""))
        assertTrue("Pro must survive the migration", ProManager.isPro)
        assertTrue(ProManager.getAuthToken().endsWith(v3LifetimeToken))
    }

    @Test
    fun testMigrationKeepsTokenOnServerError() = runBlocking {
        storeLifetimeCoupon(validLifetimeToken)
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"VERIFICATION_UNAVAILABLE"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"VERIFICATION_UNAVAILABLE"}"""))

        ProManager.migrateStoredCouponTokenIfNeeded()

        assertTrue("A backend hiccup must never revoke Pro", ProManager.isPro)
        assertTrue(ProManager.getAuthToken().endsWith(validLifetimeToken))
    }

    @Test
    fun testMigrationRemovesTokenOnlyOnExplicitInvalidSignature() = runBlocking {
        storeLifetimeCoupon(validLifetimeToken)
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"INVALID_TOKEN"}"""))

        ProManager.migrateStoredCouponTokenIfNeeded()

        assertFalse(ProManager.isPro)
        assertNull(inMemoryPrefs.getString("pro_coupon_token", null))
    }

    @Test
    fun testCurrentKeyTokenSkipsMigrationWithoutNetwork() = runBlocking {
        storeLifetimeCoupon(v3LifetimeToken)
        assertTrue(ProManager.isPro)

        ProManager.migrateStoredCouponTokenIfNeeded()

        assertEquals(0, server.requestCount)
        assertTrue(ProManager.isPro)
    }

    @Test
    fun testEmptyCodeReturnsInvalidWithoutNetwork() = runBlocking {
        val result = ProManager.redeemCoupon("")
        assertEquals(RedeemResult.INVALID, result)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun testValidLifetimeCouponRedeemSuccess() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","type":"lifetime","token":"$validLifetimeToken"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-LIFETIME-VIP")
        assertEquals(RedeemResult.SUCCESS_LIFETIME, result)
        assertEquals("lifetime", inMemoryPrefs.getString("pro_coupon_type", null))
        assertEquals("FLIP-LIFETIME-VIP", inMemoryPrefs.getString("pro_coupon_code", null))
        assertTrue(ProManager.isPro)
    }

    @Test
    fun testValidAnnualCouponRedeemSuccess() = runBlocking {
        val exp = 2000000000000L
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","type":"1year","expirationTimestamp":$exp,"token":"$validAnnualToken"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-ANNUAL-2026")
        assertEquals(RedeemResult.SUCCESS_1YEAR, result)
        assertEquals("1year", inMemoryPrefs.getString("pro_coupon_type", null))
        assertTrue(ProManager.isPro)
    }

    @Test
    fun testTamperedLocalPrefsWithoutSignedTokenRejected() {
        // Simulate rooted user editing SharedPreferences to set pro_coupon_type = "lifetime" without a valid Ed25519 token
        inMemoryPrefs.edit()
            .putString("pro_coupon_type", "lifetime")
            .putString("pro_coupon_code", "HACKED")
            .putString("pro_coupon_token", "sct_fake_forged_token")
            .apply()

        val state = ProManager.hydrateLocalProState()
        assertFalse("Tampered SharedPreferences without valid Ed25519 signature must not grant Pro", state.isPro)
        assertFalse(ProManager.isPro)
    }

    @Test
    fun testForgedTokenInRedeemResponseRejected() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","type":"lifetime","token":"sct_forged"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-LIFETIME-VIP")
        assertEquals(RedeemResult.INVALID, result)
        assertFalse(ProManager.isPro)
    }

    @Test
    fun testAlreadyRedeemedOnDevice() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"ALREADY_REDEEMED_ON_DEVICE","message":"Already used"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-USED-CODE")
        assertEquals(RedeemResult.ALREADY_REDEEMED, result)
    }

    @Test
    fun testRateLimited429() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":"TOO_MANY_REQUESTS"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-SPAM")
        assertEquals(RedeemResult.RATE_LIMITED, result)
    }

    @Test
    fun testMaxRedemptionsReached() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":"MAX_REDEMPTIONS_REACHED"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-EXHAUSTED")
        assertEquals(RedeemResult.MAX_REACHED, result)
    }

    @Test
    fun testExtractCouponCode() {
        assertEquals("FLIP-PRO-2026", ProManager.extractCouponCode("https://songflip.link?promo=FLIP-PRO-2026"))
        assertEquals("BETALIST", ProManager.extractCouponCode("Hey, try code BETALIST now!"))
        assertEquals("MYCODE123", ProManager.extractCouponCode("  my code 123  "))
    }

    @Test
    fun testOfflineRevenueCatProResilience() {
        // Pre-populate SharedPreferences with cached active Pro state
        inMemoryPrefs.edit()
            .putBoolean("rc_is_pro", true)
            .putString("rc_pro_type", "revenuecat_subscription")
            .putLong("rc_pro_expiration", System.currentTimeMillis() + 86400000L)
            .apply()

        // Fast hydration without network should immediately restore isPro
        val state = ProManager.hydrateLocalProState()

        assertTrue("Pro state should be restored from offline local cache", state.isPro)
        assertTrue("ProManager.isPro should be true", ProManager.isPro)
        assertEquals("revenuecat_subscription", state.proType)
    }

    @Test
    fun testGetAuthTokenPrioritizesRevenueCatAndIgnoresExpiredCoupon() {
        // Store an expired/invalid coupon token alongside active RevenueCat Pro (#133)
        inMemoryPrefs.edit()
            .putString("pro_coupon_token", "sct_expired_or_invalid_token")
            .putBoolean("rc_is_pro", true)
            .putString("rc_pro_type", "revenuecat_subscription")
            .putLong("rc_pro_expiration", System.currentTimeMillis() + 86400000L)
            .apply()

        ProManager.hydrateLocalProState()
        assertTrue(ProManager.isPro)
        val token = ProManager.getAuthToken()
        assertFalse("Must not return stale/invalid coupon token when RevenueCat is active", token.startsWith("coupon:"))
    }

    @Test
    fun testColdStartRenewalGraceWindowPreservesProState() {
        // Subscription expired 2 minutes ago (e.g. Play Store 5-min sandbox cycle or billing renewal in flight)
        inMemoryPrefs.edit()
            .putBoolean("rc_is_pro", true)
            .putString("rc_pro_type", "revenuecat_subscription")
            .putLong("rc_pro_expiration", System.currentTimeMillis() - 2 * 60 * 1000L)
            .apply()

        val state = ProManager.hydrateLocalProState()
        assertTrue("Pro state must be preserved within 15-min renewal grace window", state.isPro)
    }

    @Test
    fun testLegacyUuidBoundCouponSurvivesAndroidIdMigration() {
        // Simulate a user who redeemed a Lifetime coupon before v1.8.18 (Oct 14):
        // - Their SharedPreferences has `anonymous_install_id` = "1d34e71b-68e8-4ce5-b96a-8f55a1edc823" (UUID)
        // - Their stored legacy v1 HMAC token is bound to that UUID
        // - After updating to v1.8.18+, `stableAndroidId` is populated with their hardware ANDROID_ID
        val legacyUuid = "1d34e71b-68e8-4ce5-b96a-8f55a1edc823"
        val legacyHmacLifetimeToken =
            "sct_eyJjIjoiRkxJUC1DTEFNLVJLRTciLCJpIjoiMWQzNGU3MWItNjhlOC00Y2U1LWI5NmEtOGY1NWExZWRjODIzIiwiZSI6bnVsbCwidCI6ImxpZmV0aW1lIn0.wWClxkXxEY0GZwzQ2jqRyJu7ItyzhB7OricXu0s2bJA"

        inMemoryPrefs.edit()
            .putString("anonymous_install_id", legacyUuid)
            .putString("pro_coupon_type", "lifetime")
            .putString("pro_coupon_code", "FLIP-CLAM-RKE7")
            .putString("pro_coupon_token", legacyHmacLifetimeToken)
            .apply()

        ProManager.setTestStableAndroidId("a1b2c3d4e5f60718")

        val state = ProManager.hydrateLocalProState()
        assertTrue("Legacy UUID-bound coupon must remain valid after ANDROID_ID migration", state.isPro)
        assertEquals("lifetime_coupon", state.proType)
        assertEquals("coupon:$legacyHmacLifetimeToken", ProManager.getAuthToken())
    }
}

class InMemorySharedPreferences : SharedPreferences {
    private val data = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = data
    override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
    override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
    override fun contains(key: String): Boolean = data.containsKey(key)
    override fun edit(): SharedPreferences.Editor = EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    inner class EditorImpl : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor { temp[key] = value; removed.remove(key); return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { temp[key] = values; removed.remove(key); return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { temp[key] = value; removed.remove(key); return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { temp[key] = value; removed.remove(key); return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { temp[key] = value; removed.remove(key); return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { temp[key] = value; removed.remove(key); return this }
        override fun remove(key: String): SharedPreferences.Editor { removed.add(key); temp.remove(key); return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            for (k in removed) data.remove(k)
            for ((k, v) in temp) {
                if (v != null) data[k] = v else data.remove(k)
            }
        }
    }
}
