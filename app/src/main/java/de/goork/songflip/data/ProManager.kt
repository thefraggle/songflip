package de.goork.songflip.data

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    MAX_REACHED,
    INACTIVE,
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
    private const val KEY_COUPON_EXPIRATION = "pro_coupon_expiration"

    // RevenueCat Configuration
    private const val REVENUECAT_API_KEY = "goog_mzQwhCFXsoDHGcFDxkzsIqBcHfO"
    const val ENTITLEMENT_PRO = "pro"

    private var prefs: SharedPreferences? = null
    private val _proState = MutableStateFlow(ProState())
    val proState: StateFlow<ProState> = _proState.asStateFlow()

    val isPro: Boolean
        get() = _proState.value.isPro

    fun getAppUserId(): String {
        return try {
            Purchases.sharedInstance.appUserID
        } catch (e: Exception) {
            "anonymous_local_user"
        }
    }

    fun getAuthToken(): String {
        val state = _proState.value
        return if (state.proType.endsWith("_coupon")) {
            "coupon:${state.proType}"
        } else {
            getAppUserId()
        }
    }

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        // Initialize RevenueCat SDK
        try {
            Purchases.logLevel = LogLevel.DEBUG
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
                    evaluateProState(revenueCatActive = false)
                }
            })
        } catch (e: Exception) {
            evaluateProState(revenueCatActive = false)
        }
    }

    private fun updateFromCustomerInfo(customerInfo: CustomerInfo) {
        val hasExplicitEntitlement = customerInfo.entitlements["pro"]?.isActive == true ||
                                     customerInfo.entitlements["songflip_pro"]?.isActive == true
        val hasAnyActiveEntitlement = customerInfo.entitlements.active.isNotEmpty()
        val hasActiveSubscription = customerInfo.activeSubscriptions.isNotEmpty()
        val hasNonSubTransaction = customerInfo.nonSubscriptionTransactions.isNotEmpty()
        val hasProEntitlement = hasExplicitEntitlement || hasAnyActiveEntitlement || hasActiveSubscription || hasNonSubTransaction

        val activeEntitlement = customerInfo.entitlements["pro"] ?: customerInfo.entitlements["songflip_pro"] ?: customerInfo.entitlements.active.values.firstOrNull()
        val expDate = activeEntitlement?.expirationDate?.time ?: customerInfo.allExpirationDatesByProduct.values.mapNotNull { it?.time }.maxOrNull()
        val isLifetimeRc = hasNonSubTransaction || (hasProEntitlement && expDate == null)

        evaluateProState(
            revenueCatActive = hasProEntitlement,
            rcExpirationMillis = expDate,
            rcIsLifetime = isLifetimeRc
        )
    }

    @Synchronized
    private fun evaluateProState(
        revenueCatActive: Boolean,
        rcExpirationMillis: Long? = null,
        rcIsLifetime: Boolean = false
    ) {
        val sp = prefs ?: return
        val couponType = sp.getString(KEY_COUPON_TYPE, null)
        val expiration = sp.getLong(KEY_COUPON_EXPIRATION, 0L)
        val now = System.currentTimeMillis()

        if (revenueCatActive) {
            _proState.value = ProState(
                isPro = true,
                proType = if (rcIsLifetime) "revenuecat_lifetime" else "revenuecat_subscription",
                expirationDate = if (rcIsLifetime) null else rcExpirationMillis
            )
            return
        }

        if (couponType == "lifetime") {
            _proState.value = ProState(isPro = true, proType = "lifetime_coupon")
            return
        }

        if (!couponType.isNullOrBlank() && expiration > now) {
            val normalizedType = if (couponType.endsWith("_coupon")) couponType else "${couponType}_coupon"
            _proState.value = ProState(isPro = true, proType = normalizedType, expirationDate = expiration)
            return
        }

        // Neither RevenueCat nor active coupon
        _proState.value = ProState(isPro = false)
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

    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val params = PurchaseParams.Builder(activity, packageToPurchase).build()
            Purchases.sharedInstance.purchase(
                params,
                object : com.revenuecat.purchases.interfaces.PurchaseCallback {
                    override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                        updateFromCustomerInfo(customerInfo)
                        if (isPro) {
                            onSuccess()
                        } else {
                            onError("Purchase completed but entitlement not active.")
                        }
                    }

                    override fun onError(error: PurchasesError, userCancelled: Boolean) {
                        if (!userCancelled) {
                            onError(error.message)
                        }
                    }
                }
            )
        } catch (e: Exception) {
            onError(e.message ?: "Purchase error")
        }
    }

    fun restorePurchases(onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    updateFromCustomerInfo(customerInfo)
                    if (isPro) {
                        onSuccess()
                    } else {
                        onError("No active PRO subscription found.")
                    }
                }

                override fun onError(error: PurchasesError) {
                    onError(error.message)
                }
            })
        } catch (e: Exception) {
            onError(e.message ?: "Restore error")
        }
    }

    suspend fun redeemCoupon(code: String): RedeemResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isEmpty()) return@withContext RedeemResult.INVALID

        val sp = prefs ?: return@withContext RedeemResult.INVALID

        val endpoints = listOf(
            "https://cache.songflip.link/redeemPromoCode",
            "https://songflip-web.web.app/redeemPromoCode"
        )

        val jsonBody = org.json.JSONObject().apply {
            put("code", cleanCode)
        }.toString()

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toRequestBody(mediaType)

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        for (endpoint in endpoints) {
            try {
                val request = okhttp3.Request.Builder()
                    .url(endpoint)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: ""
                val isOk = response.isSuccessful
                response.close()

                if (isOk) {
                    val resJson = org.json.JSONObject(bodyStr)
                    val type = resJson.optString("type", "1month").lowercase()
                    val expTimestamp = if (resJson.has("expirationTimestamp") && !resJson.isNull("expirationTimestamp")) {
                        resJson.optLong("expirationTimestamp")
                    } else null

                    if (type == "lifetime") {
                        if (_proState.value.isPro && _proState.value.proType == "lifetime_coupon") {
                            return@withContext RedeemResult.ALREADY_ACTIVE
                        }
                        sp.edit()
                            .putString(KEY_COUPON_TYPE, "lifetime")
                            .remove(KEY_COUPON_EXPIRATION)
                            .apply()
                        evaluateProState(revenueCatActive = false)
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemedSuccess(cleanCode)
                        return@withContext RedeemResult.SUCCESS_LIFETIME
                    } else {
                        val durationDays = when (type) {
                            "1year", "annual" -> 365L
                            "3months" -> 90L
                            else -> 30L
                        }
                        val expireTime = expTimestamp ?: (System.currentTimeMillis() + durationDays * 24 * 60 * 60 * 1000L)

                        sp.edit()
                            .putString(KEY_COUPON_TYPE, type)
                            .putLong(KEY_COUPON_EXPIRATION, expireTime)
                            .apply()
                        evaluateProState(revenueCatActive = false)
                        de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemedSuccess(cleanCode)

                        return@withContext when (type) {
                            "1year", "annual" -> RedeemResult.SUCCESS_1YEAR
                            "3months" -> RedeemResult.SUCCESS_3MONTHS
                            else -> RedeemResult.SUCCESS_1MONTH
                        }
                    }
                } else {
                    val errJson = try { org.json.JSONObject(bodyStr) } catch (e: Exception) { null }
                    val errCode = errJson?.optString("error", "") ?: ""
                    return@withContext when (errCode) {
                        "MAX_REDEMPTIONS_REACHED" -> RedeemResult.MAX_REACHED
                        "CODE_INACTIVE" -> RedeemResult.INACTIVE
                        "CODE_EXPIRED" -> RedeemResult.INACTIVE
                        else -> RedeemResult.INVALID
                    }
                }
            } catch (e: Exception) {
                // Try next endpoint
            }
        }

        return@withContext RedeemResult.NETWORK_ERROR
    }

    fun hashUrl(rawUrl: String): String {
        val normalized = rawUrl.trim().lowercase()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    fun getUniversalWebShareUrl(rawUrl: String): String {
        val hash = hashUrl(rawUrl)
        val shortId = if (hash.length > 8) hash.substring(0, 8) else hash
        return "https://songflip.link/s/$shortId"
    }

    fun warmupUniversalShare(rawUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                OdesliRepository().queryL2ServerCache(rawUrl, "universal")
            } catch (_: Throwable) {}
        }
    }

    fun resetProForTesting() {
        prefs?.edit()?.clear()?.apply()
        _proState.value = ProState(isPro = false, proType = "")
    }
}
