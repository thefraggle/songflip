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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

enum class RedeemResult {
    SUCCESS_LIFETIME,
    SUCCESS_1YEAR,
    INVALID,
    ALREADY_ACTIVE
}

data class ProState(
    val isPro: Boolean = false,
    val proType: String = "", // "revenuecat", "lifetime_coupon", "annual_coupon"
    val expirationDate: Long? = null
)

object ProManager {

    private const val PREFS_NAME = "songflip_pro_prefs"
    private const val KEY_COUPON_TYPE = "pro_coupon_type"
    private const val KEY_COUPON_EXPIRATION = "pro_coupon_expiration"

    // RevenueCat Configuration
    private const val REVENUECAT_API_KEY = "goog_mzQwhCFXsoDHGcFDxkzsIqBcHfO"
    const val ENTITLEMENT_PRO = "pro"

    // SHA-256 Hashes of Secret Codes (Raw strings are not stored in repository)
    // "SONGFLIP_FOUNDER_2026" -> Lifetime Free
    private const val HASH_FOUNDER_LIFETIME = "b6375a62ef6661d3bfe3bcb1b54aec2812d77a288e66e2f2180f2a78c5836afe"
    // "SONGFLIP_VIP_2026" -> 1 Year Free
    private const val HASH_VIP_1YEAR = "f0d7b3ce7f1899be88ad44f102ad27cc9998cb501cb9bae4eeb52226923a37e6"

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
        val hasProEntitlement = customerInfo.entitlements["pro"]?.isActive == true ||
                                customerInfo.entitlements["songflip_pro"]?.isActive == true
        evaluateProState(revenueCatActive = hasProEntitlement)
    }

    @Synchronized
    private fun evaluateProState(revenueCatActive: Boolean) {
        val sp = prefs ?: return
        val couponType = sp.getString(KEY_COUPON_TYPE, null)
        val expiration = sp.getLong(KEY_COUPON_EXPIRATION, 0L)
        val now = System.currentTimeMillis()

        if (revenueCatActive) {
            _proState.value = ProState(isPro = true, proType = "revenuecat")
            return
        }

        if (couponType == "lifetime") {
            _proState.value = ProState(isPro = true, proType = "lifetime_coupon")
            return
        }

        if (couponType == "annual" && expiration > now) {
            _proState.value = ProState(isPro = true, proType = "annual_coupon", expirationDate = expiration)
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

    fun redeemCoupon(code: String): RedeemResult {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isEmpty()) return RedeemResult.INVALID

        val hash = sha256(cleanCode)
        val sp = prefs ?: return RedeemResult.INVALID

        if (hash == HASH_FOUNDER_LIFETIME) {
            if (_proState.value.isPro && _proState.value.proType == "lifetime_coupon") {
                return RedeemResult.ALREADY_ACTIVE
            }
            sp.edit()
                .putString(KEY_COUPON_TYPE, "lifetime")
                .remove(KEY_COUPON_EXPIRATION)
                .apply()
            evaluateProState(revenueCatActive = false)
            return RedeemResult.SUCCESS_LIFETIME
        }

        if (hash == HASH_VIP_1YEAR) {
            val oneYearMs = 365L * 24 * 60 * 60 * 1000L
            val expireTime = System.currentTimeMillis() + oneYearMs
            sp.edit()
                .putString(KEY_COUPON_TYPE, "annual")
                .putLong(KEY_COUPON_EXPIRATION, expireTime)
                .apply()
            evaluateProState(revenueCatActive = false)
            return RedeemResult.SUCCESS_1YEAR
        }

        return RedeemResult.INVALID
    }

    fun resetProForTesting() {
        prefs?.edit()?.clear()?.apply()
        _proState.value = ProState(isPro = false, proType = "")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
