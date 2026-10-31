import SwiftUI
import Combine
import SongFlipKit
import os

private let logger = Logger(subsystem: "de.goork.songflip", category: "PlaylistConverter")

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

struct SwiftPlaylistChunk: Codable, Identifiable {
    var id: Int { partIndex }
    let partIndex: Int
    let rangeStart: Int
    let rangeEnd: Int
    let zeroOAuthUrl: String?
    let matchedCount: Int
    let totalTracks: Int
}

struct ShareSheetItem: Identifiable {
    var id: String { url.absoluteString }
    let url: URL
}

struct ActivityViewController: UIViewControllerRepresentable {
    var activityItems: [Any]
    var applicationActivities: [UIActivity]? = nil

    func makeUIViewController(context: UIViewControllerRepresentableContext<ActivityViewController>) -> UIActivityViewController {
        UIActivityViewController(activityItems: activityItems, applicationActivities: applicationActivities)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: UIViewControllerRepresentableContext<ActivityViewController>) {}
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
    let isPartial: Bool?
    let tracks: [SwiftPlaylistTrackItem]
    let parts: [SwiftPlaylistChunk]?
}

enum PlaylistErrorType {
    case privateOrRestricted
    case unsupportedPlatform
    case timeout
    case empty
    case notFound
    case generic
}

enum ActivePlaylistState {
    case loading(step: Int)
    case success(result: SwiftPlaylistConversionResult)
    case error(type: PlaylistErrorType, message: String)
}

struct ActivePlaylistData {
    let playlistUrl: String
    let targetPlatformKey: String
    let state: ActivePlaylistState
}

class ActivePlaylistModel: ObservableObject {
    static let shared = ActivePlaylistModel()

    @Published var current: ActivePlaylistData? = nil

    private init() {}

    func clear() {
        current = nil
    }
}

struct PlaylistConverterView: View {
    let playlistUrl: String
    let targetPlatformKey: String
    var dismissAction: () -> Void
    var onOpenPaywall: () -> Void
    var importedFileUrl: URL? = nil

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    @State private var isLoading = true
    @State private var progressStep = 0
    @State private var conversionResult: SwiftPlaylistConversionResult? = nil
    @State private var errorType: PlaylistErrorType? = nil
    @State private var errorMessage: String? = nil
    @State private var showCopiedAlert = false
    @State private var selectedPartIndex = 0
    @State private var shareFileItem: ShareSheetItem? = nil
    @State private var stepTimer: Timer? = nil
    @State private var retryCooldownSeconds = 0
    @State private var retryTimer: Timer? = nil

    var lang: String { settings.selectedLanguage }

    private var isFileImport: Bool {
        importedFileUrl != nil || playlistUrl.contains("file-import") || playlistUrl.hasPrefix("file://")
    }

    private var sourcePlatformName: String {
        if isFileImport {
            return LocalizationManager.string(for: "playlist_import_source_chip", lang: lang)
        }
        return detectSourcePlatformName(url: playlistUrl)
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
            .sheet(item: $shareFileItem) { item in
                ActivityViewController(activityItems: [item.url])
            }
            .onAppear {
                if let fileUrl = importedFileUrl {
                    handleImportedFile(url: fileUrl)
                    return
                }
                if let current = ActivePlaylistModel.shared.current,
                   current.playlistUrl == playlistUrl,
                   current.targetPlatformKey == targetPlatformKey {
                    switch current.state {
                    case .success(let res):
                        if proManager.isPro && res.isLimited {
                            break
                        }
                        self.conversionResult = res
                        self.isLoading = false
                        return
                    case .error(let type, let msg):
                        self.errorType = type
                        self.errorMessage = msg
                        self.isLoading = false
                        return
                    case .loading:
                        break
                    }
                }
                startConversion()
            }
            .onDisappear {
                stepTimer?.invalidate()
                stepTimer = nil
            }
            .onChange(of: proManager.isPro) { isPro in
                if isPro && (conversionResult?.isLimited == true) {
                    startConversion(forceRefresh: true)
                }
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

            if type == .privateOrRestricted {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 48))
                    .foregroundColor(.orange)

                VStack(spacing: 10) {
                    Text(errorTitle(for: type))
                        .font(.headline)
                        .multilineTextAlignment(.center)

                    Text(errorDescription(for: type))
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(16)
                .background(Color(uiColor: .secondarySystemBackground))
                .cornerRadius(16)
                .padding(.horizontal, 20)
            } else {
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

                HStack(alignment: .top, spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .font(.body)
                        .foregroundColor(.secondary)
                        .padding(.top, 2)

                    Text(LocalizationManager.string(for: "playlist_error_private_desc", lang: lang))
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Spacer()
                }
                .padding(12)
                .background(Color(uiColor: .secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal, 20)
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

                if type != .privateOrRestricted && type != .unsupportedPlatform && type != .empty && type != .notFound {
                    Button(action: {
                        guard retryCooldownSeconds <= 0 else { return }
                        retryCooldownSeconds = 4
                        retryTimer?.invalidate()
                        retryTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { timer in
                            if retryCooldownSeconds > 1 {
                                retryCooldownSeconds -= 1
                            } else {
                                retryCooldownSeconds = 0
                                timer.invalidate()
                            }
                        }
                        startConversion(forceRefresh: true)
                    }) {
                        Text(retryCooldownSeconds > 0
                            ? "\(LocalizationManager.string(for: "playlist_btn_retry", lang: lang)) (\(retryCooldownSeconds)s)"
                            : LocalizationManager.string(for: "playlist_btn_retry", lang: lang))
                            .font(.headline)
                            .foregroundColor(retryCooldownSeconds > 0 ? .secondary : .primary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                            .background(Color(uiColor: .secondarySystemBackground))
                            .cornerRadius(12)
                    }
                    .disabled(retryCooldownSeconds > 0)
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
            // Cover Artwork (if available)
            if let thumbStr = result.thumbnailUrl, let thumbUrl = URL(string: thumbStr) {
                AsyncImage(url: thumbUrl) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFill()
                            .frame(width: 76, height: 76)
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                    default:
                        EmptyView()
                    }
                }
                .frame(width: 76, height: 76)
                .padding(.top, 8)
            }

            // Title & Matched Count
            VStack(spacing: 4) {
                Text(result.title?.isEmpty == false ? result.title! : LocalizationManager.string(for: "playlist_converter_title", lang: lang))
                    .font(.title3)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .padding(.horizontal, 20)

                let displayTotal = max(result.totalTracks, result.originalTotalTracks ?? 0)
                let matchedMsg = String(format: LocalizationManager.string(for: "playlist_matched_count", lang: lang), result.matchedCount, displayTotal)
                Text(matchedMsg)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }

            let chunks: [SwiftPlaylistChunk] = {
                if let p = result.parts, !p.isEmpty {
                    return p
                }
                return calculateChunks(tracks: result.tracks, targetPlatform: targetPlatformKey, playlistTitle: result.title ?? "")
            }()
            let currentChunk = chunks.indices.contains(selectedPartIndex) ? chunks[selectedPartIndex] : nil
            let displayedTracks: [SwiftPlaylistTrackItem] = {
                if chunks.count > 1, let c = currentChunk {
                    let fromIdx = max(0, c.rangeStart - 1)
                    let toIdx = min(result.tracks.count, c.rangeEnd)
                    if fromIdx < toIdx {
                        return Array(result.tracks[fromIdx..<toIdx])
                    }
                }
                return result.tracks
            }()
            let trackOffset = (chunks.count > 1 && currentChunk != nil) ? max(0, currentChunk!.rangeStart - 1) : 0

            // Part selector chips (when playlist has multiple chunks)
            if chunks.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(chunks) { chunk in
                            let isSelected = selectedPartIndex == (chunk.partIndex - 1)
                            Button(action: {
                                selectedPartIndex = chunk.partIndex - 1
                            }) {
                                let partFmt = LocalizationManager.string(for: "playlist_part_chip", lang: lang)
                                Text(String(format: partFmt, chunk.partIndex, chunk.rangeStart, chunk.rangeEnd))
                                    .font(.system(size: 13, weight: .semibold))
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(isSelected ? Color.green : Color.secondary.opacity(0.15))
                                    .foregroundColor(isSelected ? .white : .primary)
                                    .cornerRadius(10)
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }

            // Track list
            List {
                ForEach(Array(displayedTracks.enumerated()), id: \.offset) { index, track in
                    HStack(spacing: 12) {
                        Text("\(trackOffset + index + 1)")
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

            // Large Playlist Notice Banner (only shown if total tracks exceeds 200)
            let isLargePlaylist = (result.originalTotalTracks ?? 0) > 200
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

            // Spotify 100-Track Limit Notice (shown when Spotify playlist reaches public 100-track boundary)
            let isSpotify100Limit = (result.sourcePlatform == "spotify") && (result.tracks.count >= 100)
            if isSpotify100Limit && !isLargePlaylist {
                HStack(alignment: .center, spacing: 10) {
                    Image(systemName: "info.circle.fill")
                        .font(.body)
                        .foregroundColor(.secondary)

                    Text(LocalizationManager.string(for: "playlist_spotify_limit_notice", lang: lang))
                        .font(.caption2)
                        .foregroundColor(.secondary)

                    Spacer()
                }
                .padding(12)
                .background(Color.secondary.opacity(0.08))
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
                        AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "playlist_limit_gate"])
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
                let ctaText: String = {
                    if chunks.count > 1, let c = currentChunk {
                        let partFmt = LocalizationManager.string(for: "playlist_open_import_part", lang: lang)
                        return String(format: partFmt, c.partIndex, targetPlatformName)
                    }
                    return String(format: LocalizationManager.string(for: "playlist_open_import", lang: lang), targetPlatformName)
                }()
                let activeZeroUrl = (chunks.count > 1 && currentChunk?.zeroOAuthUrl != nil) ? currentChunk?.zeroOAuthUrl : result.zeroOAuthUrl

                if let zeroUrl = activeZeroUrl, !zeroUrl.isEmpty {
                    // Primary: Open in Target Player
                    Button(action: {
                        openInTargetPlayer(result: result, customZeroUrl: zeroUrl)
                    }) {
                        HStack(spacing: 8) {
                            Image(systemName: "play.circle.fill")
                                .font(.headline)
                            Text(ctaText)
                                .font(.headline)
                        }
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Color.green)
                        .cornerRadius(14)
                    }
                } else {
                    // Info notice for platforms like Apple Music that don't support direct playlist creation via URL
                    HStack(spacing: 12) {
                        Image(systemName: "info.circle.fill")
                            .foregroundColor(.blue)
                            .font(.title3)
                        Text(String(format: LocalizationManager.string(for: "playlist_apple_music_no_import_desc", lang: lang), targetPlatformName))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(12)
                }

                // File Export Row (M3U8 & CSV)
                HStack(spacing: 10) {
                    Button(action: {
                        if let url = exportM3U8(title: result.title ?? "Playlist", tracks: result.tracks) {
                            shareFileItem = ShareSheetItem(url: url)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "arrow.down.doc")
                                .font(.subheadline)
                            Text(LocalizationManager.string(for: "playlist_export_m3u8", lang: lang))
                                .font(.system(size: 13, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundColor(.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .cornerRadius(12)
                    }

                    Button(action: {
                        if let url = exportCSV(title: result.title ?? "Playlist", tracks: result.tracks) {
                            shareFileItem = ShareSheetItem(url: url)
                        }
                    }) {
                        HStack(spacing: 6) {
                            Image(systemName: "tablecells")
                                .font(.subheadline)
                            Text(LocalizationManager.string(for: "playlist_export_csv", lang: lang))
                                .font(.system(size: 13, weight: .semibold))
                                .lineLimit(1)
                        }
                        .foregroundColor(.primary)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .cornerRadius(12)
                    }
                }

                // Secondary: Copy / Share Web Link
                let effectiveShareUrl: String? = {
                    guard let base = result.webShareUrl else { return nil }
                    if (result.parts?.count ?? 0) > 1 && selectedPartIndex > 0 {
                        let partNum = selectedPartIndex + 1
                        return base.contains("?") ? "\(base)&part=\(partNum)" : "\(base)?part=\(partNum)"
                    }
                    return base
                }()

                if let webUrl = effectiveShareUrl, let url = URL(string: webUrl) {
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
    private func startConversion(forceRefresh: Bool = false) {
        isLoading = true
        errorType = nil
        errorMessage = nil
        conversionResult = nil
        progressStep = 0

        ActivePlaylistModel.shared.current = ActivePlaylistData(
            playlistUrl: playlistUrl,
            targetPlatformKey: targetPlatformKey,
            state: .loading(step: 0)
        )

        // Progress timer
        stepTimer?.invalidate()
        stepTimer = Timer.scheduledTimer(withTimeInterval: 0.6, repeats: true) { timer in
            if !isLoading {
                timer.invalidate()
            } else {
                progressStep += 1
            }
        }

        let maxTracks: Int32 = proManager.isPro ? ProLimits.shared.MAX_PRO_PLAYLIST_TRACKS : ProLimits.shared.MAX_FREE_PLAYLIST_TRACKS
        let token = proManager.authToken

        Task {
            do {
                let kmpResult = try await PlaylistConverterEngine.companion.shared.convertPlaylistOrThrow(
                    url: playlistUrl,
                    targetPlatformKey: targetPlatformKey,
                    isPro: proManager.isPro,
                    maxTracks: maxTracks,
                    authToken: token.isEmpty ? nil : token,
                    forceRefresh: forceRefresh
                )

                let mappedTracks = kmpResult.tracks.map { t in
                    SwiftPlaylistTrackItem(
                        title: t.title,
                        artist: t.artist,
                        matched: t.matched,
                        targetUrl: t.targetUrl,
                        targetId: t.targetId,
                        sourceUrl: t.sourceUrl,
                        thumbnailUrl: t.thumbnailUrl
                    )
                }
                let mappedParts = kmpResult.parts.map { p in
                    SwiftPlaylistChunk(
                        partIndex: Int(p.partIndex),
                        rangeStart: Int(p.rangeStart),
                        rangeEnd: Int(p.rangeEnd),
                        zeroOAuthUrl: p.zeroOAuthUrl,
                        matchedCount: Int(p.matchedCount),
                        totalTracks: Int(p.totalTracks)
                    )
                }

                let decoded = SwiftPlaylistConversionResult(
                    status: kmpResult.status,
                    playlistId: kmpResult.playlistId,
                    title: kmpResult.title,
                    sourcePlatform: kmpResult.sourcePlatform,
                    targetPlatform: kmpResult.targetPlatform,
                    thumbnailUrl: kmpResult.thumbnailUrl,
                    totalTracks: Int(kmpResult.totalTracks),
                    originalTotalTracks: kmpResult.originalTotalTracks > 0 ? Int(kmpResult.originalTotalTracks) : nil,
                    convertedTracks: Int(kmpResult.convertedTracks),
                    matchedCount: Int(kmpResult.matchedCount),
                    zeroOAuthUrl: kmpResult.zeroOAuthUrl,
                    webShareUrl: kmpResult.webShareUrl,
                    isLimited: kmpResult.isLimited,
                    isPartial: kmpResult.isPartial,
                    tracks: mappedTracks,
                    parts: mappedParts
                )

                await MainActor.run {
                    self.conversionResult = decoded
                    self.isLoading = false
                    ActivePlaylistModel.shared.current = ActivePlaylistData(
                        playlistUrl: playlistUrl,
                        targetPlatformKey: targetPlatformKey,
                        state: .success(result: decoded)
                    )
                    // Save to history
                    let targetLink = decoded.zeroOAuthUrl ?? decoded.webShareUrl ?? playlistUrl
                    let histTotal = max(decoded.totalTracks, decoded.originalTotalTracks ?? 0)
                    HistoryModel.shared.add(
                        title: decoded.title?.isEmpty == false ? decoded.title! : "Playlist",
                        artist: String(format: LocalizationManager.string(for: "history_playlist_tracks_summary", lang: self.lang), decoded.matchedCount, histTotal),
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
            } catch {
                let nsError = error as NSError
                let kmpException = nsError.userInfo["KotlinException"] as? PlaylistConversionException
                let codeName = kmpException?.errorCode.name ?? ""
                let resolvedErrorType = mapErrorCode(codeName)
                await MainActor.run {
                    self.errorType = resolvedErrorType
                    self.isLoading = false
                    ActivePlaylistModel.shared.current = ActivePlaylistData(
                        playlistUrl: playlistUrl,
                        targetPlatformKey: targetPlatformKey,
                        state: .error(type: resolvedErrorType, message: errorDescription(for: resolvedErrorType))
                    )
                }
            }
        }
    }

    private func handleImportedFile(url: URL) {
        let hasAccess = url.startAccessingSecurityScopedResource()
        defer {
            if hasAccess {
                url.stopAccessingSecurityScopedResource()
            }
        }
        guard let content = try? String(contentsOf: url, encoding: .utf8) else {
            return
        }
        let fileName = url.lastPathComponent
        let parsed = PlaylistImportParser.shared.parse(content: content, fileNameHint: fileName)
        guard !parsed.isEmpty else {
            return
        }

        isLoading = true
        progressStep = 0
        stepTimer?.invalidate()
        stepTimer = Timer.scheduledTimer(withTimeInterval: 0.6, repeats: true) { timer in
            if !isLoading {
                timer.invalidate()
            } else {
                progressStep += 1
            }
        }

        let token = proManager.authToken
        let cleanTitle = url.deletingPathExtension().lastPathComponent

        Task {
            do {
                let kmpResult = try await PlaylistConverterEngine.companion.shared.convertImportedTracksOrThrow(
                    tracks: parsed,
                    targetPlatformKey: targetPlatformKey,
                    isPro: proManager.isPro,
                    playlistTitle: cleanTitle,
                    authToken: token.isEmpty ? nil : token
                )

                let mappedTracks = kmpResult.tracks.map { t in
                    SwiftPlaylistTrackItem(
                        title: t.title,
                        artist: t.artist,
                        matched: t.matched,
                        targetUrl: t.targetUrl,
                        targetId: t.targetId,
                        sourceUrl: t.sourceUrl,
                        thumbnailUrl: t.thumbnailUrl
                    )
                }
                let mappedParts = kmpResult.parts.map { p in
                    SwiftPlaylistChunk(
                        partIndex: Int(p.partIndex),
                        rangeStart: Int(p.rangeStart),
                        rangeEnd: Int(p.rangeEnd),
                        zeroOAuthUrl: p.zeroOAuthUrl,
                        matchedCount: Int(p.matchedCount),
                        totalTracks: Int(p.totalTracks)
                    )
                }

                let decoded = SwiftPlaylistConversionResult(
                    status: kmpResult.status,
                    playlistId: kmpResult.playlistId,
                    title: kmpResult.title,
                    sourcePlatform: kmpResult.sourcePlatform,
                    targetPlatform: kmpResult.targetPlatform,
                    thumbnailUrl: kmpResult.thumbnailUrl,
                    totalTracks: Int(kmpResult.totalTracks),
                    originalTotalTracks: kmpResult.originalTotalTracks > 0 ? Int(kmpResult.originalTotalTracks) : nil,
                    convertedTracks: Int(kmpResult.convertedTracks),
                    matchedCount: Int(kmpResult.matchedCount),
                    zeroOAuthUrl: kmpResult.zeroOAuthUrl,
                    webShareUrl: kmpResult.webShareUrl,
                    isLimited: kmpResult.isLimited,
                    isPartial: kmpResult.isPartial,
                    tracks: mappedTracks,
                    parts: mappedParts
                )

                await MainActor.run {
                    self.conversionResult = decoded
                    self.isLoading = false
                    ActivePlaylistModel.shared.current = ActivePlaylistData(
                        playlistUrl: "https://songflip.link/file-import/\(decoded.playlistId ?? "file")",
                        targetPlatformKey: targetPlatformKey,
                        state: .success(result: decoded)
                    )
                    // Save to history
                    let targetLink = decoded.zeroOAuthUrl ?? decoded.webShareUrl ?? playlistUrl
                    let histTotal = max(decoded.totalTracks, decoded.originalTotalTracks ?? 0)
                    HistoryModel.shared.add(
                        title: decoded.title?.isEmpty == false ? decoded.title! : "Playlist",
                        artist: String(format: LocalizationManager.string(for: "history_playlist_tracks_summary", lang: self.lang), decoded.matchedCount, histTotal),
                        sourceUrl: "https://songflip.link/file-import/\(decoded.playlistId ?? "file")",
                        targetUrl: targetLink,
                        targetPlatform: "\(targetPlatformKey)_playlist",
                        isAlbum: false,
                        thumbnailUrl: decoded.thumbnailUrl
                    )
                }
            } catch {
                let nsError = error as NSError
                let kmpException = nsError.userInfo["KotlinException"] as? PlaylistConversionException
                let codeName = kmpException?.errorCode.name ?? ""
                let resolvedErrorType = mapErrorCode(codeName)
                await MainActor.run {
                    self.errorType = resolvedErrorType
                    self.isLoading = false
                }
            }
        }
    }

    private func openInTargetPlayer(result: SwiftPlaylistConversionResult, customZeroUrl: String? = nil) {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        let targetZeroUrl = customZeroUrl ?? result.zeroOAuthUrl
        if let zeroUrl = targetZeroUrl, let url = URL(string: zeroUrl) {
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

    private func toKmpTracks(_ tracks: [SwiftPlaylistTrackItem]) -> [PlaylistTrackItem] {
        return tracks.map { t in
            PlaylistTrackItem(
                title: t.title,
                artist: t.artist,
                matched: t.matched,
                targetUrl: t.targetUrl,
                targetId: t.targetId,
                sourceUrl: t.sourceUrl,
                thumbnailUrl: t.thumbnailUrl
            )
        }
    }

    private func calculateChunks(tracks: [SwiftPlaylistTrackItem], targetPlatform: String, playlistTitle: String) -> [SwiftPlaylistChunk] {
        let kmpChunks = PlaylistExporter.shared.calculateChunks(
            tracks: toKmpTracks(tracks),
            chunkSize: PlaylistExporter.shared.DEFAULT_CHUNK_SIZE,
            targetPlatform: targetPlatform,
            playlistTitle: playlistTitle
        )
        return kmpChunks.map { c in
            SwiftPlaylistChunk(
                partIndex: Int(c.partIndex),
                rangeStart: Int(c.rangeStart),
                rangeEnd: Int(c.rangeEnd),
                zeroOAuthUrl: c.zeroOAuthUrl,
                matchedCount: Int(c.matchedCount),
                totalTracks: Int(c.totalTracks)
            )
        }
    }

    private func exportM3U8(title: String, tracks: [SwiftPlaylistTrackItem]) -> URL? {
        let safeTitle = title.isEmpty ? "SongFlip Playlist" : title
        let content = PlaylistExporter.shared.exportToM3u8(title: safeTitle, tracks: toKmpTracks(tracks))
        let tempDir = FileManager.default.temporaryDirectory
        let fileUrl = tempDir.appendingPathComponent("\(sanitizeFilename(safeTitle)).m3u8")
        do {
            try content.write(to: fileUrl, atomically: true, encoding: .utf8)
            return fileUrl
        } catch {
            logger.warning("Failed to export M3U8 file: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private func exportCSV(title: String, tracks: [SwiftPlaylistTrackItem]) -> URL? {
        let safeTitle = title.isEmpty ? "SongFlip Playlist" : title
        let content = PlaylistExporter.shared.exportToCsv(title: safeTitle, tracks: toKmpTracks(tracks))
        let tempDir = FileManager.default.temporaryDirectory
        let fileUrl = tempDir.appendingPathComponent("\(sanitizeFilename(safeTitle)).csv")
        do {
            try content.write(to: fileUrl, atomically: true, encoding: .utf8)
            return fileUrl
        } catch {
            logger.warning("Failed to export CSV file: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private func sanitizeFilename(_ input: String) -> String {
        return input.components(separatedBy: CharacterSet.alphanumerics.inverted).joined(separator: "_").prefix(50).description
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
        switch code.uppercased() {
        case "PRIVATE_OR_RESTRICTED": return .privateOrRestricted
        case "UNSUPPORTED_PLATFORM": return .unsupportedPlatform
        case "UPSTREAM_TIMEOUT": return .timeout
        case "EMPTY_PLAYLIST": return .empty
        case "NOT_FOUND": return .notFound
        default: return .generic
        }
    }

    private func errorTitle(for type: PlaylistErrorType) -> String {
        switch type {
        case .privateOrRestricted: return LocalizationManager.string(for: "playlist_error_private_title", lang: lang)
        case .unsupportedPlatform: return LocalizationManager.string(for: "playlist_error_unsupported_title", lang: lang)
        case .timeout: return LocalizationManager.string(for: "playlist_error_timeout_title", lang: lang)
        case .empty: return LocalizationManager.string(for: "playlist_error_empty_title", lang: lang)
        case .notFound, .generic: return LocalizationManager.string(for: "playlist_error_title", lang: lang)
        }
    }

    private func errorDescription(for type: PlaylistErrorType) -> String {
        switch type {
        case .privateOrRestricted: return LocalizationManager.string(for: "playlist_error_private_desc", lang: lang)
        case .unsupportedPlatform: return LocalizationManager.string(for: "playlist_error_unsupported_desc", lang: lang)
        case .timeout: return LocalizationManager.string(for: "playlist_error_timeout_desc", lang: lang)
        case .empty: return LocalizationManager.string(for: "playlist_error_empty_desc", lang: lang)
        case .notFound, .generic: return LocalizationManager.string(for: "playlist_error_desc", lang: lang)
        }
    }
}
