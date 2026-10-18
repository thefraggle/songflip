package de.goork.songflip.core.engine

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
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
    val isLegacyToken: Boolean = false
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

        // Embedded Ed25519 public keys (32 bytes each, hex-encoded):
        // 1. Production public key derived from COUPON_SIGNING_SECRET
        // 2. Test environment public key for unit/integration tests
        private val TRUSTED_PUBLIC_KEYS_HEX = listOf(
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

            val sigValid = TRUSTED_PUBLIC_KEYS.any { pubKey ->
                Ed25519Verifier.verify(msgBytes, sigBytes, pubKey)
            }
            if (!sigValid) return VerifiedCouponPayload(valid = false)

            return parseAndValidatePayload(
                payloadB64 = payloadB64,
                expectedInstallId = expectedInstallId,
                effectiveNow = effectiveNow,
                isLegacy = false
            )
        }

        // Legacy v1 HMAC token (sct_<payload>.<sig>): requires valid base64url payload + 32-byte HMAC signature
        if (cleanToken.startsWith("sct_")) {
            val raw = cleanToken.substring(4)
            val dotIdx = raw.indexOf('.')
            if (dotIdx <= 0 || dotIdx >= raw.length - 1) return VerifiedCouponPayload(valid = false)
            val payloadB64 = raw.substring(0, dotIdx)
            val sigB64 = raw.substring(dotIdx + 1)
            val sigBytes = decodeBase64Url(sigB64) ?: return VerifiedCouponPayload(valid = false)
            if (sigBytes.size != 32) return VerifiedCouponPayload(valid = false)

            return parseAndValidatePayload(
                payloadB64 = payloadB64,
                expectedInstallId = expectedInstallId,
                effectiveNow = effectiveNow,
                isLegacy = true
            )
        }

        return VerifiedCouponPayload(valid = false)
    }

    private fun parseAndValidatePayload(
        payloadB64: String,
        expectedInstallId: String,
        effectiveNow: Long,
        isLegacy: Boolean
    ): VerifiedCouponPayload {
        val payloadBytes = decodeBase64Url(payloadB64) ?: return VerifiedCouponPayload(valid = false)
        val payloadJson = try {
            json.parseToJsonElement(payloadBytes.decodeToString()) as? JsonObject
        } catch (_: Throwable) {
            null
        } ?: return VerifiedCouponPayload(valid = false)

        val code = payloadJson["c"]?.jsonPrimitive?.content?.trim().orEmpty()
        val tokenInstallId = payloadJson["i"]?.jsonPrimitive?.content?.trim().orEmpty()
        val type = payloadJson["t"]?.jsonPrimitive?.content?.trim()?.lowercase().orEmpty()
        val exp = payloadJson["e"]?.jsonPrimitive?.longOrNull
        val iat = payloadJson["iat"]?.jsonPrimitive?.longOrNull

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
            isLegacyToken = isLegacy
        )
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
                    val type = obj["type"]?.jsonPrimitive?.content?.lowercase() ?: "1month"
                    val exp = obj["expirationTimestamp"]?.jsonPrimitive?.longOrNull
                    val token = obj["token"]?.jsonPrimitive?.content.orEmpty()

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
                    val status = when (type) {
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
