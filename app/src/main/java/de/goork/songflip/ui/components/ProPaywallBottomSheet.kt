package de.goork.songflip.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.models.Price
import de.goork.songflip.R
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.RedeemResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

enum class SelectedProTier {
    ANNUAL,
    MONTHLY,
    LIFETIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallBottomSheet(
    onDismissRequest: () -> Unit,
    initialShowPromo: Boolean = false
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val proState by ProManager.proState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    var selectedTier by remember { mutableStateOf(SelectedProTier.LIFETIME) }
    var hasUserChosenTier by remember { mutableStateOf(false) }
    var currentOffering by remember { mutableStateOf<Offering?>(null) }
    val paywallLayout = PaywallPricing.resolveLayout(currentOffering?.metadata?.get(PaywallPricing.LAYOUT_METADATA_KEY))
    var availablePackages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var isPurchasing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    // Coupon code state
    var showCouponInput by remember { mutableStateOf(initialShowPromo) }
    var couponCodeText by remember { mutableStateOf("") }
    var isRedeemingCoupon by remember { mutableStateOf(false) }

    var isLoadingOfferings by remember { mutableStateOf(true) }
    var offeringsError by remember { mutableStateOf(false) }

    fun loadOfferings() {
        isLoadingOfferings = true
        offeringsError = false
        ProManager.getOfferings(
            onSuccess = { offerings ->
                currentOffering = offerings.current
                availablePackages = offerings.current?.availablePackages ?: emptyList()
                offeringsError = availablePackages.isEmpty()
                isLoadingOfferings = false
            },
            onError = {
                isLoadingOfferings = false
                offeringsError = true
            }
        )
    }

    LaunchedEffect(Unit) {
        loadOfferings()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
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

            if (proState.isPro) {
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

                val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (isDebuggable) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            ProManager.resetProForTesting()
                            Toast.makeText(context, context.getString(R.string.pro_debug_reset_status), Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.pro_debug_reset_button),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Subtitle & Feature Highlights
                Text(
                    text = stringResource(R.string.pro_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProFeatureRow(
                            title = stringResource(R.string.pro_feature_playlists_title),
                            description = stringResource(R.string.pro_feature_playlists_desc)
                        )
                        ProFeatureRow(
                            title = stringResource(R.string.pro_feature_history_title),
                            description = stringResource(R.string.pro_feature_history_desc)
                        )
                        ProFeatureRow(
                            title = stringResource(R.string.pro_feature_cache_title),
                            description = stringResource(R.string.pro_feature_cache_desc)
                        )
                        ProFeatureRow(
                            title = stringResource(R.string.pro_feature_links_title),
                            description = stringResource(R.string.pro_feature_links_desc)
                        )
                        ProFeatureRow(
                            title = stringResource(R.string.pro_feature_future_title),
                            description = stringResource(R.string.pro_feature_future_desc)
                        )
                    }
                }

                if (offeringsError && availablePackages.isEmpty() && !isLoadingOfferings) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.pro_offerings_error),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            TextButton(onClick = { loadOfferings() }) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }

                val annualPackage = availablePackages.firstOrNull { it.packageType == PackageType.ANNUAL }
                val monthlyPackage = availablePackages.firstOrNull { it.packageType == PackageType.MONTHLY }
                val lifetimePackage = availablePackages.firstOrNull { it.packageType == PackageType.LIFETIME }

                val isLifetimeSale = isLifetimeSaleActive(lifetimePackage, annualPackage, monthlyPackage, currentOffering)
                val lifetimeOriginalStrike = if (isLifetimeSale) formatOriginalStrikePrice(lifetimePackage) else null

                val isAnnualIntro = isAnnualIntroOfferActive(annualPackage)
                val annualEffectivePrice = getEffectivePrice(annualPackage)
                val monthlyRegularPrice = monthlyPackage?.product?.price
                // One clear message: savings badge + "x / month (instead of y)". The old
                // "33 % OFF" + "4 months free" pair said the same thing twice and was hardcoded.
                val annualSavingsPercent = PaywallPricing.annualSavingsPercent(
                    annualMicros = annualEffectivePrice?.amountMicros,
                    annualCurrency = annualEffectivePrice?.currencyCode,
                    monthlyMicros = monthlyRegularPrice?.amountMicros,
                    monthlyCurrency = monthlyRegularPrice?.currencyCode
                )
                val annualBadge = annualSavingsPercent?.let { stringResource(R.string.pro_save_badge, it) }
                val annualSub = if (annualPackage != null && monthlyRegularPrice != null && annualSavingsPercent != null) {
                    val perMonth = formatMonthlyPrice(annualPackage)
                    if (isAnnualIntro) {
                        stringResource(R.string.pro_price_annual_vs_monthly_intro, perMonth, monthlyRegularPrice.formatted)
                    } else {
                        stringResource(R.string.pro_price_annual_vs_monthly, perMonth, monthlyRegularPrice.formatted)
                    }
                } else {
                    null
                }

                val tierOrder = PaywallPricing.resolveTierOrder(paywallLayout, isLifetimeSale)
                // Preselect the leading card once offerings arrive, but never override a user's tap.
                LaunchedEffect(tierOrder.first()) {
                    if (!hasUserChosenTier) selectedTier = tierOrder.first()
                }

                // Lifetime keeps "POPULAR • ONE-TIME" only while it leads; at the bottom "POPULAR" would
                // compete with the highlighted annual card, so it just states the purchase type.
                val lifetimeBadge = when {
                    isLifetimeSale -> stringResource(R.string.pro_lifetime_sale_badge)
                    tierOrder.first() == SelectedProTier.LIFETIME -> stringResource(R.string.pro_lifetime_badge)
                    else -> stringResource(R.string.pro_lifetime_onetime_badge)
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    tierOrder.forEach { tier ->
                        val onTierClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            hasUserChosenTier = true
                            selectedTier = tier
                        }
                        when (tier) {
                            SelectedProTier.LIFETIME -> ProTierCard(
                                title = stringResource(R.string.pro_tier_lifetime),
                                price = getEffectivePrice(lifetimePackage)?.formatted ?: "—",
                                originalPrice = lifetimeOriginalStrike,
                                subtitle = if (isLifetimeSale) stringResource(R.string.pro_lifetime_sale_sub) else null,
                                badge = lifetimeBadge,
                                isSelected = selectedTier == SelectedProTier.LIFETIME,
                                onClick = onTierClick
                            )
                            SelectedProTier.ANNUAL -> ProTierCard(
                                title = stringResource(R.string.pro_tier_annual),
                                price = getEffectivePrice(annualPackage)?.formatted ?: "—",
                                subtitle = annualSub,
                                badge = annualBadge,
                                isSelected = selectedTier == SelectedProTier.ANNUAL,
                                onClick = onTierClick
                            )
                            SelectedProTier.MONTHLY -> ProTierCard(
                                title = stringResource(R.string.pro_tier_monthly),
                                price = monthlyPackage?.product?.price?.formatted ?: "—",
                                subtitle = null,
                                badge = null,
                                isSelected = selectedTier == SelectedProTier.MONTHLY,
                                onClick = onTierClick
                            )
                        }
                    }
                }

                val selectedPackage = when (selectedTier) {
                    SelectedProTier.LIFETIME -> lifetimePackage
                    SelectedProTier.ANNUAL -> annualPackage
                    SelectedProTier.MONTHLY -> monthlyPackage
                }

                val isButtonEnabled = !isPurchasing && !isRestoring && !isLoadingOfferings && selectedPackage != null

                // Main CTA Button
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null && selectedPackage != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isPurchasing = true
                            ProManager.purchase(
                                activity = activity,
                                packageToPurchase = selectedPackage,
                                onSuccess = {
                                    isPurchasing = false
                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackProPurchased(selectedPackage.identifier, layout = paywallLayout.metadataValue)
                                    Toast.makeText(context, context.getString(R.string.pro_active_status), Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                },
                                onError = { errorMsg, errorCode, underlyingMsg ->
                                    isPurchasing = false
                                    de.goork.songflip.core.analytics.AptabaseClient.shared.trackProPurchaseFailed(
                                        error = errorMsg,
                                        errorCode = errorCode,
                                        underlyingError = underlyingMsg
                                    )
                                    val userFriendlyMsg = if (errorMsg.contains("NETWORK", ignoreCase = true) || errorMsg.contains("resolve host", ignoreCase = true) || errorCode == "NetworkError") {
                                        context.getString(R.string.pro_coupon_network_error)
                                    } else if (errorMsg.contains("PLAY_STORE", ignoreCase = true) || errorMsg.contains("STORE_PROBLEM", ignoreCase = true) || errorCode == "StoreProblemError") {
                                        context.getString(R.string.pro_play_store_unavailable)
                                    } else {
                                        context.getString(R.string.pro_purchase_failed)
                                    }
                                    Toast.makeText(context, userFriendlyMsg, Toast.LENGTH_LONG).show()
                                },
                                onCancelled = {
                                    isPurchasing = false
                                }
                            )
                        } else if (activity == null) {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackProPurchaseFailed(
                                error = "activity_not_found",
                                errorCode = "ACTIVITY_NOT_FOUND"
                            )
                            Toast.makeText(context, context.getString(R.string.pro_purchase_failed), Toast.LENGTH_SHORT).show()
                        } else if (selectedPackage == null) {
                            de.goork.songflip.core.analytics.AptabaseClient.shared.trackProPurchaseFailed(
                                error = "no_package_available",
                                errorCode = "NO_PACKAGE_AVAILABLE"
                            )
                            Toast.makeText(context, context.getString(R.string.pro_play_store_unavailable), Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 54.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    enabled = isButtonEnabled
                ) {
                    if (isPurchasing || isLoadingOfferings) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPurchasing) stringResource(R.string.pro_processing) else stringResource(R.string.redirecting_toast),
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        val priceText = getEffectivePrice(selectedPackage)?.formatted
                        val buttonText = if (!priceText.isNullOrBlank()) {
                            when (selectedTier) {
                                SelectedProTier.LIFETIME -> stringResource(R.string.pro_btn_lifetime, priceText)
                                SelectedProTier.ANNUAL -> stringResource(R.string.pro_btn_annual, priceText)
                                SelectedProTier.MONTHLY -> stringResource(R.string.pro_btn_monthly, priceText)
                            }
                        } else {
                            stringResource(R.string.pro_title)
                        }
                        Text(
                            text = buttonText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Restore Purchases Button
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isRestoring = true
                        ProManager.restorePurchases(
                            onSuccess = {
                                isRestoring = false
                                Toast.makeText(context, context.getString(R.string.pro_active_status), Toast.LENGTH_SHORT).show()
                                onDismissRequest()
                            },
                            onError = { errorMsg ->
                                isRestoring = false
                                val userFriendlyMsg = if (errorMsg.contains("NETWORK", ignoreCase = true)) {
                                    context.getString(R.string.pro_coupon_network_error)
                                } else {
                                    context.getString(R.string.pro_restore_no_subscription)
                                }
                                Toast.makeText(context, userFriendlyMsg, Toast.LENGTH_LONG).show()
                            }
                        )
                    },
                    enabled = !isPurchasing && !isRestoring
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 1.5.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = stringResource(R.string.pro_btn_restore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Expandable Promo Code Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    TextButton(
                        onClick = { showCouponInput = !showCouponInput }
                    ) {
                        Icon(
                            imageVector = if (showCouponInput) Icons.Outlined.ExpandLess else Icons.Outlined.ConfirmationNumber,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.pro_coupon_toggle),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }

                    AnimatedVisibility(visible = showCouponInput) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = couponCodeText,
                                onValueChange = { couponCodeText = ProManager.extractCouponCode(it) },
                                placeholder = { Text(stringResource(R.string.pro_coupon_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                enabled = !isRedeemingCoupon && couponCodeText.isNotBlank(),
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isRedeemingCoupon = true
                                    coroutineScope.launch {
                                        val result = ProManager.redeemCoupon(couponCodeText)
                                        isRedeemingCoupon = false
                                        when (result) {
                                            RedeemResult.SUCCESS_LIFETIME -> {
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_success_lifetime), Toast.LENGTH_LONG).show()
                                                couponCodeText = ""
                                                onDismissRequest()
                                            }
                                            RedeemResult.SUCCESS_1YEAR -> {
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_success_1year), Toast.LENGTH_LONG).show()
                                                couponCodeText = ""
                                                onDismissRequest()
                                            }
                                            RedeemResult.SUCCESS_3MONTHS -> {
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_success_3months), Toast.LENGTH_LONG).show()
                                                couponCodeText = ""
                                                onDismissRequest()
                                            }
                                            RedeemResult.SUCCESS_1MONTH -> {
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_success_1month), Toast.LENGTH_LONG).show()
                                                couponCodeText = ""
                                                onDismissRequest()
                                            }
                                            RedeemResult.ALREADY_ACTIVE -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "already_active")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_already_active), Toast.LENGTH_SHORT).show()
                                            }
                                            RedeemResult.ALREADY_REDEEMED -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "already_redeemed_on_device")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_already_redeemed), Toast.LENGTH_LONG).show()
                                            }
                                            RedeemResult.MAX_REACHED -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "max_reached")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_max_reached), Toast.LENGTH_LONG).show()
                                            }
                                            RedeemResult.INACTIVE -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "inactive")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_inactive), Toast.LENGTH_LONG).show()
                                            }
                                            RedeemResult.RATE_LIMITED -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "rate_limited")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_rate_limited), Toast.LENGTH_LONG).show()
                                            }
                                            RedeemResult.NETWORK_ERROR -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "network_error")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_network_error), Toast.LENGTH_SHORT).show()
                                            }
                                            RedeemResult.INVALID -> {
                                                de.goork.songflip.core.analytics.AptabaseClient.shared.trackPromoRedeemFailed(couponCodeText, "invalid")
                                                Toast.makeText(context, context.getString(R.string.pro_coupon_invalid), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isRedeemingCoupon) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(stringResource(R.string.pro_coupon_btn_redeem), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

@Composable
fun ProFeatureRow(title: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(title)
                }
                append(": ")
                append(description)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProTierCard(
    title: String,
    price: String,
    originalPrice: String? = null,
    subtitle: String?,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val bgColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                if (originalPrice != null) {
                    Text(
                        text = originalPrice,
                        style = MaterialTheme.typography.labelSmall.copy(
                            textDecoration = TextDecoration.LineThrough
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = price,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun isLifetimeSaleActive(
    lifetimePackage: Package?,
    annualPackage: Package?,
    monthlyPackage: Package?,
    currentOffering: Offering? = null
): Boolean {
    if (lifetimePackage == null) return false
    // 1. Offering Metadata check from RevenueCat Dashboard (Master Switch)
    val metaSale = currentOffering?.metadata?.get("is_lifetime_sale") as? Boolean
        ?: (currentOffering?.metadata?.get("is_lifetime_sale") as? String)?.toBooleanStrictOrNull()
    if (metaSale == true) return true

    val lifetimeMicros = getEffectivePrice(lifetimePackage)?.amountMicros ?: return false

    // 2. Compare against BASE regular annual price (product.price, ignoring 1st year intro 50% discount)
    val regularAnnualMicros = annualPackage?.product?.price?.amountMicros
    if (regularAnnualMicros != null && regularAnnualMicros > 0) {
        // Regular lifetime (~19.99€) is ~2.5x regular annual (~7.99€-9.99€).
        // Discounted lifetime (~9.99€) is <= 1.5x regular annual (~11.98€).
        if (lifetimeMicros <= (regularAnnualMicros * 1.5)) return true
    }

    // 3. Fallback comparison against monthly price (regular lifetime 19.99€ is ~20x of 0.99€ monthly; on sale 9.99€ it is ~10x)
    val regularMonthlyMicros = monthlyPackage?.product?.price?.amountMicros
    if (regularMonthlyMicros != null && regularMonthlyMicros > 0) {
        // On sale (10x monthly), lifetime is <= 13x monthly (~12.87€)
        if (lifetimeMicros <= (regularMonthlyMicros * 13.0)) return true
    }

    return false
}

private const val PAYWALL_TAG = "ProPaywall"

private fun formatOriginalStrikePrice(pkg: Package?): String? {
    val price = getEffectivePrice(pkg) ?: return null
    return PaywallPricing.formatSaleOriginal(price.amountMicros, price.currencyCode)
        ?: run {
            // Without a valid currency we cannot render a trustworthy strike price → hide it.
            Log.w(PAYWALL_TAG, "Unknown currency '${price.currencyCode}', hiding lifetime strike price")
            null
        }
}

private fun getEffectivePrice(pkg: Package?): Price? {
    if (pkg == null) return null
    val introPrice = pkg.product.defaultOption?.pricingPhases?.firstOrNull()?.price
    return introPrice ?: pkg.product.price
}

private fun isAnnualIntroOfferActive(annualPackage: Package?): Boolean {
    if (annualPackage == null) return false
    val defaultOption = annualPackage.product.defaultOption ?: return false
    val introPhase = defaultOption.introPhase
    if (introPhase != null && introPhase.price.amountMicros < annualPackage.product.price.amountMicros) {
        return true
    }
    val phases = defaultOption.pricingPhases
    if (phases.size > 1) {
        val firstPhase = phases.firstOrNull()
        if (firstPhase != null && firstPhase.price.amountMicros < annualPackage.product.price.amountMicros) {
            return true
        }
    }
    return false
}

private fun formatMonthlyPrice(annualPackage: Package): String {
    val price = getEffectivePrice(annualPackage) ?: annualPackage.product.price
    return PaywallPricing.formatPerMonth(price.amountMicros, price.currencyCode)
        ?: run {
            Log.w(PAYWALL_TAG, "Unknown currency '${price.currencyCode}', falling back to plain monthly amount")
            String.format(Locale.getDefault(), "%.2f %s", price.amountMicros / 12.0 / 1_000_000.0, price.currencyCode)
        }
}

