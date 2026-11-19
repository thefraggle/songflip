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

enum ProStateStorage {
    static let appGroupId = "group.de.goork.songflip"
    static let keyCouponType = "pro_coupon_type"
    static let keyCouponCode = "pro_coupon_code"
    static let keyCouponExpiration = "pro_coupon_expiration"
    static let keyCouponToken = "pro_coupon_token"
    static let keyIsProCached = "is_pro_cached"
    static let keyProCacheType = "pro_cache_type"
    static let keyProCacheExp = "pro_cache_exp"
    static let keyProAuthToken = "pro_auth_token"
    static let rcExpiryGraceMs: Double = 15 * 60 * 1000

    static func writeVerifiedProState(defaults: UserDefaults, isPro: Bool, proType: String?, expMs: Int64) {
        defaults.set(isPro, forKey: keyIsProCached)
        defaults.set(proType ?? "", forKey: keyProCacheType)
        defaults.set(expMs, forKey: keyProCacheExp)
    }

    static func isProActive(defaults: UserDefaults = UserDefaults(suiteName: appGroupId) ?? .standard) -> Bool {
        let isPro = defaults.bool(forKey: keyIsProCached)
        guard isPro else { return false }

        let expMs = defaults.object(forKey: keyProCacheExp) as? Int64 ?? (defaults.object(forKey: keyProCacheExp) as? NSNumber)?.int64Value ?? 0
        if expMs > 0 {
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            if nowMs > (expMs + Int64(rcExpiryGraceMs)) {
                return false
            }
        }
        return true
    }
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
    private static let keyIsProCached = ProStateStorage.keyIsProCached
    private static let keyProCacheType = ProStateStorage.keyProCacheType
    private static let keyProCacheExp = ProStateStorage.keyProCacheExp
    private static let keyProAuthToken = ProStateStorage.keyProAuthToken

    nonisolated static func writeVerifiedProState(defaults: UserDefaults, isPro: Bool, proType: String?, expMs: Int64) {
        ProStateStorage.writeVerifiedProState(defaults: defaults, isPro: isPro, proType: proType, expMs: expMs)
    }

    nonisolated static func isProActive(defaults: UserDefaults = UserDefaults(suiteName: ProStateStorage.appGroupId) ?? .standard) -> Bool {
        ProStateStorage.isProActive(defaults: defaults)
    }

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
        UserDefaults(suiteName: ProStateStorage.appGroupId) ?? UserDefaults.standard
    }

    private lazy var entitlementStore: ProEntitlementStore = {
        let port = IosSecureKeyValuePort(
            defaults: defaults,
            readKeychain: { [weak self] account in self?.readKeychainString(account: account) },
            writeKeychain: { [weak self] account, value in self?.writeKeychainString(account: account, value: value) }
        )
        return ProEntitlementStore(
            storage: port,
            couponService: CouponService.companion.shared,
            installIdsProvider: { [weak self] in
                guard let self else { return [] }
                var ids = [self.getPersistentInstallId()]
                if let vendorId = UIDevice.current.identifierForVendor?.uuidString.lowercased(), !vendorId.isEmpty {
                    ids.append(vendorId)
                }
                return ids
            },
            rcAppUserIdProvider: { [weak self] in
                if Purchases.isConfigured {
                    return Purchases.shared.appUserID
                }
                return self?.getDeviceFingerprint() ?? ""
            },
            clockMs: { KotlinLong(value: Int64(Date().timeIntervalSince1970 * 1000)) }
        )
    }()

    private var currentEntitlementState: EntitlementState {
        EntitlementState(
            isPro: isPro,
            proType: proType ?? "",
            expirationDateMs: expirationDate.map { KotlinLong(value: Int64($0.timeIntervalSince1970 * 1000)) }
        )
    }

    private func applyEntitlementState(_ state: EntitlementState) {
        self.isPro = state.isPro
        self.proType = state.proType.isEmpty ? nil : state.proType
        if let expMs = state.expirationDateMs?.int64Value, expMs > 0 {
            self.expirationDate = Date(timeIntervalSince1970: Double(expMs) / 1000)
        } else {
            self.expirationDate = nil
        }
        Self.writeVerifiedProState(
            defaults: defaults,
            isPro: state.isPro,
            proType: state.proType.isEmpty ? nil : state.proType,
            expMs: state.expirationDateMs?.int64Value ?? 0
        )
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

    var authToken: String {
        entitlementStore.resolveAuthToken(currentState: currentEntitlementState)
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

    private var isAutomatedTest: Bool = false

    func configure(isDebug: Bool = false, isAutomatedTest: Bool = false) {
        self.isAutomatedTest = isAutomatedTest
        let key = Self.apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty, !key.contains("your_revenuecat_key") else {
            Self.logger.warning("[ProManager] Skipping RevenueCat configure: empty/placeholder API key")
            Task {
                await migrateStoredCouponTokenIfNeeded()
            }
            return
        }
        Purchases.logLevel = isDebug ? .debug : .warn
        Purchases.configure(withAPIKey: key)
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
        do {
            guard let result = try await entitlementStore.migrateStoredCouponTokenIfNeeded(
                currentState: currentEntitlementState,
                customEndpoints: nil
            ) else { return }
            applyEntitlementState(result.newState)
            switch result.status {
            case .upgraded:
                Self.logger.info("[ProManager] Coupon token migrated to current signing key")
            case .revoked:
                Self.logger.warning("[ProManager] Stored coupon token rejected by server (invalid signature) – removed")
            default:
                Self.logger.info("[ProManager] Coupon token migration skipped: \(String(describing: result.status))")
            }
        } catch {
            Self.logger.error("[ProManager] Coupon token migration failed: \(error.localizedDescription)")
        }
    }

    private func loadCachedState() {
        applyEntitlementState(entitlementStore.hydrateFromCache())
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
        guard Purchases.isConfigured else { return }
        do {
            let customerInfo = try await Purchases.shared.customerInfo()
            updateFromCustomerInfo(customerInfo)
        } catch {
            Self.logger.error("[ProManager] Failed to fetch customer info: \(error.localizedDescription)")
        }
    }

    func updateFromCustomerInfo(_ customerInfo: CustomerInfo) {
        let proEntitlement = customerInfo.entitlements["pro"] ?? customerInfo.entitlements["songflip_pro"]
        let isRevenueCatPro = proEntitlement?.isActive == true
        let snapshot: RevenueCatSnapshot?
        if isRevenueCatPro {
            let isLifetime = proEntitlement?.periodType == .normal && proEntitlement?.expirationDate == nil
            let expMillis = proEntitlement?.expirationDate.map { KotlinLong(value: Int64($0.timeIntervalSince1970 * 1000)) }
            snapshot = RevenueCatSnapshot(active: true, expirationMs: expMillis, isLifetime: isLifetime)
        } else {
            snapshot = nil
        }
        let state = entitlementStore.evaluate(rcSnapshot: snapshot, isExplicitRcCheck: true)
        applyEntitlementState(state)
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
        isLoading = true
        defer { isLoading = false }

        do {
            let outcome = try await entitlementStore.redeemCoupon(
                rawCode: code,
                currentState: currentEntitlementState,
                customEndpoints: nil
            )
            applyEntitlementState(outcome.newState)
            switch outcome.status {
            case .successLifetime:
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.cleanCode)
                return .successLifetime
            case .success1year:
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.cleanCode)
                return .success1Year
            case .success3months:
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.cleanCode)
                return .success3Months
            case .success1month:
                AptabaseClient.shared.trackPromoRedeemedSuccess(code: outcome.cleanCode)
                return .success1Month
            case .alreadyActive:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "already_active")
                return .alreadyActive
            case .alreadyRedeemed:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "already_redeemed")
                return .alreadyRedeemed
            case .maxReached:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "max_reached")
                return .maxReached
            case .inactive:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "inactive")
                return .inactive
            case .rateLimited:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "rate_limited")
                return .rateLimited
            case .invalid:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "invalid")
                return .invalid
            default:
                AptabaseClient.shared.trackPromoRedeemFailed(code: outcome.cleanCode, error: "network_error")
                return .networkError
            }
        } catch {
            AptabaseClient.shared.trackPromoRedeemFailed(code: code, error: "exception")
            return .networkError
        }
    }
}

private final class IosSecureKeyValuePort: SecureKeyValuePort {
    private let defaults: UserDefaults
    private let readKeychain: (String) -> String?
    private let writeKeychain: (String, String?) -> Void

    init(
        defaults: UserDefaults,
        readKeychain: @escaping (String) -> String?,
        writeKeychain: @escaping (String, String?) -> Void
    ) {
        self.defaults = defaults
        self.readKeychain = readKeychain
        self.writeKeychain = writeKeychain
    }

    func getString(key: String) -> String? {
        switch key {
        case ProEntitlementStore.companion.KEY_COUPON_TOKEN:
            if let kc = readKeychain("pro_coupon_token_v2") { return kc }
            if let legacy = defaults.string(forKey: key), !legacy.isEmpty {
                writeKeychain("pro_coupon_token_v2", legacy)
                defaults.removeObject(forKey: key)
                return legacy
            }
            return nil
        case ProEntitlementStore.companion.KEY_LAST_SEEN_SERVER_TIME:
            return readKeychain("last_seen_server_time_v2")
        default:
            return defaults.string(forKey: key)
        }
    }

    func putString(key: String, value: String) {
        switch key {
        case ProEntitlementStore.companion.KEY_COUPON_TOKEN:
            writeKeychain("pro_coupon_token_v2", value)
            defaults.removeObject(forKey: key)
        case ProEntitlementStore.companion.KEY_LAST_SEEN_SERVER_TIME:
            writeKeychain("last_seen_server_time_v2", value)
        default:
            defaults.set(value, forKey: key)
        }
    }

    func remove(key: String) {
        switch key {
        case ProEntitlementStore.companion.KEY_COUPON_TOKEN:
            writeKeychain("pro_coupon_token_v2", nil)
            defaults.removeObject(forKey: key)
        case ProEntitlementStore.companion.KEY_LAST_SEEN_SERVER_TIME:
            writeKeychain("last_seen_server_time_v2", nil)
        default:
            defaults.removeObject(forKey: key)
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
