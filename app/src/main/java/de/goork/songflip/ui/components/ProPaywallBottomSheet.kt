package de.goork.songflip.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import de.goork.songflip.R
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.RedeemResult
import java.text.DateFormat
import java.util.Date

enum class SelectedProTier {
    ANNUAL,
    MONTHLY,
    LIFETIME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProPaywallBottomSheet(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val proState by ProManager.proState.collectAsState()
    var selectedTier by remember { mutableStateOf(SelectedProTier.ANNUAL) }
    var availablePackages by remember { mutableStateOf<List<Package>>(emptyList()) }

    // Coupon code state
    var showCouponInput by remember { mutableStateOf(false) }
    var couponCodeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        ProManager.getOfferings(
            onSuccess = { offerings ->
                availablePackages = offerings.current?.availablePackages ?: emptyList()
            },
            onError = { /* fallback to default prices */ }
        )
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
                            "lifetime_coupon" -> stringResource(R.string.pro_active_lifetime)
                            "annual_coupon" -> {
                                val dateStr = proState.expirationDate?.let {
                                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
                                } ?: ""
                                stringResource(R.string.pro_active_annual, dateStr)
                            }
                            else -> stringResource(R.string.pro_active_lifetime)
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
                            Toast.makeText(context, "PRO Status zurückgesetzt (Debug)", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = "PRO-Status zurücksetzen (Debug)",
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
                        ProFeatureRow(text = stringResource(R.string.pro_feature_history))
                        ProFeatureRow(text = stringResource(R.string.pro_feature_support))
                        ProFeatureRow(text = stringResource(R.string.pro_feature_future))
                    }
                }

                // 3 Tier Pricing Cards
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Annual (Bestseller)
                    ProTierCard(
                        title = stringResource(R.string.pro_tier_annual),
                        price = getPackagePrice(availablePackages, PackageType.ANNUAL) ?: stringResource(R.string.pro_price_annual_default),
                        subtitle = stringResource(R.string.pro_price_annual_sub),
                        badge = stringResource(R.string.pro_bestseller_badge),
                        isSelected = selectedTier == SelectedProTier.ANNUAL,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTier = SelectedProTier.ANNUAL
                        }
                    )

                    // 2. Monthly
                    ProTierCard(
                        title = stringResource(R.string.pro_tier_monthly),
                        price = getPackagePrice(availablePackages, PackageType.MONTHLY) ?: stringResource(R.string.pro_price_monthly_default),
                        subtitle = null,
                        badge = null,
                        isSelected = selectedTier == SelectedProTier.MONTHLY,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTier = SelectedProTier.MONTHLY
                        }
                    )

                    // 3. Lifetime
                    ProTierCard(
                        title = stringResource(R.string.pro_tier_lifetime),
                        price = getPackagePrice(availablePackages, PackageType.LIFETIME) ?: stringResource(R.string.pro_price_lifetime_default),
                        subtitle = null,
                        badge = stringResource(R.string.pro_lifetime_badge),
                        isSelected = selectedTier == SelectedProTier.LIFETIME,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTier = SelectedProTier.LIFETIME
                        }
                    )
                }

                // Main CTA Button (Disabled / Coming Soon until products live in Play Store)
                Button(
                    onClick = { /* Disabled - Coming Soon */ },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = false
                ) {
                    Text(
                        text = stringResource(R.string.pro_btn_coming_soon),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                // Restore Purchases Button (Disabled until live)
                TextButton(
                    onClick = { /* Disabled */ },
                    enabled = false
                ) {
                    Text(
                        text = stringResource(R.string.pro_btn_restore),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                                onValueChange = { couponCodeText = it },
                                placeholder = { Text(stringResource(R.string.pro_coupon_hint)) },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (ProManager.redeemCoupon(couponCodeText)) {
                                        RedeemResult.SUCCESS_LIFETIME -> {
                                            Toast.makeText(context, context.getString(R.string.pro_coupon_success_lifetime), Toast.LENGTH_LONG).show()
                                            couponCodeText = ""
                                        }
                                        RedeemResult.SUCCESS_1YEAR -> {
                                            Toast.makeText(context, context.getString(R.string.pro_coupon_success_1year), Toast.LENGTH_LONG).show()
                                            couponCodeText = ""
                                        }
                                        RedeemResult.ALREADY_ACTIVE -> {
                                            Toast.makeText(context, context.getString(R.string.pro_coupon_already_active), Toast.LENGTH_SHORT).show()
                                        }
                                        RedeemResult.INVALID -> {
                                            Toast.makeText(context, context.getString(R.string.pro_coupon_invalid), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.pro_coupon_btn_redeem), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ProFeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
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
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ProTierCard(
    title: String,
    price: String,
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
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

            Text(
                text = price,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun getPackagePrice(packages: List<Package>, type: PackageType): String? {
    return packages.firstOrNull { it.packageType == type }?.product?.price?.formatted
}

private fun findPackageForTier(packages: List<Package>, tier: SelectedProTier): Package? {
    return when (tier) {
        SelectedProTier.ANNUAL -> packages.firstOrNull { it.packageType == PackageType.ANNUAL }
        SelectedProTier.MONTHLY -> packages.firstOrNull { it.packageType == PackageType.MONTHLY }
        SelectedProTier.LIFETIME -> packages.firstOrNull { it.packageType == PackageType.LIFETIME }
    }
}
