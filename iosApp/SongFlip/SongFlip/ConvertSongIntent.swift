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
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.9.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "79"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        let aptabaseKey = Bundle.main.infoDictionary?["APTABASE_APP_KEY"] as? String ?? "A-SH-4092372492"
        let aptabaseHost = Bundle.main.infoDictionary?["APTABASE_HOST"] as? String ?? "https://telemetry-apps.goork.de"

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

        let isProCached = defaults.bool(forKey: "is_pro_cached")
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
            let nsError = error as NSError
            let errorReason: String
            if nsError.domain == NSURLErrorDomain {
                switch nsError.code {
                case NSURLErrorTimedOut:
                    errorReason = "timeout"
                case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost, NSURLErrorNotConnectedToInternet:
                    errorReason = "io_error"
                default:
                    errorReason = "network_error"
                }
            } else {
                errorReason = "engine_error"
            }
            bridgeErrorDetail = error.localizedDescription
            res = ResolutionResult.Error(message: error.localizedDescription, isUnsupported: false, errorReason: errorReason)
        }

        if let success = res as? ResolutionResult.Success {
            try? await engine.cache.addHistoryEntry(
                sourceUrl: urlToConvert,
                targetPlatformKey: targetPlatform,
                targetUrl: success.targetUrl,
                title: success.title ?? LocalizationManager.string(for: "unknown_song"),
                artist: success.artist,
                isAlbum: success.isAlbum,
                thumbnailUrl: success.thumbnailUrl,
                currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                isAcoustic: false
            )
            await MainActor.run {
                HistoryModel.shared.loadHistory()
            }

            let srcKey = UrlUtils.shared.detectPlatform(url: urlToConvert)?.key ?? "unknown"
            AptabaseClient.shared.trackLinkFlipped(
                target: targetPlatform,
                isAlbum: success.isAlbum,
                isSearch: success.isSearchFallback,
                source: srcKey,
                cacheHit: KotlinBoolean(bool: success.isCacheHit)
            )

            let cleanTargetKey = targetPlatform.components(separatedBy: "_").first ?? targetPlatform
            var primaryUri = success.nativeAppUri
            if let preferredClient = defaults.string(forKey: "preferred_player_\(cleanTargetKey)"), !preferredClient.isEmpty {
                if let customUri = UrlUtils.shared.toAlternativeClientUri(
                    url: success.targetUrl,
                    platformKey: cleanTargetKey,
                    clientId: preferredClient,
                    title: success.title,
                    artist: success.artist
                ) {
                    primaryUri = customUri
                }
            }

            var opened = false
            if let nativeUri = primaryUri, let nativeUrl = URL(string: nativeUri) {
                opened = await UIApplication.shared.open(nativeUrl)
            }

            let webFallbackStr = UrlUtils.shared.toWebFallbackUrl(rawUrl: success.targetUrl, targetPlatformKey: cleanTargetKey)
            if !opened, let webUrl = URL(string: webFallbackStr) ?? URL(string: success.targetUrl) {
                await UIApplication.shared.open(webUrl)
            }

            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()
        } else if let podcast = res as? ResolutionResult.Podcast {
            AptabaseClient.shared.trackPodcastFlipped(
                source: UrlUtils.shared.detectPlatform(url: urlToConvert)?.key ?? "unknown",
                target: targetPlatform,
                isDeepSearch: podcast.isDeepSearch
            )
            let target = podcast.nativeAppUri ?? podcast.targetUrl
            if let url = URL(string: target) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()
        } else if let playlist = res as? ResolutionResult.Playlist {
            AptabaseClient.shared.trackPlaylistRouted(
                target: targetPlatform
            )
            if let url = URL(string: playlist.originalUrl) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()
        } else if let podcast = res as? ResolutionResult.PodcastOrAudiobook {
            if podcast.isAudiobook {
                AptabaseClient.shared.trackAudiobookIntercepted(target: targetPlatform)
            } else {
                AptabaseClient.shared.trackPodcastIntercepted(target: targetPlatform)
            }
            if let url = URL(string: podcast.originalUrl) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()
        } else if let unsupported = res as? ResolutionResult.UnsupportedEntity {
            let srcDomain = UrlUtils.shared.extractDomain(url: unsupported.originalUrl)
            AptabaseClient.shared.trackUnsupportedEntityIntercepted(
                target: targetPlatform,
                entityType: unsupported.entityType,
                sourceDomain: srcDomain
            )
            if let url = URL(string: unsupported.originalUrl) {
                await UIApplication.shared.open(url)
            }
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            return .result()
        } else {
            let defaultMsg = LocalizationManager.string(for: "intent_error_failed_to_resolve", lang: LocalizationManager.currentLanguage())
            let err = res as? ResolutionResult.Error
            let reason = err?.message ?? defaultMsg
            let errorReason = err?.errorReason ?? "no_match_found"
            let sourceDomain = UrlUtils.shared.extractDomain(url: urlToConvert)
            let sourcePlatform = UrlUtils.shared.detectPlatform(url: urlToConvert)?.key ?? "unknown"

            AptabaseClient.shared.trackLinkFlipFailed(
                target: targetPlatform,
                reason: reason,
                sourceDomain: sourceDomain,
                sourcePlatform: sourcePlatform,
                errorReason: errorReason,
                errorDetail: bridgeErrorDetail
            )
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

