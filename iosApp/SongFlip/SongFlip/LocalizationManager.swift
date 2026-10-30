import Foundation
import SwiftUI

/**
 * High-performance localization helper backed by the native Apple String Catalog (`Localizable.xcstrings`).
 *
 * Supports dynamic in-app language switching via Bundle resolution, avoiding the compile-time overhead
 * and binary bloat of large static Swift dictionaries.
 */
enum LocalizationManager {

    private nonisolated static let supportedLanguageCodes: Set<String> = [
        "de", "en", "da", "nb", "no", "sv", "nl", "fr", "es", "it", "pt", "pl", "ru",
        "tr", "uk", "ja", "ko", "zh", "in", "id", "vi", "bn", "hi", "mr", "cs", "el",
        "fi", "hu", "ro", "th"
    ]

    nonisolated static func currentLanguage() -> String {
        let storage = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        if let savedLang = storage.string(forKey: "app_language") {
            return normalizeLanguageCode(savedLang)
        }
        for preferred in Locale.preferredLanguages {
            let lower = preferred.lowercased()
            let prefix2 = String(lower.prefix(2))
            if lower.starts(with: "zh") { return "zh" }
            if prefix2 == "id" || prefix2 == "in" { return "id" }
            if prefix2 == "no" || prefix2 == "nb" || prefix2 == "nn" { return "nb" }
            if supportedLanguageCodes.contains(prefix2) { return prefix2 }
        }
        return "en"
    }

    private nonisolated static func normalizeLanguageCode(_ code: String) -> String {
        let lower = code.lowercased()
        if lower == "in" { return "id" }
        if lower == "no" { return "nb" }
        return lower
    }

    private nonisolated static func bundle(for lang: String) -> Bundle {
        let norm = normalizeLanguageCode(lang)
        let candidates = [norm, lang, norm == "id" ? "in" : norm, norm == "nb" ? "no" : norm]
        for c in candidates {
            if let path = Bundle.main.path(forResource: c, ofType: "lproj"),
               let b = Bundle(path: path) {
                return b
            }
        }
        return Bundle.main
    }

    nonisolated static func string(for key: String) -> String {
        string(for: key, lang: currentLanguage())
    }

    nonisolated static func string(for key: String, lang: String) -> String {
        let targetBundle = bundle(for: lang)
        let localized = NSLocalizedString(key, bundle: targetBundle, comment: "")
        if localized != key {
            return localized
        }

        // Fallback to English bundle if translation is missing for key
        if lang != "en" {
            let enBundle = bundle(for: "en")
            let enLocalized = NSLocalizedString(key, bundle: enBundle, comment: "")
            if enLocalized != key {
                return enLocalized
            }
        }

        return key
    }
}

extension View {
    func localized(_ key: String, lang: String) -> String {
        LocalizationManager.string(for: key, lang: lang)
    }
}
