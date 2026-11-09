package de.goork.songflip.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import de.goork.songflip.data.ProManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProPaywallBottomSheet(
    onDismissRequest: () -> Unit,
    initialShowPromo: Boolean = false,
    source: String = "paywall_sheet"
) {
    LaunchedEffect(source) {
        ProManager.trackPaywallImpression()
    }

    var selectedOffering by remember { mutableStateOf<Offering?>(null) }
    var offeringsLoaded by remember { mutableStateOf(!initialShowPromo) }

    LaunchedEffect(initialShowPromo) {
        if (initialShowPromo) {
            withContext(Dispatchers.IO) {
                try {
                    ProManager.getOfferings(
                        onSuccess = { offerings ->
                            selectedOffering = offerings["lifetime_promo_offering"]
                                ?: offerings["promo_lifetime_offering"]
                                ?: offerings.current
                            offeringsLoaded = true
                        },
                        onError = {
                            offeringsLoaded = true
                        }
                    )
                } catch (_: Exception) {
                    offeringsLoaded = true
                }
            }
        }
    }

    if (offeringsLoaded) {
        val listener = remember {
            object : PaywallListener {
                override fun onPurchaseCompleted(customerInfo: CustomerInfo, storeTransaction: StoreTransaction) {
                    ProManager.updateFromCustomerInfo(customerInfo)
                    onDismissRequest()
                }

                override fun onRestoreCompleted(customerInfo: CustomerInfo) {
                    ProManager.updateFromCustomerInfo(customerInfo)
                    if (customerInfo.entitlements["pro"]?.isActive == true ||
                        customerInfo.entitlements["songflip_pro"]?.isActive == true) {
                        onDismissRequest()
                    }
                }
            }
        }

        val options = remember(selectedOffering) {
            PaywallDialogOptions.Builder()
                .setDismissRequest(onDismissRequest)
                .setListener(listener)
                .apply {
                    if (selectedOffering != null) {
                        setOffering(selectedOffering)
                    }
                }
                .build()
        }

        PaywallDialog(options)
    }
}

internal fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
