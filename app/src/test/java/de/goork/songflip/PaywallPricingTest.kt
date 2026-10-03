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
}
