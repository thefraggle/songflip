import AppIntents
import UIKit
import SongFlipKit

struct ConvertSongIntent: AppIntent {
    static var title: LocalizedStringResource = "Song in SongFlip öffnen"
    static var description = IntentDescription("Konvertiert einen kopierten oder übergebenen Musik-Link und öffnet die Ziel-App.")

    @Parameter(title: "Musik Link")
    var inputUrl: String?

    func perform() async throws -> some IntentResult {
        let urlToConvert: String
        if let input = inputUrl, !input.isEmpty {
            urlToConvert = input
        } else if let clip = UIPasteboard.general.string {
            urlToConvert = clip
        } else {
            return .result()
        }

        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        let targetPlatform = defaults.string(forKey: "target_platform") ?? "youtubeMusic"
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

            let targetString = success.nativeAppUri ?? success.targetUrl
            if let targetUrl = URL(string: targetString) {
                await UIApplication.shared.open(targetUrl)
            }
        }

        return .result()
    }
}
