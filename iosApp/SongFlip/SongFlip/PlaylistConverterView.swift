import SwiftUI
import SongFlipKit

struct SwiftPlaylistTrackItem: Codable, Identifiable {
    var id: String { targetId ?? "\(title)_\(artist)" }
    let title: String
    let artist: String
    let matched: Bool
    let targetUrl: String?
    let targetId: String?
    let sourceUrl: String?
    let thumbnailUrl: String?

    enum CodingKeys: String, CodingKey {
        case title, artist, matched, targetUrl, targetId, sourceUrl, thumbnailUrl
    }
}

struct SwiftPlaylistConversionResult: Codable {
    let status: String?
    let playlistId: String?
    let title: String?
    let sourcePlatform: String?
    let targetPlatform: String?
    let thumbnailUrl: String?
    let totalTracks: Int
    let originalTotalTracks: Int?
    let convertedTracks: Int
    let matchedCount: Int
    let zeroOAuthUrl: String?
    let webShareUrl: String?
    let isLimited: Bool
    let tracks: [SwiftPlaylistTrackItem]
}

enum PlaylistErrorType {
    case privateOrRestricted
    case unsupportedPlatform
    case timeout
    case empty
    case generic
}

struct PlaylistConverterView: View {
    let playlistUrl: String
    let targetPlatformKey: String
    var dismissAction: () -> Void
    var onOpenPaywall: () -> Void

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    @State private var isLoading = true
    @State private var progressStep = 0
    @State private var conversionResult: SwiftPlaylistConversionResult? = nil
    @State private var errorType: PlaylistErrorType? = nil
    @State private var errorMessage: String? = nil
    @State private var showCopiedAlert = false

    var lang: String { settings.selectedLanguage }

    private var sourcePlatformName: String {
        detectSourcePlatformName(url: playlistUrl)
    }

    private var targetPlatformName: String {
        PlatformChoice(rawValue: targetPlatformKey)?.displayName ?? targetPlatformKey
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Header Bar with Platforms
                HStack(spacing: 8) {
                    Text(sourcePlatformName)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.orange.opacity(0.18))
                        .foregroundColor(.orange)
                        .cornerRadius(6)

                    Image(systemName: "arrow.right")
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    Text(targetPlatformName)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.green.opacity(0.18))
                        .foregroundColor(.green)
                        .cornerRadius(6)

                    Spacer()

                    Button(action: {
                        dismissAction()
                        dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundColor(.secondary.opacity(0.7))
                    }
                }
                .padding(.horizontal, 20)
                .padding(.top, 16)
                .padding(.bottom, 12)

                Divider()

                if isLoading {
                    loadingView
                } else if let err = errorType {
                    errorView(type: err)
                } else if let res = conversionResult {
                    successView(result: res)
                }
            }
            .background(Color(uiColor: .systemBackground))
            .navigationBarHidden(true)
            .onAppear {
                startConversion()
            }
        }
    }

    // MARK: - Loading View
    private var loadingView: some View {
        VStack(spacing: 24) {
            Spacer()

            ZStack {
                Circle()
                    .stroke(Color.gray.opacity(0.2), lineWidth: 5)
                    .frame(width: 80, height: 80)

                Circle()
                    .trim(from: 0, to: 0.7)
                    .stroke(Color.green, style: StrokeStyle(lineWidth: 5, lineCap: .round))
                    .frame(width: 80, height: 80)
                    .rotationEffect(.degrees(Double(progressStep) * 60))
                    .animation(.linear(duration: 0.6), value: progressStep)

                Image(systemName: "music.note.list")
                    .font(.title)
                    .foregroundColor(.green)
            }

            VStack(spacing: 8) {
                Text(statusTextForStep(progressStep))
                    .font(.headline)
                    .multilineTextAlignment(.center)

                Text(LocalizationManager.string(for: "playlist_dialog_body", lang: lang))
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
            }

            Spacer()
        }
        .padding(.horizontal, 24)
    }

    // MARK: - Error View
    @ViewBuilder
    private func errorView(type: PlaylistErrorType) -> some View {
        VStack(spacing: 20) {
            Spacer()

            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 48))
                .foregroundColor(.orange)

            VStack(spacing: 8) {
                Text(errorTitle(for: type))
                    .font(.headline)
                    .multilineTextAlignment(.center)

                Text(errorDescription(for: type))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }

            Spacer()

            VStack(spacing: 12) {
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    if let url = URL(string: playlistUrl) {
                        UIApplication.shared.open(url)
                    }
                    dismissAction()
                    dismiss()
                }) {
                    Text(LocalizationManager.string(for: "playlist_btn_open_original", lang: lang))
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.orange)
                        .cornerRadius(12)
                }

                if type != .privateOrRestricted && type != .unsupportedPlatform && type != .empty {
                    Button(action: {
                        startConversion()
                    }) {
                        Text(LocalizationManager.string(for: "playlist_btn_retry", lang: lang))
                            .font(.headline)
                            .foregroundColor(.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color(uiColor: .secondarySystemBackground))
                            .cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
    }

    // MARK: - Success View
    @ViewBuilder
    private func successView(result: SwiftPlaylistConversionResult) -> some View {
        VStack(spacing: 16) {
            // Title & Matched Count
            VStack(spacing: 4) {
                Text(result.title?.isEmpty == false ? result.title! : LocalizationManager.string(for: "playlist_converter_title", lang: lang))
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                let matchedMsg = String(format: LocalizationManager.string(for: "playlist_matched_count", lang: lang), result.matchedCount, result.totalTracks)
                Text(matchedMsg)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            // Track list
            List {
                ForEach(Array(result.tracks.enumerated()), id: \.offset) { index, track in
                    HStack(spacing: 12) {
                        Text("\(index + 1)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(width: 24, alignment: .trailing)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(track.title)
                                .font(.system(size: 14, weight: .medium))
                                .lineLimit(1)
                            Text(track.artist)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }

                        Spacer()

                        if track.matched {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.subheadline)
                        } else {
                            Image(systemName: "questionmark.circle")
                                .foregroundColor(.secondary.opacity(0.6))
                                .font(.subheadline)
                        }
                    }
                    .padding(.vertical, 2)
                    .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                    .listRowBackground(Color.clear)
                }
            }
            .listStyle(.plain)
            .frame(maxHeight: 220)

            // Large Playlist Notice Banner (Issue #36 short-term notice)
            let isLargePlaylist = (result.originalTotalTracks ?? 0) > 50 || (result.convertedTracks >= 50 && (result.originalTotalTracks ?? 0) >= 50)
            if isLargePlaylist {
                let origCount = (result.originalTotalTracks ?? 0) > 0 ? (result.originalTotalTracks ?? 0) : result.totalTracks
                let titleMsg = String(format: LocalizationManager.string(for: "playlist_large_notice_title", lang: lang), origCount)
                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .font(.body)
                        .foregroundColor(.blue)
                        .padding(.top, 2)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(titleMsg)
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)
                        Text(LocalizationManager.string(for: "playlist_large_notice_desc", lang: lang))
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }

                    Spacer()
                }
                .padding(12)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
                .padding(.horizontal, 20)
            }

            // Freemium Pro Banner
            if !proManager.isPro && result.isLimited {
                HStack(spacing: 12) {
                    Text("💎")
                        .font(.title2)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(LocalizationManager.string(for: "playlist_free_limit_title", lang: lang))
                            .font(.caption)
                            .fontWeight(.bold)
                        Text(LocalizationManager.string(for: "playlist_free_limit_desc", lang: lang))
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button(action: {
                        onOpenPaywall()
                    }) {
                        Text(LocalizationManager.string(for: "playlist_btn_unlock_pro", lang: lang))
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.green)
                            .cornerRadius(8)
                    }
                }
                .padding(12)
                .background(Color.green.opacity(0.12))
                .cornerRadius(12)
                .padding(.horizontal, 20)
            }

            Spacer()

            // Action Buttons
            VStack(spacing: 10) {
                // Primary: Open in Target Player
                Button(action: {
                    openInTargetPlayer(result: result)
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "play.circle.fill")
                            .font(.headline)
                        Text(String(format: LocalizationManager.string(for: "playlist_open_import", lang: lang), targetPlatformName))
                            .font(.headline)
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color.green)
                    .cornerRadius(14)
                }

                // Secondary: Copy / Share Web Link
                if let webUrl = result.webShareUrl, let url = URL(string: webUrl) {
                    ShareLink(item: url, message: Text(result.title ?? "SongFlip Playlist")) {
                        HStack(spacing: 8) {
                            Image(systemName: "square.and.arrow.up")
                            Text(LocalizationManager.string(for: "playlist_share_link", lang: lang))
                                .font(.subheadline)
                                .fontWeight(.medium)
                        }
                        .foregroundColor(.primary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .cornerRadius(12)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
    }

    // MARK: - Actions
    private func startConversion() {
        isLoading = true
        errorType = nil
        errorMessage = nil
        conversionResult = nil
        progressStep = 0

        // Progress timer
        Timer.scheduledTimer(withTimeInterval: 0.6, repeats: true) { timer in
            if !isLoading {
                timer.invalidate()
            } else {
                progressStep += 1
            }
        }

        let maxTracks = proManager.isPro ? 50 : 5
        let endpoints = [
            "https://songflip.link/api/playlist/convert",
            "https://songflip-web.web.app/api/playlist/convert"
        ]

        let payload: [String: Any] = [
            "url": playlistUrl,
            "targetPlatform": targetPlatformKey,
            "isPro": proManager.isPro,
            "maxTracks": maxTracks
        ]

        Task {
            for endpoint in endpoints {
                guard let url = URL(string: endpoint) else { continue }
                var request = URLRequest(url: url)
                request.httpMethod = "POST"
                request.setValue("application/json", forHTTPHeaderField: "Content-Type")
                request.setValue("songflip-app", forHTTPHeaderField: "x-web-client")
                request.timeoutInterval = 25

                do {
                    request.httpBody = try JSONSerialization.data(withJSONObject: payload)
                    let (data, response) = try await URLSession.shared.data(for: request)

                    if let http = response as? HTTPURLResponse {
                        if http.statusCode == 200 {
                            let decoder = JSONDecoder()
                            if let decoded = try? decoder.decode(SwiftPlaylistConversionResult.self, from: data) {
                                await MainActor.run {
                                    self.conversionResult = decoded
                                    self.isLoading = false
                                    // Save to history
                                    let targetLink = decoded.zeroOAuthUrl ?? decoded.webShareUrl ?? playlistUrl
                                    HistoryModel.shared.add(
                                        title: decoded.title?.isEmpty == false ? decoded.title! : "Playlist",
                                        artist: "\(decoded.matchedCount)/\(decoded.totalTracks) Songs",
                                        sourceUrl: playlistUrl,
                                        targetUrl: targetLink,
                                        targetPlatform: "\(targetPlatformKey)_playlist",
                                        isAlbum: false,
                                        thumbnailUrl: decoded.thumbnailUrl
                                    )
                                    AptabaseClient.shared.trackEvent(eventName: "playlist_converted_ios", props: [
                                        "source": sourcePlatformName,
                                        "target": targetPlatformKey,
                                        "matched": "\(decoded.matchedCount)",
                                        "total": "\(decoded.totalTracks)"
                                    ])
                                    if decoded.matchedCount > 0 {
                                        ReviewManager.shared.maybeRequestReview(trigger: "playlist_success", settings: self.settings)
                                    }
                                }
                                return
                            }
                        } else {
                            // Parse error code
                            if let errJson = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                                let code = ((errJson["code"] as? String) ?? (errJson["error"] as? String) ?? "").uppercased()
                                await MainActor.run {
                                    self.errorType = mapErrorCode(code)
                                    self.isLoading = false
                                }
                                return
                            }
                        }
                    }
                } catch {
                    // Try next endpoint
                }
            }

            await MainActor.run {
                self.errorType = .generic
                self.isLoading = false
            }
        }
    }

    private func openInTargetPlayer(result: SwiftPlaylistConversionResult) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        if let zeroUrl = result.zeroOAuthUrl, let url = URL(string: zeroUrl) {
            UIApplication.shared.open(url) { success in
                if !success, let web = result.webShareUrl, let webUrl = URL(string: web) {
                    UIApplication.shared.open(webUrl)
                }
            }
        } else if let web = result.webShareUrl, let webUrl = URL(string: web) {
            UIApplication.shared.open(webUrl)
        }
        dismissAction()
        dismiss()
    }

    private func statusTextForStep(_ step: Int) -> String {
        switch step {
        case 0, 1:
            return LocalizationManager.string(for: "playlist_converting", lang: lang)
        case 2, 3, 4:
            return String(format: LocalizationManager.string(for: "playlist_searching_tracks", lang: lang), targetPlatformName)
        default:
            return LocalizationManager.string(for: "playlist_creating_queue", lang: lang)
        }
    }

    private func mapErrorCode(_ code: String) -> PlaylistErrorType {
        switch code {
        case "PRIVATE_OR_RESTRICTED": return .privateOrRestricted
        case "UNSUPPORTED_PLATFORM": return .unsupportedPlatform
        case "UPSTREAM_TIMEOUT": return .timeout
        case "EMPTY_PLAYLIST": return .empty
        default: return .generic
        }
    }

    private func errorTitle(for type: PlaylistErrorType) -> String {
        switch type {
        case .privateOrRestricted: return LocalizationManager.string(for: "playlist_error_private_title", lang: lang)
        case .unsupportedPlatform: return LocalizationManager.string(for: "playlist_error_unsupported_title", lang: lang)
        case .timeout: return LocalizationManager.string(for: "playlist_error_timeout_title", lang: lang)
        case .empty: return LocalizationManager.string(for: "playlist_error_empty_title", lang: lang)
        case .generic: return LocalizationManager.string(for: "playlist_error_title", lang: lang)
        }
    }

    private func errorDescription(for type: PlaylistErrorType) -> String {
        switch type {
        case .privateOrRestricted: return LocalizationManager.string(for: "playlist_error_private_desc", lang: lang)
        case .unsupportedPlatform: return LocalizationManager.string(for: "playlist_error_unsupported_desc", lang: lang)
        case .timeout: return LocalizationManager.string(for: "playlist_error_timeout_desc", lang: lang)
        case .empty: return LocalizationManager.string(for: "playlist_error_empty_desc", lang: lang)
        case .generic: return LocalizationManager.string(for: "playlist_error_desc", lang: lang)
        }
    }
}
