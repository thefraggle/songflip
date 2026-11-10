import SwiftUI
import SongFlipKit
import CryptoKit
import UniformTypeIdentifiers
import os

private let logger = Logger(subsystem: "de.goork.songflip", category: "ContentView")

struct ContentView: View {
    @Environment(\.scenePhase) private var scenePhase
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared
    @ObservedObject var proManager = ProManager.shared
    @ObservedObject var promoBannerManager = PromoBannerManager.shared
    @ObservedObject var activePlaylistModel = ActivePlaylistModel.shared

    @State private var detectedClipboardUrl: String? = nil
    @State private var dismissedClipboardUrl: String? = nil
    @State private var showingSettingsSheet = false
    @State private var showingSearchSheet = false
    @State private var showingListenSheet = false
    @State private var showingHistorySheet = false
    @State private var showingPaywallSheet = false
    @State private var showingShortcutsGuide = false
    @State private var showingShareGuide = false
    @State private var showingPlaylistNotice = false
    @State private var playlistNoticeUrl: String? = nil
    @State private var showingPlaylistFileImporter = false
    @State private var importedPlaylistFileUrl: URL? = nil
    @State private var showingPodcastNotice = false
    @State private var podcastNoticeUrl: String? = nil
    @State private var isPodcastNoticeAudiobook = false
    @State private var showingToast = false
    @State private var toastMessage: String? = nil
    @State private var activeMilestone: Int = 0
    @State private var showingQuickPicker = false
    @State private var quickPickerUrl: String? = nil

    var lang: String { settings.selectedLanguage }
    var appVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.5.0"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 18) {
                        headerBannerView

                        if let activePlaylist = activePlaylistModel.current {
                            ActivePlaylistBannerView(
                                activePlaylist: activePlaylist,
                                lang: lang,
                                isPro: proManager.isPro,
                                onDismiss: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        activePlaylistModel.clear()
                                    }
                                },
                                onOpenDetails: { url in
                                    playlistNoticeUrl = url
                                    showingPlaylistNotice = true
                                }
                            )
                        }

                        if let detectedUrl = detectedClipboardUrl {
                            ClipboardSmartBannerView(
                                detectedUrl: detectedUrl,
                                lang: lang,
                                targetPlatform: settings.targetPlatform,
                                isResolving: settings.isResolving,
                                askEveryTime: settings.askEveryTime,
                                onDismiss: {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        dismissedClipboardUrl = detectedUrl
                                        detectedClipboardUrl = nil
                                    }
                                },
                                onOpenPlaylist: { url in
                                    playlistNoticeUrl = url
                                    showingPlaylistNotice = true
                                },
                                onOpenAudiobook: { url in
                                    podcastNoticeUrl = url
                                    isPodcastNoticeAudiobook = true
                                    showingPodcastNotice = true
                                },
                                onConvert: { url in
                                    if settings.askEveryTime {
                                        quickPickerUrl = url
                                        showingQuickPicker = true
                                    } else {
                                        convertLink(urlToConvert: url)
                                    }
                                }
                            )
                        }

                        if promoBannerManager.isBannerVisible(isPro: proManager.isPro) {
                            PromoBannerCardView(
                                config: promoBannerManager.config,
                                lang: lang,
                                onClick: {
                                    AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: [
                                        "source": "promo_banner_\(promoBannerManager.config.campaignId)",
                                        "layout": "lifetime_first",
                                        "is_sale": "true"
                                    ])
                                    showingPaywallSheet = true
                                },
                                onDismiss: {
                                    withAnimation(.easeInOut(duration: 0.25)) {
                                        promoBannerManager.dismiss()
                                    }
                                }
                            )
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                        }

                        // PRO Upgrade Milestone Nudge Banner
                        if !proManager.isPro && activeMilestone > 0 {
                            ProNudgeBannerCardView(
                                milestone: activeMilestone,
                                lang: lang,
                                onLearnMore: {
                                    AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: [
                                        "source": "milestone_\(activeMilestone)_learn_more"
                                    ])
                                    showingPaywallSheet = true
                                },
                                onDismiss: {
                                    withAnimation(.easeInOut(duration: 0.25)) {
                                        settings.dismissProNudgeMilestone(activeMilestone)
                                        activeMilestone = 0
                                    }
                                }
                            )
                            .transition(.opacity.combined(with: .scale(scale: 0.96)))
                        }

                        platformPickerSection

                        GuideCardsSectionView(
                            lang: lang,
                            onOpenShareGuide: { showingShareGuide = true },
                            onOpenShortcutsGuide: { showingShortcutsGuide = true }
                        )

                        if !history.items.isEmpty {
                            recentSongsSection
                        }

                        footerSection
                    }
                    .padding(.horizontal, 16)
                }

                toastOverlayView
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: {
                        showingHistorySheet = true
                    }) {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundColor(.primary)
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        showingSettingsSheet = true
                    }) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.primary)
                    }
                }
            }
            .sheet(isPresented: $showingSearchSheet) {
                SearchSheetView(
                    onImportPlaylistFile: {
                        showingSearchSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            showingPlaylistFileImporter = true
                        }
                    },
                    onOpenPlaylistConversion: { pUrl in
                        showingSearchSheet = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            playlistNoticeUrl = pUrl
                            showingPlaylistNotice = true
                        }
                    }
                )
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingListenSheet) {
                ListenAndFlipSheetView(
                    onOpenPaywall: {
                        showingPaywallSheet = true
                    }
                )
                .environmentObject(settings)
                .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingSettingsSheet) {
                SettingsSheetView(onImportPlaylistFile: {
                    showingSettingsSheet = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                        showingPlaylistFileImporter = true
                    }
                })
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingHistorySheet) {
                HistorySheetView(onOpenPlaylist: { url, _ in
                    showingHistorySheet = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                        playlistNoticeUrl = url
                        showingPlaylistNotice = true
                    }
                })
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
            .sheet(isPresented: $showingPlaylistNotice, onDismiss: {
                importedPlaylistFileUrl = nil
            }) {
                if let pUrl = playlistNoticeUrl {
                    PlaylistConverterView(
                        playlistUrl: pUrl,
                        targetPlatformKey: settings.targetPlatform,
                        dismissAction: {
                            showingPlaylistNotice = false
                            importedPlaylistFileUrl = nil
                        },
                        onOpenPaywall: {
                            showingPlaylistNotice = false
                            importedPlaylistFileUrl = nil
                            showingPaywallSheet = true
                        },
                        importedFileUrl: importedPlaylistFileUrl
                    )
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
                }
            }
            .fileImporter(
                isPresented: $showingPlaylistFileImporter,
                allowedContentTypes: [.item],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let firstUrl = urls.first {
                        importedPlaylistFileUrl = firstUrl
                        playlistNoticeUrl = "https://songflip.link/file-import/\(firstUrl.lastPathComponent)"
                        showingPlaylistNotice = true
                    }
                case .failure(let error):
                    logger.warning("File import failed or cancelled: \(error.localizedDescription)")
                }
            }
            .sheet(isPresented: $showingPodcastNotice) {
                if let pUrl = podcastNoticeUrl {
                    PodcastNoticeSheetView(
                        url: pUrl,
                        platformName: detectSourcePlatformName(url: pUrl),
                        isAudiobook: isPodcastNoticeAudiobook,
                        dismissAction: { showingPodcastNotice = false }
                    )
                    .preferredColorScheme(settings.colorScheme)
                }
            }
            .sheet(isPresented: $showingPaywallSheet) {
                ProPaywallSheetView()
                    .environmentObject(settings)
                    .preferredColorScheme(settings.colorScheme)
            }
            .sheet(isPresented: $showingQuickPicker) {
                if let pUrl = quickPickerUrl ?? detectedClipboardUrl {
                    QuickTargetPickerSheetView(
                        url: pUrl,
                        lang: lang,
                        onSelectTarget: { chosenPlatform in
                            convertLink(urlToConvert: pUrl, overrideTarget: chosenPlatform)
                        },
                        onDismiss: {
                            showingQuickPicker = false
                        }
                    )
                    .preferredColorScheme(settings.colorScheme)
                }
            }
            .onAppear {
                AptabaseClient.shared.trackAppLaunched(platform: "iOS", language: lang)
                AppDelegate.updateQuickActions(lang: lang)
                consumePendingListenSheetIfNeeded()
                history.loadHistory()
                activeMilestone = settings.getActiveProNudgeMilestone()
                Task {
                    await promoBannerManager.fetchPromoBanner()
                    let resolved = await AcousticRecognizer.shared.processPendingOfflineScansIfNeeded(
                        targetPlatformKey: settings.targetPlatform,
                        isPro: proManager.isPro
                    )
                    if resolved > 0 {
                        showToast(LocalizationManager.plural(for: "listen_offline_resolved_toast", count: resolved, lang: lang))
                    }
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    checkClipboard()
                }
            }
            // Single foreground hook: a parallel didBecomeActive observer caused double reloads
            // and duplicate pasteboard access prompts (#116).
            .onChange(of: scenePhase) { newPhase in
                if newPhase == .active {
                    AppDelegate.updateQuickActions(lang: lang)
                    consumePendingListenSheetIfNeeded()
                    history.loadHistory()
                    activeMilestone = settings.getActiveProNudgeMilestone()
                    Task {
                        await promoBannerManager.fetchPromoBanner()
                        let resolved = await AcousticRecognizer.shared.processPendingOfflineScansIfNeeded(
                            targetPlatformKey: settings.targetPlatform,
                            isPro: proManager.isPro
                        )
                        if resolved > 0 {
                            showToast(LocalizationManager.plural(for: "listen_offline_resolved_toast", count: resolved, lang: lang))
                        }
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        checkClipboard()
                    }
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: .openListenAndFlip)) { _ in
                consumePendingListenSheetIfNeeded(force: true)
            }
            .onChange(of: settings.selectedLanguage) { _ in
                AppDelegate.updateQuickActions(lang: lang)
            }
        }
    }

    // MARK: - Subviews

    @ViewBuilder
    private var headerBannerView: some View {
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

            HStack(spacing: 8) {
                Text(LocalizationManager.string(for: "app_name", lang: lang))
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(.primary)

                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    if !proManager.isPro {
                        AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "header_pro_badge"])
                    }
                    showingPaywallSheet = true
                }) {
                    HStack(spacing: 3) {
                        if !proManager.isPro {
                            Image(systemName: "lock.fill")
                                .font(.system(size: 9, weight: .semibold))
                        }
                        Text("PRO")
                            .font(.system(size: 11, weight: proManager.isPro ? .black : .semibold))
                    }
                    .foregroundColor(proManager.isPro ? .green : Color.secondary.opacity(0.6))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(proManager.isPro ? Color.green.opacity(0.18) : Color.secondary.opacity(0.08))
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(proManager.isPro ? Color.green.opacity(0.6) : Color.secondary.opacity(0.22), lineWidth: 1)
                    )
                    .cornerRadius(6)
                }
                .buttonStyle(.plain)
            }

            Text(LocalizationManager.string(for: "app_tagline", lang: lang))
                .font(.subheadline)
                .foregroundColor(.secondary)

            // Prominent In-App Search Bar (#77) + Listen & Flip Action Button (#78)
            HStack(spacing: 10) {
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    showingSearchSheet = true
                }) {
                    HStack(spacing: 10) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 15))
                            .foregroundColor(.secondary)

                        Text(LocalizationManager.string(for: "search_hint", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .lineLimit(1)

                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 12)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)

                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    showingListenSheet = true
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "waveform")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.green)

                        Text(LocalizationManager.string(for: "listen_and_flip_title", lang: lang))
                            .font(.caption.weight(.bold))
                            .foregroundColor(.green)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 12)
                    .background(Color.green.opacity(0.15))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color.green.opacity(0.35), lineWidth: 1)
                    )
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 6)
        }
    }

    @ViewBuilder
    private var platformPickerSection: some View {
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
                        if settings.targetPlatform != platform.rawValue {
                            settings.targetPlatform = platform.rawValue
                            AptabaseClient.shared.trackTargetPlatformChanged(target: platform.rawValue)
                        }
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

            if settings.targetPlatform == "soundcloud" {
                Text(LocalizationManager.string(for: "soundcloud_target_hint", lang: lang))
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(red: 1.0, green: 0.33, blue: 0.0).opacity(0.10))
                    .cornerRadius(10)
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

    @ViewBuilder
    private var recentSongsSection: some View {
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
                let cleanPlatform = item.targetPlatform
                    .replacingOccurrences(of: "_playlist", with: "")
                    .replacingOccurrences(of: "_search", with: "")
                    .replacingOccurrences(of: "_artist", with: "")
                    .replacingOccurrences(of: "_fallback", with: "")
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
                    HStack(spacing: 12) {
                        Image(systemName: platformIcon(for: cleanPlatform))
                            .font(.system(size: 16))
                            .foregroundColor(platformColor(for: cleanPlatform))
                            .frame(width: 28, height: 28)
                            .background(platformColor(for: cleanPlatform).opacity(0.15))
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

    @ViewBuilder
    private var footerSection: some View {
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

    @ViewBuilder
    private var toastOverlayView: some View {
        if showingToast, let msg = toastMessage {
            VStack {
                Spacer()
                HStack(spacing: 8) {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(.orange)
                    Text(msg)
                        .font(.footnote)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                }
                .padding(.vertical, 10)
                .padding(.horizontal, 16)
                .background(Color.black.opacity(0.85))
                .cornerRadius(20)
                .padding(.bottom, 24)
                .transition(.move(edge: .bottom).combined(with: .opacity))
            }
            .zIndex(10)
        }
    }

    private func consumePendingListenSheetIfNeeded(force: Bool = false) {
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        if force || defaults.bool(forKey: "pending_open_listen_sheet") {
            defaults.set(false, forKey: "pending_open_listen_sheet")
            showingListenSheet = true
        }
    }

    private func checkClipboard() {
        guard settings.autoClipboardDetect else {
            detectedClipboardUrl = nil
            return
        }

        // Check if pasteboard has web URLs or strings without triggering system paste notification when possible
        guard UIPasteboard.general.hasStrings || UIPasteboard.general.hasURLs else {
            detectedClipboardUrl = nil
            return
        }

        let rawClip = UIPasteboard.general.string ?? UIPasteboard.general.url?.absoluteString
        guard let clip = rawClip, !clip.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return
        }
        let clean = UrlUtils.shared.extractCleanUrl(rawInput: clip) ?? clip
        let isMusic = UrlUtils.shared.isSupportedMusicOrAggregatorUrl(url: clean)

        if isMusic && clean != dismissedClipboardUrl {
            detectedClipboardUrl = clean
            // Privacy: Do not prefetch over the network unprompted; prefetch occurs upon user interaction
        } else if !isMusic {
            detectedClipboardUrl = nil
        }
    }

    private func convertLink(urlToConvert: String, overrideTarget: String? = nil) {
        guard !urlToConvert.isEmpty, !settings.isResolving else { return }
        if overrideTarget == nil && settings.askEveryTime {
            quickPickerUrl = urlToConvert
            showingQuickPicker = true
            return
        }
        settings.isResolving = true
        let effectiveTarget = overrideTarget ?? settings.targetPlatform

        Task {
            let res: ResolutionResult
            var bridgeErrorDetail: String? = nil
            do {
                res = try await settings.engine.resolveTargetUrl(
                    inputUrl: urlToConvert,
                    targetPlatformKey: effectiveTarget,
                    customApiUrl: settings.customApiUrl,
                    customApiToken: settings.customApiToken,
                    isPro: proManager.isPro,
                    authToken: proManager.authToken,
                    isPrefetch: false,
                    forceRefresh: false,
                    audiophileMode: proManager.isPro && settings.audiophileMode
                )
            } catch {
                let errInfo = FlipCoordinator.mapError(error)
                bridgeErrorDetail = errInfo.detail
                res = ResolutionResult.Error(message: errInfo.reason, isUnsupported: false, errorReason: errInfo.errorReason)
            }

            await MainActor.run {
                settings.isResolving = false
                let action = FlipCoordinator.resolveAction(result: res, targetPlatform: effectiveTarget)
                FlipCoordinator.trackAction(action, inputUrl: urlToConvert, targetPlatform: effectiveTarget, errorDetail: bridgeErrorDetail)

                switch action {
                case .openResolved(let targetUrl, let nativeAppUri, let platformKey, let title, let artist, let isAlbum, _, _, let thumb):
                    HistoryModel.shared.add(
                        title: title ?? LocalizationManager.string(for: "unknown_song", lang: lang),
                        artist: artist,
                        sourceUrl: urlToConvert,
                        targetUrl: targetUrl,
                        targetPlatform: platformKey,
                        isAlbum: isAlbum,
                        thumbnailUrl: thumb
                    )
                    settings.openResolvedTarget(
                        targetUrl: targetUrl,
                        nativeAppUri: nativeAppUri,
                        platformKey: platformKey,
                        title: title,
                        artist: artist
                    )
                    settings.incrementSuccessfulFlipCount()
                    activeMilestone = settings.getActiveProNudgeMilestone()
                    if activeMilestone == 0 {
                        ReviewManager.shared.maybeRequestReview(trigger: "flip_success", settings: settings)
                    }

                case .openPodcast(let targetUrl, let nativeAppUri, _):
                    let target = nativeAppUri ?? targetUrl
                    if let url = URL(string: target) {
                        UIApplication.shared.open(url)
                    }
                    dismissedClipboardUrl = urlToConvert
                    detectedClipboardUrl = nil

                case .showPlaylistNotice(let originalUrl):
                    playlistNoticeUrl = originalUrl
                    showingPlaylistNotice = true

                case .showPodcastNotice(let originalUrl, let isAudiobook):
                    podcastNoticeUrl = originalUrl
                    isPodcastNoticeAudiobook = isAudiobook
                    showingPodcastNotice = true

                case .unsupportedEntity(let originalUrl, let entityType):
                    if entityType == "unsupported_domain" {
                        showToast(LocalizationManager.string(for: "unsupported_domain_toast", lang: lang))
                    }
                    let targetUrlStr = originalUrl.isEmpty ? urlToConvert : originalUrl
                    if let original = URL(string: targetUrlStr) {
                        UIApplication.shared.open(original)
                    }
                    dismissedClipboardUrl = urlToConvert
                    detectedClipboardUrl = nil

                case .failure:
                    showToast(LocalizationManager.string(for: "redirect_error_toast", lang: lang))
                    if let original = URL(string: urlToConvert) {
                        UIApplication.shared.open(original)
                    }
                    dismissedClipboardUrl = urlToConvert
                    detectedClipboardUrl = nil
                }
            }
        }
    }

    private func showToast(_ message: String) {
        toastMessage = message
        withAnimation(.easeInOut(duration: 0.2)) {
            showingToast = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
            withAnimation(.easeInOut(duration: 0.2)) {
                showingToast = false
            }
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
                    .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
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
                    .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
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
