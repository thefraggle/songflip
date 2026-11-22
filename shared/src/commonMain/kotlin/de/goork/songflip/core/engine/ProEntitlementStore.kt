package de.goork.songflip.core.engine

/**
 * Port for reading and writing sensitive entitlement state (coupon tokens, timestamps, RC cache).
 */
interface SecureKeyValuePort {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}

data class EntitlementState(
    val isPro: Boolean = false,
    val proType: String = "",
    val expirationDateMs: Long? = null
)

data class RevenueCatSnapshot(
    val active: Boolean,
    val expirationMs: Long? = null,
    val isLifetime: Boolean = false
)

data class RedeemEntitlementOutcome(
    val status: CouponRedeemStatus,
    val cleanCode: String,
    val newState: EntitlementState
)

data class CouponMigrationResult(
    val status: CouponRefreshStatus,
    val newState: EntitlementState
)

/**
 * Deep KMP module owning cross-platform Pro entitlement precedence, 15-minute RC grace window,
 * monotonic anti-rollback server timestamps, coupon redemption persistence, and authToken derivation.
 */
class ProEntitlementStore(
    private val storage: SecureKeyValuePort,
    private val couponService: CouponService = CouponService.shared,
    private val installIdsProvider: () -> List<String>,
    private val rcAppUserIdProvider: () -> String,
    private val clockMs: () -> Long = { getCurrentTimeMillis() }
) {
    companion object {
        const val RC_EXPIRY_GRACE_MS = 15 * 60 * 1000L
        const val KEY_COUPON_TYPE = "pro_coupon_type"
        const val KEY_COUPON_CODE = "pro_coupon_code"
        const val KEY_COUPON_TOKEN = "pro_coupon_token"
        const val KEY_COUPON_EXPIRATION = "pro_coupon_expiration"
        const val KEY_LAST_SEEN_SERVER_TIME = "last_seen_server_time"
        const val KEY_RC_IS_PRO = "rc_is_pro"
        const val KEY_RC_TYPE = "rc_pro_type"
        const val KEY_RC_EXPIRATION = "rc_pro_expiration"
    }

    fun getLastSeenServerTimeMs(): Long =
        storage.getString(KEY_LAST_SEEN_SERVER_TIME)?.toLongOrNull()?.takeIf { it > 0L } ?: 0L

    fun recordServerTimestamp(timestampMs: Long) {
        if (timestampMs > getLastSeenServerTimeMs()) {
            storage.putString(KEY_LAST_SEEN_SERVER_TIME, timestampMs.toString())
        }
    }

    fun verifyStoredCouponOffline(
        storedToken: String? = storage.getString(KEY_COUPON_TOKEN),
        currentTimeMs: Long = clockMs()
    ): VerifiedCouponPayload {
        val ids = installIdsProvider().map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val lastSeen = getLastSeenServerTimeMs()
        if (ids.isEmpty()) {
            return couponService.verifyCouponToken(
                token = storedToken,
                expectedInstallId = "",
                currentTimeMs = currentTimeMs,
                lastSeenServerTimeMs = lastSeen
            )
        }
        var firstResult: VerifiedCouponPayload? = null
        for (id in ids) {
            val verified = couponService.verifyCouponToken(
                token = storedToken,
                expectedInstallId = id,
                currentTimeMs = currentTimeMs,
                lastSeenServerTimeMs = lastSeen
            )
            if (firstResult == null) firstResult = verified
            if (verified.valid) return verified
        }
        return firstResult!!
    }

    fun hydrateFromCache(): EntitlementState {
        val cachedRcPro = storage.getString(KEY_RC_IS_PRO) == "true"
        val cachedRcType = storage.getString(KEY_RC_TYPE) ?: "revenuecat_subscription"
        val cachedRcExp = storage.getString(KEY_RC_EXPIRATION)?.toLongOrNull() ?: 0L
        val isLifetime = cachedRcType == "revenuecat_lifetime"
        val effectiveNowMs = maxOf(clockMs(), getLastSeenServerTimeMs())
        val isExpired = !isLifetime && (cachedRcExp <= 0L || (cachedRcExp + RC_EXPIRY_GRACE_MS) <= effectiveNowMs)

        return if (cachedRcPro && !isExpired) {
            evaluate(
                rcSnapshot = RevenueCatSnapshot(
                    active = true,
                    expirationMs = if (isLifetime) null else cachedRcExp,
                    isLifetime = isLifetime
                ),
                isExplicitRcCheck = false
            )
        } else {
            evaluate(rcSnapshot = null, isExplicitRcCheck = false)
        }
    }

    fun evaluate(
        rcSnapshot: RevenueCatSnapshot?,
        isExplicitRcCheck: Boolean = false
    ): EntitlementState {
        val now = clockMs()
        val storedToken = storage.getString(KEY_COUPON_TOKEN)
        val verified = verifyStoredCouponOffline(storedToken = storedToken, currentTimeMs = now)
        val issuedAt = verified.issuedAtTimestamp
        if (verified.valid && issuedAt != null) {
            recordServerTimestamp(issuedAt)
        }

        val activeRc = rcSnapshot?.takeIf { it.active }
        if (activeRc != null) {
            storage.putString(KEY_RC_IS_PRO, "true")
            storage.putString(KEY_RC_TYPE, if (activeRc.isLifetime) "revenuecat_lifetime" else "revenuecat_subscription")
            storage.putString(KEY_RC_EXPIRATION, (activeRc.expirationMs ?: 0L).toString())
        } else if (isExplicitRcCheck) {
            storage.putString(KEY_RC_IS_PRO, "false")
            storage.remove(KEY_RC_EXPIRATION)
        }

        return when {
            activeRc != null -> EntitlementState(
                isPro = true,
                proType = if (activeRc.isLifetime) "revenuecat_lifetime" else "revenuecat_subscription",
                expirationDateMs = if (activeRc.isLifetime) null else activeRc.expirationMs
            )
            verified.valid && verified.type == "lifetime" -> EntitlementState(
                isPro = true,
                proType = "lifetime_coupon",
                expirationDateMs = null
            )
            verified.valid -> {
                val labelType = storage.getString(KEY_COUPON_TYPE)
                    ?.takeIf { it.isNotBlank() && it != "lifetime" }
                    ?: verified.type
                val normalizedType = if (labelType.endsWith("_coupon")) labelType else "${labelType}_coupon"
                EntitlementState(
                    isPro = true,
                    proType = normalizedType,
                    expirationDateMs = verified.expirationTimestamp
                )
            }
            else -> EntitlementState(isPro = false)
        }
    }

    fun reevaluateKeepingRevenueCat(currentState: EntitlementState): EntitlementState {
        val rcActive = currentState.isPro && currentState.proType.startsWith("revenuecat")
        val snapshot = if (rcActive) {
            RevenueCatSnapshot(
                active = true,
                expirationMs = currentState.expirationDateMs,
                isLifetime = currentState.proType == "revenuecat_lifetime"
            )
        } else {
            null
        }
        return evaluate(rcSnapshot = snapshot, isExplicitRcCheck = false)
    }

    fun resolveAuthToken(currentState: EntitlementState): String {
        if (currentState.isPro && currentState.proType.startsWith("revenuecat")) {
            return rcAppUserIdProvider()
        }
        val savedToken = storage.getString(KEY_COUPON_TOKEN)
        if (!savedToken.isNullOrBlank()) {
            val verified = verifyStoredCouponOffline(storedToken = savedToken, currentTimeMs = clockMs())
            if (verified.valid) {
                return "coupon:$savedToken"
            }
        }
        return rcAppUserIdProvider()
    }

    suspend fun redeemCoupon(
        rawCode: String,
        currentState: EntitlementState,
        customEndpoints: List<String>? = null
    ): RedeemEntitlementOutcome {
        val cleanCode = couponService.extractCouponCode(rawCode)
        if (cleanCode.isEmpty()) {
            return RedeemEntitlementOutcome(CouponRedeemStatus.INVALID, cleanCode, currentState)
        }
        val primaryInstallId = installIdsProvider().firstOrNull()?.trim().orEmpty()
        if (primaryInstallId.isEmpty()) {
            return RedeemEntitlementOutcome(CouponRedeemStatus.INVALID, cleanCode, currentState)
        }

        val outcome = couponService.redeemCoupon(
            rawCode = cleanCode,
            installId = primaryInstallId,
            customEndpoints = customEndpoints
        )

        return when (outcome.status) {
            CouponRedeemStatus.SUCCESS_LIFETIME -> {
                if (currentState.isPro && currentState.proType == "lifetime_coupon") {
                    return RedeemEntitlementOutcome(CouponRedeemStatus.ALREADY_ACTIVE, cleanCode, currentState)
                }
                recordServerTimestamp(outcome.issuedAtTimestamp ?: clockMs())
                storage.putString(KEY_COUPON_TYPE, "lifetime")
                storage.putString(KEY_COUPON_CODE, cleanCode)
                storage.remove(KEY_COUPON_EXPIRATION)
                storage.putString(KEY_COUPON_TOKEN, outcome.token)
                val updated = reevaluateKeepingRevenueCat(currentState)
                RedeemEntitlementOutcome(CouponRedeemStatus.SUCCESS_LIFETIME, cleanCode, updated)
            }
            CouponRedeemStatus.SUCCESS_1YEAR,
            CouponRedeemStatus.SUCCESS_3MONTHS,
            CouponRedeemStatus.SUCCESS_1MONTH -> {
                recordServerTimestamp(outcome.issuedAtTimestamp ?: clockMs())
                val type = outcome.type.ifBlank { "1month" }
                val durationDays = when (type) {
                    "1year", "annual" -> 365L
                    "3months" -> 90L
                    "1week" -> 7L
                    else -> 30L
                }
                val expireTime = outcome.expirationTimestamp
                    ?: (clockMs() + durationDays * 24 * 60 * 60 * 1000L)
                storage.putString(KEY_COUPON_TYPE, type)
                storage.putString(KEY_COUPON_CODE, cleanCode)
                storage.putString(KEY_COUPON_EXPIRATION, expireTime.toString())
                storage.putString(KEY_COUPON_TOKEN, outcome.token)
                val updated = reevaluateKeepingRevenueCat(currentState)
                RedeemEntitlementOutcome(outcome.status, cleanCode, updated)
            }
            else -> RedeemEntitlementOutcome(outcome.status, cleanCode, currentState)
        }
    }

    suspend fun migrateStoredCouponTokenIfNeeded(
        currentState: EntitlementState,
        customEndpoints: List<String>? = null
    ): CouponMigrationResult? {
        val storedToken = storage.getString(KEY_COUPON_TOKEN) ?: return null
        val verified = verifyStoredCouponOffline(storedToken = storedToken, currentTimeMs = clockMs())
        if (!couponService.needsTokenMigration(verified)) return null

        val outcome = couponService.refreshCouponToken(
            token = storedToken,
            installId = verified.installId,
            customEndpoints = customEndpoints
        )
        return when (outcome.status) {
            CouponRefreshStatus.UPGRADED -> {
                storage.putString(KEY_COUPON_TOKEN, outcome.token)
                outcome.issuedAtTimestamp?.let { recordServerTimestamp(it) }
                CouponMigrationResult(CouponRefreshStatus.UPGRADED, reevaluateKeepingRevenueCat(currentState))
            }
            CouponRefreshStatus.REVOKED -> {
                storage.remove(KEY_COUPON_TOKEN)
                storage.remove(KEY_COUPON_TYPE)
                storage.remove(KEY_COUPON_CODE)
                storage.remove(KEY_COUPON_EXPIRATION)
                CouponMigrationResult(CouponRefreshStatus.REVOKED, reevaluateKeepingRevenueCat(currentState))
            }
            else -> CouponMigrationResult(outcome.status, currentState)
        }
    }
}
