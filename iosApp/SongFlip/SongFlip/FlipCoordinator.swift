import Foundation
import UIKit
import SongFlipKit

enum FlipAction {
    case openResolved(
        targetUrl: String,
        nativeAppUri: String?,
        platformKey: String,
        title: String?,
        artist: String?,
        isAlbum: Bool,
        isSearchFallback: Bool,
        isCacheHit: Bool,
        thumbnailUrl: String?
    )
    case openPodcast(
        targetUrl: String,
        nativeAppUri: String?,
        isDeepSearch: Bool
    )
    case showPlaylistNotice(
        originalUrl: String
    )
    case showPodcastNotice(
        originalUrl: String,
        isAudiobook: Bool
    )
    case unsupportedEntity(
        originalUrl: String,
        entityType: String
    )
    case failure(
        reason: String,
        errorReason: String,
        errorDetail: String?
    )
}

protocol FlipEnginePort {
    func resolveTargetUrl(
        inputUrl: String,
        targetPlatformKey: String,
        customApiUrl: String,
        customApiToken: String,
        isPro: Bool,
        authToken: String,
        isPrefetch: Bool,
        forceRefresh: Bool,
        audiophileMode: Bool
    ) async throws -> ResolutionResult
}

extension SongLinkEngine: FlipEnginePort {}

protocol FlipAnalyticsPort {
    func ensureInitialized()
    func track(action: FlipAction, inputUrl: String, targetPlatform: String, errorDetail: String?)
}

struct DefaultFlipAnalyticsAdapter: FlipAnalyticsPort {
    func ensureInitialized() {
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
        let rawHost = (Bundle.main.infoDictionary?["APTABASE_HOST"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\/", with: "/") ?? ""
        let aptabaseHost: String
        if rawHost.hasPrefix("https://") || rawHost.hasPrefix("http://") {
            aptabaseHost = rawHost
        } else if rawHost == "https:" || rawHost == "http:" || rawHost.hasPrefix("$(") || rawHost.isEmpty {
            aptabaseHost = "https://api.aptabase.com"
        } else {
            aptabaseHost = rawHost
        }

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

    func track(action: FlipAction, inputUrl: String, targetPlatform: String, errorDetail: String?) {
        FlipCoordinator.trackAction(action, inputUrl: inputUrl, targetPlatform: targetPlatform, errorDetail: errorDetail)
    }
}

struct FlipExecutionRequest {
    let inputUrl: String
    var targetPlatformOverride: String? = nil
    var customApiUrlOverride: String? = nil
    var customApiTokenOverride: String? = nil
    var isProOverride: Bool? = nil
    var authTokenOverride: String? = nil
    var audiophileModeOverride: Bool? = nil
}

struct FlipExecutionOutcome {
    let action: FlipAction
    let targetPlatform: String
    let primaryUrl: URL?
    let fallbackUrl: URL?
}

final class FlipCoordinator {
    static let shared = FlipCoordinator()

    private let engine: FlipEnginePort
    private let defaults: UserDefaults
    private let analytics: FlipAnalyticsPort

    init(
        engine: FlipEnginePort = SongLinkEngine.shared,
        defaults: UserDefaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? .standard,
        analytics: FlipAnalyticsPort = DefaultFlipAnalyticsAdapter()
    ) {
        self.engine = engine
        self.defaults = defaults
        self.analytics = analytics
    }

    func executeFlip(_ request: FlipExecutionRequest) async -> FlipExecutionOutcome {
        analytics.ensureInitialized()

        let rawTarget = request.targetPlatformOverride ?? defaults.string(forKey: "target_platform") ?? "appleMusic"
        let targetPlatform = PlatformChoice(rawValue: rawTarget) != nil ? rawTarget : "appleMusic"
        let customUrl = request.customApiUrlOverride ?? defaults.string(forKey: "custom_api_url") ?? ""
        let customToken = request.customApiTokenOverride ?? defaults.string(forKey: "custom_api_token") ?? ""
        let isPro = request.isProOverride ?? ProStateStorage.isProActive(defaults: defaults)
        let authToken = request.authTokenOverride ?? (isPro ? (defaults.string(forKey: "pro_auth_token") ?? "") : "")
        let audiophileMode = request.audiophileModeOverride ?? (isPro && defaults.bool(forKey: "audiophile_mode"))

        let result: ResolutionResult
        var bridgeErrorDetail: String? = nil
        do {
            result = try await engine.resolveTargetUrl(
                inputUrl: request.inputUrl,
                targetPlatformKey: targetPlatform,
                customApiUrl: customUrl,
                customApiToken: customToken,
                isPro: isPro,
                authToken: authToken,
                isPrefetch: false,
                forceRefresh: false,
                audiophileMode: audiophileMode
            )
        } catch {
            let errInfo = Self.mapError(error)
            bridgeErrorDetail = errInfo.detail
            result = ResolutionResult.Error(message: errInfo.reason, isUnsupported: false, errorReason: errInfo.errorReason)
        }

        let action = Self.resolveAction(result: result, targetPlatform: targetPlatform)
        analytics.track(action: action, inputUrl: request.inputUrl, targetPlatform: targetPlatform, errorDetail: bridgeErrorDetail)

        var primaryUrl: URL? = nil
        var fallbackUrl: URL? = nil
        if case .openResolved(let targetUrl, let nativeAppUri, let platformKey, let title, let artist, _, _, _, _) = action {
            Self.incrementSuccessfulFlipCount(defaults: defaults)
            let urls = Self.computeLaunchUrls(
                targetUrl: targetUrl,
                nativeAppUri: nativeAppUri,
                platformKey: platformKey,
                title: title,
                artist: artist,
                defaults: defaults
            )
            primaryUrl = urls.primaryUrl
            fallbackUrl = urls.fallbackUrl
        }

        return FlipExecutionOutcome(
            action: action,
            targetPlatform: targetPlatform,
            primaryUrl: primaryUrl,
            fallbackUrl: fallbackUrl
        )
    }

    /// Normalizes Swift/ObjC Error into structured error reasons matching KMP engine contract
    static func mapError(_ error: Error) -> (reason: String, errorReason: String, detail: String) {
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
        return (error.localizedDescription, errorReason, error.localizedDescription)
    }

    /// Maps a ResolutionResult into a strongly typed FlipAction
    static func resolveAction(result: ResolutionResult, targetPlatform: String) -> FlipAction {
        if let success = result as? ResolutionResult.Success {
            return .openResolved(
                targetUrl: success.targetUrl,
                nativeAppUri: success.nativeAppUri,
                platformKey: targetPlatform,
                title: success.title,
                artist: success.artist,
                isAlbum: success.isAlbum,
                isSearchFallback: success.isSearchFallback,
                isCacheHit: success.isCacheHit,
                thumbnailUrl: success.thumbnailUrl
            )
        } else if let podcast = result as? ResolutionResult.Podcast {
            return .openPodcast(
                targetUrl: podcast.targetUrl,
                nativeAppUri: podcast.nativeAppUri,
                isDeepSearch: podcast.isDeepSearch
            )
        } else if let playlist = result as? ResolutionResult.Playlist {
            return .showPlaylistNotice(originalUrl: playlist.originalUrl)
        } else if let podcastOrAudiobook = result as? ResolutionResult.PodcastOrAudiobook {
            return .showPodcastNotice(
                originalUrl: podcastOrAudiobook.originalUrl,
                isAudiobook: podcastOrAudiobook.isAudiobook
            )
        } else if let unsupported = result as? ResolutionResult.UnsupportedEntity {
            return .unsupportedEntity(
                originalUrl: unsupported.originalUrl,
                entityType: unsupported.entityType
            )
        } else {
            let err = result as? ResolutionResult.Error
            let reason = err?.message ?? "unknown_error"
            let errorReason = err?.errorReason ?? "no_match_found"
            return .failure(reason: reason, errorReason: errorReason, errorDetail: nil)
        }
    }

    /// Centralized Aptabase telemetry tracking for all flip outcomes
    static func trackAction(
        _ action: FlipAction,
        inputUrl: String,
        targetPlatform: String,
        errorDetail: String? = nil
    ) {
        let srcKey = UrlUtils.shared.detectPlatform(url: inputUrl)?.key ?? "unknown"
        let srcDomain = UrlUtils.shared.extractDomain(url: inputUrl)

        switch action {
        case .openResolved(_, _, _, _, _, let isAlbum, let isSearchFallback, let isCacheHit, _):
            AptabaseClient.shared.trackLinkFlipped(
                target: targetPlatform,
                isAlbum: isAlbum,
                isSearch: isSearchFallback,
                source: srcKey,
                cacheHit: KotlinBoolean(bool: isCacheHit)
            )

        case .openPodcast(_, _, let isDeepSearch):
            AptabaseClient.shared.trackPodcastFlipped(
                source: srcKey,
                target: targetPlatform,
                isDeepSearch: isDeepSearch
            )

        case .showPlaylistNotice:
            AptabaseClient.shared.trackPlaylistRouted(target: targetPlatform)

        case .showPodcastNotice(_, let isAudiobook):
            if isAudiobook {
                AptabaseClient.shared.trackAudiobookIntercepted(target: targetPlatform)
            } else {
                AptabaseClient.shared.trackPodcastIntercepted(target: targetPlatform)
            }

        case .unsupportedEntity(_, let entityType):
            AptabaseClient.shared.trackUnsupportedEntityIntercepted(
                target: targetPlatform,
                entityType: entityType,
                sourceDomain: srcDomain
            )

        case .failure(let reason, let errorReason, let detail):
            AptabaseClient.shared.trackLinkFlipFailed(
                target: targetPlatform,
                reason: reason,
                sourceDomain: srcDomain,
                sourcePlatform: srcKey,
                errorReason: errorReason,
                errorDetail: detail ?? errorDetail
            )
        }
    }

    /// Computes the primary and fallback launch URLs taking alternative player preferences into account
    static func computeLaunchUrls(
        targetUrl: String,
        nativeAppUri: String?,
        platformKey: String,
        title: String?,
        artist: String?,
        defaults: UserDefaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? .standard
    ) -> (primaryUrl: URL?, fallbackUrl: URL?) {
        let cleanKey = platformKey.components(separatedBy: "_").first ?? platformKey
        let webFallbackStr = UrlUtils.shared.toWebFallbackUrl(rawUrl: targetUrl, targetPlatformKey: cleanKey)
        let fallbackUrl = URL(string: webFallbackStr) ?? URL(string: targetUrl)

        var primaryStr = nativeAppUri ?? UrlUtils.shared.toNativeAppUri(url: targetUrl, platformKey: cleanKey)
        if let preferredClient = defaults.string(forKey: "preferred_player_\(cleanKey)"), !preferredClient.isEmpty {
            if let customUri = UrlUtils.shared.toAlternativeClientUri(
                url: targetUrl,
                platformKey: cleanKey,
                clientId: preferredClient,
                title: title,
                artist: artist
            ) {
                primaryStr = customUri
            }
        }

        let primaryUrl = URL(string: primaryStr)
        return (primaryUrl: primaryUrl, fallbackUrl: fallbackUrl)
    }

    static func incrementSuccessfulFlipCount(
        defaults: UserDefaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? .standard
    ) {
        let key = "successful_flip_count"
        let current = defaults.integer(forKey: key)
        defaults.set(current + 1, forKey: key)
    }
}
