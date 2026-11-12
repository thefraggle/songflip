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

    private func ensureAptabaseInitialized() {
        if AptabaseClient.shared.isInitialized { return }
        let isDebug: Bool = {
            #if DEBUG
            return true
            #else
            return false
            #endif
        }()
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        let aptabaseKey = Bundle.main.infoDictionary?["APTABASE_APP_KEY"] as? String ?? ""
        let rawHost = (Bundle.main.infoDictionary?["APTABASE_HOST"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let aptabaseHost = (rawHost.hasPrefix("https://") || rawHost.hasPrefix("http://")) ? rawHost : "https://api.aptabase.com"

        #if DEBUG
        if aptabaseKey.isEmpty {
            Self.logger.error("[AppIntent] APTABASE_APP_KEY missing in Info.plist! Check Config.xcconfig")
        }
        #endif

        AptabaseClient.shared.doInit(
            appKey: aptabaseKey,
            host: aptabaseHost,
            osName: "iOS",
            osVersion: osVersion,
            locale: locale,
            appVersion: appVersion,
            appBuildNumber: buildNumber,
            isDebug: isDebug
        )
    }

    func perform() async throws -> some IntentResult {
        ensureAptabaseInitialized()

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

        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        let rawTarget = defaults.string(forKey: "target_platform") ?? "appleMusic"
        let targetPlatform = PlatformChoice(rawValue: rawTarget) != nil ? rawTarget : "appleMusic"
        let customUrl = defaults.string(forKey: "custom_api_url") ?? ""
        let customToken = defaults.string(forKey: "custom_api_token") ?? ""

        let isProCached = ProManager.isProActive(defaults: defaults)
        let audiophileMode = isProCached && defaults.bool(forKey: "audiophile_mode")

        let engine = SongLinkEngine.shared
        let res: ResolutionResult
        var bridgeErrorDetail: String? = nil
        do {
            res = try await engine.resolveTargetUrl(
                inputUrl: urlToConvert,
                targetPlatformKey: targetPlatform,
                customApiUrl: customUrl,
                customApiToken: customToken,
                isPro: isProCached,
                authToken: defaults.string(forKey: "pro_auth_token") ?? "",
                isPrefetch: false,
                forceRefresh: false,
                audiophileMode: audiophileMode
            )
        } catch {
            Self.logger.error("[AppIntent] Resolution threw error: \(error.localizedDescription)")
            let errInfo = FlipCoordinator.mapError(error)
            bridgeErrorDetail = errInfo.detail
            res = ResolutionResult.Error(message: errInfo.reason, isUnsupported: false, errorReason: errInfo.errorReason)
        }

        let action = FlipCoordinator.resolveAction(result: res, targetPlatform: targetPlatform)
        FlipCoordinator.trackAction(action, inputUrl: urlToConvert, targetPlatform: targetPlatform, errorDetail: bridgeErrorDetail)

        switch action {
        case .openResolved(let targetUrl, let nativeAppUri, let platformKey, let title, let artist, let isAlbum, _, _, let thumb):
            _ = try? await engine.cache.addHistoryEntry(
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

            let urls = FlipCoordinator.computeLaunchUrls(
                targetUrl: targetUrl,
                nativeAppUri: nativeAppUri,
                platformKey: platformKey,
                title: title,
                artist: artist,
                defaults: defaults
            )

            var opened = false
            if let primaryUrl = urls.primaryUrl {
                opened = await UIApplication.shared.open(primaryUrl)
            }
            if !opened, let fallbackUrl = urls.fallbackUrl {
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
            intent: ConvertSongIntent(),
            phrases: [
                "Flip song in \(.applicationName)"
            ],
            shortTitle: LocalizedStringResource("intent_title", defaultValue: "Open Song in SongFlip"),
            systemImageName: "arrow.triangle.2.circlepath"
        )
    }
}

