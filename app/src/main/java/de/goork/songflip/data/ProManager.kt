package de.goork.songflip.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.SyncPurchasesCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import de.goork.songflip.core.engine.CouponRedeemStatus
import de.goork.songflip.core.engine.CouponRefreshStatus
import de.goork.songflip.core.engine.EntitlementState
import de.goork.songflip.core.engine.ProEntitlementStore
import de.goork.songflip.core.engine.RevenueCatSnapshot
import de.goork.songflip.core.engine.SecureKeyValuePort
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

enum class RedeemResult {
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

data class ProState(
    val isPro: Boolean = false,
    val proType: String = "", // "revenuecat", "lifetime_coupon", "1year_coupon", "3months_coupon", "1month_coupon"
    val expirationDate: Long? = null
)

object ProManager {

    private const val PREFS_NAME = "songflip_pro_prefs"
    private const val KEY_COUPON_TYPE = "pro_coupon_type"
    private const val KEY_COUPON_CODE = "pro_coupon_code"
    private const val KEY_COUPON_TOKEN = "pro_coupon_token"
    private const val KEY_COUPON_EXPIRATION = "pro_coupon_expiration"
    private const val KEY_LAST_SEEN_SERVER_TIME = "last_seen_server_time"
    private const val KEY_INSTALL_ID = "anonymous_install_id"
    private const val KEY_RC_IS_PRO = "rc_is_pro"
    private const val KEY_RC_TYPE = "rc_pro_type"
    private const val KEY_RC_EXPIRATION = "rc_pro_expiration"
    private const val KEYSTORE_ALIAS = "songflip_coupon_keystore_key_v1"
    private const val ENC_PREFIX = "enc_v1:"

    // RevenueCat Configuration
    private val REVENUECAT_API_KEY = de.goork.songflip.BuildConfig.REVENUECAT_API_KEY
    const val ENTITLEMENT_PRO = "pro"

    private var prefs: SharedPreferences? = null
    private var stableAndroidId: String? = null
    private val isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile
    private var isAutomatedTestEnv: Boolean = false
    @Volatile
    internal var isLastCustomerInfoSandbox: Boolean = false
        private set
    private val _proState = MutableStateFlow(ProState())
    val proState: StateFlow<ProState> = _proState.asStateFlow()

    fun setAutomatedTestEnvironment(isBot: Boolean) {
        isAutomatedTestEnv = isBot
    }

    // Process-wide scope (ProManager is a singleton); SupervisorJob isolates failing fire-and-forget work
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val isPro: Boolean
        get() = _proState.value.isPro

    private fun encryptForStorage(plainText: String): String {
        return try {
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val secretKey = if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
                (keyStore.getEntry(KEYSTORE_ALIAS, null) as java.security.KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGen = javax.crypto.KeyGenerator.getInstance(
                    android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                    KEYSTORE_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                        android.security.keystore.KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGen.init(spec)
                keyGen.generateKey()
            }
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            ENC_PREFIX + java.util.Base64.getEncoder().encodeToString(combined)
        } catch (t: Throwable) {
            de.goork.songflip.core.util.SongFlipLogger.d("ProManager", "AndroidKeyStore encrypt unavailable (expected in unit tests): ${t.message}")
            plainText
        }
    }

    private fun decryptFromStorage(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        if (!stored.startsWith(ENC_PREFIX)) return stored
        return try {
            val raw = java.util.Base64.getDecoder().decode(stored.removePrefix(ENC_PREFIX))
            if (raw.size < 14) return null
            val ivLen = raw[0].toInt() and 0xff
            if (ivLen <= 0 || raw.size <= 1 + ivLen) return null
            val iv = raw.copyOfRange(1, 1 + ivLen)
            val cipherBytes = raw.copyOfRange(1 + ivLen, raw.size)
            val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val entry = keyStore.getEntry(KEYSTORE_ALIAS, null) as? java.security.KeyStore.SecretKeyEntry
                ?: return null
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                entry.secretKey,
                javax.crypto.spec.GCMParameterSpec(128, iv)
            )
            String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
        } catch (t: Throwable) {
            de.goork.songflip.core.util.SongFlipLogger.w("ProManager", "Failed to decrypt value from storage: ${t.message}", t)
            null
        }
    }

    private fun getStoredCouponToken(): String? {
        val raw = prefs?.getString(KEY_COUPON_TOKEN, null)
        return decryptFromStorage(raw)
    }

    private fun saveStoredCouponToken(editor: SharedPreferences.Editor, token: String) {
        editor.putString(KEY_COUPON_TOKEN, encryptForStorage(token))
    }

    private fun getLastSeenServerTime(): Long {
        val sp = prefs ?: return 0L
        val rawStr = sp.getString(KEY_LAST_SEEN_SERVER_TIME, null)
        if (!rawStr.isNullOrBlank()) {
            val dec = decryptFromStorage(rawStr)?.toLongOrNull()
            if (dec != null && dec > 0L) return dec
        }
        return 0L
    }

    private fun updateLastSeenServerTime(timestampMs: Long) {
        val sp = prefs ?: return
        val current = getLastSeenServerTime()
        if (timestampMs > current) {
            sp.edit().putString(KEY_LAST_SEEN_SERVER_TIME, encryptForStorage(timestampMs.toString())).apply()
        }
    }

    fun getAppUserId(): String {
        return try {
            Purchases.sharedInstance.appUserID
        } catch (e: Exception) {
            "anonymous_local_user"
        }
    }

    fun getAnonymousInstallId(): String {
        val hwId = stableAndroidId
        if (!hwId.isNullOrBlank()) {
            return hwId
        }
        val sp = prefs ?: return java.util.UUID.randomUUID().toString()
        var id = sp.getString(KEY_INSTALL_ID, null)
        if (id.isNullOrBlank()) {
            id = java.util.UUID.randomUUID().toString()
            sp.edit().putString(KEY_INSTALL_ID, id).apply()
        }
        return id
    }

    private fun CouponRedeemStatus.toRedeemResult(): RedeemResult = when (this) {
        CouponRedeemStatus.SUCCESS_LIFETIME -> RedeemResult.SUCCESS_LIFETIME
        CouponRedeemStatus.SUCCESS_1YEAR -> RedeemResult.SUCCESS_1YEAR
        CouponRedeemStatus.SUCCESS_3MONTHS -> RedeemResult.SUCCESS_3MONTHS
        CouponRedeemStatus.SUCCESS_1MONTH -> RedeemResult.SUCCESS_1MONTH
        CouponRedeemStatus.ALREADY_ACTIVE -> RedeemResult.ALREADY_ACTIVE
        CouponRedeemStatus.ALREADY_REDEEMED -> RedeemResult.ALREADY_REDEEMED
        CouponRedeemStatus.MAX_REACHED -> RedeemResult.MAX_REACHED
        CouponRedeemStatus.INACTIVE -> RedeemResult.INACTIVE
        CouponRedeemStatus.RATE_LIMITED -> RedeemResult.RATE_LIMITED
        CouponRedeemStatus.INVALID -> RedeemResult.INVALID
        CouponRedeemStatus.NETWORK_ERROR -> RedeemResult.NETWORK_ERROR
    }

    private fun EntitlementState.toProState(): ProState = ProState(
        isPro = isPro,
        proType = proType,
        expirationDate = expirationDateMs
    )

    private fun ProState.toEntitlementState(): EntitlementState = EntitlementState(
        isPro = isPro,
        proType = proType,
        expirationDateMs = expirationDate
    )

    private val secureStoragePort = object : SecureKeyValuePort {
        override fun getString(key: String): String? {
            val sp = prefs ?: return null
            return when (key) {
                KEY_COUPON_TOKEN, KEY_LAST_SEEN_SERVER_TIME -> decryptFromStorage(sp.getString(key, null))
                KEY_RC_IS_PRO -> sp.getBoolean(key, false).toString()
                KEY_RC_EXPIRATION, KEY_COUPON_EXPIRATION -> if (sp.contains(key)) sp.getLong(key, 0L).toString() else null
                else -> sp.getString(key, null)
            }
        }

        override fun putString(key: String, value: String) {
            val sp = prefs ?: return
            val editor = sp.edit()
            when (key) {
                KEY_COUPON_TOKEN, KEY_LAST_SEEN_SERVER_TIME -> editor.putString(key, encryptForStorage(value))
                KEY_RC_IS_PRO -> editor.putBoolean(key, value == "true")
                KEY_RC_EXPIRATION, KEY_COUPON_EXPIRATION -> editor.putLong(key, value.toLongOrNull() ?: 0L)
                else -> editor.putString(key, value)
            }
            editor.apply()
        }

        override fun remove(key: String) {
            prefs?.edit()?.remove(key)?.apply()
        }
    }

    private val entitlementStore = ProEntitlementStore(
        storage = secureStoragePort,
        installIdsProvider = {
            listOfNotNull(
                getAnonymousInstallId(),
                prefs?.getString(KEY_INSTALL_ID, null)?.trim()
            )
        },
        rcAppUserIdProvider = { getAppUserId() }
    )

    fun getAuthToken(): String = entitlementStore.resolveAuthToken(_proState.value.toEntitlementState())

    fun hydrateLocalProState(): ProState {
        val state = entitlementStore.hydrateFromCache().toProState()
        _proState.value = state
        return state
    }

    fun init(context: Context) {
        if (!isInitialized.compareAndSet(false, true)) {
            return
        }
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        if (stableAndroidId == null) {
            try {
                val rawAndroidId = android.provider.Settings.Secure.getString(
                    context.applicationContext.contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                )
                if (!rawAndroidId.isNullOrBlank() && rawAndroidId != "9774d56d682e549c") {
                    stableAndroidId = rawAndroidId.lowercase()
                }
            } catch (_: Exception) {
                // Fallback to SharedPreferences UUID in unit tests or restricted environments
            }
        }

        // Fast offline hydration from local preferences before network responds (#110)
        hydrateLocalProState()

        // Re-sign older coupon tokens with the current key in the background (#141); never blocks startup.
        backgroundScope.launch { migrateStoredCouponTokenIfNeeded() }

        // Initialize RevenueCat SDK
        if (REVENUECAT_API_KEY.isBlank()) {
            return
        }

        try {
            Purchases.logLevel = if (de.goork.songflip.BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.WARN
            Purchases.configure(
                PurchasesConfiguration.Builder(context.applicationContext, REVENUECAT_API_KEY).build()
            )

            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                updateFromCustomerInfo(customerInfo)
            }

            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    updateFromCustomerInfo(customerInfo)
                }

                override fun onError(error: PurchasesError) {
                    // Do NOT revoke Pro entitlement on network timeout or temporary error (#110).
                    // If user already had a valid Pro entitlement, preserve it while offline.
                    val current = _proState.value
                    if (current.isPro && current.proType.startsWith("revenuecat")) {
                        val exp = current.expirationDate
                        if (exp == null || (exp + ProEntitlementStore.RC_EXPIRY_GRACE_MS) > System.currentTimeMillis()) {
                            return
                        }
                    }
                    evaluateProState(revenueCatActive = false)
                }
            })
        } catch (e: Exception) {
            val current = _proState.value
            if (!current.isPro || !current.proType.startsWith("revenuecat")) {
                evaluateProState(revenueCatActive = false)
            }
        }
    }

    internal fun updateFromCustomerInfo(customerInfo: CustomerInfo): ProState {
        // Strictly verify 'pro' or 'songflip_pro' entitlement. Other purchases (tips/consumables) do not grant Pro (#108).
        val activeEntitlement = customerInfo.entitlements["pro"] ?: customerInfo.entitlements["songflip_pro"]
        val hasProEntitlement = activeEntitlement?.isActive == true
        isLastCustomerInfoSandbox = activeEntitlement?.isSandbox == true
        val expDate = activeEntitlement?.expirationDate?.time
        val isLifetimeRc = hasProEntitlement && expDate == null

        return evaluateProState(
            revenueCatActive = hasProEntitlement,
            rcExpirationMillis = expDate,
            rcIsLifetime = isLifetimeRc,
            isExplicitRcCheck = true
        )
    }

    @Synchronized
    private fun evaluateProState(
        revenueCatActive: Boolean,
        rcExpirationMillis: Long? = null,
        rcIsLifetime: Boolean = false,
        isExplicitRcCheck: Boolean = false
    ): ProState {
        val snapshot = if (revenueCatActive) {
            RevenueCatSnapshot(
                active = true,
                expirationMs = rcExpirationMillis,
                isLifetime = rcIsLifetime
            )
        } else {
            null
        }
        val newState = entitlementStore.evaluate(snapshot, isExplicitRcCheck).toProState()
        _proState.value = newState
        return newState
    }

    private val tokenMigrationAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    // Swappable so JVM unit tests (no android.util.Log mocks in this module) can exercise the migration.
    internal var migrationLogger: (String) -> Unit = { msg -> android.util.Log.i("ProManager", msg) }

    /**
     * One attempt per process to exchange a legacy/compat coupon token for a v3-signed one (#141).
     * Pro access never depends on this call: on network errors or unknown outcomes the existing
     * token is kept; only an explicit server verdict of an invalid signature removes it.
     */
    internal suspend fun migrateStoredCouponTokenIfNeeded() {
        if (isAutomatedTestEnv || !tokenMigrationAttempted.compareAndSet(false, true)) return
        if (prefs == null) return
        val result = entitlementStore.migrateStoredCouponTokenIfNeeded(
            currentState = _proState.value.toEntitlementState(),
            customEndpoints = testBaseUrl?.let { listOf("$it/redeemPromoCode") }
        ) ?: return

        val status = result.status
        _proState.value = result.newState.toProState()
        when (status) {
            CouponRefreshStatus.UPGRADED -> migrationLogger("Coupon token migrated to current signing key")
            CouponRefreshStatus.REVOKED -> migrationLogger("Stored coupon token rejected by server (invalid signature) – removed")
            else -> migrationLogger("Coupon token migration skipped: $status")
        }
    }

    /** Re-evaluates coupon state without dropping an active RevenueCat entitlement from memory. */
    private fun reevaluateKeepingRevenueCat() {
        _proState.value = entitlementStore.reevaluateKeepingRevenueCat(_proState.value.toEntitlementState()).toProState()
    }

    fun trackPaywallImpression() {
        if (isAutomatedTestEnv) return
        try {
            if (Purchases.isConfigured) {
                Purchases.sharedInstance.trackCustomPaywallImpression()
            }
        } catch (_: Exception) {
            // Safe no-op in unit tests or when SDK is uninitialized
        }
    }

    fun getOfferings(onSuccess: (Offerings) -> Unit, onError: (String) -> Unit) {
        try {
            Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
                override fun onReceived(offerings: Offerings) {
                    onSuccess(offerings)
                }

                override fun onError(error: PurchasesError) {
                    onError(error.message)
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Failed to load offerings")
        }
    }

    private val httpClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    internal fun isSandboxPurchase(customerInfo: CustomerInfo): Boolean {
        val activeEntitlement = customerInfo.entitlements["pro"] ?: customerInfo.entitlements["songflip_pro"]
        return activeEntitlement?.isSandbox == true
    }

    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: (isSandbox: Boolean) -> Unit,
        onError: (errorMsg: String, errorCode: String?, underlyingMsg: String?) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        try {
            val params = PurchaseParams.Builder(activity, packageToPurchase).build()
            Purchases.sharedInstance.purchase(
                params,
                object : com.revenuecat.purchases.interfaces.PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        val proState = updateFromCustomerInfo(customerInfo)
                        if (proState.isPro) {
                            onSuccess(isSandboxPurchase(customerInfo))
                        } else {
                            onError("Purchase completed but entitlement not active.", "ENTITLEMENT_NOT_ACTIVE", null)
                        }
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (userCancelled) {
                            onCancelled()
                        } else if (error.code == PurchasesErrorCode.ProductAlreadyPurchasedError) {
                            // Automatically sync purchases when Google Play reports the product is already owned
                            Purchases.sharedInstance.syncPurchases(object : SyncPurchasesCallback {
                                override fun onSuccess(customerInfo: CustomerInfo) {
                                    val syncedState = updateFromCustomerInfo(customerInfo)
                                    if (syncedState.isPro) {
                                        onSuccess(isSandboxPurchase(customerInfo))
                                    } else {
                                        // As a secondary recovery attempt, invoke restorePurchases
                                        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                                            override fun onReceived(restoredInfo: CustomerInfo) {
                                                val restoredState = updateFromCustomerInfo(restoredInfo)
                                                if (restoredState.isPro) {
                                                    onSuccess(isSandboxPurchase(restoredInfo))
                                                } else {
                                                    onError(error.message, error.code.name, error.underlyingErrorMessage)
                                                }
                                            }

                                            override fun onError(restoreErr: PurchasesError) {
                                                onError(error.message, error.code.name, error.underlyingErrorMessage)
                                            }
                                        })
                                    }
                                }

                                override fun onError(error: PurchasesError) {
                                    this@ProManager.restorePurchases(
                                        onSuccess = { onSuccess(false) },
                                        onError = { _ -> onError(error.message, error.code.name, error.underlyingErrorMessage) }
                                    )
                                }
                            })
                        } else {
                            onError(error.message, error.code.name, error.underlyingErrorMessage)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            onError(e.message ?: "Purchase error", "EXCEPTION", e.cause?.message)
        }
    }

    fun restorePurchases(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    val proState = updateFromCustomerInfo(customerInfo)
                    if (proState.isPro) {
                        onSuccess()
                    } else {
                        // Fallback: syncPurchases to catch unacknowledged/out-of-sync store purchases
                        Purchases.sharedInstance.syncPurchases(object : SyncPurchasesCallback {
                            override fun onSuccess(customerInfo: CustomerInfo) {
                                val syncedState = updateFromCustomerInfo(customerInfo)
                                if (syncedState.isPro) {
                                    onSuccess()
                                } else {
                                    onError("No active PRO subscription found.")
                                }
                            }

                            override fun onError(error: PurchasesError) {
                                onError("No active PRO subscription found.")
                            }
                        })
                    }
                }

                override fun onError(error: PurchasesError) {
                    Purchases.sharedInstance.syncPurchases(object : SyncPurchasesCallback {
                        override fun onSuccess(customerInfo: CustomerInfo) {
                            val syncedState = updateFromCustomerInfo(customerInfo)
                            if (syncedState.isPro) {
                                onSuccess()
                            } else {
                                onError(error.message)
                            }
                        }

                        override fun onError(error: PurchasesError) {
                            onError(error.message)
                        }
                    })
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Restore error")
        }
    }

    fun extractCouponCode(rawInput: String): String {
        return de.goork.songflip.core.engine.CouponService.shared.extractCouponCode(rawInput)
    }

    @androidx.annotation.VisibleForTesting
    var testBaseUrl: String? = null

    @androidx.annotation.VisibleForTesting
    var testHttpClient: okhttp3.OkHttpClient? = null

    @androidx.annotation.VisibleForTesting
    fun setTestSharedPreferences(sp: SharedPreferences) {
        prefs = sp
    }

    @androidx.annotation.VisibleForTesting
    fun setTestStableAndroidId(androidId: String?) {
        stableAndroidId = androidId
    }

    suspend fun redeemCoupon(code: String): RedeemResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (prefs == null) return@withContext RedeemResult.INVALID
        val endpoints = testBaseUrl?.let { listOf("$it/redeemPromoCode") }
        val outcome = entitlementStore.redeemCoupon(
            rawCode = code,
            currentState = _proState.value.toEntitlementState(),
            customEndpoints = endpoints
        )
        _proState.value = outcome.newState.toProState()
        if (outcome.status in setOf(
                CouponRedeemStatus.SUCCESS_LIFETIME,
                CouponRedeemStatus.SUCCESS_1YEAR,
                CouponRedeemStatus.SUCCESS_3MONTHS,
                CouponRedeemStatus.SUCCESS_1MONTH
            )
        ) {
            de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemedSuccess(outcome.cleanCode)
        }
        return@withContext outcome.status.toRedeemResult()
    }

    fun hashUrl(rawUrl: String): String {
        val normalized = de.goork.songflip.core.util.UrlUtils.normalizeUrl(rawUrl).trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getUniversalWebShareUrl(rawUrl: String): String {
        val hash = hashUrl(rawUrl)
        val shortId = if (hash.length > 12) hash.substring(0, 12) else hash
        return "https://songflip.link/s/$shortId"
    }

    fun getPlaylistWebShareUrl(rawUrl: String, targetPlatform: String): String {
        val cleanUrl = rawUrl.trim()
        val cleanPlatform = targetPlatform.removeSuffix("_playlist").trim()
        val hash = hashUrl("$cleanUrl|$cleanPlatform")
        val shortId = if (hash.length > 10) hash.substring(0, 10) else hash
        return "https://songflip.link/p/$shortId"
    }

    fun warmupUniversalShare(rawUrl: String) {
        backgroundScope.launch {
            try {
                de.goork.songflip.core.engine.SongLinkEngine.shared.queryL2ServerCache(rawUrl, "universal", getAuthToken())
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Warmup only pre-fills the cache; the share link works without it.
                android.util.Log.w("ProManager", "Universal share warmup failed: ${t.message}")
            }
        }
    }

    fun resetProForTesting() {
        isInitialized.set(false)
        isLastCustomerInfoSandbox = false
        prefs?.edit()?.clear()?.apply()
        _proState.value = ProState(isPro = false, proType = "")
        tokenMigrationAttempted.set(false)
    }
}
