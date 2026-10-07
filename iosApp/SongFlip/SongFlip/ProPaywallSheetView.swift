import SwiftUI
import RevenueCat
import SongFlipKit

struct ProPaywallSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    @State private var selectedPackageType: PackageType = .lifetime
    @State private var showCouponField: Bool = false
    @State private var couponCode: String = ""
    @State private var couponStatusMessage: String? = nil
    @State private var isRedeemingCoupon: Bool = false
    @State private var errorMessage: String? = nil
    @State private var showErrorAlert: Bool = false

    var lang: String { settings.selectedLanguage }

    var packages: [Package] {
        proManager.currentOffering?.availablePackages ?? []
    }

    var lifetimePackage: Package? {
        packages.first { $0.packageType == .lifetime }
    }

    var annualPackage: Package? {
        packages.first { $0.packageType == .annual }
    }

    var monthlyPackage: Package? {
        packages.first { $0.packageType == .monthly }
    }

    var selectedPackage: Package? {
        packages.first { $0.packageType == selectedPackageType } ?? (shouldShowAnnualFirst ? annualPackage : lifetimePackage) ?? packages.first
    }

    var isLifetimeSale: Bool {
        guard let lifetime = lifetimePackage else { return false }
        // 1. Offering Metadata check from RevenueCat Dashboard (Master Switch)
        if let metaSale = proManager.currentOffering?.metadata["is_lifetime_sale"] as? Bool, metaSale {
            return true
        }
        if let metaSaleStr = proManager.currentOffering?.metadata["is_lifetime_sale"] as? String, metaSaleStr.lowercased() == "true" {
            return true
        }
        // 2. Compare against regular annual price (threshold 1.6x)
        if let annual = annualPackage {
            let lifetimePrice = lifetime.storeProduct.price as Decimal
            let annualPrice = annual.storeProduct.price as Decimal
            if annualPrice > 0 && lifetimePrice <= (annualPrice * 1.6) {
                return true
            }
        }
        // 3. Fallback comparison against monthly price (regular lifetime is ~20x monthly; on sale it is <= 13x)
        if let monthly = monthlyPackage {
            let lifetimePrice = lifetime.storeProduct.price as Decimal
            let monthlyPrice = monthly.storeProduct.price as Decimal
            if monthlyPrice > 0 && lifetimePrice <= (monthlyPrice * 13.0) {
                return true
            }
        }
        return false
    }

    var paywallLayout: String {
        let metaLayout = (proManager.currentOffering?.metadata["paywall_layout"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return metaLayout == "annual_first" ? "annual_first" : "lifetime_first"
    }

    var shouldShowAnnualFirst: Bool {
        // Active sale always forces lifetime to the top
        !isLifetimeSale && paywallLayout == "annual_first"
    }

    var lifetimeBadge: String {
        if isLifetimeSale {
            return LocalizationManager.string(for: "pro_lifetime_sale_badge", lang: lang)
        }
        if !shouldShowAnnualFirst {
            return LocalizationManager.string(for: "pro_lifetime_badge", lang: lang)
        }
        return LocalizationManager.string(for: "pro_lifetime_onetime_badge", lang: lang)
    }

    var lifetimeSubtitle: String? {
        if isLifetimeSale {
            return LocalizationManager.string(for: "pro_lifetime_sale_sub", lang: lang)
        }
        return nil
    }

    @ViewBuilder
    private var lifetimeTierCard: some View {
        if let pkg = lifetimePackage {
            tierCard(
                package: pkg,
                title: LocalizationManager.string(for: "pro_tier_lifetime", lang: lang),
                badge: lifetimeBadge,
                subtitle: lifetimeSubtitle,
                isSelected: selectedPackageType == .lifetime
            ) {
                selectedPackageType = .lifetime
            }
        }
    }

    @ViewBuilder
    private var annualTierCard: some View {
        if let pkg = annualPackage {
            let trialDays = proManager.getFreeTrialDays(for: pkg)
            let savingsPercent: Int? = {
                guard let monthlyPkg = monthlyPackage else { return nil }
                return PaywallPricing.annualSavingsPercent(
                    annualPrice: pkg.storeProduct.price as Decimal,
                    monthlyPrice: monthlyPkg.storeProduct.price as Decimal
                )
            }()

            let annualBadge: String? = {
                if let trial = trialDays {
                    return String(format: LocalizationManager.string(for: "pro_trial_badge", lang: lang), trial)
                } else if let savings = savingsPercent {
                    return String(format: LocalizationManager.string(for: "pro_save_badge", lang: lang), savings)
                }
                return nil
            }()

            let annualSubtitle: String? = {
                if let monthlyPkg = monthlyPackage, savingsPercent != nil {
                    let perMonth = PaywallPricing.formatMonthlyPrice(for: pkg.storeProduct)
                    let monthly = monthlyPkg.storeProduct.localizedPriceString
                    if let trial = trialDays {
                        return String(format: LocalizationManager.string(for: "pro_price_annual_trial", lang: lang), trial, perMonth, monthly)
                    } else {
                        return String(format: LocalizationManager.string(for: "pro_price_annual_vs_monthly", lang: lang), perMonth, monthly)
                    }
                } else if let trial = trialDays {
                    return String(format: LocalizationManager.string(for: "pro_price_annual_trial_simple", lang: lang), trial, pkg.storeProduct.localizedPriceString)
                }
                return nil
            }()

            tierCard(
                package: pkg,
                title: LocalizationManager.string(for: "pro_tier_annual", lang: lang),
                badge: annualBadge,
                subtitle: annualSubtitle,
                isSelected: selectedPackageType == .annual
            ) {
                selectedPackageType = .annual
            }
        }
    }

    @ViewBuilder
    private var monthlyTierCard: some View {
        if let pkg = monthlyPackage {
            tierCard(
                package: pkg,
                title: LocalizationManager.string(for: "pro_tier_monthly", lang: lang),
                badge: nil,
                subtitle: nil,
                isSelected: selectedPackageType == .monthly
            ) {
                selectedPackageType = .monthly
            }
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    VStack(spacing: 8) {
                        Text("💎")
                            .font(.system(size: 48))
                        Text(LocalizationManager.string(for: "pro_title", lang: lang))
                            .font(.system(size: 26, weight: .bold))
                            .multilineTextAlignment(.center)
                        Text(LocalizationManager.string(for: "pro_subtitle", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                    }
                    .padding(.top, 10)

                    // Pricing Tiers
                    VStack(spacing: 12) {
                        if shouldShowAnnualFirst {
                            annualTierCard
                            monthlyTierCard
                            lifetimeTierCard
                        } else {
                            lifetimeTierCard
                            annualTierCard
                            monthlyTierCard
                        }
                    }
                    .padding(.horizontal)

                    // CTA Button
                    Button(action: purchaseSelected) {
                        ZStack {
                            if proManager.isLoading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text(ctaButtonLabel)
                                    .font(.headline)
                                    .fontWeight(.bold)
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color.green)
                        .cornerRadius(16)
                        .shadow(color: Color.green.opacity(0.3), radius: 8, y: 4)
                    }
                    .disabled(proManager.isLoading || selectedPackage == nil)
                    .padding(.horizontal)

                    // Restore Purchases
                    Button(action: restorePurchases) {
                        Text(LocalizationManager.string(for: "pro_btn_restore", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                    .disabled(proManager.isLoading)

                    // Subscription Terms
                    Text(LocalizationManager.string(for: "pro_subscription_terms", lang: lang))
                        .font(.caption2)
                        .foregroundColor(.secondary.opacity(0.8))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 24)

                    Divider().padding(.horizontal)

                    // Feature Highlights
                    VStack(alignment: .leading, spacing: 14) {
                        featureRow(icon: "music.note.list", title: LocalizationManager.string(for: "pro_feature_playlists_title", lang: lang), desc: LocalizationManager.string(for: "pro_feature_playlists_desc", lang: lang))
                        featureRow(icon: "clock.arrow.circlepath", title: LocalizationManager.string(for: "pro_feature_history_title", lang: lang), desc: LocalizationManager.string(for: "pro_feature_history_desc", lang: lang))
                        featureRow(icon: "bolt.fill", title: LocalizationManager.string(for: "pro_feature_cache_title", lang: lang), desc: LocalizationManager.string(for: "pro_feature_cache_desc", lang: lang))
                        featureRow(icon: "link", title: LocalizationManager.string(for: "pro_feature_links_title", lang: lang), desc: LocalizationManager.string(for: "pro_feature_links_desc", lang: lang))
                        featureRow(icon: "sparkles", title: LocalizationManager.string(for: "pro_feature_future_title", lang: lang), desc: LocalizationManager.string(for: "pro_feature_future_desc", lang: lang))
                    }
                    .padding(.horizontal, 20)

                    Divider().padding(.horizontal)

                    // Coupon Toggle & Redemption
                    VStack(spacing: 10) {
                        Button(action: {
                            withAnimation { showCouponField.toggle() }
                        }) {
                            Text(LocalizationManager.string(for: "pro_coupon_toggle", lang: lang))
                                .font(.footnote)
                                .foregroundColor(.green)
                        }

                        if showCouponField {
                            HStack {
                                TextField(LocalizationManager.string(for: "pro_coupon_hint", lang: lang), text: $couponCode)
                                    .font(.subheadline)
                                    .textInputAutocapitalization(.characters)
                                    .autocorrectionDisabled()
                                    .padding(10)
                                    .background(Color(uiColor: .secondarySystemBackground))
                                    .cornerRadius(10)

                                Button(action: redeemCoupon) {
                                    if isRedeemingCoupon {
                                        ProgressView().padding(.horizontal, 8)
                                    } else {
                                        Text(LocalizationManager.string(for: "pro_coupon_btn_redeem", lang: lang))
                                            .font(.subheadline)
                                            .fontWeight(.bold)
                                            .foregroundColor(.white)
                                            .padding(.horizontal, 14)
                                            .padding(.vertical, 10)
                                            .background(Color.green)
                                            .cornerRadius(10)
                                    }
                                }
                                .disabled(couponCode.trimmingCharacters(in: .whitespaces).isEmpty || isRedeemingCoupon)
                            }
                            .padding(.horizontal, 24)

                            if let msg = couponStatusMessage {
                                Text(msg)
                                    .font(.caption)
                                    .foregroundColor(.green)
                                    .padding(.horizontal, 24)
                            }
                        }
                    }

                    // Legal Links
                    HStack(spacing: 12) {
                        Link(LocalizationManager.string(for: "legal_privacy", lang: lang), destination: URL(string: "https://songflip.link/privacy")!)
                        Text("•").foregroundColor(.secondary)
                        Link(LocalizationManager.string(for: "legal_terms", lang: lang), destination: URL(string: "https://songflip.link/terms")!)
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.vertical, 8)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LocalizationManager.string(for: "btn_close", lang: lang)) {
                        dismiss()
                    }
                }
            }
            .alert(isPresented: $showErrorAlert) {
                Alert(
                    title: Text(LocalizationManager.string(for: "pro_purchase_failed", lang: lang)),
                    message: Text(errorMessage ?? ""),
                    dismissButton: .default(Text("OK"))
                )
            }
            .onAppear {
                let initialType: PackageType = shouldShowAnnualFirst ? .annual : .lifetime
                selectedPackageType = initialType
                AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: [
                    "source": "paywall_sheet",
                    "layout": shouldShowAnnualFirst ? "annual_first" : "lifetime_first",
                    "is_sale": isLifetimeSale ? "true" : "false"
                ])
            }
            .preferredColorScheme(settings.colorScheme)
        }
    }

    private var ctaButtonLabel: String {
        guard let pkg = selectedPackage else {
            return LocalizationManager.string(for: "pro_btn_subscribe", lang: lang)
        }

        switch pkg.packageType {
        case .lifetime:
            return String(format: LocalizationManager.string(for: "pro_btn_lifetime", lang: lang), pkg.storeProduct.localizedPriceString)
        case .annual:
            if let trialDays = proManager.getFreeTrialDays(for: pkg) {
                return String(format: LocalizationManager.string(for: "pro_btn_annual_trial", lang: lang), trialDays)
            } else {
                return String(format: LocalizationManager.string(for: "pro_btn_annual", lang: lang), pkg.storeProduct.localizedPriceString)
            }
        case .monthly:
            return String(format: LocalizationManager.string(for: "pro_btn_monthly", lang: lang), pkg.storeProduct.localizedPriceString)
        default:
            return LocalizationManager.string(for: "pro_btn_subscribe", lang: lang)
        }
    }

    @ViewBuilder
    private func tierCard(
        package: Package,
        title: String,
        badge: String?,
        subtitle: String?,
        isSelected: Bool,
        onSelect: @escaping () -> Void
    ) -> some View {
        Button(action: {
            UISelectionFeedbackGenerator().selectionChanged()
            onSelect()
        }) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 8) {
                        Text(title)
                            .font(.headline)
                            .foregroundColor(.primary)
                        if let badge = badge {
                            Text(badge)
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(6)
                        }
                    }
                    if let subtitle = subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                Text(package.storeProduct.localizedPriceString)
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(isSelected ? .green : .primary)
            }
            .padding(16)
            .background(isSelected ? Color.green.opacity(0.12) : Color(uiColor: .secondarySystemBackground))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(isSelected ? Color.green : Color.clear, lineWidth: 2)
            )
        }
    }

    @ViewBuilder
    private func featureRow(icon: String, title: String, desc: String) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.title3)
                .foregroundColor(.green)
                .frame(width: 28, alignment: .center)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                Text(desc)
                    .font(.footnote)
                    .foregroundColor(.secondary)
            }
        }
    }

    private func purchaseSelected() {
        guard let pkg = selectedPackage else { return }
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        Task {
            do {
                let success = try await proManager.purchase(
                    package: pkg,
                    extraProps: [
                        "layout": shouldShowAnnualFirst ? "annual_first" : "lifetime_first",
                        "is_sale": isLifetimeSale ? "true" : "false"
                    ]
                )
                if success {
                    dismiss()
                }
            } catch {
                errorMessage = error.localizedDescription
                showErrorAlert = true
            }
        }
    }

    private func restorePurchases() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task {
            do {
                let success = try await proManager.restorePurchases()
                if success {
                    dismiss()
                } else {
                    errorMessage = LocalizationManager.string(for: "pro_restore_no_subscription", lang: lang)
                    showErrorAlert = true
                }
            } catch {
                errorMessage = error.localizedDescription
                showErrorAlert = true
            }
        }
    }

    private func redeemCoupon() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        isRedeemingCoupon = true
        couponStatusMessage = nil

        Task {
            let result = await proManager.redeemCoupon(code: couponCode)
            isRedeemingCoupon = false
            switch result {
            case .successLifetime:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_success_lifetime", lang: lang)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { dismiss() }
            case .success1Year:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_success_1year", lang: lang)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { dismiss() }
            case .success3Months:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_success_3months", lang: lang)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { dismiss() }
            case .success1Month:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_success_1month", lang: lang)
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { dismiss() }
            case .alreadyActive:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_already_active", lang: lang)
            case .alreadyRedeemed:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_already_redeemed", lang: lang)
            case .invalid:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_invalid", lang: lang)
            case .networkError:
                couponStatusMessage = LocalizationManager.string(for: "pro_coupon_network_error", lang: lang)
            }
        }
    }
}
