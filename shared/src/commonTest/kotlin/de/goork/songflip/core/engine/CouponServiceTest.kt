package de.goork.songflip.core.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CouponServiceTest {

    // Pre-signed RFC 8032 Ed25519 test vectors for offline signature verification
    // Payload: {"c":"VIP-LIFE","i":"install-123","e":0,"t":"lifetime","iat":1700000000000}
    private val validLifetimeToken =
        "sct_v2_eyJjIjoiVklQLUxJRkUiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjowLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.DEBFd4HtxJ_0VFIFgAAcwXLb0f8p2a8TnNAegvy-NnLwFD18kvKkgo91bIC0BoQbKI9qUE06Ss_3K9RcXmk-BA"

    // Payload: {"c":"VIP-NULL","i":"install-123","e":null,"t":"lifetime","iat":1700000000000}
    private val validLifetimeNullExpToken =
        "sct_v2_eyJjIjoiVklQLU5VTEwiLCJpIjoiaW5zdGFsbC0xMjMiLCJlIjpudWxsLCJ0IjoibGlmZXRpbWUiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.gKcOxd6Bc7dmJKXZ6r1i_526B41v9cGqmmmCaXIsJXXlzhwhVK2eCpyG8ywTwwS4Lg8FtbLYz9RKeDMpiqDYAg"

    // Payload: {"c":"ANNUAL2026","i":"install-123","e":2000000000000,"t":"time_limited","iat":1700000000000}
    private val validAnnualToken =
        "sct_v2_eyJjIjoiQU5OVUFMMjAyNiIsImkiOiJpbnN0YWxsLTEyMyIsImUiOjIwMDAwMDAwMDAwMDAsInQiOiJ0aW1lX2xpbWl0ZWQiLCJpYXQiOjE3MDAwMDAwMDAwMDB9.rrEMwtIUR1auVbgNq0iXGBnqbuII_rS8L7Hm_6J9vxgJcN19ceTk9wpqsdisDtHKhqJklGgyy22uuOXc1VocAw"

    // Same payload signed with the current v3 key and with the older compat key (#141):
    // {"c":"FLIP-UNIT-TEST","i":"kmp_unit_test_install","e":2015360000000,"t":"1year","iat":1700000000000}
    private val v3SignedToken =
        "sct_v2_eyJjIjoiRkxJUC1VTklULVRFU1QiLCJpIjoia21wX3VuaXRfdGVzdF9pbnN0YWxsIiwiZSI6MjAxNTM2MDAwMDAwMCwidCI6IjF5ZWFyIiwiaWF0IjoxNzAwMDAwMDAwMDAwfQ.KIJNaV1uH9dw6LVI7USxkGyBtHtVI2isZ0Qy_UDWL6UB1ba-sm7GNGiem0R8VR0t5ivSaS1Ty-5YoNyWDO6nAA"
    private val compatSignedToken =
        "sct_v2_eyJjIjoiRkxJUC1VTklULVRFU1QiLCJpIjoia21wX3VuaXRfdGVzdF9pbnN0YWxsIiwiZSI6MjAxNTM2MDAwMDAwMCwidCI6IjF5ZWFyIiwiaWF0IjoxNzAwMDAwMDAwMDAwfQ.dAvA02zATX7vX8G9oYjeY5XwzSG9R5eDO57NYa75URuBYb4CccAFC4CyBla1KKk7IOjDtMDTck3dW2PZ2N6pDg"

    @Test
    fun v3SignedTokenVerifiesAsCurrentKey() {
        val res = CouponService.shared.verifyCouponToken(
            token = v3SignedToken,
            expectedInstallId = "kmp_unit_test_install",
            currentTimeMs = 1750000000000L
        )
        assertTrue(res.valid, "Token signed with the pinned v3 key must verify")
        assertTrue(res.isCurrentKey)
        assertFalse(CouponService.shared.needsTokenMigration(res))
    }

    @Test
    fun compatSignedTokenStaysValidButNeedsMigration() {
        val res = CouponService.shared.verifyCouponToken(
            token = compatSignedToken,
            expectedInstallId = "kmp_unit_test_install",
            currentTimeMs = 1750000000000L
        )
        // Existing customers must keep access while their token is migrated in the background.
        assertTrue(res.valid, "Compat-signed tokens must remain valid")
        assertFalse(res.isCurrentKey)
        assertTrue(CouponService.shared.needsTokenMigration(res))
    }

    @Test
    fun invalidTokenNeverNeedsMigration() {
        val res = CouponService.shared.verifyCouponToken(
            token = v3SignedToken,
            expectedInstallId = "other_device_install",
            currentTimeMs = 1750000000000L
        )
        assertFalse(res.valid)
        assertFalse(CouponService.shared.needsTokenMigration(res))
    }

    @Test
    fun verifyValidEd25519TokenRequiringSqrtMinusOneDecompression() {
        // Public key whose y-coordinate requires the I_CONST (sqrt(-1) mod p) branch in unpackNeg
        val pubKey = Ed25519Verifier.hexToBytes("4e63c6f2461fb47cb45b49b99e6fa95c3cd4cdad2b7f2dd3180df8de6f0273a6")
        val msg = """{"c":"FLIP-B5FE-2NYW","i":"02ca8625bd4ba616","e":0,"t":"lifetime","iat":1792517000000}""".encodeToByteArray()
        val sig = Ed25519Verifier.hexToBytes(
            "9e0864e6bf91670595de095e6452cf59b098fff3212bb12036f5396dd2806686549a2c159e27fbaed3c84b82d4c16b498b703854f62150baa924d59968df520a"
        )
        assertTrue(
            Ed25519Verifier.verify(msg, sig, pubKey),
            "Ed25519 public key requiring I_CONST sqrt(-1) decompression must verify"
        )
    }

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
