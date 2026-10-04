import Foundation
import Combine
import SwiftUI
import SongFlipKit

struct PromoBannerConfig: Codable, Equatable {
    var enabled: Bool = false
    var campaignId: String = ""
    var endTimestampMs: Int64 = 0
    var badge: [String: String] = [:]
    var title: [String: String] = [:]
    var subtitle: [String: String] = [:]
    var buttonText: [String: String] = [:]

    enum CodingKeys: String, CodingKey {
        case enabled
        case campaignId = "campaign_id"
        case endTimestampMs = "end_timestamp"
        case badge
        case title
        case subtitle
        case buttonText = "button_text"
    }

    func isValid(currentTimeMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)) -> Bool {
        guard enabled else { return false }
        if endTimestampMs > 0 && currentTimeMs >= endTimestampMs {
            return false
        }
        return true
    }

    func getLocalizedBadge(locale: String) -> String {
        resolveLocalization(map: badge, locale: locale)
    }

    func getLocalizedTitle(locale: String) -> String {
        resolveLocalization(map: title, locale: locale)
    }

    func getLocalizedSubtitle(locale: String) -> String {
        resolveLocalization(map: subtitle, locale: locale)
    }

    func getLocalizedButtonText(locale: String) -> String {
        resolveLocalization(map: buttonText, locale: locale)
    }

    private func resolveLocalization(map: [String: String], locale: String) -> String {
        if map.isEmpty { return "" }
        let clean = locale.lowercased().replacingOccurrences(of: "_", with: "-").trimmingCharacters(in: .whitespacesAndNewlines)
        let primary = clean.components(separatedBy: "-").first ?? clean
        return map[clean] ?? map[primary] ?? map["en"] ?? map.values.first ?? ""
    }
}

@MainActor
final class PromoBannerManager: ObservableObject {
    static let shared = PromoBannerManager()

    @Published var config: PromoBannerConfig = PromoBannerConfig()
    @Published var isDismissed: Bool = false

    private let endpointUrl = "https://songflip-web.web.app/api/promo-banner"
    private let cacheKey = "cached_promo_banner_json"
    private var lastFetchTime: Date?

    private var defaults: UserDefaults {
        UserDefaults(suiteName: SettingsModel.appGroupId) ?? UserDefaults.standard
    }

    private init() {
        loadCachedConfig()
    }

    func dismiss() {
        isDismissed = true
    }

    func resetDismissed() {
        isDismissed = false
    }

    func isBannerVisible(isPro: Bool) -> Bool {
        if isPro { return false }
        if isDismissed { return false }
        return config.isValid()
    }

    func loadCachedConfig() {
        if let rawJson = defaults.string(forKey: cacheKey),
           let data = rawJson.data(using: .utf8),
           let decoded = try? JSONDecoder().decode(PromoBannerConfig.self, data: data),
           decoded.isValid() {
            self.config = decoded
        }
    }

    func fetchPromoBanner(forceRefresh: Bool = false) async {
        if !forceRefresh, let last = lastFetchTime, Date().timeIntervalSince(last) < 900 {
            return
        }

        guard let url = URL(string: endpointUrl) else { return }

        do {
            var request = URLRequest(url: url)
            request.timeoutInterval = 10
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
                return
            }

            let decoded = try JSONDecoder().decode(PromoBannerConfig.self, data: data)
            self.config = decoded
            self.lastFetchTime = Date()

            if let rawJson = String(data: data, encoding: .utf8) {
                defaults.set(rawJson, forKey: cacheKey)
            }
        } catch {
            // Keep existing/cached config on transient connection failures
        }
    }
}
