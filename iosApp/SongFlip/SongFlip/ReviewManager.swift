import Foundation
import StoreKit
import UIKit
import SongFlipKit

@MainActor
final class ReviewManager {
    static let shared = ReviewManager()

    private static let minFlipsForReview = 5
    private static let minDaysAfterInstallMs: Double = 3 * 24 * 60 * 60 * 1000 // 3 days
    private static let promptCooldownMs: Double = 21 * 24 * 60 * 60 * 1000 // 21 days

    private init() {}

    func isEligibleForReview(
        now: Double,
        flips: Int,
        installTimestamp: Double,
        lastPromptTimestamp: Double
    ) -> Bool {
        let isInstalledLongEnough = (now - installTimestamp) >= Self.minDaysAfterInstallMs
        let isCooldownPassed = (now - lastPromptTimestamp) >= Self.promptCooldownMs
        let hasEnoughFlips = flips >= Self.minFlipsForReview
        return hasEnoughFlips && isInstalledLongEnough && isCooldownPassed
    }

    func maybeRequestReview(trigger: String, settings: SettingsModel) {
        let now = Date().timeIntervalSince1970 * 1000
        let flips = settings.successfulFlipCount
        let installTs = settings.firstInstallTimestamp
        let lastPrompt = settings.lastReviewPromptTimestamp

        guard isEligibleForReview(now: now, flips: flips, installTimestamp: installTs, lastPromptTimestamp: lastPrompt) else {
            return
        }

        settings.lastReviewPromptTimestamp = now
        requestReview(trigger: trigger)
    }

    func requestReview(trigger: String) {
        AptabaseClient.shared.trackEvent(eventName: "review_prompt_triggered", props: ["trigger": trigger])

        if let windowScene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive }) {
            SKStoreReviewController.requestReview(in: windowScene)
        }
    }
}
