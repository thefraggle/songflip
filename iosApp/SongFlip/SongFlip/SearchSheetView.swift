import SwiftUI
import CryptoKit
import SongFlipKit

struct SearchTrackItem: Identifiable, Equatable {
    let id: String
    let title: String
    let artist: String
    let album: String?
    let coverUrl: String?
    let sourceUrl: String
    let durationSec: Int

    var formattedDuration: String {
        let mins = durationSec / 60
        let secs = durationSec % 60
        return String(format: "%d:%02d", mins, secs)
    }
}

struct SearchSheetView: View {
    var onImportPlaylistFile: (() -> Void)? = nil
    var onOpenPlaylistConversion: ((String) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var proManager = ProManager.shared

    @State private var searchQuery: String = ""
    @State private var searchResults: [SearchTrackItem] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var resolvingTrackId: String? = nil
    @State private var showFlipErrorAlert: Bool = false
    @State private var clipboardMusicUrl: String? = nil

    // Sheets
    @State private var showingPaywallSheet: Bool = false
    @State private var qrCodePayload: QRCodePayload? = nil
    @State private var pickerTrack: SearchTrackItem? = nil

    struct QRCodePayload: Identifiable {
        let id = UUID()
        let url: String
        let title: String
        let artist: String?
    }

    var lang: String { settings.selectedLanguage }

    private var targetPlatformName: String {
        PlatformChoice(rawValue: settings.targetPlatform)?.displayName ?? settings.targetPlatform
    }

    private func universalShareUrl(for sourceUrl: String) -> URL? {
        let normalized = UrlUtils.shared.normalizeUrl(url: sourceUrl).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let digest = SHA256.hash(data: Data(normalized.utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        let shortId = String(hex.prefix(12))
        return URL(string: "https://songflip.link/s/\(shortId)")
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
                            LocalizationManager.string(for: "search_hint_unified", lang: lang),
                            text: $searchQuery
                        )
                        .autocorrectionDisabled(true)
                        .textInputAutocapitalization(.never)
                        .submitLabel(.search)

                        if !searchQuery.isEmpty {
                            Button(action: {
                                searchQuery = ""
                                searchResults = []
                            }) {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(.secondary)
                            }
                            .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
                        } else if let clip = clipboardMusicUrl {
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                searchQuery = clip
                            }) {
                                Image(systemName: "doc.on.clipboard")
                                    .foregroundColor(Color("AccentColor"))
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                    .padding(.horizontal, 16)
                    .padding(.top, 12)

                    // Target Service Indicator Chip & Paste Chip
                    HStack(spacing: 8) {
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
                        }

                        if searchQuery.isEmpty, let clip = clipboardMusicUrl {
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                searchQuery = clip
                            }) {
                                HStack(spacing: 4) {
                                    Image(systemName: "doc.on.clipboard")
                                        .font(.system(size: 10))
                                    Text(LocalizationManager.string(for: "search_paste_clipboard_action", lang: lang))
                                        .font(.system(size: 11, weight: .medium))
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color.blue.opacity(0.12))
                                .foregroundColor(.blue)
                                .cornerRadius(6)
                            }
                        }

                        Spacer()
                    }
                    .padding(.horizontal, 16)

                    let trimmedQuery = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                    let isDirectMusicUrl = !trimmedQuery.isEmpty && UrlUtils.shared.isSupportedMusicOrAggregatorUrl(url: trimmedQuery)

                    if isDirectMusicUrl {
                        let isPlaylist = UrlUtils.shared.isPlaylistUrl(url: trimmedQuery)
                        let platformName = detectSourcePlatformName(url: trimmedQuery)

                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Image(systemName: "link")
                                    .foregroundColor(Color("AccentColor"))
                                Text(LocalizationManager.string(for: "search_link_detected_title", lang: lang))
                                    .font(.subheadline)
                                    .fontWeight(.bold)
                                Spacer()
                                Text(platformName)
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color("AccentColor").opacity(0.15))
                                    .foregroundColor(Color("AccentColor"))
                                    .cornerRadius(6)
                            }

                            Text(trimmedQuery)
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .lineLimit(2)

                            Button(action: {
                                flipDirectUrl(trimmedQuery)
                            }) {
                                HStack(spacing: 6) {
                                    if resolvingTrackId == "direct_url" {
                                        ProgressView()
                                            .tint(.white)
                                    } else {
                                        Image(systemName: isPlaylist ? "music.note.list" : "play.fill")
                                        Text(isPlaylist
                                             ? LocalizationManager.string(for: "search_flip_playlist_action", lang: lang)
                                             : String(format: LocalizationManager.string(for: "search_flip_track_action", lang: lang), targetPlatformName))
                                            .fontWeight(.bold)
                                    }
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(isPlaylist ? Color.orange : Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                            .disabled(resolvingTrackId == "direct_url")
                        }
                        .padding(14)
                        .background(Color("CardBackgroundColor"))
                        .cornerRadius(14)
                        .padding(.horizontal, 16)
                    }

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
                        VStack(spacing: 16) {
                            Image(systemName: "music.note")
                                .font(.system(size: 44))
                                .foregroundColor(Color("AccentColor").opacity(0.7))
                            Text(LocalizationManager.string(for: "search_initial_prompt", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)

                            if let onImport = onImportPlaylistFile {
                                Button(action: {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    onImport()
                                }) {
                                    HStack(spacing: 8) {
                                        Image(systemName: "doc.badge.plus")
                                        Text(LocalizationManager.string(for: "playlist_import_file_btn", lang: lang))
                                    }
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 10)
                                    .background(Color(uiColor: .secondarySystemBackground))
                                    .cornerRadius(12)
                                }
                                .buttonStyle(.plain)
                            }
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
                        .scrollDismissesKeyboard(.immediately)
                    }
                }
            }
            .navigationTitle(LocalizationManager.string(for: "search_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        dismiss()
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .foregroundColor(.secondary.opacity(0.7))
                    }
                    .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
                }
            }
            .onChange(of: searchQuery) { query in
                performSearch(query: query)
            }
            .onAppear {
                checkClipboard()
            }
            .alert(
                LocalizationManager.string(for: "redirect_error_toast", lang: lang),
                isPresented: $showFlipErrorAlert
            ) {
                Button("OK", role: .cancel) {}
            }
            .sheet(isPresented: $showingPaywallSheet) {
                ProPaywallSheetView()
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(item: $qrCodePayload) { payload in
                QRCodeSheetView(url: payload.url, title: payload.title, artist: payload.artist, lang: lang)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(item: $pickerTrack) { tr in
                QuickTargetPickerSheetView(
                    url: tr.sourceUrl,
                    lang: lang,
                    onSelectTarget: { pickedTarget in
                        pickerTrack = nil
                        flipTrack(tr, targetOverride: pickedTarget)
                    },
                    onDismiss: {
                        pickerTrack = nil
                    }
                )
                .preferredColorScheme(settings.colorScheme)
            }
        }
    }

    @ViewBuilder
    private func trackRow(track: SearchTrackItem) -> some View {
        let isResolving = resolvingTrackId == track.id
        let shareUrl = universalShareUrl(for: track.sourceUrl)

        HStack(spacing: 12) {
            // Cover Image with subtle Play Badge
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

                // Subtle Play Indicator Badge (unten rechts auf dem Cover)
                Image(systemName: "play.circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
                    .background(Circle().fill(Color.black.opacity(0.4)).frame(width: 14, height: 14))
                    .offset(x: 2, y: 2)
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
                // Option A: 3-Punkte-Aktionsmenü (⋮)
                Menu {
                    // 1. Teilen / FlipPage (mit 💎 bei Free)
                    if proManager.isPro, let validShareUrl = shareUrl {
                        ShareLink(item: validShareUrl, message: Text(track.title)) {
                            Label(LocalizationManager.string(for: "share_universal_link", lang: lang), systemImage: "square.and.arrow.up")
                        }
                    } else {
                        Button {
                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                            AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "search_universal_share"])
                            showingPaywallSheet = true
                        } label: {
                            Label("\(LocalizationManager.string(for: "share_universal_link", lang: lang)) 💎", systemImage: "square.and.arrow.up")
                        }
                    }

                    // 2. QR Code (nur bei Pro sichtbar!)
                    if proManager.isPro, let validShareUrl = shareUrl {
                        Button {
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                            qrCodePayload = QRCodePayload(url: validShareUrl.absoluteString, title: track.title, artist: track.artist)
                        } label: {
                            Label(LocalizationManager.string(for: "history_qr_action", lang: lang), systemImage: "qrcode")
                        }
                    }

                    // 3. In anderem Player öffnen
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        pickerTrack = track
                    } label: {
                        Label(LocalizationManager.string(for: "quick_picker_title", lang: lang), systemImage: "arrow.triangle.branch")
                    }

                    // 4. Link kopieren
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        UIPasteboard.general.string = track.sourceUrl
                    } label: {
                        Label(LocalizationManager.string(for: "action_copy", lang: lang), systemImage: "doc.on.doc")
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(.secondary)
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
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

    private func checkClipboard() {
        if UIPasteboard.general.hasStrings,
           let clipString = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines) {
            if UrlUtils.shared.isSupportedMusicOrAggregatorUrl(url: clipString) {
                clipboardMusicUrl = clipString
            }
        }
    }

    private func performSearch(query: String) {
        searchTask?.cancel()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if UrlUtils.shared.isSupportedMusicOrAggregatorUrl(url: trimmed) {
            searchResults = []
            isLoading = false
            return
        }
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

            do {
                let kmpResults = try await CatalogSearchEngine.companion.shared.searchOrThrow(
                    query: trimmed,
                    limit: 35
                )
                if Task.isCancelled { return }

                let items: [SearchTrackItem] = kmpResults.compactMap { r in
                    guard let sourceUrl = r.sourceUrl, !sourceUrl.isEmpty else { return nil }
                    let durationSec = Int((r.durationMs?.int64Value ?? 0) / 1000)
                    return SearchTrackItem(
                        id: r.id,
                        title: r.title,
                        artist: r.artist,
                        album: r.album,
                        coverUrl: r.coverUrl,
                        sourceUrl: sourceUrl,
                        durationSec: durationSec
                    )
                }

                await MainActor.run {
                    self.searchResults = items
                    self.isLoading = false
                    if items.isEmpty {
                        self.errorMessage = LocalizationManager.string(for: "search_empty", lang: lang)
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
    private func flipTrack(_ track: SearchTrackItem, targetOverride: String? = nil) {
        guard !track.sourceUrl.isEmpty else { return }
        let targetKey = targetOverride ?? settings.targetPlatform
        resolvingTrackId = track.id
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        Task {
            do {
                let res = try await SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl: track.sourceUrl,
                    targetPlatformKey: targetKey,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    isPro: proManager.isPro,
                    authToken: proManager.authToken,
                    isPrefetch: false,
                    forceRefresh: false,
                    audiophileMode: proManager.isPro && settings.audiophileMode
                )

                await MainActor.run {
                    resolvingTrackId = nil
                    if let success = res as? ResolutionResult.Success,
                       !success.targetUrl.isEmpty {
                        // Track in Aptabase
                        AptabaseClient.shared.trackLinkFlipped(
                            target: targetKey,
                            isAlbum: success.isAlbum,
                            isSearch: success.isSearchFallback,
                            source: "in_app_search"
                        )

                        settings.incrementSuccessfulFlipCount()

                        // Save to History
                        HistoryModel.shared.add(
                            title: success.title ?? track.title,
                            artist: success.artist ?? track.artist,
                            sourceUrl: track.sourceUrl,
                            targetUrl: success.targetUrl,
                            targetPlatform: targetKey,
                            isAlbum: success.isAlbum,
                            thumbnailUrl: success.thumbnailUrl ?? track.coverUrl
                        )

                        // Launch player (preferred client or native URI with web URL fallback) & dismiss
                        settings.openResolvedTarget(
                            targetUrl: success.targetUrl,
                            nativeAppUri: success.nativeAppUri,
                            platformKey: targetKey,
                            title: success.title ?? track.title,
                            artist: success.artist ?? track.artist
                        )
                        dismiss()
                    } else {
                        showFlipErrorAlert = true
                    }
                }
            } catch {
                await MainActor.run {
                    resolvingTrackId = nil
                    showFlipErrorAlert = true
                }
            }
        }
    }

    private func flipDirectUrl(_ url: String) {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if UrlUtils.shared.isPlaylistUrl(url: trimmed) {
            dismiss()
            onOpenPlaylistConversion?(trimmed)
            return
        }

        let targetKey = settings.targetPlatform
        resolvingTrackId = "direct_url"
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        Task {
            do {
                let res = try await SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl: trimmed,
                    targetPlatformKey: targetKey,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    isPro: proManager.isPro,
                    authToken: proManager.authToken,
                    isPrefetch: false,
                    forceRefresh: false,
                    audiophileMode: proManager.isPro && settings.audiophileMode
                )

                await MainActor.run {
                    resolvingTrackId = nil
                    if let success = res as? ResolutionResult.Success,
                       !success.targetUrl.isEmpty {
                        AptabaseClient.shared.trackLinkFlipped(
                            target: targetKey,
                            isAlbum: success.isAlbum,
                            isSearch: success.isSearchFallback,
                            source: "in_app_search_url"
                        )
                        settings.incrementSuccessfulFlipCount()
                        HistoryModel.shared.add(
                            title: success.title ?? "SongFlip Track",
                            artist: success.artist ?? "",
                            sourceUrl: trimmed,
                            targetUrl: success.targetUrl,
                            targetPlatform: targetKey,
                            isAlbum: success.isAlbum,
                            thumbnailUrl: success.thumbnailUrl
                        )
                        settings.openResolvedTarget(
                            targetUrl: success.targetUrl,
                            nativeAppUri: success.nativeAppUri,
                            platformKey: targetKey,
                            title: success.title,
                            artist: success.artist
                        )
                        dismiss()
                    } else {
                        showFlipErrorAlert = true
                    }
                }
            } catch {
                await MainActor.run {
                    resolvingTrackId = nil
                    showFlipErrorAlert = true
                }
            }
        }
    }
}
