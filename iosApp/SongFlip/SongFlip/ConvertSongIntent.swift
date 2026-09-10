import AppIntents
import UIKit
import SongFlipKit

enum ConvertSongIntentError: Swift.Error, CustomLocalizedStringResourceConvertible, LocalizedError {
    case emptyClipboard
    case conversionFailed(String)

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .emptyClipboard:
            return LocalizedStringResource("intent_error_empty_clipboard", defaultValue: "No music link provided and clipboard is empty.")
        case .conversionFailed(let reason):
            return LocalizedStringResource("intent_error_failed_to_convert", defaultValue: "Error converting link: \(reason)")
        }
    }

    var errorDescription: String? {
        let lang = LocalizationManager.currentLanguage()
        switch self {
        case .emptyClipboard:
            return LocalizationManager.string(for: "intent_error_empty_clipboard", lang: lang)
        case .conversionFailed(let reason):
            let format = LocalizationManager.string(for: "intent_error_failed_to_convert", lang: lang)
            return String(format: format, reason)
        }
    }
}

struct ConvertSongIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("intent_title", defaultValue: "Open Song in SongFlip")
    static var description = IntentDescription(LocalizedStringResource("intent_description", defaultValue: "Converts a copied or shared music link and opens the target app."))

    @Parameter(title: LocalizedStringResource("intent_param_music_link", defaultValue: "Music Link"))
    var inputUrl: String?

    func perform() async throws -> some IntentResult {
        let urlToConvert: String
        if let input = inputUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !input.isEmpty {
            urlToConvert = input
        } else if let clip = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !clip.isEmpty {
            urlToConvert = clip
        } else {
            throw ConvertSongIntentError.emptyClipboard
        }

        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        let rawTarget = defaults.string(forKey: "target_platform") ?? "appleMusic"
        let targetPlatform = PlatformChoice(rawValue: rawTarget) != nil ? rawTarget : "appleMusic"
        let customUrl = defaults.string(forKey: "custom_api_url") ?? ""
        let customToken = defaults.string(forKey: "custom_api_token") ?? ""

        let engine = SongLinkEngine.shared
        let res = try? await engine.resolveTargetUrl(
            inputUrl: urlToConvert,
            targetPlatformKey: targetPlatform,
            customApiUrl: customUrl,
            customApiToken: customToken
        )

        if let success = res as? ResolutionResult.Success {
            await MainActor.run {
                HistoryModel.shared.add(
                    title: success.title ?? LocalizationManager.string(for: "unknown_song"),
                    artist: success.artist,
                    sourceUrl: urlToConvert,
                    targetUrl: success.targetUrl,
                    targetPlatform: targetPlatform,
                    isAlbum: success.isAlbum
                )
            }

            AptabaseClient.shared.trackLinkFlipped(
                target: targetPlatform,
                isAlbum: success.isAlbum,
                isSearch: false
            )

            var opened = false
            if let nativeUri = success.nativeAppUri, let nativeUrl = URL(string: nativeUri) {
                opened = await UIApplication.shared.open(nativeUrl)
            }

            if !opened, let webUrl = URL(string: success.targetUrl) {
                await UIApplication.shared.open(webUrl)
            }

            return .result()
        } else if let playlist = res as? ResolutionResult.Playlist {
            AptabaseClient.shared.trackLinkFlipFailed(
                target: targetPlatform,
                reason: "playlist_detected"
            )
            if let url = URL(string: playlist.originalUrl) {
                await UIApplication.shared.open(url)
            }
            return .result()
        } else {
            let defaultMsg = LocalizationManager.string(for: "intent_error_failed_to_resolve", lang: LocalizationManager.currentLanguage())
            let reason = (res as? ResolutionResult.Error)?.message ?? defaultMsg
            AptabaseClient.shared.trackLinkFlipFailed(
                target: targetPlatform,
                reason: reason
            )
            throw ConvertSongIntentError.conversionFailed(reason)
        }
    }
}
