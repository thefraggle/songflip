import SwiftUI
import AVFoundation
import SongFlipKit

struct SearchTrackItem: Identifiable, Equatable {
    let id: String
    let title: String
    let artist: String
    let album: String?
    let coverUrl: String?
    let previewUrl: String?
    let sourceUrl: String
    let durationSec: Int

    var formattedDuration: String {
        let mins = durationSec / 60
        let secs = durationSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

struct SearchSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    @State private var searchQuery: String = ""
    @State private var searchResults: [SearchTrackItem] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var resolvingTrackId: String? = nil

    // Audio Preview playback
    @State private var player: AVPlayer? = nil
    @State private var playingPreviewUrl: String? = nil

    var lang: String { settings.selectedLanguage }

    private var targetPlatformName: String {
        PlatformChoice(rawValue: settings.targetPlatform)?.displayName ?? settings.targetPlatform
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                VStack(spacing: 14) {
                    // Search bar
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .foregroundColor(.secondary)

                        TextField(
                            LocalizationManager.string(for: "search_hint", lang: lang),
                            text: $searchQuery
                        )
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)
                        .submitLabel(.search)

                        if !searchQuery.isEmpty {
                            Button(action: {
                                searchQuery = ""
                                searchResults = []
                                stopPreview()
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    // Target Service Indicator Chip
                    HStack(spacing: 6) {
                        Text(LocalizationManager.string(for: "target_service_label", lang: lang))
                            .font(.caption2)
                            .foregroundColor(.secondary)

                        Text(targetPlatformName)
                            .font(.caption2)
                            .fontWeight(.bold)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.green.opacity(0.18))
                            .foregroundColor(.green)
                            .cornerRadius(6)

                        Spacer()
                    }
                    .padding(.horizontal, 16)

                    // Results content
                    if isLoading {
                        Spacer()
                        VStack(spacing: 12) {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle())
                                .scaleEffect(1.2)
                            Text(LocalizationManager.string(for: "test_converting", lang: lang))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    } else if searchResults.isEmpty && !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty {
                        Spacer()
                        VStack(spacing: 10) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 40))
                                .foregroundColor(.secondary.opacity(0.6))
                            Text(errorMessage ?? LocalizationManager.string(for: "search_empty", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    } else if searchResults.isEmpty {
                        Spacer()
                        VStack(spacing: 10) {
                            Image(systemName: "music.note")
                                .font(.system(size: 44))
                                .foregroundColor(Color("AccentColor").opacity(0.7))
                            Text(LocalizationManager.string(for: "search_initial_prompt", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    } else {
                        List {
                            ForEach(searchResults) { track in
                                trackRow(track: track)
                                    .listRowBackground(Color("CardBackgroundColor"))
                            }
                        }
                        .listStyle(.insetGrouped)
                    }
                }
            }
            .navigationTitle(LocalizationManager.string(for: "search_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        stopPreview()
                        dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundColor(.secondary.opacity(0.7))
                    }
                }
            }
            .onChange(of: searchQuery) { query in
                performSearch(query: query)
            }
            .onDisappear {
                stopPreview()
            }
        }
    }

    @ViewBuilder
    private func trackRow(track: SearchTrackItem) -> some View {
        let isResolving = resolvingTrackId == track.id
        let isPlaying = playingPreviewUrl == track.previewUrl && track.previewUrl != nil

        HStack(spacing: 12) {
            // Cover Image with 1-tap Preview Button
            ZStack(alignment: .bottomTrailing) {
                if let coverStr = track.coverUrl, let url = URL(string: coverStr) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img):
                            img
                                .resizable()
                                .scaledToFill()
                                .frame(width: 48, height: 48)
                                .clipped()
                        case .failure, .empty:
                            fallbackArtwork
                        @unknown default:
                            fallbackArtwork
                        }
                    }
                    .frame(width: 48, height: 48)
                    .cornerRadius(8)
                } else {
                    fallbackArtwork
                }

                if let preview = track.previewUrl, !preview.isEmpty {
                    Button(action: {
                        togglePreview(url: preview)
                    }) {
                        Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 15))
                            .foregroundColor(.white)
                            .background(Circle().fill(Color.black.opacity(0.55)).frame(width: 15, height: 15))
                            .offset(x: 2, y: 2)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Title & Artist
            VStack(alignment: .leading, spacing: 3) {
                Text(track.title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                HStack(spacing: 4) {
                    Text(track.artist)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)

                    if track.durationSec > 0 {
                        Text("•")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                        Text(track.formattedDuration)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Spacer()

            if isResolving {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle())
                    .scaleEffect(0.9)
            } else {
                Image(systemName: "arrow.right.circle.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.green)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if !isResolving {
                flipTrack(track)
            }
        }
    }

    private var fallbackArtwork: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color("AccentColor").opacity(0.12))
                .frame(width: 48, height: 48)
            Image(systemName: "music.note")
                .foregroundColor(Color("AccentColor"))
                .font(.system(size: 20))
        }
    }

    // MARK: - Search Execution
    @State private var searchTask: Task<Void, Never>? = nil

    private func performSearch(query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count < 2 {
            searchResults = []
            isLoading = false
            return
        }

        isLoading = true
        errorMessage = nil

        searchTask = Task {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }

            guard let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                  let url = URL(string: "https://itunes.apple.com/search?term=\(encoded)&entity=song&limit=20") else {
                await MainActor.run { isLoading = false }
                return
            }

            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if Task.isCancelled { return }

                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let results = json["results"] as? [[String: Any]] {
                    var items: [SearchTrackItem] = []
                    for r in results {
                        let trackId = "\(r["trackId"] ?? UUID().uuidString)"
                        let name = (r["trackName"] as? String) ?? ""
                        let artist = (r["artistName"] as? String) ?? ""
                        if name.isEmpty { continue }

                        let album = r["collectionName"] as? String
                        let rawArt = (r["artworkUrl100"] as? String) ?? (r["artworkUrl60"] as? String)
                        let cover = rawArt?.replacingOccurrences(of: "100x100bb", with: "300x300bb")
                            .replacingOccurrences(of: "60x60bb", with: "300x300bb")
                        let preview = r["previewUrl"] as? String
                        let trackViewUrl = (r["trackViewUrl"] as? String) ?? ""
                        let millis = (r["trackTimeMillis"] as? Int) ?? 0

                        items.append(SearchTrackItem(
                            id: trackId,
                            title: name,
                            artist: artist,
                            album: album,
                            coverUrl: cover,
                            previewUrl: preview,
                            sourceUrl: trackViewUrl,
                            durationSec: millis / 1000
                        ))
                    }
                    await MainActor.run {
                        self.searchResults = items
                        self.isLoading = false
                        if items.isEmpty {
                            self.errorMessage = LocalizationManager.string(for: "search_empty", lang: lang)
                        }
                    }
                } else {
                    await MainActor.run {
                        self.isLoading = false
                    }
                }
            } catch {
                if !Task.isCancelled {
                    await MainActor.run {
                        self.isLoading = false
                        self.errorMessage = LocalizationManager.string(for: "search_error", lang: lang)
                    }
                }
            }
        }
    }

    // MARK: - Track Flip & Open
    private func flipTrack(_ track: SearchTrackItem) {
        guard !track.sourceUrl.isEmpty else { return }
        stopPreview()
        resolvingTrackId = track.id
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        Task {
            do {
                let res = try await SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl: track.sourceUrl,
                    targetPlatformKey: settings.targetPlatform,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    forceRefresh: false
                )

                await MainActor.run {
                    resolvingTrackId = nil
                    if let success = res as? ResolutionResult.Success,
                       let targetUrl = URL(string: success.targetUrl) {
                        // Track in Aptabase
                        AptabaseClient.shared.trackLinkFlipped(
                            target: settings.targetPlatform,
                            isAlbum: success.isAlbum,
                            isSearch: success.isSearchFallback,
                            source: "in_app_search"
                        )

                        // Save to History
                        HistoryModel.shared.add(
                            title: success.title ?? track.title,
                            artist: success.artist ?? track.artist,
                            sourceUrl: track.sourceUrl,
                            targetUrl: success.targetUrl,
                            targetPlatform: settings.targetPlatform,
                            isAlbum: success.isAlbum,
                            thumbnailUrl: success.thumbnailUrl ?? track.coverUrl
                        )

                        // Launch player & dismiss
                        UIApplication.shared.open(targetUrl)
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    resolvingTrackId = nil
                }
            }
        }
    }

    // MARK: - Audio Preview
    private func togglePreview(url: String) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if playingPreviewUrl == url {
            stopPreview()
        } else {
            stopPreview()
            guard let audioUrl = URL(string: url) else { return }
            let item = AVPlayerItem(url: audioUrl)
            let newPlayer = AVPlayer(playerItem: item)
            self.player = newPlayer
            self.playingPreviewUrl = url
            newPlayer.play()

            NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: item,
                queue: .main
            ) { _ in
                self.stopPreview()
            }
        }
    }

    private func stopPreview() {
        player?.pause()
        player = nil
        playingPreviewUrl = nil
    }
}
