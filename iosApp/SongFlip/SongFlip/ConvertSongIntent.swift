import AppIntents
import UIKit
import SongFlipKit
import os

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
    private static let logger = Logger(subsystem: "de.goork.songflip", category: "AppIntent")

    static var title: LocalizedStringResource = LocalizedStringResource("intent_title", defaultValue: "Open Song in SongFlip")
    static var description = IntentDescription(LocalizedStringResource("intent_description", defaultValue: "Converts a copied or shared music link and opens the target app."))
    static var openAppWhenRun: Bool = true

    @Parameter(title: LocalizedStringResource("intent_param_music_link", defaultValue: "Music Link"))
    var inputUrl: String?

    func perform() async throws -> some IntentResult {
        let urlToConvert: String
        let rawSource: String
        if let input = inputUrl?.trimmingCharacters(in: .whitespacesAndNewlines), !input.isEmpty {
            rawSource = input
        } else if let clip = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !clip.isEmpty {
            rawSource = clip
        } else {
            throw ConvertSongIntentError.emptyClipboard
        }
        urlToConvert = UrlUtils.shared.extractCleanUrl(rawInput: rawSource) ?? rawSource

        let outcome = await FlipCoordinator.shared.executeFlip(
            FlipExecutionRequest(inputUrl: urlToConvert)
        )

        switch outcome.action {
        case .openResolved(let targetUrl, _, let platformKey, let title, let artist, let isAlbum, _, _, let thumb):
            _ = try? await SongLinkEngine.shared.cache.addHistoryEntry(
                sourceUrl: urlToConvert,
                targetPlatformKey: platformKey,
                targetUrl: targetUrl,
                title: title ?? LocalizationManager.string(for: "unknown_song"),
                artist: artist,
                isAlbum: isAlbum,
                thumbnailUrl: thumb,
                currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                isAcoustic: false
            )
            await MainActor.run {
                HistoryModel.shared.loadHistory()
            }

            var opened = false
            if let primaryUrl = outcome.primaryUrl {
                opened = await UIApplication.shared.open(primaryUrl)
            }
            if !opened, let fallbackUrl = outcome.fallbackUrl {
                await UIApplication.shared.open(fallbackUrl)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()

        case .openPodcast(let targetUrl, let nativeAppUri, _):
            let target = nativeAppUri ?? targetUrl
            if let url = URL(string: target) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()

        case .showPlaylistNotice(let originalUrl),
             .showPodcastNotice(let originalUrl, _):
            if let url = URL(string: originalUrl) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()

        case .unsupportedEntity(let originalUrl, _):
            if let url = URL(string: originalUrl) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()

        case .failure(let reason, _, _):
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            throw ConvertSongIntentError.conversionFailed(reason)
        }
    }
}

extension Notification.Name {
    static let openListenAndFlip = Notification.Name("de.goork.songflip.openListenAndFlip")
    static let openFlipClipboard = Notification.Name("de.goork.songflip.openFlipClipboard")
    static let openProPaywall = Notification.Name("de.goork.songflip.openProPaywall")
}

struct ListenAndFlipIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("listen_and_flip_title", defaultValue: "Listen & Flip")
    static var description = IntentDescription(LocalizedStringResource("listen_and_flip_subtitle", defaultValue: "Identify music playing around you"))
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        defaults.set(true, forKey: "pending_open_listen_sheet")
        NotificationCenter.default.post(name: .openListenAndFlip, object: nil)
        return .result()
    }
}

struct FlipClipboardIntent: AppIntent {
    static var title: LocalizedStringResource = LocalizedStringResource("shortcut_flip_clipboard_short", defaultValue: "Flip Clipboard")
    static var description = IntentDescription(LocalizedStringResource("shortcut_flip_clipboard_long", defaultValue: "Flip music link from clipboard"))
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        defaults.set(true, forKey: "pending_flip_clipboard")
        NotificationCenter.default.post(name: .openFlipClipboard, object: nil)
        return .result()
    }
}

struct SongFlipShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ListenAndFlipIntent(),
            phrases: [
                "Listen and Flip with \(.applicationName)",
                "Identify song with \(.applicationName)"
            ],
            shortTitle: LocalizedStringResource("listen_and_flip_title", defaultValue: "Listen & Flip"),
            systemImageName: "waveform"
        )
        AppShortcut(
            intent: FlipClipboardIntent(),
            phrases: [
                "Flip clipboard in \(.applicationName)",
                "Convert copied song in \(.applicationName)"
            ],
            shortTitle: LocalizedStringResource("shortcut_flip_clipboard_short", defaultValue: "Flip Clipboard"),
            systemImageName: "doc.on.clipboard"
        )
        AppShortcut(
            intent: ConvertSongIntent(),
            phrases: [
                "Flip song in \(.applicationName)"
            ],
            shortTitle: LocalizedStringResource("intent_title", defaultValue: "Open Song in SongFlip"),
            systemImageName: "arrow.triangle.2.circlepath"
        )
    }
}

