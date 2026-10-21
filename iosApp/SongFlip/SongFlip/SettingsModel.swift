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
    case soundcloud = "soundcloud"
    case bandcamp = "bandcamp"
    case qobuz = "qobuz"
    case beatport = "beatport"

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .youtubeMusic: return "YouTube Music"
        case .appleMusic: return "Apple Music"
        case .spotify: return "Spotify"
        case .tidal: return "Tidal"
        case .deezer: return "Deezer"
        case .amazonMusic: return "Amazon Music"
        case .soundcloud: return "SoundCloud"
        case .bandcamp: return "Bandcamp"
        case .qobuz: return "Qobuz"
        case .beatport: return "Beatport"
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
        case .soundcloud: return "cloud.fill"
        case .bandcamp: return "opticaldisc"
        case .qobuz: return "waveform.circle.fill"
        case .beatport: return "headphones"
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
        case .soundcloud: return Color(red: 1.0, green: 0.33, blue: 0.0)
        case .bandcamp: return Color(red: 0.11, green: 0.63, blue: 0.76)
        case .qobuz: return Color(red: 0.0, green: 0.45, blue: 0.82)
        case .beatport: return Color(red: 0.0, green: 0.85, blue: 0.5)
        }
    }
}

struct IosAlternativePlayer: Identifiable, Hashable {
    let id: String
    let appName: String
    let platformKey: String
    let probeScheme: String

    static let allCandidates: [IosAlternativePlayer] = [
        IosAlternativePlayer(id: "demus", appName: "Demus", platformKey: "youtubeMusic", probeScheme: "demus://"),
        IosAlternativePlayer(id: "yattee", appName: "Yattee", platformKey: "youtubeMusic", probeScheme: "yattee://"),
        IosAlternativePlayer(id: "marvispro", appName: "Marvis Pro", platformKey: "appleMusic", probeScheme: "marvispro://"),
        IosAlternativePlayer(id: "soor", appName: "Soor", platformKey: "appleMusic", probeScheme: "soor://"),
        IosAlternativePlayer(id: "joitunes", appName: "Joi Tunes", platformKey: "appleMusic", probeScheme: "joitunes://")
    ]
}

func platformColor(for key: String) -> Color {
    let clean = key.lowercased()
        .replacingOccurrences(of: "_playlist", with: "")
        .replacingOccurrences(of: "_search", with: "")
        .replacingOccurrences(of: "_artist", with: "")
        .replacingOccurrences(of: "_fallback", with: "")
    switch clean {
    case "spotify": return Color(red: 0.11, green: 0.73, blue: 0.33)
    case "applemusic", "apple": return Color(red: 0.99, green: 0.24, blue: 0.27)
    case "youtubemusic", "youtube": return Color(red: 1.0, green: 0.0, blue: 0.0)
    case "deezer": return Color(red: 0.64, green: 0.22, blue: 1.0)
    case "tidal": return Color(red: 0.0, green: 0.9, blue: 0.9)
    case "amazonmusic", "amazon": return Color(red: 0.15, green: 0.82, blue: 0.85)
    case "soundcloud": return Color(red: 1.0, green: 0.33, blue: 0.0)
    case "bandcamp": return Color(red: 0.11, green: 0.63, blue: 0.76)
    case "qobuz": return Color(red: 0.0, green: 0.45, blue: 0.82)
    case "beatport": return Color(red: 0.0, green: 0.85, blue: 0.5)
    default: return .green
    }
}

func platformIcon(for key: String) -> String {
    let clean = key.lowercased()
        .replacingOccurrences(of: "_playlist", with: "")
        .replacingOccurrences(of: "_search", with: "")
        .replacingOccurrences(of: "_artist", with: "")
        .replacingOccurrences(of: "_fallback", with: "")
    switch clean {
    case "spotify": return "dot.radiowaves.left.and.right"
    case "applemusic", "apple": return "music.note"
    case "youtubemusic", "youtube": return "play.rectangle.fill"
    case "deezer": return "music.quarternote.3"
    case "tidal": return "waveform"
    case "amazonmusic", "amazon": return "cart.fill"
    case "soundcloud": return "cloud.fill"
    case "bandcamp": return "opticaldisc"
    case "qobuz": return "waveform.circle.fill"
    case "beatport": return "headphones"
    default: return "music.note.list"
    }
}

func detectSourcePlatformName(url: String) -> String {
    let lower = url.lowercased()
    if lower.contains("spotify.com") || lower.contains("spotify.link") { return "Spotify" }
    if lower.contains("apple.com") || lower.contains("apple.co") { return "Apple Music" }
    if lower.contains("youtube.com") || lower.contains("youtu.be") { return "YouTube Music" }
    if lower.contains("deezer.com") || lower.contains("deezer.page.link") { return "Deezer" }
    if lower.contains("tidal.com") { return "Tidal" }
    if lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co") { return "Amazon Music" }
    if lower.contains("soundcloud.com") || lower.contains("on.soundcloud.com") { return "SoundCloud" }
    if lower.contains("bandcamp.com") { return "Bandcamp" }
    if lower.contains("qobuz.com") { return "Qobuz" }
    if lower.contains("beatport.com") { return "Beatport" }
    if lower.contains("song.link") || lower.contains("album.link") || lower.contains("odesli.co") { return "Universal Link" }
    return "Music Link"
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
        LanguageOption(code: "mr", name: "मराठी", flag: "🇮🇳"),
        LanguageOption(code: "cs", name: "Čeština", flag: "🇨🇿"),
        LanguageOption(code: "el", name: "Ελληνικά", flag: "🇬🇷"),
        LanguageOption(code: "fi", name: "Suomi", flag: "🇫🇮"),
        LanguageOption(code: "hu", name: "Magyar", flag: "🇭🇺"),
        LanguageOption(code: "ro", name: "Română", flag: "🇷🇴"),
        LanguageOption(code: "th", name: "ไทย", flag: "🇹🇭")
    ]

    @Published var targetPlatform: String {
        didSet { defaults.set(targetPlatform, forKey: "target_platform") }
    }
    @Published var autoClipboardDetect: Bool {
        didSet { defaults.set(autoClipboardDetect, forKey: "auto_clipboard_detect") }
    }
    @Published var askEveryTime: Bool {
        didSet { defaults.set(askEveryTime, forKey: "ask_every_time") }
    }
    @Published var audiophileMode: Bool {
        didSet { defaults.set(audiophileMode, forKey: "audiophile_mode") }
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

    var colorScheme: ColorScheme? {
        switch themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    let engine = SongLinkEngine.shared

    init() {
        let storage = UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
        let savedTarget = storage.string(forKey: "target_platform") ?? "appleMusic"
        let validTarget = PlatformChoice(rawValue: savedTarget) != nil ? savedTarget : "appleMusic"
        self.targetPlatform = validTarget
        if savedTarget != validTarget {
            storage.set(validTarget, forKey: "target_platform")
        }
        self.autoClipboardDetect = storage.object(forKey: "auto_clipboard_detect") as? Bool ?? true
        self.askEveryTime = storage.object(forKey: "ask_every_time") as? Bool ?? false
        self.audiophileMode = storage.object(forKey: "audiophile_mode") as? Bool ?? false

        let savedLang = storage.string(forKey: "app_language")
        if let savedLang = savedLang {
            self.selectedLanguage = savedLang
        } else {
            // Auto-detect system language with fallback to EN
            var detectedLang = "en"
            for preferred in Locale.preferredLanguages {
                let lower = preferred.lowercased()
                let prefix2 = String(lower.prefix(2))

                if lower.starts(with: "zh") {
                    detectedLang = "zh"
                    break
                } else if prefix2 == "id" || prefix2 == "in" {
                    detectedLang = "in"
                    break
                } else if prefix2 == "no" || prefix2 == "nb" || prefix2 == "nn" {
                    detectedLang = "nb"
                    break
                } else if SettingsModel.supportedLanguages.contains(where: { $0.code == prefix2 }) {
                    detectedLang = prefix2
                    break
                }
            }
            self.selectedLanguage = detectedLang
        }

        self.themeMode = storage.string(forKey: "theme_mode") ?? "system"
        self.customApiUrl = storage.string(forKey: "custom_api_url") ?? ""
        self.customApiToken = storage.string(forKey: "custom_api_token") ?? ""

        self.successfulFlipCount = storage.integer(forKey: Self.keySuccessfulFlipCount)
        self.lastDismissedProNudgeMilestone = storage.integer(forKey: Self.keyLastDismissedProNudgeMilestone)
        let savedInstall = storage.double(forKey: Self.keyFirstInstallTimestamp)
        if savedInstall == 0 {
            let now = Date().timeIntervalSince1970 * 1000
            storage.set(now, forKey: Self.keyFirstInstallTimestamp)
            self.firstInstallTimestamp = now
        } else {
            self.firstInstallTimestamp = savedInstall
        }
        self.lastReviewPromptTimestamp = storage.double(forKey: Self.keyLastReviewPromptTimestamp)
    }

    static let keySuccessfulFlipCount = "successful_flip_count"
    static let keyLastDismissedProNudgeMilestone = "last_dismissed_pro_nudge_milestone"
    static let keyFirstInstallTimestamp = "first_install_timestamp"
    static let keyLastReviewPromptTimestamp = "last_review_prompt_timestamp"

    @Published var successfulFlipCount: Int {
        didSet { defaults.set(successfulFlipCount, forKey: Self.keySuccessfulFlipCount) }
    }
    @Published var lastDismissedProNudgeMilestone: Int {
        didSet { defaults.set(lastDismissedProNudgeMilestone, forKey: Self.keyLastDismissedProNudgeMilestone) }
    }
    @Published var firstInstallTimestamp: Double {
        didSet { defaults.set(firstInstallTimestamp, forKey: Self.keyFirstInstallTimestamp) }
    }
    @Published var lastReviewPromptTimestamp: Double {
        didSet { defaults.set(lastReviewPromptTimestamp, forKey: Self.keyLastReviewPromptTimestamp) }
    }

    @discardableResult
    func incrementSuccessfulFlipCount() -> Int {
        successfulFlipCount += 1
        return successfulFlipCount
    }

    func getActiveProNudgeMilestone() -> Int {
        let flips = successfulFlipCount
        let milestone: Int
        switch flips {
        case 100...:
            milestone = (flips / 50) * 50
        case 50..<100:
            milestone = 50
        case 20..<50:
            milestone = 20
        default:
            milestone = 0
        }
        return (milestone > 0 && lastDismissedProNudgeMilestone < milestone) ? milestone : 0
    }

    func dismissProNudgeMilestone(_ milestone: Int) {
        lastDismissedProNudgeMilestone = milestone
    }

    // MARK: - Alternative Player Selection & Unified Launch Routing (#139)

    func preferredPlayerId(for platformKey: String) -> String? {
        let cleanKey = platformKey.components(separatedBy: "_").first ?? platformKey
        let val = defaults.string(forKey: "preferred_player_\(cleanKey)")?.trimmingCharacters(in: .whitespacesAndNewlines)
        return (val?.isEmpty == false) ? val : nil
    }

    func setPreferredPlayerId(_ playerId: String?, for platformKey: String) {
        let cleanKey = platformKey.components(separatedBy: "_").first ?? platformKey
        objectWillChange.send()
        if let playerId = playerId, !playerId.isEmpty {
            defaults.set(playerId, forKey: "preferred_player_\(cleanKey)")
        } else {
            defaults.removeObject(forKey: "preferred_player_\(cleanKey)")
        }
    }

    func installedAlternativePlayers(for platformKey: String) -> [IosAlternativePlayer] {
        let cleanKey = platformKey.components(separatedBy: "_").first ?? platformKey
        return IosAlternativePlayer.allCandidates.filter { candidate in
            guard candidate.platformKey == cleanKey,
                  let url = URL(string: candidate.probeScheme) else { return false }
            return UIApplication.shared.canOpenURL(url)
        }
    }

    func openResolvedTarget(
        targetUrl: String,
        nativeAppUri: String? = nil,
        platformKey: String,
        title: String? = nil,
        artist: String? = nil
    ) {
        let cleanKey = platformKey.components(separatedBy: "_").first ?? platformKey
        let webFallbackStr = UrlUtils.shared.toWebFallbackUrl(rawUrl: targetUrl, targetPlatformKey: cleanKey)
        let fallbackUrl = URL(string: webFallbackStr) ?? URL(string: targetUrl)

        var primaryStr = nativeAppUri ?? UrlUtils.shared.toNativeAppUri(url: targetUrl, platformKey: cleanKey)
        if let preferredId = preferredPlayerId(for: cleanKey) {
            if let customUri = UrlUtils.shared.toAlternativeClientUri(
                url: targetUrl,
                platformKey: cleanKey,
                clientId: preferredId,
                title: title,
                artist: artist
            ) {
                primaryStr = customUri
            }
        }

        if let primaryUrl = URL(string: primaryStr) {
            UIApplication.shared.open(primaryUrl, options: [:]) { opened in
                if !opened, let fallbackUrl = fallbackUrl, primaryUrl != fallbackUrl {
                    UIApplication.shared.open(fallbackUrl)
                }
            }
        } else if let fallbackUrl = fallbackUrl {
            UIApplication.shared.open(fallbackUrl)
        }
    }
}
