import SwiftUI
import SongFlipKit
import CryptoKit

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared

    @State private var detectedClipboardUrl: String? = nil
    @State private var dismissedClipboardUrl: String? = nil
    @State private var showingSettingsSheet = false
    @State private var showingHistorySheet = false
    @State private var showingShortcutsGuide = false
    @State private var showingShareGuide = false
    @State private var showingPlaylistNotice = false
    @State private var playlistNoticeUrl: String? = nil

    var lang: String { settings.selectedLanguage }
    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.2.4"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 18) {
                        // 1. Header Banner
                        VStack(spacing: 6) {
                            ZStack {
                                Circle()
                                    .fill(
                                        LinearGradient(
                                            colors: [Color.green.opacity(0.3), Color.blue.opacity(0.15)],
                                            startPoint: .topLeading,
                                            endPoint: .bottomTrailing
                                        )
                                    )
                                    .frame(width: 68, height: 68)

                                Image(systemName: "music.note.list")
                                    .font(.system(size: 32, weight: .bold))
                                    .foregroundColor(.green)
                            }
                            .padding(.top, 8)

                            Text(LocalizationManager.string(for: "app_name", lang: lang))
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.primary)

                            Text(LocalizationManager.string(for: "app_tagline", lang: lang))
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }

                        // 1.5 Clipboard Smart-Banner (when music link is copied)
                        if let detectedUrl = detectedClipboardUrl {
                            let isPlaylist = UrlUtils.shared.isPlaylistUrl(url: detectedUrl)
                            VStack(alignment: .leading, spacing: 10) {
                                HStack {
                                    Image(systemName: isPlaylist ? "music.note.list" : "doc.on.clipboard.fill")
                                        .foregroundColor(isPlaylist ? .orange : .green)
                                        .font(.system(size: 16))
                                    Text(LocalizationManager.string(for: isPlaylist ? "playlist_dialog_title" : "clipboard_banner_title", lang: lang))
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                        .foregroundColor(.primary)
                                    Spacer()
                                    Button(action: {
                                        withAnimation(.easeInOut(duration: 0.2)) {
                                            dismissedClipboardUrl = detectedUrl
                                            detectedClipboardUrl = nil
                                        }
                                    }) {
                                        Image(systemName: "xmark")
                                            .font(.system(size: 12, weight: .bold))
                                            .foregroundColor(.secondary)
                                            .padding(4)
                                    }
                                }

                                HStack(spacing: 8) {
                                    Text(detectSourcePlatformName(url: detectedUrl))
                                        .font(.caption2)
                                        .fontWeight(.semibold)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background((isPlaylist ? Color.orange : Color.green).opacity(0.15))
                                        .foregroundColor(isPlaylist ? .orange : .green)
                                        .cornerRadius(6)

                                    if isPlaylist {
                                        Text(LocalizationManager.string(for: "playlist_badge", lang: lang))
                                            .font(.caption2)
                                            .fontWeight(.bold)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color.orange.opacity(0.2))
                                            .foregroundColor(.orange)
                                            .cornerRadius(6)
                                    }

                                    Text(detectedUrl)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                }

                                if isPlaylist {
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        playlistNoticeUrl = detectedUrl
                                        showingPlaylistNotice = true
                                    }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "arrow.up.right")
                                                .font(.system(size: 12))
                                            Text(String(format: LocalizationManager.string(for: "playlist_banner_action_open", lang: lang), detectSourcePlatformName(url: detectedUrl)))
                                                .font(.system(size: 13, weight: .bold))
                                                .lineLimit(1)
                                        }
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 12)
                                        .frame(maxWidth: .infinity)
                                        .background(Color.orange)
                                        .foregroundColor(.white)
                                        .cornerRadius(10)
                                    }
                                } else {
                                    let targetChoice = PlatformChoice.allCases.first { $0.rawValue == settings.targetPlatform }
                                    let targetName = targetChoice?.displayName ?? "Player"
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        convertLink(urlToConvert: detectedUrl)
                                    }) {
                                        HStack(spacing: 6) {
                                            Image(systemName: "play.fill")
                                                .font(.system(size: 12))
                                            Text(String(format: LocalizationManager.string(for: "clipboard_banner_action_open", lang: lang), targetName))
                                                .font(.system(size: 13, weight: .bold))
                                                .lineLimit(1)
                                        }
                                        .padding(.vertical, 8)
                                        .padding(.horizontal, 12)
                                        .frame(maxWidth: .infinity)
                                        .background(Color.green)
                                        .foregroundColor(.white)
                                        .cornerRadius(10)
                                    }
                                }
                            }
                            .padding(14)
                            .background((isPlaylist ? Color.orange : Color.green).opacity(0.12))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke((isPlaylist ? Color.orange : Color.green).opacity(0.35), lineWidth: 1)
                            )
                            .cornerRadius(16)
                            .transition(.opacity.combined(with: .scale(scale: 0.95)))
                        }

                        // 2. Preferred Target Player Card (2x3 Grid with Brand Accents)
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text(LocalizationManager.string(for: "target_service_label", lang: lang).uppercased())
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .foregroundColor(.secondary)

                                Spacer()

                                Text(LocalizationManager.string(for: "one_tap_selection", lang: lang))
                                    .font(.caption2)
                                    .foregroundColor(.green)
                            }

                            LazyVGrid(
                                columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)],
                                spacing: 10
                            ) {
                                ForEach(PlatformChoice.allCases) { platform in
                                    let isSelected = settings.targetPlatform == platform.rawValue
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        settings.targetPlatform = platform.rawValue
                                        AptabaseClient.shared.trackTargetPlatformChanged(target: platform.rawValue)
                                    }) {
                                        HStack(spacing: 10) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 8)
                                                    .fill(platform.brandColor.opacity(isSelected ? 0.35 : 0.15))
                                                    .frame(width: 32, height: 32)

                                                Image(systemName: platform.iconName)
                                                    .font(.system(size: 15))
                                                    .foregroundColor(platform.brandColor)
                                            }

                                            Text(platform.displayName)
                                                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                                .lineLimit(1)
                                                .minimumScaleFactor(0.8)
                                                .foregroundColor(.primary)

                                            Spacer(minLength: 0)

                                            if isSelected {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .font(.system(size: 16))
                                                    .foregroundColor(.green)
                                            }
                                        }
                                        .padding(.horizontal, 10)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 52)
                                        .background(isSelected ? platform.brandColor.opacity(0.15) : Color(uiColor: .secondarySystemGroupedBackground))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(isSelected ? platform.brandColor : Color(uiColor: .separator).opacity(0.3), lineWidth: isSelected ? 1.5 : 1)
                                        )
                                        .cornerRadius(12)
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .background(Color("CardBackgroundColor"))
                        .cornerRadius(16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(uiColor: .separator).opacity(0.25), lineWidth: 1)
                        )

                        // 3. iOS Setup & How-To Card
                        VStack(alignment: .leading, spacing: 12) {
                            Text(LocalizationManager.string(for: "how_it_works_ios", lang: lang))
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.secondary)

                            // 1. Share Sheet Action
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showingShareGuide = true
                            }) {
                                HStack(alignment: .center, spacing: 12) {
                                    Image(systemName: "square.and.arrow.up.fill")
                                        .font(.title3)
                                        .foregroundColor(.blue)
                                        .frame(width: 28)

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(LocalizationManager.string(for: "step1_title", lang: lang))
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .foregroundColor(.primary)
                                        Text(LocalizationManager.string(for: "step1_desc", lang: lang))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .multilineTextAlignment(.leading)
                                    }

                                    Spacer(minLength: 4)

                                    Image(systemName: "info.circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(.blue)
                                }
                            }
                            .buttonStyle(.plain)

                            Divider()

                            // 2. Action Button & Shortcuts Action
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                showingShortcutsGuide = true
                            }) {
                                HStack(alignment: .center, spacing: 12) {
                                    Image(systemName: "button.programmable")
                                        .font(.title3)
                                        .foregroundColor(.orange)
                                        .frame(width: 28)

                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(LocalizationManager.string(for: "step2_title", lang: lang))
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                            .foregroundColor(.primary)
                                        Text(LocalizationManager.string(for: "step2_desc", lang: lang))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                            .multilineTextAlignment(.leading)
                                    }

                                    Spacer(minLength: 4)

                                    Image(systemName: "info.circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(.orange)
                                }
                            }
                            .buttonStyle(.plain)

                            Divider()

                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "doc.on.clipboard.fill")
                                    .font(.title3)
                                    .foregroundColor(.green)
                                    .frame(width: 28)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(LocalizationManager.string(for: "step3_title", lang: lang))
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.primary)
                                    Text(LocalizationManager.string(for: "step3_desc", lang: lang))
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color("CardBackgroundColor"))
                        .cornerRadius(16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(Color(uiColor: .separator).opacity(0.25), lineWidth: 1)
                        )

                        // 4. Recent History Preview Card (if history exists)
                        if !history.items.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text(LocalizationManager.string(for: "recent_songs", lang: lang))
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.secondary)

                                    Spacer()

                                    Button(action: { showingHistorySheet = true }) {
                                        HStack(spacing: 4) {
                                            Text(LocalizationManager.string(for: "show_all", lang: lang))
                                            Image(systemName: "chevron.right")
                                        }
                                        .font(.caption)
                                        .foregroundColor(.green)
                                    }
                                }

                                ForEach(history.items.prefix(3)) { item in
                                    Button(action: {
                                        if let url = URL(string: item.targetUrl) {
                                            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                            UIApplication.shared.open(url)
                                        }
                                    }) {
                                        HStack(spacing: 12) {
                                            Image(systemName: platformIcon(for: item.targetPlatform))
                                                .font(.system(size: 16))
                                                .foregroundColor(platformColor(for: item.targetPlatform))
                                                .frame(width: 28, height: 28)
                                                .background(platformColor(for: item.targetPlatform).opacity(0.15))
                                                .cornerRadius(6)

                                            VStack(alignment: .leading, spacing: 2) {
                                                Text(item.title)
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundColor(.primary)
                                                    .lineLimit(1)

                                                if let artist = item.artist, !artist.isEmpty {
                                                    Text(artist)
                                                        .font(.caption2)
                                                        .foregroundColor(.secondary)
                                                        .lineLimit(1)
                                                }
                                            }

                                            Spacer()

                                            Image(systemName: "arrow.up.forward.app")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                        .padding(.vertical, 4)
                                    }
                                }
                            }
                            .padding(16)
                            .background(Color("CardBackgroundColor"))
                            .cornerRadius(16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color(uiColor: .separator).opacity(0.25), lineWidth: 1)
                            )
                        }

                        // 6. Footer Section
                        VStack(spacing: 6) {
                            Text("SongFlip v\(appVersion)")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.primary)

                            Text("© 2026 Daniel Notthoff")
                                .font(.caption2)
                                .foregroundColor(.secondary)

                            HStack(spacing: 10) {
                                Link(LocalizationManager.string(for: "legal_privacy", lang: lang), destination: URL(string: "https://songflip.link/privacy-policy.html")!)
                                Text("•").foregroundColor(.secondary.opacity(0.6))
                                Link(LocalizationManager.string(for: "legal_imprint", lang: lang), destination: URL(string: "https://songflip.link/imprint.html")!)
                                Text("•").foregroundColor(.secondary.opacity(0.6))
                                Link(LocalizationManager.string(for: "legal_terms", lang: lang), destination: URL(string: "https://songflip.link/terms.html")!)
                            }
                            .font(.caption)
                            .foregroundColor(.secondary)
                        }
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: {
                        AptabaseClient.shared.trackHistoryOpened()
                        showingHistorySheet = true
                    }) {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundColor(.primary)
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        AptabaseClient.shared.trackSettingsOpened()
                        showingSettingsSheet = true
                    }) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.primary)
                    }
                }
            }
            .sheet(isPresented: $showingSettingsSheet) {
                SettingsSheetView()
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingHistorySheet) {
                HistorySheetView()
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingShortcutsGuide) {
                ShortcutsGuideSheet(lang: lang)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingShareGuide) {
                ShareGuideSheet(lang: lang)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingPlaylistNotice) {
                if let pUrl = playlistNoticeUrl {
                    PlaylistNoticeSheetView(
                        playlistUrl: pUrl,
                        platformName: detectSourcePlatformName(url: pUrl),
                        dismissAction: { showingPlaylistNotice = false }
                    )
                    .preferredColorScheme(settings.colorScheme)
                }
            }
            .onAppear {
                AptabaseClient.shared.trackAppLaunched(platform: "iOS", language: lang)
                history.loadHistory()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    checkClipboard()
                }
            }
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .active {
                    history.loadHistory()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        checkClipboard()
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
                history.loadHistory()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    checkClipboard()
                }
            }
        }
    }

    private func detectSourcePlatformName(url: String) -> String {
        let lower = url.lowercased()
        if lower.contains("spotify.com") || lower.contains("spotify.link") { return "Spotify" }
        if lower.contains("apple.com") || lower.contains("apple.co") { return "Apple Music" }
        if lower.contains("youtube.com") || lower.contains("youtu.be") { return "YouTube Music" }
        if lower.contains("deezer.com") || lower.contains("deezer.page.link") { return "Deezer" }
        if lower.contains("tidal.com") { return "Tidal" }
        if lower.contains("amazon.") || lower.contains("amzn.to") || lower.contains("a.co") { return "Amazon Music" }
        if lower.contains("soundcloud.com") || lower.contains("on.soundcloud.com") { return "SoundCloud" }
        if lower.contains("bandcamp.com") { return "Bandcamp" }
        if lower.contains("song.link") || lower.contains("album.link") || lower.contains("odesli.co") { return "Universal Link" }
        return "Music Link"
    }

    private func checkClipboard() {
        guard settings.autoClipboardDetect else {
            detectedClipboardUrl = nil
            return
        }
        let rawClip = UIPasteboard.general.string ?? UIPasteboard.general.url?.absoluteString
        guard let clip = rawClip, !clip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        let clean = UrlUtils.shared.extractCleanUrl(rawInput: clip) ?? clip
        let lower = clean.lowercased()
        let isMusic = lower.contains("spotify.com") ||
                      lower.contains("spotify.link") ||
                      lower.contains("apple.com") ||
                      lower.contains("apple.co") ||
                      lower.contains("youtube.com") ||
                      lower.contains("youtu.be") ||
                      lower.contains("deezer.com") ||
                      lower.contains("deezer.page.link") ||
                      lower.contains("tidal.com") ||
                      lower.contains("amazon.") ||
                      lower.contains("amzn.to") ||
                      lower.contains("a.co") ||
                      lower.contains("soundcloud.com") ||
                      lower.contains("on.soundcloud.com") ||
                      lower.contains("bandcamp.com") ||
                      lower.contains("song.link") ||
                      lower.contains("album.link") ||
                      lower.contains("odesli.co")

        if isMusic && clean != dismissedClipboardUrl {
            detectedClipboardUrl = clean
            Task {
                _ = try? await settings.engine.resolveTargetUrl(
                    inputUrl: clean,
                    targetPlatformKey: settings.targetPlatform,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    forceRefresh: false
                )
            }
        } else if !isMusic {
            detectedClipboardUrl = nil
        }
    }

    private func convertLink(urlToConvert: String) {
        guard !urlToConvert.isEmpty else { return }
        settings.isResolving = true

        Task {
            let res = try? await settings.engine.resolveTargetUrl(
                inputUrl: urlToConvert,
                targetPlatformKey: settings.targetPlatform,
                customApiUrl: settings.customApiUrl,
                customApiToken: settings.customApiToken
            )

            await MainActor.run {
                settings.isResolving = false
                if let success = res as? ResolutionResult.Success {
                    // Add to shared History
                    HistoryModel.shared.add(
                        title: success.title ?? LocalizationManager.string(for: "unknown_song", lang: lang),
                        artist: success.artist,
                        sourceUrl: urlToConvert,
                        targetUrl: success.targetUrl,
                        targetPlatform: settings.targetPlatform,
                        isAlbum: success.isAlbum
                    )

                    AptabaseClient.shared.trackLinkFlipped(
                        target: settings.targetPlatform,
                        isAlbum: success.isAlbum,
                        isSearch: false
                    )

                    let target = success.nativeAppUri ?? success.targetUrl
                    if let url = URL(string: target) {
                        UIApplication.shared.open(url)
                    }
                } else if let playlist = res as? ResolutionResult.Playlist {
                    AptabaseClient.shared.trackLinkFlipFailed(
                        target: settings.targetPlatform,
                        reason: "playlist_detected"
                    )
                    playlistNoticeUrl = playlist.originalUrl
                    showingPlaylistNotice = true
                } else if let error = res as? ResolutionResult.Error {
                    AptabaseClient.shared.trackLinkFlipFailed(
                        target: settings.targetPlatform,
                        reason: error.message
                    )
                } else {
                    AptabaseClient.shared.trackLinkFlipFailed(
                        target: settings.targetPlatform,
                        reason: "timeout_or_unknown"
                    )
                }
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

    private func platformColor(for key: String) -> Color {
        switch key.lowercased() {
        case "spotify": return Color(red: 0.11, green: 0.73, blue: 0.33)
        case "applemusic", "apple": return Color(red: 0.99, green: 0.24, blue: 0.27)
        case "youtubemusic", "youtube": return Color(red: 1.0, green: 0.0, blue: 0.0)
        case "deezer": return Color(red: 0.64, green: 0.22, blue: 1.0)
        case "tidal": return Color(red: 0.0, green: 0.9, blue: 0.9)
        case "amazonmusic", "amazon": return Color(red: 0.15, green: 0.82, blue: 0.85)
        case "soundcloud": return Color(red: 1.0, green: 0.33, blue: 0.0)
        case "bandcamp": return Color(red: 0.11, green: 0.63, blue: 0.76)
        default: return .green
        }
    }
}

struct ShortcutsGuideSheet: View {
    let lang: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    VStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .fill(Color.orange.opacity(0.15))
                                .frame(width: 64, height: 64)
                            Image(systemName: "button.programmable")
                                .font(.system(size: 30, weight: .bold))
                                .foregroundColor(.orange)
                        }
                        .padding(.top, 12)

                        Text(LocalizationManager.string(for: "shortcuts_guide_title", lang: lang))
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)

                        Text(LocalizationManager.string(for: "shortcuts_guide_subtitle", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }

                    VStack(spacing: 12) {
                        GuideStepCard(
                            stepNumber: "1",
                            iconName: "plus.circle.fill",
                            iconColor: .orange,
                            title: LocalizationManager.string(for: "shortcuts_step1_title", lang: lang),
                            description: LocalizationManager.string(for: "shortcuts_step1_desc", lang: lang)
                        )

                        GuideStepCard(
                            stepNumber: "2",
                            iconName: "square.stack.3d.up.fill",
                            iconColor: .blue,
                            title: LocalizationManager.string(for: "shortcuts_step2_title", lang: lang),
                            description: LocalizationManager.string(for: "shortcuts_step2_desc", lang: lang)
                        )

                        GuideStepCard(
                            stepNumber: "3",
                            iconName: "button.programmable",
                            iconColor: .green,
                            title: LocalizationManager.string(for: "shortcuts_step3_title", lang: lang),
                            description: LocalizationManager.string(for: "shortcuts_step3_desc", lang: lang)
                        )
                    }
                    .padding(.horizontal, 16)

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    }) {
                        Text(LocalizationManager.string(for: "btn_done", lang: lang))
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.accentColor)
                            .cornerRadius(14)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                }
                .padding(.bottom, 24)
            }
            .background(Color("BackgroundColor").ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                            .font(.title3)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

struct ShareGuideSheet: View {
    let lang: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    VStack(spacing: 8) {
                        ZStack {
                            Circle()
                                .fill(Color.blue.opacity(0.15))
                                .frame(width: 64, height: 64)
                            Image(systemName: "square.and.arrow.up.fill")
                                .font(.system(size: 30, weight: .bold))
                                .foregroundColor(.blue)
                        }
                        .padding(.top, 12)

                        Text(LocalizationManager.string(for: "share_guide_title", lang: lang))
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)

                        Text(LocalizationManager.string(for: "share_guide_subtitle", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }

                    VStack(spacing: 12) {
                        GuideStepCard(
                            stepNumber: "1",
                            iconName: "music.note",
                            iconColor: .blue,
                            title: LocalizationManager.string(for: "share_step1_title", lang: lang),
                            description: LocalizationManager.string(for: "share_step1_desc", lang: lang)
                        )

                        GuideStepCard(
                            stepNumber: "2",
                            iconName: "ellipsis.circle.fill",
                            iconColor: .orange,
                            title: LocalizationManager.string(for: "share_step2_title", lang: lang),
                            description: LocalizationManager.string(for: "share_step2_desc", lang: lang)
                        )

                        GuideStepCard(
                            stepNumber: "3",
                            iconName: "star.fill",
                            iconColor: .green,
                            title: LocalizationManager.string(for: "share_step3_title", lang: lang),
                            description: LocalizationManager.string(for: "share_step3_desc", lang: lang)
                        )
                    }
                    .padding(.horizontal, 16)

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        dismiss()
                    }) {
                        Text(LocalizationManager.string(for: "btn_done", lang: lang))
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color.accentColor)
                            .cornerRadius(14)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 10)
                }
                .padding(.bottom, 24)
            }
            .background(Color("BackgroundColor").ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                            .font(.title3)
                    }
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

struct GuideStepCard: View {
    let stepNumber: String
    let iconName: String
    let iconColor: Color
    let title: String
    let description: String

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Circle()
                    .fill(iconColor.opacity(0.15))
                    .frame(width: 36, height: 36)
                Image(systemName: iconName)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(iconColor)
            }

            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)

                Text(description)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .lineSpacing(2)
            }

            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color("CardBackgroundColor"))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(uiColor: .separator).opacity(0.25), lineWidth: 1)
        )
    }
}
