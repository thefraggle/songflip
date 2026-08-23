import SwiftUI
import Combine
import SongFlipKit

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
}

class SettingsModel: ObservableObject {
    @Published var targetPlatform: String {
        didSet {
            UserDefaults.standard.set(targetPlatform, forKey: "target_platform")
        }
    }
    @Published var autoClipboardDetect: Bool {
        didSet {
            UserDefaults.standard.set(autoClipboardDetect, forKey: "auto_clipboard_detect")
        }
    }
    @Published var customApiUrl: String {
        didSet {
            UserDefaults.standard.set(customApiUrl, forKey: "custom_api_url")
        }
    }
    @Published var customApiToken: String {
        didSet {
            UserDefaults.standard.set(customApiToken, forKey: "custom_api_token")
        }
    }

    @Published var lastConvertedTitle: String? = nil
    @Published var lastConvertedArtist: String? = nil
    @Published var lastTargetUrl: String? = nil
    @Published var isResolving: Bool = false

    let engine = SongLinkEngine()

    init() {
        self.targetPlatform = UserDefaults.standard.string(forKey: "target_platform") ?? "youtubeMusic"
        self.autoClipboardDetect = UserDefaults.standard.object(forKey: "auto_clipboard_detect") as? Bool ?? true
        self.customApiUrl = UserDefaults.standard.string(forKey: "custom_api_url") ?? ""
        self.customApiToken = UserDefaults.standard.string(forKey: "custom_api_token") ?? ""
    }
}
