import Foundation
import SwiftUI
import Combine
import RevenueCat
import SongFlipKit
import CryptoKit

enum RedeemResult {
    case successLifetime
    case success1Year
    case success3Months
    case success1Month
    case invalid
    case alreadyRedeemed
    case alreadyActive
    case networkError
}

@MainActor
final class ProManager: NSObject, ObservableObject {
    static let shared = ProManager()

    private static let apiKey = "appl_yaWywAHSMSWeevrQKcyjtHeNedw"
    private static let appGroupId = "group.de.goork.songflip"
    private static let keyCouponType = "pro_coupon_type"
    private static let keyCouponCode = "pro_coupon_code"
    private static let keyCouponExpiration = "pro_coupon_expiration"
    private static let keyCouponToken = "pro_coupon_token"
    private static let keyIsProCached = "is_pro_cached"
    private static let keyCacheSignature = "pro_cache_signature"

    @Published var isPro: Bool = false
    @Published var proType: String? = nil
    @Published var expirationDate: Date? = nil
    @Published var currentOffering: Offering? = nil
    @Published var isLoading: Bool = false
    @Published var lastErrorMessage: String? = nil

    private var defaults: UserDefaults {
        UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
    }

    private override init() {
        super.init()
        loadCachedState()
    }

    private func getDeviceFingerprint() -> String {
        return UIDevice.current.identifierForVendor?.uuidString ?? "generic_device_id"
    }

    private func computeSignature(isPro: Bool, proType: String?, expiration: Double?) -> String {
        let raw = "\(isPro):\(proType ?? ""):\(expiration ?? 0):\(getDeviceFingerprint()):songflip_secure_salt_8f92"
        let digest = SHA256.hash(data: Data(raw.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    private func persistProState(isPro: Bool, proType: String?, expiration: Double? = nil) {
        defaults.set(isPro, forKey: Self.keyIsProCached)
        let sig = computeSignature(isPro: isPro, proType: proType, expiration: expiration)
        defaults.set(sig, forKey: Self.keyCacheSignature)
    }

    func configure(isDebug: Bool = false) {
        Purchases.logLevel = isDebug ? .debug : .warn
        Purchases.configure(withAPIKey: Self.apiKey)
        Purchases.shared.delegate = self

        Task {
            await refreshOfferings()
            await refreshCustomerInfo()
        }
    }

    private func loadCachedState() {
        let cachedIsPro = defaults.bool(forKey: Self.keyIsProCached)
        let couponType = defaults.string(forKey: Self.keyCouponType)
        let couponExpiration = defaults.double(forKey: Self.keyCouponExpiration)
        let storedSignature = defaults.string(forKey: Self.keyCacheSignature)

        let expValue: Double? = (couponExpiration > 0) ? couponExpiration : nil
        let expectedSignature = computeSignature(isPro: cachedIsPro, proType: couponType, expiration: expValue)

        // Enforce cryptographic integrity: if Pro is claimed but signature doesn't match, reject state
        if cachedIsPro && storedSignature != expectedSignature {
            self.isPro = false
            self.proType = nil
            self.expirationDate = nil
            defaults.set(false, forKey: Self.keyIsProCached)
            defaults.removeObject(forKey: Self.keyCacheSignature)
            return
        }

        if let couponType = couponType {
            if couponType == "lifetime" {
                self.isPro = true
                self.proType = "lifetime_coupon"
                return
            } else if couponExpiration > Date().timeIntervalSince1970 * 1000 {
                self.isPro = true
                self.proType = "\(couponType)_coupon"
                self.expirationDate = Date(timeIntervalSince1970: couponExpiration / 1000)
                return
            }
        }

        self.isPro = cachedIsPro
    }

    func refreshOfferings() async {
        do {
            let offerings = try await Purchases.shared.offerings()
            self.currentOffering = offerings.current ?? offerings["default"]
        } catch {
            print("[ProManager] Failed to fetch offerings: \(error)")
        }
    }

    func refreshCustomerInfo() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateFromCustomerInfo(customerInfo)
        } catch {
            print("[ProManager] Failed to fetch customer info: \(error)")
        }
    }

    private func updateFromCustomerInfo(_ customerInfo: CustomerInfo) {
        let proEntitlement = customerInfo.entitlements["pro"] ?? customerInfo.entitlements["songflip_pro"]
        let isRevenueCatPro = proEntitlement?.isActive == true

        if isRevenueCatPro {
            self.isPro = true
            let isLifetime = proEntitlement?.periodType == .normal && proEntitlement?.expirationDate == nil
            self.proType = isLifetime ? "revenuecat_lifetime" : "revenuecat_subscription"
            self.expirationDate = proEntitlement?.expirationDate
            let expMillis = proEntitlement?.expirationDate.map { $0.timeIntervalSince1970 * 1000 }
            persistProState(isPro: true, proType: self.proType, expiration: expMillis)
            return
        }

        // Fallback: check valid coupon
        let couponType = defaults.string(forKey: Self.keyCouponType)
        let couponExpiration = defaults.double(forKey: Self.keyCouponExpiration)

        if let couponType = couponType {
            if couponType == "lifetime" {
                self.isPro = true
                self.proType = "lifetime_coupon"
                self.expirationDate = nil
                persistProState(isPro: true, proType: "lifetime_coupon", expiration: nil)
                return
            } else if couponExpiration > Date().timeIntervalSince1970 * 1000 {
                self.isPro = true
                self.proType = "\(couponType)_coupon"
                self.expirationDate = Date(timeIntervalSince1970: couponExpiration / 1000)
                persistProState(isPro: true, proType: couponType, expiration: couponExpiration)
                return
            }
        }

        self.isPro = false
        self.proType = nil
        self.expirationDate = nil
        persistProState(isPro: false, proType: nil, expiration: nil)
    }

    func purchase(package: Package, extraProps: [String: String] = [:]) async throws -> Bool {
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await Purchases.shared.purchase(package: package)
            if !result.userCancelled {
                updateFromCustomerInfo(result.customerInfo)
                var props = [
                    "package": package.identifier,
                    "store": "app_store"
                ]
                for (k, v) in extraProps {
                    props[k] = v
                }
                AptabaseClient.shared.trackEvent(eventName: "pro_purchased", props: props)
                return isPro
            }
            return false
        } catch {
            lastErrorMessage = error.localizedDescription
            throw error
        }
    }

    func restorePurchases() async throws -> Bool {
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }

        do {
            let customerInfo = try await Purchases.shared.restorePurchases()
            updateFromCustomerInfo(customerInfo)
            return isPro
        } catch {
            lastErrorMessage = error.localizedDescription
            throw error
        }
    }

    func redeemCoupon(code: String) async -> RedeemResult {
        let cleanCode = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        if cleanCode.count < 3 { return .invalid }

        isLoading = true
        defer { isLoading = false }

        guard let url = URL(string: "https://songflip-web.web.app/redeemPromoCode") else {
            return .networkError
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let installId = (UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString).lowercased()
        let body: [String: Any] = [
            "code": cleanCode,
            "installId": installId
        ]

        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            let (data, response) = try await URLSession.shared.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                return .networkError
            }

            if httpResponse.statusCode == 200 {
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                    let type = (json["type"] as? String)?.lowercased() ?? "1month"
                    let expTimestamp = json["expirationTimestamp"] as? Double
                    let token = json["token"] as? String ?? ""

                    if type == "lifetime" {
                        if self.isPro && self.proType == "lifetime_coupon" {
                            return .alreadyActive
                        }
                        defaults.set("lifetime", forKey: Self.keyCouponType)
                        defaults.set(cleanCode, forKey: Self.keyCouponCode)
                        defaults.set(token, forKey: Self.keyCouponToken)
                        defaults.removeObject(forKey: Self.keyCouponExpiration)
                        persistProState(isPro: true, proType: "lifetime_coupon", expiration: nil)
                        self.isPro = true
                        self.proType = "lifetime_coupon"
                        self.expirationDate = nil
                        AptabaseClient.shared.trackEvent(eventName: "promo_redeemed", props: ["code": cleanCode, "type": "lifetime"])
                        return .successLifetime
                    } else {
                        let durationDays: Double = (type == "1year" || type == "annual") ? 365 : ((type == "3months") ? 90 : 30)
                        let expireTime = expTimestamp ?? (Date().timeIntervalSince1970 * 1000 + durationDays * 24 * 60 * 60 * 1000)

                        defaults.set(type, forKey: Self.keyCouponType)
                        defaults.set(cleanCode, forKey: Self.keyCouponCode)
                        defaults.set(token, forKey: Self.keyCouponToken)
                        defaults.set(expireTime, forKey: Self.keyCouponExpiration)
                        persistProState(isPro: true, proType: type, expiration: expireTime)

                        self.isPro = true
                        self.proType = "\(type)_coupon"
                        self.expirationDate = Date(timeIntervalSince1970: expireTime / 1000)

                        AptabaseClient.shared.trackEvent(eventName: "promo_redeemed", props: ["code": cleanCode, "type": type])
                        if type == "1year" || type == "annual" {
                            return .success1Year
                        } else if type == "3months" {
                            return .success3Months
                        } else {
                            return .success1Month
                        }
                    }
                }
            } else if httpResponse.statusCode == 409 {
                return .alreadyRedeemed
            } else {
                return .invalid
            }
        } catch {
            return .networkError
        }

        return .networkError
    }

    // Helper: Detect introductory free trial in days
    func getFreeTrialDays(for package: Package) -> Int? {
        guard let intro = package.storeProduct.introductoryDiscount,
              intro.paymentMode == .freeTrial else {
            return nil
        }
        let period = intro.subscriptionPeriod
        switch period.unit {
        case .day: return period.value
        case .week: return period.value * 7
        case .month: return period.value * 30
        case .year: return period.value * 365
        @unknown default: return 7
        }
    }
}

extension ProManager: PurchasesDelegate {
    nonisolated func purchases(_ purchases: Purchases, receivedUpdated customerInfo: CustomerInfo) {
        Task { @MainActor in
            self.updateFromCustomerInfo(customerInfo)
        }
    }
}
