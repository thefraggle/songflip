package de.goork.songflip.core.engine

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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class InMemorySecureKeyValuePort : SecureKeyValuePort {
    val data = mutableMapOf<String, String>()
    override fun getString(key: String): String? = data[key]
    override fun putString(key: String, value: String) {
        data[key] = value
    }
    override fun remove(key: String) {
        data.remove(key)
    }
}

class ProEntitlementStoreTest {

    @Test
    fun testRevenueCatPrecedenceAnd15MinuteGraceWindow() {
        val storage = InMemorySecureKeyValuePort()
        var nowMs = 1_700_000_000_000L
        val store = ProEntitlementStore(
            storage = storage,
            couponService = CouponService(),
            installIdsProvider = { listOf("install-1") },
            rcAppUserIdProvider = { "rc_user_123" },
            clockMs = { nowMs }
        )

        val expMs = nowMs + 60_000L
        val activeState = store.evaluate(
            rcSnapshot = RevenueCatSnapshot(active = true, expirationMs = expMs, isLifetime = false),
            isExplicitRcCheck = true
        )
        assertTrue(activeState.isPro)
        assertEquals("revenuecat_subscription", activeState.proType)
        assertEquals(expMs, activeState.expirationDateMs)
        assertEquals("rc_user_123", store.resolveAuthToken(activeState))

        // Within 15-minute grace window after expiration: still hydrated as active
        nowMs = expMs + (10 * 60 * 1000L)
        val withinGrace = store.hydrateFromCache()
        assertTrue(withinGrace.isPro)
        assertEquals("revenuecat_subscription", withinGrace.proType)

        // Past 15-minute grace window: expired
        nowMs = expMs + (16 * 60 * 1000L)
        val pastGrace = store.hydrateFromCache()
        assertFalse(pastGrace.isPro)
        assertEquals("", store.resolveAuthToken(pastGrace).takeIf { it.startsWith("coupon:") } ?: "")
    }

    @Test
    fun testRedeemCouponSuccessLifetimeAndAlreadyActiveGuard() = runTest {
        val validLifetimeToken =
            "sct_v2_eyJjIjoiVklQLUxJRkUiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjowLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.DEBFd4HtxJ_0VFIFgAAcwXLb0f8p2a8TnNAegvy-NnLwFD18kvKkgo91bIC0BoQbKI9qUE06Ss_3K9RcXmk-BA"
        val storage = InMemorySecureKeyValuePort()
        val nowMs = 1_700_000_000_000L
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                      "status": "success",
                      "type": "lifetime",
                      "token": "$validLifetimeToken",
                      "issuedAtTimestamp": 1700000000000
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val couponService = CouponService(client = HttpClient(mockEngine))
        val store = ProEntitlementStore(
            storage = storage,
            couponService = couponService,
            installIdsProvider = { listOf("install-123") },
            rcAppUserIdProvider = { "rc_user_123" },
            clockMs = { nowMs }
        )

        val firstOutcome = store.redeemCoupon(
            rawCode = "FLIP-LIFE-2026",
            currentState = EntitlementState(),
            customEndpoints = listOf("https://mock.songflip.link/redeem")
        )
        assertEquals(CouponRedeemStatus.SUCCESS_LIFETIME, firstOutcome.status)
        assertEquals("lifetime", storage.getString(ProEntitlementStore.KEY_COUPON_TYPE))
        assertEquals("FLIP-LIFE-2026", storage.getString(ProEntitlementStore.KEY_COUPON_CODE))
        assertEquals(validLifetimeToken, storage.getString(ProEntitlementStore.KEY_COUPON_TOKEN))
        assertNull(storage.getString(ProEntitlementStore.KEY_COUPON_EXPIRATION))
        assertTrue(firstOutcome.newState.isPro)
        assertEquals("lifetime_coupon", firstOutcome.newState.proType)
        assertEquals("coupon:$validLifetimeToken", store.resolveAuthToken(firstOutcome.newState))

        // Already active lifetime coupon must short-circuit with ALREADY_ACTIVE
        val secondOutcome = store.redeemCoupon(
            rawCode = "FLIP-OTHER-2026",
            currentState = firstOutcome.newState,
            customEndpoints = listOf("https://mock.songflip.link/redeem")
        )
        assertEquals(CouponRedeemStatus.ALREADY_ACTIVE, secondOutcome.status)
    }

    @Test
    fun testAntiRollbackUpdatesLastSeenServerTimeMonotonically() {
        val storage = InMemorySecureKeyValuePort()
        val store = ProEntitlementStore(
            storage = storage,
            couponService = CouponService(),
            installIdsProvider = { listOf("install-1") },
            rcAppUserIdProvider = { "rc_user_123" },
            clockMs = { 1_700_000_000_000L }
        )

        store.recordServerTimestamp(1_700_000_050_000L)
        assertEquals(1_700_000_050_000L, store.getLastSeenServerTimeMs())

        // Older timestamp must never overwrite newer timestamp
        store.recordServerTimestamp(1_700_000_010_000L)
        assertEquals(1_700_000_050_000L, store.getLastSeenServerTimeMs())
    }

    @Test
    fun testHydrateFromCacheRejectsClockRollbackAndZeroExpiration() {
        val storage = InMemorySecureKeyValuePort()
        var nowMs = 1_700_000_000_000L
        val store = ProEntitlementStore(
            storage = storage,
            couponService = CouponService(),
            installIdsProvider = { listOf("install-1") },
            rcAppUserIdProvider = { "rc_user_123" },
            clockMs = { nowMs }
        )

        // Case A: Clock rollback after server timestamp moved past expiration + grace window
        val expMs = 1_700_000_060_000L
        store.evaluate(
            rcSnapshot = RevenueCatSnapshot(active = true, expirationMs = expMs, isLifetime = false),
            isExplicitRcCheck = true
        )
        store.recordServerTimestamp(1_700_001_500_000L) // > expMs + 15min grace
        nowMs = 1_700_000_000_000L // Device clock rolled back before expiration
        val rolledBackState = store.hydrateFromCache()
        assertFalse(rolledBackState.isPro, "Clock rollback must not reactivate expired RC cache")

        // Case B: Non-lifetime RC cache with missing/zero expiration must be treated as expired
        storage.putString(ProEntitlementStore.KEY_RC_IS_PRO, "true")
        storage.putString(ProEntitlementStore.KEY_RC_TYPE, "revenuecat_subscription")
        storage.putString(ProEntitlementStore.KEY_RC_EXPIRATION, "0")
        val zeroExpState = store.hydrateFromCache()
        assertFalse(zeroExpState.isPro, "Zero expiration on non-lifetime RC cache must be treated as expired")
    }
}
