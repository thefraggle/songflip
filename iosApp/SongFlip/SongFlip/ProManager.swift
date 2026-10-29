import Foundation
import SwiftUI
import Combine
import RevenueCat
import SongFlipKit
import CryptoKit
import os

enum RedeemResult {
    case successLifetime
    case success1Year
    case success3Months
    case success1Month
    case invalid
    case alreadyRedeemed
    case alreadyActive
    case maxReached
    case inactive
    case rateLimited
    case networkError
}

@MainActor
final class ProManager: NSObject, ObservableObject {
    static let shared = ProManager()
    private static let logger = Logger(subsystem: "de.goork.songflip", category: "ProManager")

    private static var apiKey: String {
        let key = Bundle.main.infoDictionary?["REVENUECAT_PUBLIC_KEY"] as? String ?? ""
        #if DEBUG
        if key.isEmpty {
            logger.error("[ProManager] REVENUECAT_PUBLIC_KEY is not configured! Check Config.xcconfig")
        }
        #endif
        return key
    }
    private static let appGroupId = "group.de.goork.songflip"
    private static let keyCouponType = "pro_coupon_type"
    private static let keyCouponCode = "pro_coupon_code"
    private static let keyCouponExpiration = "pro_coupon_expiration"
    private static let keyCouponToken = "pro_coupon_token"
    private static let keyIsProCached = "is_pro_cached"
    private static let keyCacheSignature = "pro_cache_signature"
    private static let keyProAuthToken = "pro_auth_token"

    @Published var isPro: Bool = false {
        didSet {
            syncAuthTokenToGroupStorage()
        }
    }
    @Published var proType: String? = nil
    @Published var expirationDate: Date? = nil
    @Published var currentOffering: Offering? = nil
    @Published var isLoadingOfferings: Bool = false
    @Published var isLoading: Bool = false
    @Published var lastErrorMessage: String? = nil

    private var defaults: UserDefaults {
        UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
    }

    /// Syncs Pro auth token to shared App Group storage so Share Extension and Siri Shortcuts
    /// can authorize requests against the L2 server cache (#173).
    func syncAuthTokenToGroupStorage() {
        if isPro {
            let token = self.authToken
            if !token.isEmpty {
                defaults.set(token, forKey: Self.keyProAuthToken)
            } else {
                defaults.removeObject(forKey: Self.keyProAuthToken)
            }
        } else {
            defaults.removeObject(forKey: Self.keyProAuthToken)
        }
    }

    private static let rcExpiryGraceMs: Double = 15 * 60 * 1000

    private func verifyStoredCouponOffline(token: String?, currentTimeMs: Int64) -> VerifiedCouponPayload {
        let primaryId = getPersistentInstallId()
        let lastSeen = getLastSeenServerTime()
        let primaryVerified = CouponService.companion.shared.verifyCouponToken(
            token: token,
            expectedInstallId: primaryId,
            currentTimeMs: currentTimeMs,
            lastSeenServerTimeMs: lastSeen
        )
        if primaryVerified.valid {
            return primaryVerified
        }
        if let vendorId = UIDevice.current.identifierForVendor?.uuidString.lowercased(),
           !vendorId.isEmpty,
           vendorId != primaryId {
            let vendorVerified = CouponService.companion.shared.verifyCouponToken(
                token: token,
                expectedInstallId: vendorId,
                currentTimeMs: currentTimeMs,
                lastSeenServerTimeMs: lastSeen
            )
            if vendorVerified.valid {
                return vendorVerified
            }
        }
        return primaryVerified
    }

    var authToken: String {
        if isPro, let type = proType, type.hasPrefix("revenuecat") {
            if Purchases.isConfigured {
                return Purchases.shared.appUserID
            }
            return getDeviceFingerprint()
        }
        if let token = getStoredCouponToken(), !token.isEmpty {
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            let verified = verifyStoredCouponOffline(token: token, currentTimeMs: nowMs)
            if verified.valid {
                return "coupon:\(token)"
            }
        }
        if Purchases.isConfigured {
            return Purchases.shared.appUserID
        }
        return getDeviceFingerprint()
    }

    private override init() {
        super.init()
        loadCachedState()
        syncAuthTokenToGroupStorage()
    }

    private static let keychainService = "de.goork.songflip.install"
    private static let keychainAccountInstallId = "persistent_install_id"
    private static let keychainAccountCouponToken = "pro_coupon_token_v2"
    private static let keychainAccountLastSeenTime = "last_seen_server_time_v2"
    private static let keychainAccountRcState = "rc_pro_state_v2"

    private func readKeychainString(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.keychainService,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var dataTypeRef: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &dataTypeRef)
        if status == errSecSuccess,
           let data = dataTypeRef as? Data,
           let value = String(data: data, encoding: .utf8),
           !value.isEmpty {
            return value
        }
        if status != errSecSuccess && status != errSecItemNotFound {
            Self.logger.warning("[ProManager] SecItemCopyMatching for \(account, privacy: .public) returned status \(status)")
        }
        return nil
    }

    private func writeKeychainString(account: String, value: String?) {
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.keychainService,
            kSecAttrAccount as String: account
        ]
        let delStatus = SecItemDelete(deleteQuery as CFDictionary)
        if delStatus != errSecSuccess && delStatus != errSecItemNotFound {
            Self.logger.debug("[ProManager] SecItemDelete for \(account, privacy: .public) returned status \(delStatus)")
        }
        guard let value = value, !value.isEmpty, let data = value.data(using: .utf8) else {
            return
        }
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: Self.keychainService,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let addStatus = SecItemAdd(addQuery as CFDictionary, nil)
        if addStatus != errSecSuccess {
            Self.logger.warning("[ProManager] SecItemAdd for \(account, privacy: .public) returned status \(addStatus)")
        }
    }

    private func getPersistentInstallId() -> String {
        if let existing = readKeychainString(account: Self.keychainAccountInstallId) {
            return existing
        }
        let newId = (UIDevice.current.identifierForVendor?.uuidString ?? UUID().uuidString).lowercased()
        writeKeychainString(account: Self.keychainAccountInstallId, value: newId)
        return newId
    }

    private func getDeviceFingerprint() -> String {
        return getPersistentInstallId()
    }

    private func getStoredCouponToken() -> String? {
        if let kcToken = readKeychainString(account: Self.keychainAccountCouponToken) {
            return kcToken
        }
        // One-time migration from legacy UserDefaults token storage
        if let legacyToken = defaults.string(forKey: Self.keyCouponToken), !legacyToken.isEmpty {
            writeKeychainString(account: Self.keychainAccountCouponToken, value: legacyToken)
            defaults.removeObject(forKey: Self.keyCouponToken)
            defaults.removeObject(forKey: Self.keyCacheSignature)
            return legacyToken
        }
        return nil
    }

    private func saveStoredCouponToken(_ token: String) {
        writeKeychainString(account: Self.keychainAccountCouponToken, value: token)
        defaults.removeObject(forKey: Self.keyCouponToken)
        defaults.removeObject(forKey: Self.keyCacheSignature)
    }

    private func getLastSeenServerTime() -> Int64 {
        if let raw = readKeychainString(account: Self.keychainAccountLastSeenTime),
           let ts = Int64(raw), ts > 0 {
            return ts
        }
        return 0
    }

    private func updateLastSeenServerTime(_ timestampMs: Int64) {
        let current = getLastSeenServerTime()
        if timestampMs > current {
            writeKeychainString(account: Self.keychainAccountLastSeenTime, value: String(timestampMs))
        }
    }

    private func persistRevenueCatProState(isPro: Bool, proType: String?, expiration: Double? = nil) {
        defaults.removeObject(forKey: Self.keyCacheSignature)
        defaults.set(isPro, forKey: Self.keyIsProCached)
        if isPro {
            let expMs = Int64(expiration ?? 0)
            let stateStr = "1|\(proType ?? "revenuecat_subscription")|\(expMs)"
            writeKeychainString(account: Self.keychainAccountRcState, value: stateStr)
        } else {
            writeKeychainString(account: Self.keychainAccountRcState, value: nil)
        }
    }

    private var isAutomatedTest: Bool = false

    func configure(isDebug: Bool = false, isAutomatedTest: Bool = false) {
        self.isAutomatedTest = isAutomatedTest
        Purchases.logLevel = isDebug ? .debug : .warn
        Purchases.configure(withAPIKey: Self.apiKey)
        Purchases.shared.delegate = self

        Task {
            await refreshOfferings()
            await refreshCustomerInfo()
            await migrateStoredCouponTokenIfNeeded()
        }
    }

    private var tokenMigrationAttempted = false

    /// One attempt per launch to re-sign a legacy/compat coupon token with the current key (#141).
    /// Pro access never depends on this: unknown outcomes keep the token; only the server's explicit
    /// "invalid signature" verdict removes it.
    func migrateStoredCouponTokenIfNeeded() async {
        guard !isAutomatedTest, !tokenMigrationAttempted else { return }
        tokenMigrationAttempted = true
        guard let storedToken = getStoredCouponToken(), !storedToken.isEmpty else { return }
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let verified = verifyStoredCouponOffline(token: storedToken, currentTimeMs: nowMs)
        let service = CouponService.companion.shared
        guard service.needsTokenMigration(verified: verified) else { return }

        do {
            let outcome = try await service.refreshCouponToken(
                token: storedToken,
                installId: verified.installId,
                customEndpoints: nil
            )
            switch outcome.status {
            case .upgraded:
                saveStoredCouponToken(outcome.token)
                if let iat = outcome.issuedAtTimestamp?.int64Value {
                    updateLastSeenServerTime(iat)
                }
                reevaluateCouponKeepingRevenueCat()
                Self.logger.info("[ProManager] Coupon token migrated to current signing key")
            case .revoked:
                writeKeychainString(account: Self.keychainAccountCouponToken, value: nil)
                defaults.removeObject(forKey: Self.keyCouponType)
                defaults.removeObject(forKey: Self.keyCouponCode)
                defaults.removeObject(forKey: Self.keyCouponExpiration)
                reevaluateCouponKeepingRevenueCat()
                Self.logger.warning("[ProManager] Stored coupon token rejected by server (invalid signature) – removed")
            default:
                Self.logger.info("[ProManager] Coupon token migration skipped: \(String(describing: outcome.status))")
            }
        } catch {
            Self.logger.error("[ProManager] Coupon token migration failed: \(error.localizedDescription)")
        }
    }

    /// Refreshes coupon-derived state without touching an active RevenueCat entitlement.
    private func reevaluateCouponKeepingRevenueCat() {
        if isPro, let type = proType, type.hasPrefix("revenuecat") { return }
        if !evaluateVerifiedCouponState() {
            loadCachedState()
        }
    }

    private func evaluateVerifiedCouponState() -> Bool {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let verified = verifyStoredCouponOffline(
            token: getStoredCouponToken(),
            currentTimeMs: nowMs
        )
        guard verified.valid else {
            return false
        }
        if let iat = verified.issuedAtTimestamp?.int64Value {
            updateLastSeenServerTime(iat)
        }
        if verified.type == "lifetime" {
            self.isPro = true
            self.proType = "lifetime_coupon"
            self.expirationDate = nil
            defaults.set(true, forKey: Self.keyIsProCached)
            return true
        }
        let labelType = defaults.string(forKey: Self.keyCouponType) ?? verified.type
        let normalizedType = labelType.hasSuffix("_coupon") ? labelType : "\(labelType)_coupon"
        self.isPro = true
        self.proType = normalizedType
        if let expMs = verified.expirationTimestamp?.doubleValue, expMs > 0 {
            self.expirationDate = Date(timeIntervalSince1970: expMs / 1000)
        } else {
            self.expirationDate = nil
        }
        defaults.set(true, forKey: Self.keyIsProCached)
        return true
    }

    private func loadCachedState() {
        // 1. Check Ed25519-verified coupon token in Keychain (#109)
        if evaluateVerifiedCouponState() {
            return
        }

        // 2. Check Keychain-backed RevenueCat cached entitlement
        if let rcState = readKeychainString(account: Self.keychainAccountRcState) {
            let parts = rcState.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
            if parts.count >= 3, parts[0] == "1" {
                let rcType = parts[1]
                let expMs = Double(parts[2]) ?? 0
                let nowMs = Date().timeIntervalSince1970 * 1000
                if rcType == "revenuecat_lifetime" || expMs <= 0 || (expMs + Self.rcExpiryGraceMs) > nowMs {
                    self.isPro = true
                    self.proType = rcType
                    self.expirationDate = expMs > 0 ? Date(timeIntervalSince1970: expMs / 1000) : nil
                    defaults.set(true, forKey: Self.keyIsProCached)
                    return
                }
            }
        }

        self.isPro = false
        self.proType = nil
        self.expirationDate = nil
        defaults.set(false, forKey: Self.keyIsProCached)
    }

    func trackPaywallImpression() {
        guard !isAutomatedTest, Purchases.isConfigured else { return }
        Purchases.shared.trackCustomPaywallImpression()
    }

    func refreshOfferings() async {
        guard Purchases.isConfigured else { return }
        isLoadingOfferings = true
        defer { isLoadingOfferings = false }
        do {
            let offerings = try await Purchases.shared.offerings()
            self.currentOffering = offerings.current ?? offerings["default"]
        } catch {
            Self.logger.error("[ProManager] Failed to fetch offerings: \(error.localizedDescription)")
        }
    }

    func refreshCustomerInfo() async {
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateFromCustomerInfo(customerInfo)
        } catch {
            Self.logger.error("[ProManager] Failed to fetch customer info: \(error.localizedDescription)")
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
            persistRevenueCatProState(isPro: true, proType: self.proType, expiration: expMillis)
            return
        }

        // Fallback: check Ed25519-verified coupon token (#109)
        if evaluateVerifiedCouponState() {
            return
        }

        self.isPro = false
        self.proType = nil
        self.expirationDate = nil
        persistRevenueCatProState(isPro: false, proType: nil, expiration: nil)
    }

    func purchase(package: Package, extraProps: [String: String] = [:]) async throws -> Bool {
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }

        do {
            let result = try await Purchases.shared.purchase(package: package)
            if !result.userCancelled {
                updateFromCustomerInfo(result.customerInfo)
                let activeEntitlement = result.customerInfo.entitlements["pro"] ?? result.customerInfo.entitlements["songflip_pro"]
                let isSandbox = activeEntitlement?.isSandbox == true
                if !isSandbox {
                    var props = [
                        "package": package.identifier,
                        "store": "app_store"
                    ]
                    for (k, v) in extraProps {
                        props[k] = v
                    }
                    AptabaseClient.shared.trackEvent(eventName: "pro_purchased", props: props)
                }
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

    func extractCouponCode(from input: String) -> String {
        return CouponService.companion.shared.extractCouponCode(rawInput: input)
    }

    func redeemCoupon(code: String) async -> RedeemResult {
        let cleanCode = extractCouponCode(from: code)
        if cleanCode.count < 3 { return .invalid }

        isLoading = true
        defer { isLoading = false }

        let installId = getPersistentInstallId()
        let isAlreadyLifetime = self.isPro && self.proType == "lifetime_coupon"

        do {
            let outcome = try await CouponService.companion.shared.redeemCoupon(
                rawCode: cleanCode,
                installId: installId,
                isAlreadyLifetimeCoupon: isAlreadyLifetime,
                customEndpoints: nil
            )

            switch outcome.status {
            case .successLifetime:
                saveStoredCouponToken(outcome.token)
                if let iat = outcome.issuedAtTimestamp?.int64Value {
                    updateLastSeenServerTime(iat)
                }
                defaults.set("lifetime", forKey: Self.keyCouponType)
                defaults.set(outcome.code, forKey: Self.keyCouponCode)
                defaults.removeObject(forKey: Self.keyCouponExpiration)
                _ = evaluateVerifiedCouponState()
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.code)
                return .successLifetime

            case .success1year, .success3months, .success1month:
                saveStoredCouponToken(outcome.token)
                if let iat = outcome.issuedAtTimestamp?.int64Value {
                    updateLastSeenServerTime(iat)
                }
                let expireTime = outcome.expirationTimestamp?.doubleValue
                    ?? (Date().timeIntervalSince1970 * 1000 + 30 * 24 * 60 * 60 * 1000)
                defaults.set(outcome.type, forKey: Self.keyCouponType)
                defaults.set(outcome.code, forKey: Self.keyCouponCode)
                defaults.set(expireTime, forKey: Self.keyCouponExpiration)
                _ = evaluateVerifiedCouponState()
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.code)
                if outcome.status == .success1year {
                    return .success1Year
                } else if outcome.status == .success3months {
                    return .success3Months
                } else {
                    return .success1Month
                }

            case .alreadyActive:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "already_active")
                return .alreadyActive
            case .alreadyRedeemed:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "already_redeemed")
                return .alreadyRedeemed
            case .maxReached:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "max_reached")
                return .maxReached
            case .inactive:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "inactive")
                return .inactive
            case .rateLimited:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "rate_limited")
                return .rateLimited
            case .invalid:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "invalid")
                return .invalid
            default:
                AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "network_error")
                return .networkError
            }
        } catch {
            AptabaseClient.shared.trackPromoRedeemFailed(code: cleanCode, error: "exception")
            return .networkError
        }
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
