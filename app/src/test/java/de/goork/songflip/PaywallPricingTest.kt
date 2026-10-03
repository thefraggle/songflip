package de.goork.songflip

import de.goork.songflip.ui.components.PaywallPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class PaywallPricingTest {

    @Test
    fun formatPerMonth_spreadsAnnualPriceOverTwelveMonths() {
        // 7.99 € / 12 = 0.6658 → 0,67 €
        val result = PaywallPricing.formatPerMonth(7_990_000L, "EUR", Locale.GERMANY)
        assertEquals("0,67 €", result?.replace('\u00A0', ' '))
    }

    @Test
    fun formatPerMonth_respectsZeroFractionCurrencies() {
        // JPY has no minor unit → no decimals
        val result = PaywallPricing.formatPerMonth(1_200_000_000L, "JPY", Locale.JAPAN)
        assertEquals("￥100", result)
    }

    @Test
    fun formatSaleOriginal_doublesSalePrice() {
        val result = PaywallPricing.formatSaleOriginal(9_990_000L, "USD", Locale.US)
        assertEquals("$19.98", result)
    }

    @Test
    fun formatAmount_returnsNullForUnknownCurrency() {
        assertNull(PaywallPricing.formatAmount(1_000_000.0, "XXXX", Locale.US))
    }

    @Test
    fun annualSavingsPercent_matchesCurrentEuroPricing() {
        // 7.99 vs 12 × 0.99 = 11.88 → 32.7 % → 33
        assertEquals(33, PaywallPricing.annualSavingsPercent(7_990_000L, "EUR", 990_000L, "EUR"))
    }

    @Test
    fun annualSavingsPercent_usesIntroPriceWhenPassed() {
        // 3.99 intro vs 11.88 → 66.4 % → 66
        assertEquals(66, PaywallPricing.annualSavingsPercent(3_990_000L, "EUR", 990_000L, "EUR"))
    }

    @Test
    fun annualSavingsPercent_followsRegionalPrices() {
        // e.g. MXN 99 / year vs 15 / month (180) → 45 %, not the hardcoded 33 %
        assertEquals(45, PaywallPricing.annualSavingsPercent(99_000_000L, "MXN", 15_000_000L, "MXN"))
    }

    @Test
    fun annualSavingsPercent_hidesNegligibleOrNegativeSavings() {
        assertNull(PaywallPricing.annualSavingsPercent(11_500_000L, "EUR", 990_000L, "EUR")) // ~3 %
        assertNull(PaywallPricing.annualSavingsPercent(15_000_000L, "EUR", 990_000L, "EUR")) // more expensive
    }

    @Test
    fun annualSavingsPercent_rejectsMissingOrMismatchedData() {
        assertNull(PaywallPricing.annualSavingsPercent(null, "EUR", 990_000L, "EUR"))
        assertNull(PaywallPricing.annualSavingsPercent(7_990_000L, "EUR", null, "EUR"))
        assertNull(PaywallPricing.annualSavingsPercent(7_990_000L, "EUR", 0L, "EUR"))
        assertNull(PaywallPricing.annualSavingsPercent(7_990_000L, "EUR", 990_000L, "USD"))
    }
}
