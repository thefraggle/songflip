package de.goork.songflip

import com.revenuecat.purchases.PurchasesErrorCode
import de.goork.songflip.ui.components.resolveNonSandboxPurchaseProductId
import de.goork.songflip.ui.components.shouldTrackPurchaseError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProPurchaseTelemetryTest {

    @Test
    fun returnsNullWhenEntitlementIsInactive() {
        val result = resolveNonSandboxPurchaseProductId(
            isActive = false,
            isSandbox = false,
            transactionProductIds = listOf("songflip_pro_annual"),
            entitlementProductId = "songflip_pro_annual"
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenPurchaseIsSandbox() {
        val result = resolveNonSandboxPurchaseProductId(
            isActive = true,
            isSandbox = true,
            transactionProductIds = listOf("songflip_pro_annual"),
            entitlementProductId = "songflip_pro_annual"
        )
        assertNull(result)
    }

    @Test
    fun prefersStoreTransactionProductIdWhenPresent() {
        val result = resolveNonSandboxPurchaseProductId(
            isActive = true,
            isSandbox = false,
            transactionProductIds = listOf("songflip_pro_lifetime"),
            entitlementProductId = "songflip_pro_annual"
        )
        assertEquals("songflip_pro_lifetime", result)
    }

    @Test
    fun fallsBackToEntitlementProductIdWhenTransactionListIsEmpty() {
        val result = resolveNonSandboxPurchaseProductId(
            isActive = true,
            isSandbox = false,
            transactionProductIds = emptyList(),
            entitlementProductId = "songflip_pro_monthly"
        )
        assertEquals("songflip_pro_monthly", result)
    }

    @Test
    fun fallsBackToUnknownWhenNoProductIdAvailable() {
        val result = resolveNonSandboxPurchaseProductId(
            isActive = true,
            isSandbox = false,
            transactionProductIds = listOf("  "),
            entitlementProductId = ""
        )
        assertEquals("unknown", result)
    }

    @Test
    fun ignoresUserCancelledPurchaseErrorsInTelemetry() {
        assertEquals(false, shouldTrackPurchaseError(PurchasesErrorCode.PurchaseCancelledError))
        assertEquals(false, shouldTrackPurchaseError(PurchasesErrorCode.PaymentPendingError))
        assertEquals(true, shouldTrackPurchaseError(PurchasesErrorCode.StoreProblemError))
    }
}
