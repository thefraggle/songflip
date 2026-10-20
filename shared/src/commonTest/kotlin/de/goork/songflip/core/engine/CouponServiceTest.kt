package de.goork.songflip.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouponServiceTest {

    // Generated with Node.js crypto.sign(null, payload, testEd25519Key)
    // Payload: {"c":"VIP-LIFE","i":"install-123","e":0,"t":"lifetime","iat":1700000000000}
    private val validLifetimeToken =
        "sct_v2_eyJjIjoiVklQLUxJRkUiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjowLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.DEBFd4HtxJ_0VFIFgAAcwXLb0f8p2a8TnNAegvy-NnLwFD18kvKkgo91bIC0BoQbKI9qUE06Ss_3K9RcXmk-BA"

    // Payload: {"c":"VIP-NULL","i":"install-123","e":null,"t":"lifetime","iat":1700000000000}
    private val validLifetimeNullExpToken =
        "sct_v2_eyJjIjoiVklQLU5VTEwiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjpudWxsLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.gKcOxd6Bc7dmJKXZ6r1i_526B41v9cGqmmmCaXIsJXXlzhwhVK2eCpyG8ywTwwS4Lg8FtbLYz9RKeDMpiqDYAg"

    // Payload: {"c":"ANNUAL2026","i":"install-123","e":2000000000000,"t":"time_limited","iat":1700000000000}
    private val validAnnualToken =
        "sct_v2_eyJjIjoiQU5OVUFMMjAyNiIsImkiOiJpbnN0YWxsLTEyMyIsImUiOjIwMDAwMDAwMDAwMDAsInQiOiJ0aW1lX2xpbWl0ZWQiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.rrEMwtIUR1auVbgNq0iXGBnqbuII_rS8L7Hm_6J9vxgJcN19ceTk9wpqsdisDtHKhqJklGgyy22uuOXc1VocAw"

    @Test
    fun verifyValidEd25519LifetimeToken() {
        val res = CouponService.shared.verifyCouponToken(
            token = validLifetimeToken,
            expectedInstallId = "install-123",
            currentTimeMs = 1750000000000L
        )
        assertTrue(res.valid, "Valid Ed25519 lifetime token must verify")
        assertEquals("VIP-LIFE", res.code)
        assertEquals("install-123", res.installId)
        assertEquals("lifetime", res.type)
        assertEquals(null, res.expirationTimestamp)
        assertEquals(1700000000000L, res.issuedAtTimestamp)
        assertFalse(res.isLegacyToken)
    }

    @Test
    fun verifyValidEd25519LifetimeTokenWithJsonNullExp() {
        val res = CouponService.shared.verifyCouponToken(
            token = validLifetimeNullExpToken,
            expectedInstallId = "install-123",
            currentTimeMs = 1750000000000L
        )
        assertTrue(res.valid, "Ed25519 lifetime token with e: null must verify without throwing JsonDecodingException")
        assertEquals("VIP-NULL", res.code)
        assertEquals("install-123", res.installId)
        assertEquals("lifetime", res.type)
        assertEquals(null, res.expirationTimestamp)
        assertEquals(1700000000000L, res.issuedAtTimestamp)
        assertFalse(res.isLegacyToken)
    }

    @Test
    fun verifyValidEd25519AnnualToken() {
        val res = CouponService.shared.verifyCouponToken(
            token = validAnnualToken,
            expectedInstallId = "install-123",
            currentTimeMs = 1800000000000L
        )
        assertTrue(res.valid, "Valid Ed25519 time-limited token must verify before expiry")
        assertEquals("ANNUAL2026", res.code)
        assertEquals("time_limited", res.type)
        assertEquals(2000000000000L, res.expirationTimestamp)
    }

    @Test
    fun rejectTamperedSignatureOrPayload() {
        // Flip last character of signature
        val tamperedSig = validLifetimeToken.dropLast(2) + "AA"
        val res1 = CouponService.shared.verifyCouponToken(
            token = tamperedSig,
            expectedInstallId = "install-123"
        )
        assertFalse(res1.valid, "Tampered Ed25519 signature must be rejected")

        // Use annual payload with lifetime signature
        val annualPayload = validAnnualToken.substringAfter("sct_v2_").substringBefore(".")
        val lifetimeSig = validLifetimeToken.substringAfter(".")
        val forgedToken = "sct_v2_$annualPayload.$lifetimeSig"
        val res2 = CouponService.shared.verifyCouponToken(
            token = forgedToken,
            expectedInstallId = "install-123"
        )
        assertFalse(res2.valid, "Mismatched payload and signature must be rejected")
    }

    @Test
    fun rejectDeviceInstallIdMismatch() {
        val res = CouponService.shared.verifyCouponToken(
            token = validLifetimeToken,
            expectedInstallId = "another-device-id"
        )
        assertFalse(res.valid, "Token bound to a different installId must be rejected")
    }

    @Test
    fun rejectClockRollbackViaLastSeenServerTime() {
        // Device clock rolled back to 1750000000000, but lastSeenServerTime is already past 2000000000000
        val res = CouponService.shared.verifyCouponToken(
            token = validAnnualToken,
            expectedInstallId = "install-123",
            currentTimeMs = 1750000000000L,
            lastSeenServerTimeMs = 2000000000001L
        )
        assertFalse(res.valid, "Expired token must be rejected even if device clock is rolled back")
    }

    @Test
    fun rejectFakeOrEmptyTokens() {
        assertFalse(CouponService.shared.verifyCouponToken(null, "install-123").valid)
        assertFalse(CouponService.shared.verifyCouponToken("", "install-123").valid)
        assertFalse(CouponService.shared.verifyCouponToken("sct_fake_token", "install-123").valid)
        assertFalse(CouponService.shared.verifyCouponToken("sct_v2_invalid", "install-123").valid)
        assertFalse(
            CouponService.shared.verifyCouponToken(validLifetimeToken, "").valid,
            "Blank expectedInstallId must not bypass device binding when token carries an installId"
        )
    }

    @Test
    fun ed25519VerifierHandlesInvalidHexGracefully() {
        assertEquals(0, Ed25519Verifier.hexToBytes("xyz").size)
        assertEquals(0, Ed25519Verifier.hexToBytes("123").size)
        assertEquals(0, Ed25519Verifier.hexToBytes("zzzz").size)
        assertFalse(Ed25519Verifier.verify("test".encodeToByteArray(), "invalid_hex", "also_invalid"))
    }

    @Test
    fun extractCouponCodeHandlesUrlsAndText() {
        assertEquals("FLIP-PRO-2026", CouponService.shared.extractCouponCode("https://songflip.link?promo=FLIP-PRO-2026"))
        assertEquals("BETALIST", CouponService.shared.extractCouponCode("Hey, try code BETALIST now!"))
        assertEquals("MYCODE123", CouponService.shared.extractCouponCode("  my code 123  "))
        assertEquals("FLIP-ABCD-1234", CouponService.shared.extractCouponCode("Here is your gift: flip-abcd-1234 enjoy!"))
    }
}
