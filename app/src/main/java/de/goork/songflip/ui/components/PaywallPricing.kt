package de.goork.songflip.ui.components

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Pure pricing helpers for the paywall.
 *
 * Kept free of Android and RevenueCat types so they run in plain JVM unit tests
 * (the app module has no `isReturnDefaultValues`, so e.g. android.util.Log would throw there).
 * Callers are responsible for logging when a helper returns null.
 */
object PaywallPricing {

    private const val MICROS_PER_UNIT = 1_000_000.0

    /**
     * Formats [amountMicros] as a localized currency string.
     * Returns null for unknown ISO codes so the caller can log and decide on a fallback,
     * instead of silently showing a bare number without currency.
     */
    fun formatAmount(amountMicros: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String? {
        val currency = try {
            Currency.getInstance(currencyCode)
        } catch (e: IllegalArgumentException) {
            return null
        }
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = currency
            val fractionDigits = currency.defaultFractionDigits.coerceAtLeast(0)
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = fractionDigits
        }
        return format.format(amountMicros / MICROS_PER_UNIT)
    }

    /** Annual price spread over 12 months, e.g. 7.99 € → "0,67 €". */
    fun formatPerMonth(annualMicros: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String? =
        formatAmount(annualMicros / 12.0, currencyCode, locale)

    /**
     * Strike-through price for a lifetime sale.
     * Assumes the sale is exactly 50 % (matching the "50 % DEAL" badge copy) → original = 2 × sale price.
     */
    fun formatSaleOriginal(saleMicros: Long, currencyCode: String, locale: Locale = Locale.getDefault()): String? =
        formatAmount(saleMicros * 2.0, currencyCode, locale)
}
