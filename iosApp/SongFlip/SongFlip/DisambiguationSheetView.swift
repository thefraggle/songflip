import SwiftUI
import AVFoundation
import SongFlipKit

struct DisambiguationCandidateItem: Identifiable, Equatable {
    let id: String
    let title: String
    let artist: String
    let album: String?
    let targetUrl: String
    let coverUrl: String?
    let previewUrl: String?
    let durationSec: Int
    var isCurrentMatch: Bool

    var formattedDuration: String {
        let mins = durationSec / 60
        let secs = durationSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

struct DisambiguationSheetView: View {
    let item: HistoryItem
    var onMatchUpdated: (String) -> Void

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared

    @State private var candidates: [DisambiguationCandidateItem] = []
    @State private var isLoading: Bool = true
    @State private var errorMessage: String? = nil
    @State private var applyingCandidateId: String? = nil

    // Audio Preview playback
    @State private var player: AVPlayer? = nil
    @State private var playingPreviewUrl: String? = nil

    var lang: String { settings.selectedLanguage }

    private var targetPlatformName: String {
        PlatformChoice(rawValue: item.targetPlatform)?.displayName ?? item.targetPlatform
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                VStack(spacing: 14) {
                    // Header Subtitle / Info
                    HStack(spacing: 8) {
                        Image(systemName: "slider.horizontal.3")
                            .font(.caption)
                            .foregroundColor(Color("AccentColor"))

                        Text(String(format: LocalizationManager.string(for: "disambiguate_subtitle", lang: lang), targetPlatformName))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(2)

                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    // Song context card
                    HStack(spacing: 12) {
                        if let thumbStr = item.thumbnailUrl, let url = URL(string: thumbStr) {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let img):
                                    img
                                        .resizable()
                                        .scaledToFill()
                                        .frame(width: 44, height: 44)
                                        .clipped()
                                default:
                                    fallbackCover
                                }
                            }
                            .frame(width: 44, height: 44)
                            .cornerRadius(8)
                        } else {
                            fallbackCover
                        }

                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.primary)
                                .lineLimit(1)

                            if let artist = item.artist, !artist.isEmpty {
                                Text(artist)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                            }
                        }

                        Spacer()
                    }
                    .padding(12)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)

                    // Candidate List
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
                    } else if candidates.isEmpty {
                        Spacer()
                        VStack(spacing: 10) {
                            Image(systemName: "exclamationmark.triangle")
                                .font(.system(size: 36))
                                .foregroundColor(.secondary)
                            Text(errorMessage ?? LocalizationManager.string(for: "disambiguate_empty", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 24)
                        }
                        Spacer()
                    } else {
                        List {
                            ForEach(candidates) { candidate in
                                candidateRow(candidate: candidate)
                                    .listRowBackground(
                                        candidate.isCurrentMatch
                                            ? Color.green.opacity(0.12)
                                            : Color("CardBackgroundColor")
                                    )
                            }
                        }
                        .listStyle(.insetGrouped)
                    }
                }
            }
            .navigationTitle(LocalizationManager.string(for: "disambiguate_title", lang: lang))
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
            .onAppear {
                loadCandidates()
            }
            .onDisappear {
                stopPreview()
            }
        }
    }

    @ViewBuilder
    private func candidateRow(candidate: DisambiguationCandidateItem) -> some View {
        let isApplying = applyingCandidateId == candidate.id
        let isPlaying = playingPreviewUrl == candidate.previewUrl && candidate.previewUrl != nil

        HStack(spacing: 12) {
            // Radio / Checkmark Indicator
            Image(systemName: candidate.isCurrentMatch ? "checkmark.circle.fill" : "circle")
                .font(.system(size: 20))
                .foregroundColor(candidate.isCurrentMatch ? .green : .secondary.opacity(0.6))

            // Cover Image with 1-tap Preview Button
            ZStack(alignment: .bottomTrailing) {
                if let coverStr = candidate.coverUrl, let url = URL(string: coverStr) {
                    AsyncImage(url: url) { phase in
                        switch phase {
                        case .success(let img):
                            img
                                .resizable()
                                .scaledToFill()
                                .frame(width: 44, height: 44)
                                .clipped()
                        default:
                            fallbackCover
                        }
                    }
                    .frame(width: 44, height: 44)
                    .cornerRadius(8)
                } else {
                    fallbackCover
                }

                if let preview = candidate.previewUrl, !preview.isEmpty {
                    Button(action: {
                        togglePreview(url: preview)
                    }) {
                        Image(systemName: isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .background(Circle().fill(Color.black.opacity(0.55)).frame(width: 14, height: 14))
                            .offset(x: 2, y: 2)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Title, Album & Duration
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(candidate.title)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.primary)
                        .lineLimit(1)

                    if candidate.isCurrentMatch {
                        Text(LocalizationManager.string(for: "disambiguate_current", lang: lang))
                            .font(.system(size: 10, weight: .bold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.green.opacity(0.2))
                            .foregroundColor(.green)
                            .cornerRadius(4)
                    }
                }

                HStack(spacing: 4) {
                    if let album = candidate.album, !album.isEmpty {
                        Text(album)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .lineLimit(1)

                        Text("•")
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }

                    if candidate.durationSec > 0 {
                        Text(candidate.formattedDuration)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Spacer()

            if isApplying {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle())
                    .scaleEffect(0.85)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if !isApplying {
                selectCandidate(candidate)
            }
        }
    }

    private var fallbackCover: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(Color("AccentColor").opacity(0.12))
                .frame(width: 44, height: 44)
            Image(systemName: "music.note")
                .foregroundColor(Color("AccentColor"))
                .font(.system(size: 18))
        }
    }

    // MARK: - Load Candidates
    private func loadCandidates() {
        isLoading = true
        errorMessage = nil

        let query = "\(item.artist ?? "") \(item.title)".trimmingCharacters(in: .whitespacesAndNewlines)
        guard let encoded = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://itunes.apple.com/search?term=\(encoded)&entity=song&limit=10") else {
            isLoading = false
            return
        }

        Task {
            do {
                let (data, _) = try await URLSession.shared.data(from: url)
                if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                   let results = json["results"] as? [[String: Any]] {
                    var list: [DisambiguationCandidateItem] = []
                    for r in results {
                        let trackId = "\(r["trackId"] ?? UUID().uuidString)"
                        let name = (r["trackName"] as? String) ?? ""
                        let artist = (r["artistName"] as? String) ?? ""
                        if name.isEmpty { continue }

                        let album = r["collectionName"] as? String
                        let rawArt = (r["artworkUrl100"] as? String) ?? (r["artworkUrl60"] as? String)
                        let cover = rawArt?.replacingOccurrences(of: "100x100bb", with: "300x300bb")
                        let preview = r["previewUrl"] as? String
                        let trackViewUrl = (r["trackViewUrl"] as? String) ?? ""
                        let millis = (r["trackTimeMillis"] as? Int) ?? 0

                        let isCurrent = !item.targetUrl.isEmpty && (
                            trackViewUrl == item.targetUrl ||
                            item.targetUrl.contains(trackId)
                        )

                        list.append(DisambiguationCandidateItem(
                            id: trackId,
                            title: name,
                            artist: artist,
                            album: album,
                            targetUrl: trackViewUrl,
                            coverUrl: cover,
                            previewUrl: preview,
                            durationSec: millis / 1000,
                            isCurrentMatch: isCurrent
                        ))
                    }

                    // Fallback to current item if empty or make sure at least one is matched
                    if !list.isEmpty && !list.contains(where: { $0.isCurrentMatch }) {
                        list[0].isCurrentMatch = true
                    }

                    await MainActor.run {
                        self.candidates = list
                        self.isLoading = false
                        if list.isEmpty {
                            self.errorMessage = LocalizationManager.string(for: "disambiguate_empty", lang: lang)
                        }
                    }
                } else {
                    await MainActor.run { self.isLoading = false }
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = LocalizationManager.string(for: "disambiguate_error", lang: lang)
                }
            }
        }
    }

    // MARK: - Select Candidate
    private func selectCandidate(_ candidate: DisambiguationCandidateItem) {
        stopPreview()
        applyingCandidateId = candidate.id
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        Task {
            // If target platform is different from the raw candidate source, resolve it
            let finalTargetUrl: String
            let cleanPlatform = item.targetPlatform.replacingOccurrences(of: "_playlist", with: "")

            if cleanPlatform == "appleMusic" || cleanPlatform == "itunes" {
                finalTargetUrl = candidate.targetUrl
            } else {
                do {
                    let res = try await SongLinkEngine.shared.resolveTargetUrl(
                        inputUrl: candidate.targetUrl,
                        targetPlatformKey: cleanPlatform,
                        customApiUrl: settings.customApiUrl,
                        customApiToken: settings.customApiToken,
                        forceRefresh: true
                    )
                    if let success = res as? ResolutionResult.Success {
                        finalTargetUrl = success.targetUrl
                    } else {
                        finalTargetUrl = candidate.targetUrl
                    }
                } catch {
                    finalTargetUrl = candidate.targetUrl
                }
            }

            // Update LinkCache
            let cacheKey = "\(item.sourceUrl)|\(item.targetPlatform)"
            do {
                try await SongLinkEngine.shared.cache.updateTargetUrl(
                    cacheKey: cacheKey,
                    newTargetUrl: finalTargetUrl,
                    newNativeAppUri: nil,
                    currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
                )
            } catch {
                print("[DisambiguationSheet] Failed to update target url in cache: \(error)")
            }

            await MainActor.run {
                applyingCandidateId = nil

                // Update HistoryModel
                history.updateItem(
                    id: item.id,
                    newTargetUrl: finalTargetUrl,
                    newTitle: candidate.title,
                    newArtist: candidate.artist,
                    newTargetPlatform: item.targetPlatform,
                    isAlbum: item.isAlbum,
                    thumbnailUrl: candidate.coverUrl ?? item.thumbnailUrl
                )

                // Analytics
                AptabaseClient.shared.trackEvent(
                    eventName: "match_disambiguated",
                    props: [
                        "target": item.targetPlatform,
                        "title": candidate.title,
                        "artist": candidate.artist
                    ]
                )

                onMatchUpdated(finalTargetUrl)

                // Open updated target
                if let url = URL(string: finalTargetUrl) {
                    UIApplication.shared.open(url)
                }

                dismiss()
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
            let playerItem = AVPlayerItem(url: audioUrl)
            let newPlayer = AVPlayer(playerItem: playerItem)
            self.player = newPlayer
            self.playingPreviewUrl = url
            newPlayer.play()

            NotificationCenter.default.addObserver(
                forName: .AVPlayerItemDidPlayToEndTime,
                object: playerItem,
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
