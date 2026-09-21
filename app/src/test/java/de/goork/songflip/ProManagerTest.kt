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

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        inMemoryPrefs = InMemorySharedPreferences()
        ProManager.setTestSharedPreferences(inMemoryPrefs)
        ProManager.testBaseUrl = server.url("").toString().removeSuffix("/")
    }

    @After
    fun teardown() {
        server.shutdown()
        ProManager.testBaseUrl = null
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
                .setBody("""{"status":"success","type":"lifetime","token":"sct_test_token"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-LIFETIME-VIP")
        assertEquals(RedeemResult.SUCCESS_LIFETIME, result)
        assertEquals("lifetime", inMemoryPrefs.getString("pro_coupon_type", null))
        assertEquals("FLIP-LIFETIME-VIP", inMemoryPrefs.getString("pro_coupon_code", null))
        assertTrue(ProManager.isPro)
    }

    @Test
    fun testValidAnnualCouponRedeemSuccess() = runBlocking {
        val exp = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000L
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"status":"success","type":"1year","expirationTimestamp":$exp,"token":"sct_annual"}""")
        )

        val result = ProManager.redeemCoupon("FLIP-ANNUAL-2026")
        assertEquals(RedeemResult.SUCCESS_1YEAR, result)
        assertEquals("1year", inMemoryPrefs.getString("pro_coupon_type", null))
        assertTrue(ProManager.isPro)
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
