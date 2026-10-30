import SwiftUI
import CryptoKit
import SongFlipKit

struct HistorySheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared
    @ObservedObject var proManager = ProManager.shared
    @Environment(\.dismiss) var dismiss
    enum HistoryFilterMode {
        case all, favorites, acoustic
    }

    @State private var showingClearConfirmation = false
    @State private var showingPaywallSheet = false
    @State private var showingFavoriteLimitAlert = false
    @State private var filterMode: HistoryFilterMode = .all
    @State private var refreshingItemId: UUID? = nil
    @State private var disambiguateItem: HistoryItem? = nil
    @State private var qrCodePayload: QRCodePayload? = nil
    var onOpenPlaylist: ((String, String) -> Void)? = nil

    struct QRCodePayload: Identifiable {
        let id = UUID()
        let url: String
        let title: String
        let artist: String?
    }

    var lang: String { settings.selectedLanguage }

    private var favoriteCount: Int {
        history.items.filter { $0.isFavorite }.count
    }

    private var acousticCount: Int {
        history.items.filter { $0.isAcoustic }.count
    }

    private var displayedItems: [HistoryItem] {
        switch filterMode {
        case .all:
            return history.items
        case .favorites:
            return history.items.filter { $0.isFavorite }
        case .acoustic:
            return history.items.filter { $0.isAcoustic }
        }
    }

    private func toggleFavorite(for item: HistoryItem) {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        Task {
            let result = await history.toggleFavorite(item: item, isPro: proManager.isPro)
            await MainActor.run {
                if result == .limitReached {
                    AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "favorite_limit_reached"])
                    showingFavoriteLimitAlert = true
                }
            }
        }
    }

    private func refreshLink(for item: HistoryItem) async {
        await MainActor.run { refreshingItemId = item.id }
        let isPlaylist = item.targetPlatform.contains("_playlist") || UrlUtils.shared.isPlaylistUrl(url: item.sourceUrl)
        if isPlaylist {
            let targetKey = item.targetPlatform.replacingOccurrences(of: "_playlist", with: "").isEmpty
                ? settings.targetPlatform
                : item.targetPlatform.replacingOccurrences(of: "_playlist", with: "")
            let maxTracks: Int32 = proManager.isPro ? ProLimits.shared.MAX_PRO_PLAYLIST_TRACKS : ProLimits.shared.MAX_FREE_PLAYLIST_TRACKS
            let token = proManager.authToken
            do {
                let kmpResult = try await PlaylistConverterEngine.companion.shared.convertPlaylistOrThrow(
                    url: item.sourceUrl,
                    targetPlatformKey: targetKey,
                    isPro: proManager.isPro,
                    maxTracks: maxTracks,
                    authToken: token.isEmpty ? nil : token,
                    forceRefresh: true
                )
                let targetLink = kmpResult.zeroOAuthUrl ?? kmpResult.webShareUrl ?? item.sourceUrl
                let displayTotal = max(Int(kmpResult.totalTracks), Int(kmpResult.originalTotalTracks))
                await MainActor.run {
                    history.updateItem(
                        id: item.id,
                        newTargetUrl: targetLink,
                        newTitle: kmpResult.title.isEmpty ? item.title : kmpResult.title,
                        newArtist: String(format: LocalizationManager.string(for: "history_playlist_tracks_summary", lang: lang), Int(kmpResult.matchedCount), displayTotal),
                        newTargetPlatform: "\(targetKey)_playlist",
                        isAlbum: false,
                        thumbnailUrl: kmpResult.thumbnailUrl ?? item.thumbnailUrl
                    )
                }
            } catch {
                // Graceful fallback
            }
        } else {
            let engine = SongLinkEngine.shared
            let rawTarget = item.targetPlatform.replacingOccurrences(of: "_playlist", with: "")
            let targetKey = rawTarget.isEmpty ? settings.targetPlatform : rawTarget
            do {
                let res = try await engine.resolveTargetUrl(
                    inputUrl: item.sourceUrl,
                    targetPlatformKey: targetKey,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    isPro: proManager.isPro,
                    authToken: proManager.authToken,
                    isPrefetch: false,
                    forceRefresh: true,
                    audiophileMode: proManager.isPro && settings.audiophileMode
                )
                if let success = res as? ResolutionResult.Success {
                    await MainActor.run {
                        history.updateItem(
                            id: item.id,
                            newTargetUrl: success.targetUrl,
                            newTitle: success.title,
                            newArtist: success.artist,
                            newTargetPlatform: targetKey,
                            isAlbum: success.isAlbum,
                            thumbnailUrl: success.thumbnailUrl
                        )
                    }
                }
            } catch {
                // Graceful fallback
            }
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
                    let maxLimit = proManager.isPro ? 100 : 10
                    List {
                        Section(header: VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                let headerText: String = {
                                    if proManager.isPro {
                                        return String(format: LocalizationManager.string(for: "history_capacity_pro", lang: lang), history.items.count, maxLimit)
                                    } else if history.totalHistoryCount > 10 {
                                        return String(format: LocalizationManager.string(for: "history_cached_count_free_capped", lang: lang), history.totalHistoryCount)
                                    } else {
                                        return String(format: LocalizationManager.string(for: "history_capacity_free", lang: lang), history.items.count, maxLimit)
                                    }
                                }()
                                Text(headerText)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                Spacer()
                                if !proManager.isPro {
                                    Button(action: {
                                        AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "history_header"])
                                        showingPaywallSheet = true
                                    }) {
                                        Text("💎 PRO")
                                            .font(.caption2)
                                            .fontWeight(.bold)
                                            .foregroundColor(.green)
                                    }
                                }
                            }

                            if favoriteCount > 0 || acousticCount > 0 {
                                HStack(spacing: 8) {
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                        filterMode = .all
                                    }) {
                                        Text(LocalizationManager.string(for: "history_filter_all", lang: lang))
                                            .font(.caption.weight(.semibold))
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(filterMode == .all ? Color.green.opacity(0.2) : Color(uiColor: .secondarySystemGroupedBackground))
                                            .foregroundColor(filterMode == .all ? .green : .secondary)
                                            .cornerRadius(16)
                                    }
                                    .buttonStyle(.plain)

                                    if favoriteCount > 0 {
                                        Button(action: {
                                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                            filterMode = .favorites
                                        }) {
                                            HStack(spacing: 4) {
                                                Image(systemName: "star.fill")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.yellow)
                                                Text("\(LocalizationManager.string(for: "history_filter_favorites", lang: lang)) (\(favoriteCount))")
                                                    .font(.caption.weight(.semibold))
                                            }
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(filterMode == .favorites ? Color.green.opacity(0.2) : Color(uiColor: .secondarySystemGroupedBackground))
                                            .foregroundColor(filterMode == .favorites ? .green : .secondary)
                                            .cornerRadius(16)
                                        }
                                        .buttonStyle(.plain)
                                    }

                                    if acousticCount > 0 {
                                        Button(action: {
                                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                            filterMode = .acoustic
                                        }) {
                                            HStack(spacing: 4) {
                                                Text("🎙️ \(LocalizationManager.string(for: "badge_identified", lang: lang)) (\(acousticCount))")
                                                    .font(.caption.weight(.semibold))
                                            }
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(filterMode == .acoustic ? Color.green.opacity(0.2) : Color(uiColor: .secondarySystemGroupedBackground))
                                            .foregroundColor(filterMode == .acoustic ? .green : .secondary)
                                            .cornerRadius(16)
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                                .textCase(nil)
                            }
                        }) {
                        ForEach(displayedItems) { item in
                            let isPlaylist = item.targetPlatform.contains("_playlist")
                            let cleanPlatform = item.targetPlatform.replacingOccurrences(of: "_playlist", with: "")
                            Button(action: {
                                if isPlaylist, let onOpenPlaylist = onOpenPlaylist {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    onOpenPlaylist(item.sourceUrl, cleanPlatform)
                                } else {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    settings.openResolvedTarget(
                                        targetUrl: item.targetUrl,
                                        nativeAppUri: nil,
                                        platformKey: cleanPlatform,
                                        title: item.title,
                                        artist: item.artist
                                    )
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

                                        // Subtle Play Indicator Badge (1-tap quick playback to player)
                                        Button(action: {
                                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                            settings.openResolvedTarget(
                                                targetUrl: item.targetUrl,
                                                nativeAppUri: nil,
                                                platformKey: cleanPlatform,
                                                title: item.title,
                                                artist: item.artist
                                            )
                                        }) {
                                            Image(systemName: "play.circle.fill")
                                                .font(.system(size: 14))
                                                .foregroundColor(.white)
                                                .background(Circle().fill(Color.black.opacity(0.4)).frame(width: 14, height: 14))
                                                .offset(x: 2, y: 2)
                                        }
                                        .buttonStyle(.plain)
                                    }

                                    // 2. Song & Artist Info
                                    VStack(alignment: .leading, spacing: 3) {
                                        HStack(spacing: 5) {
                                            if item.isFavorite {
                                                Image(systemName: "star.fill")
                                                    .font(.system(size: 11))
                                                    .foregroundColor(.yellow)
                                            }
                                            Text(item.title)
                                                .font(.system(size: 15, weight: .bold))
                                                .foregroundColor(.primary)
                                                .lineLimit(1)
                                        }

                                        HStack(spacing: 4) {
                                            if item.isAcoustic {
                                                HStack(spacing: 3) {
                                                    Image(systemName: "waveform")
                                                        .font(.system(size: 9, weight: .bold))
                                                    Text(LocalizationManager.string(for: "badge_identified", lang: lang))
                                                        .font(.system(size: 10, weight: .bold))
                                                }
                                                .foregroundColor(.green)
                                                .padding(.horizontal, 5)
                                                .padding(.vertical, 1.5)
                                                .background(Color.green.opacity(0.16))
                                                .cornerRadius(4)
                                            }

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

                                    // 3. Favorite Star Toggle Button
                                    Button(action: {
                                        toggleFavorite(for: item)
                                    }) {
                                        Image(systemName: item.isFavorite ? "star.fill" : "star")
                                            .font(.system(size: 16, weight: .medium))
                                            .foregroundColor(item.isFavorite ? .yellow : .secondary.opacity(0.6))
                                            .frame(width: 28, height: 28)
                                            .contentShape(Rectangle())
                                    }
                                    .buttonStyle(.plain)

                                    // 4. Right Action: Overflow Menu (⋮)
                                    Menu {
                                        // 0. Favorite toggle
                                        Button {
                                            toggleFavorite(for: item)
                                        } label: {
                                            Label(
                                                LocalizationManager.string(for: item.isFavorite ? "favorite_remove" : "favorite_add", lang: lang),
                                                systemImage: item.isFavorite ? "star.slash" : "star"
                                            )
                                        }

                                        // 1. Share FlipPage
                                        if proManager.isPro, let shareUrl = universalShareUrl(for: item) {
                                            ShareLink(item: shareUrl, message: Text(item.title)) {
                                                Label(LocalizationManager.string(for: "share_universal_link", lang: lang), systemImage: "square.and.arrow.up")
                                            }
                                        } else {
                                            Button {
                                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                                AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "history_universal_share"])
                                                showingPaywallSheet = true
                                            } label: {
                                                Label("\(LocalizationManager.string(for: "share_universal_link", lang: lang)) 💎", systemImage: "square.and.arrow.up")
                                            }
                                        }

                                        // 2. QR Code (nur bei Pro sichtbar!)
                                        if proManager.isPro, let shareUrl = universalShareUrl(for: item) {
                                            Button {
                                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                                qrCodePayload = QRCodePayload(url: shareUrl.absoluteString, title: item.title, artist: item.artist)
                                            } label: {
                                                Label(LocalizationManager.string(for: "history_qr_action", lang: lang), systemImage: "qrcode")
                                            }
                                        }

                                        // 3. Copy target link
                                        Button {
                                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                            UIPasteboard.general.string = item.targetUrl
                                        } label: {
                                            Label(LocalizationManager.string(for: "action_copy", lang: lang), systemImage: "doc.on.doc")
                                        }

                                        // 4. Copy source link
                                        Button {
                                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                            UIPasteboard.general.string = item.sourceUrl
                                        } label: {
                                            Label(LocalizationManager.string(for: "history_copy_source_generic", lang: lang), systemImage: "link")
                                        }

                                        // 5. Refresh link
                                        Button {
                                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                            Task {
                                                await refreshLink(for: item)
                                            }
                                        } label: {
                                            Label(LocalizationManager.string(for: "action_refresh_link", lang: lang), systemImage: "arrow.clockwise")
                                        }

                                        // 6. Disambiguate (if Pro)
                                        if !isPlaylist {
                                            Button {
                                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                                if proManager.isPro {
                                                    disambiguateItem = item
                                                } else {
                                                    AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "history_disambiguate_pro"])
                                                    showingPaywallSheet = true
                                                }
                                            } label: {
                                                Label(LocalizationManager.string(for: "disambiguate_action", lang: lang), systemImage: "slider.horizontal.3")
                                            }
                                        }

                                        if isPlaylist, let onOpenPlaylist = onOpenPlaylist {
                                            Button {
                                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                                onOpenPlaylist(item.sourceUrl, cleanPlatform)
                                            } label: {
                                                Label(LocalizationManager.string(for: "history_open_playlist_details", lang: lang), systemImage: "music.note.list")
                                            }

                                            Button {
                                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                                onOpenPlaylist(item.sourceUrl, settings.targetPlatform)
                                            } label: {
                                                Label(LocalizationManager.string(for: "playlist_reconvert", lang: lang), systemImage: "arrow.triangle.2.circlepath")
                                            }
                                        }

                                        Divider()

                                        // 7. Delete
                                        Button(role: .destructive) {
                                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                            history.deleteItem(id: item.id)
                                        } label: {
                                            Label(LocalizationManager.string(for: "action_delete", lang: lang), systemImage: "trash")
                                        }
                                    } label: {
                                        if refreshingItemId == item.id {
                                            ProgressView()
                                                .progressViewStyle(CircularProgressViewStyle())
                                                .scaleEffect(0.8)
                                                .frame(width: 32, height: 32)
                                        } else {
                                            Image(systemName: "ellipsis")
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundColor(.secondary)
                                                .frame(width: 32, height: 32)
                                                .contentShape(Rectangle())
                                        }
                                    }
                                }
                                .padding(.vertical, 4)
                            }
                            .listRowBackground(Color("CardBackgroundColor"))
                            .swipeActions(edge: .leading, allowsFullSwipe: false) {
                                Button {
                                    toggleFavorite(for: item)
                                } label: {
                                    Label(
                                        LocalizationManager.string(for: item.isFavorite ? "favorite_remove" : "favorite_add", lang: lang),
                                        systemImage: item.isFavorite ? "star.slash" : "star.fill"
                                    )
                                }
                                .tint(.yellow)

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
                                    toggleFavorite(for: item)
                                } label: {
                                    Label(
                                        LocalizationManager.string(for: item.isFavorite ? "favorite_remove" : "favorite_add", lang: lang),
                                        systemImage: item.isFavorite ? "star.slash" : "star"
                                    )
                                }

                                if isPlaylist, let onOpenPlaylist = onOpenPlaylist {
                                    Button {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        onOpenPlaylist(item.sourceUrl, cleanPlatform)
                                    } label: {
                                        Label(LocalizationManager.string(for: "history_open_playlist_details", lang: lang), systemImage: "music.note.list")
                                    }

                                    Button {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        onOpenPlaylist(item.sourceUrl, settings.targetPlatform)
                                    } label: {
                                        Label(LocalizationManager.string(for: "playlist_reconvert", lang: lang), systemImage: "arrow.triangle.2.circlepath")
                                    }
                                }

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
                                    Label(LocalizationManager.string(for: "history_copy_source_generic", lang: lang), systemImage: "link")
                                }

                                Button {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    Task {
                                        await refreshLink(for: item)
                                    }
                                } label: {
                                    Label(LocalizationManager.string(for: "action_refresh_link", lang: lang), systemImage: "arrow.clockwise")
                                }

                                if !isPlaylist {
                                    Button {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        if proManager.isPro {
                                            disambiguateItem = item
                                        } else {
                                            AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "history_disambiguate_pro"])
                                            showingPaywallSheet = true
                                        }
                                    } label: {
                                        Label(LocalizationManager.string(for: "disambiguate_action", lang: lang), systemImage: "slider.horizontal.3")
                                    }
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

                        if !proManager.isPro && (history.items.count >= 10 || history.totalHistoryCount > 10) {
                            Button(action: {
                                AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "history_teaser_bottom"])
                                showingPaywallSheet = true
                            }) {
                                HStack(spacing: 12) {
                                    Text("💎")
                                        .font(.title2)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(LocalizationManager.string(for: "history_pro_teaser_title", lang: lang))
                                            .font(.subheadline)
                                            .fontWeight(.bold)
                                            .foregroundColor(.primary)
                                        let hiddenCount = max(0, history.totalHistoryCount - 10)
                                        let descText: String = {
                                            if hiddenCount > 0 {
                                                return String(format: LocalizationManager.string(for: "history_pro_teaser_hidden_desc", lang: lang), hiddenCount, history.totalHistoryCount)
                                            } else {
                                                return LocalizationManager.string(for: "history_pro_teaser_desc", lang: lang)
                                            }
                                        }()
                                        Text(descText)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Text(LocalizationManager.string(for: "history_btn_upgrade", lang: lang))
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.white)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 6)
                                        .background(Color.green)
                                        .cornerRadius(8)
                                }
                                .padding(.vertical, 6)
                            }
                            .listRowBackground(Color.green.opacity(0.12))
                        }
                    }
                    .scrollContentBackground(.hidden)
                }
            }
        }
        .navigationTitle(LocalizationManager.string(for: "history_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if !history.items.isEmpty {
                    ToolbarItem(placement: .navigationBarLeading) {
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
            .alert(
                "💎 SongFlip PRO",
                isPresented: $showingFavoriteLimitAlert
            ) {
                Button(LocalizationManager.string(for: "history_btn_upgrade", lang: lang)) {
                    showingPaywallSheet = true
                }
                Button(LocalizationManager.string(for: "btn_cancel", lang: lang), role: .cancel) {}
            } message: {
                Text(LocalizationManager.string(for: "favorite_limit_reached", lang: lang))
            }
            .sheet(isPresented: $showingPaywallSheet) {
                ProPaywallSheetView()
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(item: $disambiguateItem) { targetItem in
                DisambiguationSheetView(item: targetItem, onMatchUpdated: { _ in
                    history.loadHistory()
                })
                .environmentObject(settings)
                .preferredColorScheme(settings.colorScheme)
            }
            .sheet(item: $qrCodePayload) { payload in
                QRCodeSheetView(url: payload.url, title: payload.title, artist: payload.artist, lang: lang)
                    .preferredColorScheme(settings.colorScheme)
            }
            .preferredColorScheme(settings.colorScheme)
            .onAppear {
                history.loadHistory()
            }
        }
    }

    @ViewBuilder
    private func fallbackCoverView(for item: HistoryItem) -> some View {
        let cleanPlatform = item.targetPlatform
            .replacingOccurrences(of: "_playlist", with: "")
            .replacingOccurrences(of: "_search", with: "")
            .replacingOccurrences(of: "_artist", with: "")
            .replacingOccurrences(of: "_fallback", with: "")
        ZStack {
            RoundedRectangle(cornerRadius: 8)
                .fill(platformColor(for: cleanPlatform).opacity(0.18))
                .frame(width: 48, height: 48)

            Image(systemName: item.isAlbum ? "opticaldisc" : platformIcon(for: cleanPlatform))
                .font(.system(size: 20))
                .foregroundColor(platformColor(for: cleanPlatform))
        }
    }

    private func universalShareUrl(for item: HistoryItem) -> URL? {
        let isPlaylist = item.targetPlatform.contains("_playlist")
        if isPlaylist {
            let cleanPlatform = item.targetPlatform.replacingOccurrences(of: "_playlist", with: "")
            let key = "\(item.sourceUrl.trimmingCharacters(in: .whitespacesAndNewlines))|\(cleanPlatform)"
            let digest = SHA256.hash(data: Data(key.utf8))
            let hex = digest.map { String(format: "%02x", $0) }.joined()
            let shortId = String(hex.prefix(10))
            return URL(string: "https://songflip.link/p/\(shortId)")
        } else {
            let normalized = UrlUtils.shared.normalizeUrl(url: item.sourceUrl).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let digest = SHA256.hash(data: Data(normalized.utf8))
            let hex = digest.map { String(format: "%02x", $0) }.joined()
            let shortId = String(hex.prefix(12))
            return URL(string: "https://songflip.link/s/\(shortId)")
        }
    }
}
