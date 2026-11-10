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

final class FlipCoordinator {
    static let shared = FlipCoordinator()

    private init() {}

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
}
