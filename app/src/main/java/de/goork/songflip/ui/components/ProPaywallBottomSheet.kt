package de.goork.songflip.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.models.StoreTransaction
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialog
import com.revenuecat.purchases.ui.revenuecatui.PaywallDialogOptions
import com.revenuecat.purchases.ui.revenuecatui.PaywallListener
import de.goork.songflip.R
import de.goork.songflip.data.ProManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallBottomSheet(
    onDismissRequest: () -> Unit,
    initialShowPromo: Boolean = false,
    source: String = "paywall_sheet"
) {
    val proState by ProManager.proState.collectAsState()

    if (proState.isPro) {
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Row with Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("💎", fontSize = 18.sp)
                            }
                        }
                        Text(
                            text = stringResource(R.string.pro_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.pause_cancel),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Active PRO Status Card
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("🎉", fontSize = 40.sp)
                        Text(
                            text = stringResource(R.string.pro_active_status),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val subText = when (proState.proType) {
                            "lifetime_coupon", "revenuecat_lifetime" -> stringResource(R.string.pro_active_lifetime)
                            "1year_coupon", "annual_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_annual, dateStr)
                            }
                            "3months_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_3months, dateStr)
                            }
                            "1month_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_1month, dateStr)
                            }
                            "revenuecat_subscription", "revenuecat" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                }
                                if (!dateStr.isNullOrBlank()) {
                                    stringResource(R.string.pro_active_annual, dateStr)
                                } else {
                                    stringResource(R.string.pro_active_lifetime)
                                }
                            }
                            else -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                }
                                if (!dateStr.isNullOrBlank()) {
                                    stringResource(R.string.pro_active_annual, dateStr)
                                } else {
                                    stringResource(R.string.pro_active_lifetime)
                                }
                            }
                        }
                        Text(
                            text = subText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebuggable) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ProManager.resetProForTesting()
                            Toast.makeText(context, context.getString(R.string.pro_debug_reset_status), Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.pro_debug_reset_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        return
    }

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
