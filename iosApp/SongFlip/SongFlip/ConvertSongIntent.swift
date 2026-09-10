import AppIntents
import UIKit
import SongFlipKit

enum ConvertSongIntentError: Swift.Error, CustomLocalizedStringResourceConvertible, LocalizedError {
    case emptyClipboard
    case conversionFailed(String)

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case .emptyClipboard:
            return "Kein Musik-Link übergeben und Zwischenablage ist leer."
        case .conversionFailed(let reason):
            return "Fehler beim Konvertieren des Links: \(reason)"
        }
    }

    var errorDescription: String? {
        switch self {
        case .emptyClipboard:
            return "Kein Musik-Link übergeben und Zwischenablage ist leer."
        case .conversionFailed(let reason):
            return "Fehler beim Konvertieren des Links: \(reason)"
        }
    }
}

struct ConvertSongIntent: AppIntent {
    static var title: LocalizedStringResource = "Song in SongFlip öffnen"
    static var description = IntentDescription("Konvertiert einen kopierten oder übergebenen Musik-Link und öffnet die Ziel-App.")

    @Parameter(title: "Musik Link")
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

        let engine = SongLinkEngine()
        let res = try? await engine.resolveTargetUrl(
            inputUrl: urlToConvert,
            targetPlatformKey: targetPlatform,
            customApiUrl: customUrl,
            customApiToken: customToken
        )

        if let success = res as? ResolutionResult.Success {
            await MainActor.run {
                HistoryModel.shared.add(
                    title: success.title ?? "Song",
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
        } else {
            let reason = (res as? ResolutionResult.Error)?.message ?? "Konnte Ziel-URL nicht auflösen."
            AptabaseClient.shared.trackLinkFlipFailed(
                target: targetPlatform,
                reason: reason
            )
            throw ConvertSongIntentError.conversionFailed(reason)
        }
    }
}
