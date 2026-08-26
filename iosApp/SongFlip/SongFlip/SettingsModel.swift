import SwiftUI
import Combine
import SongFlipKit

struct LanguageOption: Identifiable, Hashable {
    let code: String
    let name: String
    let flag: String
    var id: String { code }
}

enum PlatformChoice: String, CaseIterable, Identifiable {
    case youtubeMusic = "youtubeMusic"
    case appleMusic = "appleMusic"
    case spotify = "spotify"
    case tidal = "tidal"
    case deezer = "deezer"
    case amazonMusic = "amazonMusic"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .youtubeMusic: return "YouTube Music"
        case .appleMusic: return "Apple Music"
        case .spotify: return "Spotify"
        case .tidal: return "Tidal"
        case .deezer: return "Deezer"
        case .amazonMusic: return "Amazon Music"
        }
    }

    var iconName: String {
        switch self {
        case .youtubeMusic: return "play.rectangle.fill"
        case .appleMusic: return "music.note"
        case .spotify: return "dot.radiowaves.left.and.right"
        case .tidal: return "waveform"
        case .deezer: return "music.quarternote.3"
        case .amazonMusic: return "cart.fill"
        }
    }

    var brandColor: Color {
        switch self {
        case .youtubeMusic: return Color(red: 1.0, green: 0.0, blue: 0.0)
        case .appleMusic: return Color(red: 0.99, green: 0.24, blue: 0.27)
        case .spotify: return Color(red: 0.11, green: 0.73, blue: 0.33)
        case .tidal: return Color(red: 0.0, green: 0.85, blue: 0.9)
        case .deezer: return Color(red: 0.64, green: 0.22, blue: 1.0)
        case .amazonMusic: return Color(red: 0.15, green: 0.82, blue: 0.85)
        }
    }
}

class SettingsModel: ObservableObject {
    static let appGroupId = "group.de.goork.songflip"

    private var defaults: UserDefaults {
        UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
    }

    static let supportedLanguages: [LanguageOption] = [
        LanguageOption(code: "de", name: "Deutsch", flag: "🇩🇪"),
        LanguageOption(code: "en", name: "English", flag: "🇬🇧"),
        LanguageOption(code: "da", name: "Dansk", flag: "🇩🇰"),
        LanguageOption(code: "nb", name: "Norsk", flag: "🇳🇴"),
        LanguageOption(code: "sv", name: "Svenska", flag: "🇸🇪"),
        LanguageOption(code: "nl", name: "Nederlands", flag: "🇳🇱"),
        LanguageOption(code: "fr", name: "Français", flag: "🇫🇷"),
        LanguageOption(code: "es", name: "Español", flag: "🇪🇸"),
        LanguageOption(code: "it", name: "Italiano", flag: "🇮🇹"),
        LanguageOption(code: "pt", name: "Português", flag: "🇵🇹"),
        LanguageOption(code: "pl", name: "Polski", flag: "🇵🇱"),
        LanguageOption(code: "ru", name: "Русский", flag: "🇷🇺"),
        LanguageOption(code: "tr", name: "Türkçe", flag: "🇹🇷"),
        LanguageOption(code: "uk", name: "Українська", flag: "🇺🇦"),
        LanguageOption(code: "ja", name: "日本語", flag: "🇯🇵"),
        LanguageOption(code: "ko", name: "한국어", flag: "🇰🇷"),
        LanguageOption(code: "zh", name: "简体中文", flag: "🇨🇳"),
        LanguageOption(code: "in", name: "Bahasa Indonesia", flag: "🇮🇩"),
        LanguageOption(code: "vi", name: "Tiếng Việt", flag: "🇻🇳"),
        LanguageOption(code: "bn", name: "বাংলা", flag: "🇧🇩"),
        LanguageOption(code: "hi", name: "हिन्दी", flag: "🇮🇳"),
        LanguageOption(code: "mr", name: "मराठी", flag: "🇮🇳")
    ]

    @Published var targetPlatform: String {
        didSet { defaults.set(targetPlatform, forKey: "target_platform") }
    }
    @Published var autoClipboardDetect: Bool {
        didSet { defaults.set(autoClipboardDetect, forKey: "auto_clipboard_detect") }
    }
    @Published var selectedLanguage: String {
        didSet { defaults.set(selectedLanguage, forKey: "app_language") }
    }
    @Published var themeMode: String {
        didSet { defaults.set(themeMode, forKey: "theme_mode") }
    }
    @Published var customApiUrl: String {
        didSet { defaults.set(customApiUrl, forKey: "custom_api_url") }
    }
    @Published var customApiToken: String {
        didSet { defaults.set(customApiToken, forKey: "custom_api_token") }
    }

    @Published var lastConvertedTitle: String? = nil
    @Published var lastConvertedArtist: String? = nil
    @Published var lastTargetUrl: String? = nil
    @Published var isResolving: Bool = false

    let engine = SongLinkEngine()

    init() {
        let storage = UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
        self.targetPlatform = storage.string(forKey: "target_platform") ?? "youtubeMusic"
        self.autoClipboardDetect = storage.object(forKey: "auto_clipboard_detect") as? Bool ?? true

        let savedLang = storage.string(forKey: "app_language")
        if let savedLang = savedLang {
            self.selectedLanguage = savedLang
        } else {
            let preferred = Locale.preferredLanguages.first?.prefix(2).lowercased() ?? "en"
            let isSupported = SettingsModel.supportedLanguages.contains { $0.code == preferred }
            self.selectedLanguage = isSupported ? String(preferred) : "en"
        }

        self.themeMode = storage.string(forKey: "theme_mode") ?? "dark"
        self.customApiUrl = storage.string(forKey: "custom_api_url") ?? ""
        self.customApiToken = storage.string(forKey: "custom_api_token") ?? ""
    }
}
