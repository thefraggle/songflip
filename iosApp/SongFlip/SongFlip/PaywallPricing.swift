import Foundation
import RevenueCat

enum PaywallPricing {
    static let minSavingsPercent: Int = 5

    /// Calculates savings percentage of annual vs 12 months of monthly plan.
    /// Returns nil if data is missing, price <= 0, or savings < 5%.
    static func annualSavingsPercent(annualPrice: Decimal, monthlyPrice: Decimal) -> Int? {
        guard annualPrice > 0, monthlyPrice > 0 else { return nil }
        let yearlyViaMonthly = monthlyPrice * 12
        guard yearlyViaMonthly > 0 else { return nil }
        
        let ratio = (annualPrice as NSDecimalNumber).doubleValue / (yearlyViaMonthly as NSDecimalNumber).doubleValue
        let percent = Int(round((1.0 - ratio) * 100.0))
        return percent >= minSavingsPercent ? percent : nil
    }

    /// Formats annual price divided by 12 using the product's exact currency formatter.
    static func formatMonthlyPrice(for annualProduct: StoreProduct) -> String {
        let perMonth = (annualProduct.price as NSDecimalNumber).dividing(by: 12)
        if let formatter = annualProduct.priceFormatter,
           let formatted = formatter.string(from: perMonth) {
            return formatted
        }
        return String(format: "%.2f %@", perMonth.doubleValue, annualProduct.currencyCode ?? "")
    }
}
