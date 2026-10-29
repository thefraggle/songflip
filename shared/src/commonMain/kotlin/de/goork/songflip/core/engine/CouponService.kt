package de.goork.songflip.core.engine

import de.goork.songflip.core.util.SongFlipLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class CouponRedeemStatus {
    SUCCESS_LIFETIME,
    SUCCESS_1YEAR,
    SUCCESS_3MONTHS,
    SUCCESS_1MONTH,
    ALREADY_ACTIVE,
    ALREADY_REDEEMED,
    MAX_REACHED,
    INACTIVE,
    RATE_LIMITED,
    INVALID,
    NETWORK_ERROR
}

@Serializable
data class CouponRedeemOutcome(
    val status: CouponRedeemStatus,
    val code: String = "",
    val type: String = "",
    val token: String = "",
    val expirationTimestamp: Long? = null,
    val issuedAtTimestamp: Long? = null
)

@Serializable
data class VerifiedCouponPayload(
    val valid: Boolean,
    val code: String = "",
    val installId: String = "",
    val type: String = "",
    val expirationTimestamp: Long? = null,
    val issuedAtTimestamp: Long? = null,
    val isLegacyToken: Boolean = false,
    /** True when signed with the current (v3) key; older tokens are migrated online (#141). */
    val isCurrentKey: Boolean = false
)

enum class CouponRefreshStatus {
    /** Server re-signed the token with the current key. */
    UPGRADED,
    /** Token is already current – nothing to do. */
    UNCHANGED,
    /** Server proved the signature invalid: the stored token must be discarded. */
    REVOKED,
    /** Network/config/unknown-record outcome: keep the stored token untouched (never lock users out). */
    KEEP
}

@Serializable
data class CouponRefreshOutcome(
    val status: CouponRefreshStatus,
    val token: String = "",
    val type: String = "",
    val expirationTimestamp: Long? = null,
    val issuedAtTimestamp: Long? = null
)

/**
 * Shared KMP Coupon Redemption & Verification Service (#112, #109).
 * Handles smart promo code extraction, multi-endpoint `/redeemPromoCode` HTTP requests,
 * offline Ed25519 signature verification (`sct_v2_`), device `installId` binding,
 * and clock-rollback protection via `lastSeenServerTime`.
 */
class CouponService(
    private val client: HttpClient = createPlatformHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }
) {
    constructor() : this(createPlatformHttpClient())

    companion object {
        val shared: CouponService by lazy { CouponService() }

        private val DEFAULT_ENDPOINTS = listOf(
            "https://cache.songflip.link/redeemPromoCode",
            "https://songflip-web.web.app/redeemPromoCode"
        )

        /** Token generation requested from the backend; 3 = sign with the v3 key pinned below. */
        const val CURRENT_TOKEN_VERSION = 3

        // Current signing key (since 1.11.0, #141). Listed first so the common case verifies fastest.
        private const val CURRENT_PUBLIC_KEY_HEX = "db038fc84e31a618133be993e096aacb6f3702149fc0d287d2a738d85949e6d7"

        // Pinned Ed25519 verification public keys (32 bytes each, hex-encoded, RFC 8032).
        // Older keys stay trusted so already issued tokens keep working; they are replaced via
        // refreshCouponToken() and can be dropped once migration has run its course.
        private val TRUSTED_PUBLIC_KEYS_HEX = listOf(
            CURRENT_PUBLIC_KEY_HEX,
            "045623e99efa5a77fc19ee5bec0dd392e61cf8ad6a48cc3716b80860efb1db9d",
            "6949b9c7bd8a6e8733fc7dfa2e42d3eb50ae6b2b45cbf1880f1b7221b23385fe"
        )

        private val TRUSTED_PUBLIC_KEYS: List<ByteArray> by lazy {
            TRUSTED_PUBLIC_KEYS_HEX.map { Ed25519Verifier.hexToBytes(it) }
        }
    }

    /**
     * Extracts a clean uppercase coupon code from raw user input (full URL with `?promo=`,
     * Reddit/social DM text blocks, or direct code strings).
     */
    fun extractCouponCode(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return ""

        // 0. Check for ?promo= or &promo= or ?code= in URL
        val urlPromoRegex = Regex("""[?&](?:promo|code)=([A-Za-z0-9_-]{3,64})""", RegexOption.IGNORE_CASE)
        urlPromoRegex.find(trimmed)?.groupValues?.getOrNull(1)?.let {
            return it.trim().uppercase()
        }

        // 1. Standard FLIP-XXXX-YYYY or unhyphenated FLIPXXXXXXXX
        val standardFlipRegex = Regex("""\b(FLIP-[A-Z0-9]{4}-[A-Z0-9]{4}|FLIP[A-Z0-9]{8})\b""", RegexOption.IGNORE_CASE)
        standardFlipRegex.find(trimmed)?.let { return it.value.uppercase() }

        // 2. Custom FLIP prefix (e.g. FLIP-MARKUS, FLIP-LIFETIME-VIP)
        val customFlipRegex = Regex("""\b(FLIP-[A-Z0-9_-]{3,32})\b""", RegexOption.IGNORE_CASE)
        customFlipRegex.find(trimmed)?.let { return it.value.uppercase() }

        // 3. Known campaign word codes in longer text
        val campaignRegex = Regex(
            """\b(BETALIST|PEERPUSH|FOUNDER-?PASS|NEO-?FOUNDER|EXCHANGE60|SHIPATON|SHIPYARD|HACKATHON|SONGFLIP_[A-Z0-9_]+)\b""",
            RegexOption.IGNORE_CASE
        )
        campaignRegex.find(trimmed)?.let { return it.value.uppercase() }

        // 4. Fallback: strip colon-delimited prefixes and whitespace
        var candidate = trimmed
        val prefixes = listOf("code:", "promo:", "coupon:", "gutschein:")
        for (p in prefixes) {
            if (candidate.lowercase().startsWith(p)) {
                candidate = candidate.drop(p.length).trim()
                break
            }
        }
        return candidate.replace("\\s+".toRegex(), "").uppercase()
    }

    /**
     * Verifies a server-issued coupon token offline (#109):
     * - For `sct_v2_<payload>.<sig>`: verifies Ed25519 signature against embedded public key,
     *   checks `installId` binding, and checks expiration against `max(currentTimeMs, lastSeenServerTimeMs)`.
     * - For legacy `sct_<payload>.<sig>` tokens already stored before v1.9.0: decodes payload,
     *   verifies `installId` and expiration, and marks `isLegacyToken = true` so callers can
     *   preserve existing users' coupons while rejecting any local pref tampering without a token.
     */
    fun verifyCouponToken(
        token: String?,
        expectedInstallId: String,
        currentTimeMs: Long = getCurrentTimeMillis(),
        lastSeenServerTimeMs: Long = 0L
    ): VerifiedCouponPayload {
        if (token.isNullOrBlank()) return VerifiedCouponPayload(valid = false)
        val cleanToken = token.removePrefix("coupon:").trim()
        val effectiveNow = maxOf(currentTimeMs, lastSeenServerTimeMs)

        if (cleanToken.startsWith("sct_v2_")) {
            val raw = cleanToken.substring(7)
            val dotIdx = raw.indexOf('.')
            if (dotIdx <= 0 || dotIdx >= raw.length - 1) return VerifiedCouponPayload(valid = false)
            val payloadB64 = raw.substring(0, dotIdx)
            val sigB64 = raw.substring(dotIdx + 1)

            val sigBytes = decodeBase64Url(sigB64) ?: return VerifiedCouponPayload(valid = false)
            if (sigBytes.size != 64) return VerifiedCouponPayload(valid = false)
            val msgBytes = payloadB64.encodeToByteArray()

            val matchedIndex = TRUSTED_PUBLIC_KEYS.indexOfFirst { pubKey ->
                Ed25519Verifier.verify(msgBytes, sigBytes, pubKey)
            }
            if (matchedIndex < 0) return VerifiedCouponPayload(valid = false)

            return parseAndValidatePayload(
                payloadB64 = payloadB64,
                expectedInstallId = expectedInstallId,
                effectiveNow = effectiveNow,
                isLegacy = false,
                isCurrentKey = matchedIndex == 0
            )
        }

        // Legacy v1 HMAC tokens (sct_<payload>.<sig>) are fully deprecated and rejected (#170).
        // Only cryptographic Ed25519-signed tokens (v3_...) are accepted.
        if (cleanToken.startsWith("sct_")) {
            return VerifiedCouponPayload(valid = false)
        }

        return VerifiedCouponPayload(valid = false)
    }

    private fun JsonElement?.safeStringOrNull(): String? {
        val prim = this as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.contentOrNull
    }

    private fun JsonElement?.safeLongOrNull(): Long? {
        val prim = this as? JsonPrimitive ?: return null
        if (prim is JsonNull || prim.isString) return null
        return prim.contentOrNull?.toLongOrNull()
    }

    private fun parseAndValidatePayload(
        payloadB64: String,
        expectedInstallId: String,
        effectiveNow: Long,
        isLegacy: Boolean,
        isCurrentKey: Boolean = false
    ): VerifiedCouponPayload {
        val payloadBytes = decodeBase64Url(payloadB64) ?: return VerifiedCouponPayload(valid = false)
        val payloadJson = try {
            json.parseToJsonElement(payloadBytes.decodeToString()) as? JsonObject
        } catch (_: Throwable) {
            null
        } ?: return VerifiedCouponPayload(valid = false)

        val code = payloadJson["c"].safeStringOrNull()?.trim().orEmpty()
        val tokenInstallId = payloadJson["i"].safeStringOrNull()?.trim().orEmpty()
        val type = payloadJson["t"].safeStringOrNull()?.trim()?.lowercase().orEmpty()
        val rawExp = payloadJson["e"].safeLongOrNull()
        val exp = rawExp?.takeIf { it > 0L }
        val iat = payloadJson["iat"].safeLongOrNull()?.takeIf { it > 0L }

        if (code.isEmpty() || type.isEmpty() || tokenInstallId.isEmpty()) {
            return VerifiedCouponPayload(valid = false)
        }

        // Strict device binding: token must belong to this device's installId
        if (tokenInstallId.isNotBlank() && (expectedInstallId.isBlank() || tokenInstallId != expectedInstallId)) {
            return VerifiedCouponPayload(valid = false)
        }

        val effectiveTime = if (iat != null && iat > effectiveNow) iat else effectiveNow
        if (type != "lifetime") {
            if (exp == null || exp <= effectiveTime) {
                return VerifiedCouponPayload(valid = false)
            }
        }

        return VerifiedCouponPayload(
            valid = true,
            code = code,
            installId = tokenInstallId,
            type = type,
            expirationTimestamp = if (type == "lifetime") null else exp,
            issuedAtTimestamp = iat,
            isLegacyToken = isLegacy,
            isCurrentKey = isCurrentKey
        )
    }

    /** True for a locally valid token that was not signed with the current key (legacy HMAC or older Ed25519). */
    fun needsTokenMigration(verified: VerifiedCouponPayload): Boolean =
        verified.valid && (verified.isLegacyToken || !verified.isCurrentKey)

    /**
     * Exchanges a stored token for one signed with the current key (#141). Uses the existing
     * `/redeemPromoCode` endpoint (`refreshToken`) so no routing changes are needed and no
     * redemption slot is consumed. Only an explicit INVALID_TOKEN verdict yields [CouponRefreshStatus.REVOKED];
     * every other failure keeps the stored token so a backend hiccup can never revoke a customer.
     */
    suspend fun refreshCouponToken(
        token: String,
        installId: String,
        customEndpoints: List<String>? = null
    ): CouponRefreshOutcome {
        val cleanToken = token.removePrefix("coupon:").trim()
        if (cleanToken.isEmpty() || installId.isBlank()) return CouponRefreshOutcome(status = CouponRefreshStatus.KEEP)

        val endpoints = customEndpoints?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENDPOINTS
        val bodyStr = buildJsonObject {
            put("installId", installId)
            put("refreshToken", cleanToken)
            put("tokenVersion", CURRENT_TOKEN_VERSION)
        }.toString()

        for (endpoint in endpoints) {
            try {
                val response = withContext(Dispatchers.Default) {
                    withTimeout(7500L) {
                        client.post(endpoint) {
                            contentType(ContentType.Application.Json)
                            header("Accept", "application/json")
                            setBody(bodyStr)
                        }
                    }
                }
                val respText = response.bodyAsText()
                val obj = try {
                    json.parseToJsonElement(respText) as? JsonObject
                } catch (e: Throwable) {
                    SongFlipLogger.w("CouponService", "unparsable refresh response from $endpoint", e)
                    null
                }
                if (response.status.isSuccess()) {
                    val newToken = obj?.get("token").safeStringOrNull().orEmpty()
                    val verified = verifyCouponToken(token = newToken, expectedInstallId = installId)
                    // Never replace a working token with something this client cannot verify.
                    if (!verified.valid || verified.isLegacyToken) return CouponRefreshOutcome(status = CouponRefreshStatus.KEEP)
                    val status = if (obj?.get("status").safeStringOrNull() == "unchanged") {
                        CouponRefreshStatus.UNCHANGED
                    } else {
                        CouponRefreshStatus.UPGRADED
                    }
                    return CouponRefreshOutcome(
                        status = status,
                        token = newToken,
                        type = verified.type,
                        expirationTimestamp = verified.expirationTimestamp,
                        issuedAtTimestamp = verified.issuedAtTimestamp
                    )
                }
                val errCode = obj?.get("error").safeStringOrNull()?.uppercase().orEmpty()
                return if (errCode == "INVALID_TOKEN") {
                    CouponRefreshOutcome(status = CouponRefreshStatus.REVOKED)
                } else {
                    SongFlipLogger.i("CouponService", "token refresh kept stored token (HTTP ${response.status.value}, $errCode)")
                    CouponRefreshOutcome(status = CouponRefreshStatus.KEEP)
                }
            } catch (c: CancellationException) {
                if (c !is kotlinx.coroutines.TimeoutCancellationException) throw c
                SongFlipLogger.w("CouponService", "token refresh timed out on $endpoint, trying next endpoint", c)
            } catch (t: Throwable) {
                SongFlipLogger.w("CouponService", "token refresh failed on $endpoint", t)
            }
        }
        return CouponRefreshOutcome(status = CouponRefreshStatus.KEEP)
    }

    suspend fun redeemCoupon(
        rawCode: String,
        installId: String,
        isAlreadyLifetimeCoupon: Boolean = false,
        customEndpoints: List<String>? = null
    ): CouponRedeemOutcome {
        val cleanCode = extractCouponCode(rawCode)
        if (cleanCode.length < 3) {
            return CouponRedeemOutcome(status = CouponRedeemStatus.INVALID)
        }

        val endpoints = customEndpoints?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENDPOINTS
        val bodyStr = buildJsonObject {
            put("code", cleanCode)
            put("installId", installId)
            // Ask for a token signed with the key this client pins (older apps omit it and get compat tokens).
            put("tokenVersion", CURRENT_TOKEN_VERSION)
        }.toString()

        for (endpoint in endpoints) {
            try {
                val response = withContext(Dispatchers.Default) {
                    withTimeout(7500L) {
                        client.post(endpoint) {
                            contentType(ContentType.Application.Json)
                            header("Accept", "application/json")
                            setBody(bodyStr)
                        }
                    }
                }

                val respText = response.bodyAsText()
                if (response.status.isSuccess()) {
                    val obj = json.parseToJsonElement(respText) as? JsonObject
                        ?: return CouponRedeemOutcome(status = CouponRedeemStatus.INVALID)
                    val type = obj["type"].safeStringOrNull()?.lowercase() ?: "1month"
                    val exp = obj["expirationTimestamp"].safeLongOrNull()?.takeIf { it > 0L }
                    val token = obj["token"].safeStringOrNull().orEmpty()

                    val verified = verifyCouponToken(
                        token = token,
                        expectedInstallId = installId
                    )
                    if (!verified.valid || verified.isLegacyToken) {
                        return CouponRedeemOutcome(status = CouponRedeemStatus.INVALID)
                    }

                    if ((type == "lifetime" || verified.type == "lifetime") && isAlreadyLifetimeCoupon) {
                        return CouponRedeemOutcome(
                            status = CouponRedeemStatus.ALREADY_ACTIVE,
                            code = cleanCode,
                            type = "lifetime",
                            token = token
                        )
                    }

                    val now = getCurrentTimeMillis()
                    val durationDays = when (type) {
                        "lifetime" -> null
                        "1year", "annual" -> 365L
                        "3months" -> 90L
                        "1week" -> 7L
                        else -> 30L
                    }
                    val expireTime = if (type == "lifetime" || verified.type == "lifetime") {
                        null
                    } else {
                        verified.expirationTimestamp ?: exp ?: (now + (durationDays ?: 30L) * 24 * 60 * 60 * 1000L)
                    }
                    val resolvedType = verified.type ?: type
                    val status = when (resolvedType) {
                        "lifetime" -> CouponRedeemStatus.SUCCESS_LIFETIME
                        "1year", "annual" -> CouponRedeemStatus.SUCCESS_1YEAR
                        "3months" -> CouponRedeemStatus.SUCCESS_3MONTHS
                        else -> CouponRedeemStatus.SUCCESS_1MONTH
                    }

                    return CouponRedeemOutcome(
                        status = status,
                        code = cleanCode,
                        type = type,
                        token = token,
                        expirationTimestamp = expireTime,
                        issuedAtTimestamp = verified.issuedAtTimestamp ?: now
                    )
                } else {
                    val statusCode = response.status.value
                    if (statusCode == 429) {
                        return CouponRedeemOutcome(status = CouponRedeemStatus.RATE_LIMITED)
                    }
                    val errObj = try {
                        json.parseToJsonElement(respText) as? JsonObject
                    } catch (_: Throwable) {
                        null
                    }
                    val errCode = errObj?.get("error")?.jsonPrimitive?.content?.uppercase().orEmpty()
                    val mappedStatus = when (errCode) {
                        "TOO_MANY_REQUESTS", "TOO_MANY_FAILED_ATTEMPTS" -> CouponRedeemStatus.RATE_LIMITED
                        "ALREADY_REDEEMED_ON_DEVICE" -> CouponRedeemStatus.ALREADY_REDEEMED
                        "MAX_REDEMPTIONS_REACHED" -> CouponRedeemStatus.MAX_REACHED
                        "CODE_INACTIVE", "CODE_EXPIRED" -> CouponRedeemStatus.INACTIVE
                        "ALREADY_ACTIVE" -> CouponRedeemStatus.ALREADY_ACTIVE
                        else -> if (statusCode == 409) CouponRedeemStatus.ALREADY_REDEEMED else CouponRedeemStatus.INVALID
                    }
                    return CouponRedeemOutcome(status = mappedStatus)
                }
            } catch (c: CancellationException) {
                if (c !is kotlinx.coroutines.TimeoutCancellationException) throw c
                // Timeout on current endpoint -> try next fallback endpoint
            } catch (_: Throwable) {
                // Network error on current endpoint -> try next fallback endpoint
            }
        }

        return CouponRedeemOutcome(status = CouponRedeemStatus.NETWORK_ERROR)
    }

    internal fun decodeBase64Url(input: String): ByteArray? {
        if (input.isEmpty()) return null
        val table = IntArray(128) { -1 }
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        for (i in chars.indices) {
            table[chars[i].code] = i
        }
        // Also support standard '+' and '/'
        table['+'.code] = 62
        table['/'.code] = 63

        val clean = input.trimEnd('=')
        val outLen = (clean.length * 3) / 4
        val out = ByteArray(outLen)
        var buffer = 0
        var bitsCollected = 0
        var outIdx = 0

        for (ch in clean) {
            val code = ch.code
            if (code >= 128 || table[code] == -1) return null
            buffer = (buffer shl 6) or table[code]
            bitsCollected += 6
            if (bitsCollected >= 8) {
                bitsCollected -= 8
                if (outIdx < outLen) {
                    out[outIdx++] = ((buffer shr bitsCollected) and 0xff).toByte()
                }
            }
        }
        return out
    }
}
