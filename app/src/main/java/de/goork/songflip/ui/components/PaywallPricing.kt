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

    /** Below this, a "save X %" badge looks petty rather than persuasive. */
    const val MIN_SAVINGS_PERCENT = 5

    /**
     * Savings of the annual plan vs. paying monthly for 12 months, rounded to whole percent.
     *
     * Computed from live store prices because regional pricing (MX, IN, AR …) does not keep the
     * 7.99 / 0.99 ratio – a hardcoded "33 %" would be wrong there.
     * Returns null when no honest comparison is possible (missing prices, currency mismatch,
     * or savings below [MIN_SAVINGS_PERCENT]).
     */
    fun annualSavingsPercent(
        annualMicros: Long?,
        annualCurrency: String?,
        monthlyMicros: Long?,
        monthlyCurrency: String?
    ): Int? {
        if (annualMicros == null || monthlyMicros == null) return null
        if (annualMicros <= 0 || monthlyMicros <= 0) return null
        if (annualCurrency == null || annualCurrency != monthlyCurrency) return null
        val yearlyViaMonthly = monthlyMicros * 12.0
        val percent = Math.round((1.0 - annualMicros / yearlyViaMonthly) * 100.0).toInt()
        return percent.takeIf { it >= MIN_SAVINGS_PERCENT }
    }

    /** RevenueCat offering metadata key, set in the dashboard: `{ "paywall_layout": "annual_first" }`. */
    const val LAYOUT_METADATA_KEY = "paywall_layout"

    /**
     * Unknown or missing values fall back to [PaywallLayout.LIFETIME_FIRST]: in Sept 2026 lifetime
     * made up 8 of 11 purchases and 93 % of revenue, so it stays the default until data says otherwise.
     */
    fun resolveLayout(rawMetadataValue: Any?): PaywallLayout {
        val value = (rawMetadataValue as? String)?.trim()?.lowercase()
        return PaywallLayout.entries.firstOrNull { it.metadataValue == value } ?: PaywallLayout.LIFETIME_FIRST
    }

    /**
     * Card order; the first entry is preselected.
     * An active lifetime sale overrides the layout – a time-limited deal buried at the bottom
     * would defeat the purpose of running it.
     */
    fun resolveTierOrder(layout: PaywallLayout, isLifetimeSale: Boolean): List<SelectedProTier> =
        if (layout == PaywallLayout.ANNUAL_FIRST && !isLifetimeSale) {
            listOf(SelectedProTier.ANNUAL, SelectedProTier.MONTHLY, SelectedProTier.LIFETIME)
        } else {
            listOf(SelectedProTier.LIFETIME, SelectedProTier.ANNUAL, SelectedProTier.MONTHLY)
        }
}

enum class PaywallLayout(val metadataValue: String) {
    LIFETIME_FIRST("lifetime_first"),
    ANNUAL_FIRST("annual_first")
}
