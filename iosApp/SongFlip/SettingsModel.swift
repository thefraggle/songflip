import SwiftUI
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
    @AppStorage("target_platform") var targetPlatform: String = "youtubeMusic"
    @AppStorage("auto_clipboard_detect") var autoClipboardDetect: Boolean = true
    @AppStorage("custom_api_url") var customApiUrl: String = ""
    @AppStorage("custom_api_token") var customApiToken: String = ""

    @Published var lastConvertedTitle: String? = nil
    @Published var lastConvertedArtist: String? = nil
    @Published var lastTargetUrl: String? = nil
    @Published var isResolving: Bool = false

    let engine = SongLinkEngine(client: HttpClientFactoryKt.createPlatformHttpClient(), cache: LinkCache(maxEntries: 200, ttlMs: 7 * 24 * 60 * 60 * 1000))
}
