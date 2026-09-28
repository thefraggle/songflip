import SwiftUI
import CryptoKit
import SongFlipKit

struct HistorySheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared
    @Environment(\.dismiss) var dismiss
    @State private var showingClearConfirmation = false
    @State private var refreshingItemId: UUID? = nil

    var lang: String { settings.selectedLanguage }

    private func refreshLink(for item: HistoryItem) async {
        await MainActor.run { refreshingItemId = item.id }
        let engine = SongLinkEngine.shared
        do {
            let res = try await engine.resolveTargetUrl(
                inputUrl: item.sourceUrl,
                targetPlatformKey: settings.targetPlatform,
                customApiUrl: settings.customApiUrl,
                customApiToken: settings.customApiToken,
                forceRefresh: true
            )
            if let success = res as? ResolutionResult.Success {
                await MainActor.run {
                    history.updateItem(
                        id: item.id,
                        newTargetUrl: success.targetUrl,
                        newTitle: success.title,
                        newArtist: success.artist,
                        newTargetPlatform: settings.targetPlatform,
                        isAlbum: success.isAlbum,
                        thumbnailUrl: success.thumbnailUrl
                    )
                }
            }
        } catch {
            // Graceful fallback
        }
        await MainActor.run { refreshingItemId = nil }
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                if history.items.isEmpty {
                    VStack(spacing: 20) {
                        ZStack {
                            RoundedRectangle(cornerRadius: 24, style: .continuous)
                                .fill(Color("AccentColor").opacity(0.12))
                                .frame(width: 84, height: 84)

                            Image(systemName: "music.note.list")
                                .font(.system(size: 38))
                                .foregroundColor(Color("AccentColor"))
                        }

                        VStack(spacing: 6) {
                            Text(LocalizationManager.string(for: "history_empty_title", lang: lang))
                                .font(.title3)
                                .fontWeight(.bold)
                                .foregroundColor(.primary)

                            Text(LocalizationManager.string(for: "history_empty", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.center)
                                .padding(.horizontal, 24)
                        }

                        HStack(spacing: 10) {
                            Text("💡")
                                .font(.system(size: 16))

                            Text(LocalizationManager.string(for: "history_empty_hint", lang: lang))
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .multilineTextAlignment(.leading)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(Color("CardBackgroundColor").opacity(0.8))
                        )
                        .padding(.horizontal, 28)
                    }
                    .padding(.vertical, 32)
                } else {
                    List {
                        ForEach(history.items) { item in
                            Button(action: {
                                if let url = URL(string: item.targetUrl) {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    UIApplication.shared.open(url)
                                }
                            }) {
                                HStack(spacing: 12) {
                                    // 1. Cover Artwork (48x48dp) with Play Indicator
                                    ZStack(alignment: .bottomTrailing) {
                                        if let thumbStr = item.thumbnailUrl, let thumbUrl = URL(string: thumbStr) {
                                            AsyncImage(url: thumbUrl) { phase in
                                                switch phase {
                                                case .success(let image):
                                                    image
                                                        .resizable()
                                                        .scaledToFill()
                                                        .frame(width: 48, height: 48)
                                                        .clipped()
                                                case .failure, .empty:
                                                    fallbackCoverView(for: item)
                                                @unknown default:
                                                    fallbackCoverView(for: item)
                                                }
                                            }
                                            .frame(width: 48, height: 48)
                                            .clipShape(RoundedRectangle(cornerRadius: 8))
                                        } else {
                                            fallbackCoverView(for: item)
                                                .frame(width: 48, height: 48)
                                                .clipShape(RoundedRectangle(cornerRadius: 8))
                                        }

                                        // Subtle Play Indicator Badge
                                        Image(systemName: "play.circle.fill")
                                            .font(.system(size: 14))
                                            .foregroundColor(.white)
                                            .background(Circle().fill(Color.black.opacity(0.4)).frame(width: 14, height: 14))
                                            .offset(x: 2, y: 2)
                                    }

                                    // 2. Song & Artist Info
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(item.title)
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.primary)
                                            .lineLimit(1)

                                        HStack(spacing: 4) {
                                            if let artist = item.artist, !artist.isEmpty {
                                                Text(artist)
                                                    .font(.caption)
                                                    .foregroundColor(.secondary)
                                                    .lineLimit(1)

                                                Text("•")
                                                    .font(.caption2)
                                                    .foregroundColor(.secondary)
                                            }

                                            Text(item.formattedDate)
                                                .font(.caption2)
                                                .foregroundColor(.secondary)
                                        }
                                    }

                                    Spacer()

                                    // 3. Share FlipPage Button
                                    if let shareUrl = universalShareUrl(for: item.sourceUrl) {
                                        ShareLink(item: shareUrl, message: Text(item.title)) {
                                            Image(systemName: "square.and.arrow.up")
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundColor(Color("AccentColor"))
                                                .frame(width: 32, height: 32)
                                        }
                                        .buttonStyle(.borderless)
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                            .listRowBackground(Color("CardBackgroundColor"))
                            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                Button {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    Task {
                                        await refreshLink(for: item)
                                    }
                                } label: {
                                    Label(LocalizationManager.string(for: "action_refresh_link", lang: lang), systemImage: "arrow.clockwise")
                                }
                                .tint(.blue)
                            }
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    history.deleteItem(id: item.id)
                                } label: {
                                    Label(LocalizationManager.string(for: "action_delete", lang: lang), systemImage: "trash")
                                }
                            }
                            .contextMenu {
                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    UIPasteboard.general.string = item.targetUrl
                                } label: {
                                    Label(LocalizationManager.string(for: "action_copy", lang: lang), systemImage: "doc.on.doc")
                                }

                                Button {
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    UIPasteboard.general.string = item.sourceUrl
                                } label: {
                                    Label(LocalizationManager.string(for: "action_copy_source", lang: lang), systemImage: "link")
                                }

                                Button {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    Task {
                                        await refreshLink(for: item)
                                    }
                                } label: {
                                    Label(LocalizationManager.string(for: "action_refresh_link", lang: lang), systemImage: "arrow.clockwise")
                                }

                                Divider()

                                Button(role: .destructive) {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    history.deleteItem(id: item.id)
                                } label: {
                                    Label(LocalizationManager.string(for: "action_delete", lang: lang), systemImage: "trash")
                                }
                            }
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
            .navigationTitle(LocalizationManager.string(for: "history_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    if !history.items.isEmpty {
                        Button(role: .destructive, action: { showingClearConfirmation = true }) {
                            Image(systemName: "trash")
                                .foregroundColor(.red.opacity(0.85))
                        }
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(LocalizationManager.string(for: "btn_done", lang: lang)) { dismiss() }
                        .fontWeight(.semibold)
                }
            }
            .confirmationDialog(
                LocalizationManager.string(for: "history_clear_confirm_title", lang: lang),
                isPresented: $showingClearConfirmation,
                titleVisibility: .visible
            ) {
                Button(LocalizationManager.string(for: "history_clear_all", lang: lang), role: .destructive) {
                    history.clear()
                }
                Button(LocalizationManager.string(for: "btn_cancel", lang: lang), role: .cancel) {}
            }
            .preferredColorScheme(settings.colorScheme)
            .onAppear {
                history.loadHistory()
            }
        }
    }

    private func platformIcon(for key: String) -> String {
        switch key.lowercased() {
        case "spotify": return "dot.radiowaves.left.and.right"
        case "applemusic", "apple": return "music.note"
        case "youtubemusic", "youtube": return "play.rectangle.fill"
        case "deezer": return "music.quarternote.3"
        case "tidal": return "waveform"
        case "amazonmusic", "amazon": return "cart.fill"
        case "soundcloud": return "waveform"
        case "bandcamp": return "opticaldisc"
        default: return "music.note.list"
        }
    }

    @ViewBuilder
    private func fallbackCoverView(for item: HistoryItem) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(platformColor(for: item.targetPlatform).opacity(0.18))
                .frame(width: 48, height: 48)

            Image(systemName: item.isAlbum ? "opticaldisc" : platformIcon(for: item.targetPlatform))
                .font(.system(size: 20))
                .foregroundColor(platformColor(for: item.targetPlatform))
        }
    }

    private func universalShareUrl(for sourceUrl: String) -> URL? {
        let normalized = sourceUrl.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let digest = SHA256.hash(data: Data(normalized.utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let shortId = String(hex.prefix(12))
        return URL(string: "https://songflip.link/s/\(shortId)")
    }
}
