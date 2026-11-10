package de.goork.songflip.ui.components.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.goork.songflip.R
import de.goork.songflip.data.ProManager
import de.goork.songflip.data.RedeemResult
import kotlinx.coroutines.launch

/**
 * Self-contained Settings Card & Dialog for redeeming voucher coupons (#241).
 */
@Composable
fun SettingsCouponCard(
    isPro: Boolean,
    onCouponRedeemed: () -> Unit = {}
) {
    if (isPro) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    var showCouponDialog by remember { mutableStateOf(false) }
    var couponText by remember { mutableStateOf("") }
    var isRedeemingCoupon by remember { mutableStateOf(false) }
    var couponError by remember { mutableStateOf<String?>(null) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showCouponDialog = true
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ConfirmationNumber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.pro_coupon_toggle),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.pro_coupon_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showCouponDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isRedeemingCoupon) {
                    showCouponDialog = false
                    couponText = ""
                    couponError = null
                }
            },
            title = {
                Text(
                    text = stringResource(R.string.pro_coupon_toggle),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = couponText,
                        onValueChange = {
                            couponText = ProManager.extractCouponCode(it).trim().uppercase()
                            couponError = null
                        },
                        placeholder = { Text(stringResource(R.string.pro_coupon_hint)) },
                        singleLine = true,
                        isError = couponError != null,
                        supportingText = couponError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRedeemingCoupon && couponText.isNotBlank(),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        isRedeemingCoupon = true
                        couponError = null
                        coroutineScope.launch {
                            val result = ProManager.redeemCoupon(couponText)
                            isRedeemingCoupon = false
                            when (result) {
                                RedeemResult.SUCCESS_LIFETIME -> {
                                    Toast.makeText(context, context.getString(R.string.pro_coupon_success_lifetime), Toast.LENGTH_LONG).show()
                                    showCouponDialog = false
                                    couponText = ""
                                    onCouponRedeemed()
                                }
                                RedeemResult.SUCCESS_1YEAR -> {
                                    Toast.makeText(context, context.getString(R.string.pro_coupon_success_1year), Toast.LENGTH_LONG).show()
                                    showCouponDialog = false
                                    couponText = ""
                                    onCouponRedeemed()
                                }
                                RedeemResult.SUCCESS_3MONTHS -> {
                                    Toast.makeText(context, context.getString(R.string.pro_coupon_success_3months), Toast.LENGTH_LONG).show()
                                    showCouponDialog = false
                                    couponText = ""
                                    onCouponRedeemed()
                                }
                                RedeemResult.SUCCESS_1MONTH -> {
                                    Toast.makeText(context, context.getString(R.string.pro_coupon_success_1month), Toast.LENGTH_LONG).show()
                                    showCouponDialog = false
                                    couponText = ""
                                    onCouponRedeemed()
                                }
                                RedeemResult.ALREADY_ACTIVE -> couponError = context.getString(R.string.pro_coupon_already_active)
                                RedeemResult.ALREADY_REDEEMED -> couponError = context.getString(R.string.pro_coupon_already_redeemed)
                                RedeemResult.MAX_REACHED -> couponError = context.getString(R.string.pro_coupon_max_reached)
                                RedeemResult.INACTIVE -> couponError = context.getString(R.string.pro_coupon_inactive)
                                RedeemResult.RATE_LIMITED -> couponError = context.getString(R.string.pro_coupon_rate_limited)
                                RedeemResult.INVALID -> couponError = context.getString(R.string.pro_coupon_invalid)
                                RedeemResult.NETWORK_ERROR -> couponError = context.getString(R.string.pro_coupon_network_error)
                            }
                        }
                    }
                ) {
                    if (isRedeemingCoupon) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(stringResource(R.string.pro_coupon_btn_redeem))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRedeemingCoupon,
                    onClick = {
                        showCouponDialog = false
                        couponText = ""
                        couponError = null
                    }
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }
}
