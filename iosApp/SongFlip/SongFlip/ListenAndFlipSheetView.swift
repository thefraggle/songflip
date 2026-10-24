import SwiftUI
import SongFlipKit

struct ListenAndFlipSheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var recognizer = AcousticRecognizer.shared
    @ObservedObject var proManager = ProManager.shared
    @ObservedObject var history = HistoryModel.shared
    @Environment(\.dismiss) var dismiss

    var onOpenPaywall: () -> Void

    @State private var isFavorited = false
    @State private var showingFavoriteLimitAlert = false
    @State private var pulseScale: CGFloat = 1.0
    @State private var showingQuickPicker = false
    @State private var pickerUrl: String? = nil
    @State private var isResolvingAlternative = false

    private var lang: String { settings.selectedLanguage }

    private var targetDisplayName: String {
        PlatformChoice(rawValue: settings.targetPlatform)?.displayName ?? settings.targetPlatform
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 22) {
                        headerSection

                        switch recognizer.state {
                        case .idle:
                            listeningSection(amplitude: 0.15, elapsedSeconds: 0)
                        case let .listening(amplitude, elapsedSeconds):
                            listeningSection(amplitude: amplitude, elapsedSeconds: elapsedSeconds)
                        case let .resolving(title, artist, artworkUrl):
                            resolvingSection(title: title, artist: artist, artworkUrl: artworkUrl)
                        case let .matched(flipResult):
                            matchedResultCard(result: flipResult)
                        case let .offlineQueued(pendingCount):
                            offlineSavedSection(pendingCount: pendingCount)
                        case .noMatch:
                            noMatchSection
                        case .permissionDenied:
                            permissionDeniedSection
                        case .quotaReached:
                            quotaReachedSection
                        case let .error(messageKey):
                            errorSection(messageKey: messageKey)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 16)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        recognizer.stopListening()
                        dismiss()
                    }) {
                        Text(LocalizationManager.string(for: "btn_close", lang: lang))
                            .fontWeight(.semibold)
                            .foregroundColor(.green)
                    }
                }
            }
            .alert(
                LocalizationManager.string(for: "pro_title", lang: lang),
                isPresented: $showingFavoriteLimitAlert
            ) {
                Button(LocalizationManager.string(for: "listen_unlock_pro", lang: lang)) {
                    recognizer.stopListening()
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        onOpenPaywall()
                    }
                }
                Button(LocalizationManager.string(for: "btn_cancel", lang: lang), role: .cancel) {}
            } message: {
                Text(LocalizationManager.string(for: "favorite_limit_reached", lang: lang))
            }
            .onAppear {
                isFavorited = false
                recognizer.startListening(
                    targetPlatformKey: settings.targetPlatform,
                    isPro: proManager.isPro
                )
            }
            .onDisappear {
                recognizer.stopListening()
            }
            .sheet(isPresented: $showingQuickPicker) {
                if let pUrl = pickerUrl {
                    QuickTargetPickerSheetView(
                        url: pUrl,
                        lang: lang,
                        onSelectTarget: { chosenPlatform in
                            showingQuickPicker = false
                            isResolvingAlternative = true
                            Task {
                                let res = try? await settings.engine.resolveTargetUrl(
                                    inputUrl: pUrl,
                                    targetPlatformKey: chosenPlatform,
                                    customApiUrl: settings.customApiUrl,
                                    customApiToken: settings.customApiToken,
                                    isPro: proManager.isPro,
                                    authToken: proManager.authToken,
                                    isPrefetch: false,
                                    forceRefresh: false,
                                    audiophileMode: proManager.isPro && settings.audiophileMode
                                )
                                await MainActor.run {
                                    isResolvingAlternative = false
                                    if let success = res as? ResolutionResult.Success {
                                        settings.openResolvedTarget(
                                            targetUrl: success.targetUrl,
                                            nativeAppUri: success.nativeAppUri,
                                            platformKey: chosenPlatform,
                                            title: success.title,
                                            artist: success.artist
                                        )
                                    } else {
                                        let fallbackUrl = UrlUtils.shared.toWebFallbackUrl(rawUrl: pUrl, targetPlatformKey: chosenPlatform)
                                        settings.openResolvedTarget(
                                            targetUrl: fallbackUrl,
                                            nativeAppUri: nil,
                                            platformKey: chosenPlatform,
                                            title: nil,
                                            artist: nil
                                        )
                                    }
                                }
                            }
                        },
                        onDismiss: {
                            showingQuickPicker = false
                        }
                    )
                    .preferredColorScheme(settings.colorScheme)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Header

    private var headerSection: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Image(systemName: "waveform")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.green)

                Text(LocalizationManager.string(for: "listen_and_flip_title", lang: lang))
                    .font(.title3.weight(.bold))
                    .foregroundColor(.primary)
            }

            if !proManager.isPro {
                let remaining = recognizer.getRemainingFreeScans(isPro: false)
                let maxFree = Int(AcousticQuotaManager.companion.MAX_FREE_SCANS_PER_MONTH)
                Text(String(
                    format: LocalizationManager.string(for: "listen_free_scans_remaining", lang: lang),
                    remaining,
                    maxFree
                ))
                .font(.caption.weight(.semibold))
                .foregroundColor(.secondary)
                .padding(.horizontal, 12)
                .padding(.vertical, 5)
                .background(Color.secondary.opacity(0.12))
                .cornerRadius(12)
            }
        }
    }

    // MARK: - Listening State

    private func listeningSection(amplitude: Float, elapsedSeconds: Int) -> some View {
        VStack(spacing: 20) {
            ZStack {
                Circle()
                    .fill(Color.green.opacity(0.10))
                    .frame(width: 170, height: 170)
                    .scaleEffect(pulseScale * CGFloat(1.0 + amplitude * 0.35))
                    .animation(.easeInOut(duration: 0.25), value: amplitude)

                Circle()
                    .fill(Color.green.opacity(0.18))
                    .frame(width: 128, height: 128)
                    .scaleEffect(CGFloat(1.0 + amplitude * 0.22))
                    .animation(.easeInOut(duration: 0.2), value: amplitude)

                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.green, Color.green.opacity(0.75)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 88, height: 88)
                    .shadow(color: Color.green.opacity(0.4), radius: 12, x: 0, y: 4)

                Image(systemName: "waveform")
                    .font(.system(size: 36, weight: .bold))
                    .foregroundColor(.black)
            }
            .padding(.vertical, 12)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.1).repeatForever(autoreverses: true)) {
                    pulseScale = 1.12
                }
            }

            VStack(spacing: 6) {
                Text(LocalizationManager.string(for: "listen_status_listening", lang: lang))
                    .font(.headline)
                    .foregroundColor(.primary)

                let subtitleKey = (elapsedSeconds >= 3 && amplitude < 0.20) ? "listen_status_hint" : "listen_and_flip_subtitle"
                Text(LocalizationManager.string(for: subtitleKey, lang: lang))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button(action: {
                recognizer.stopListening()
                dismiss()
            }) {
                Text(LocalizationManager.string(for: "btn_cancel", lang: lang))
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 10)
                    .background(Color.secondary.opacity(0.12))
                    .cornerRadius(12)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Resolving State

    private func resolvingSection(title: String, artist: String, artworkUrl: String?) -> some View {
        VStack(spacing: 16) {
            if let artworkUrl = artworkUrl, let url = URL(string: artworkUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let img):
                        img.resizable().scaledToFill()
                    default:
                        Color.secondary.opacity(0.2)
                    }
                }
                .frame(width: 96, height: 96)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            }

            Text(title)
                .font(.headline.weight(.bold))
                .multilineTextAlignment(.center)

            if !artist.isEmpty {
                Text(artist)
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
            }

            HStack(spacing: 10) {
                ProgressView()
                Text(LocalizationManager.string(for: "listen_status_resolving", lang: lang))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            .padding(.top, 4)
        }
        .padding(.vertical, 20)
    }

    // MARK: - Matched Result Card (No Auto-Play!)

    private func matchedResultCard(result: AcousticFlipResult) -> some View {
        VStack(spacing: 16) {
            // Confirmation Pill: Saved to History
            HStack(spacing: 6) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 14))
                    .foregroundColor(.green)
                Text("🎙️ \(LocalizationManager.string(for: "listen_saved_to_history", lang: lang))")
                    .font(.caption.weight(.bold))
                    .foregroundColor(.green)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(Color.green.opacity(0.15))
            .cornerRadius(20)

            // Artwork + Title + Artist Card
            VStack(spacing: 14) {
                if let artStr = result.artworkUrl, let artUrl = URL(string: artStr) {
                    AsyncImage(url: artUrl) { phase in
                        switch phase {
                        case .success(let image):
                            image.resizable().scaledToFill()
                        default:
                            ZStack {
                                Color.green.opacity(0.15)
                                Image(systemName: "music.note")
                                    .font(.system(size: 36))
                                    .foregroundColor(.green)
                            }
                        }
                    }
                    .frame(width: 132, height: 132)
                    .clipShape(RoundedRectangle(cornerRadius: 18))
                    .shadow(color: .black.opacity(0.2), radius: 10, x: 0, y: 4)
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: 18)
                            .fill(Color.green.opacity(0.15))
                            .frame(width: 132, height: 132)
                        Image(systemName: "music.note")
                            .font(.system(size: 40, weight: .bold))
                            .foregroundColor(.green)
                    }
                }

                VStack(spacing: 4) {
                    Text(result.title)
                        .font(.title3.weight(.bold))
                        .foregroundColor(.primary)
                        .multilineTextAlignment(.center)
                        .lineLimit(2)

                    if !result.artist.isEmpty {
                        Text(result.artist)
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .lineLimit(2)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .background(Color(uiColor: .secondarySystemBackground))
            .cornerRadius(20)

            // Primary Action: Open in Target Player
            Button(action: {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                settings.openResolvedTarget(
                    targetUrl: result.targetUrl,
                    nativeAppUri: nil,
                    platformKey: settings.targetPlatform,
                    title: result.title,
                    artist: result.artist
                )
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "play.fill")
                    Text(String(
                        format: LocalizationManager.string(for: "listen_open_in_player", lang: lang),
                        targetDisplayName
                    ))
                    .fontWeight(.bold)
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 50)
                .background(Color.green)
                .cornerRadius(14)
            }
            .buttonStyle(.plain)

            // Switch / Change Target Player Action
            Button(action: {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                pickerUrl = result.sourceUrl
                showingQuickPicker = true
            }) {
                HStack(spacing: 6) {
                    if isResolvingAlternative {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .green))
                            .scaleEffect(0.8)
                    } else {
                        Image(systemName: "arrow.triangle.branch")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    Text(LocalizationManager.string(for: "listen_change_target", lang: lang))
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundColor(.green)
                .padding(.vertical, 2)
            }
            .buttonStyle(.plain)
            .disabled(isResolvingAlternative)

            // Secondary Row: Favorite + Share
            HStack(spacing: 12) {
                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    Task {
                        let toggleRes = try? await SongLinkEngine.shared.cache.toggleFavoriteWithLimit(
                            cacheKey: result.cacheKey,
                            isPro: proManager.isPro,
                            maxFreeFavorites: 10
                        )
                        await history.syncFromKmpStore()
                        await MainActor.run {
                            if toggleRes == .added {
                                isFavorited = true
                            } else if toggleRes == .removed {
                                isFavorited = false
                            } else if toggleRes == .limitReached {
                                showingFavoriteLimitAlert = true
                            }
                        }
                    }
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: isFavorited ? "star.fill" : "star")
                            .foregroundColor(isFavorited ? .yellow : .primary)
                        Text(LocalizationManager.string(
                            for: isFavorited ? "favorite_remove" : "favorite_add",
                            lang: lang
                        ))
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    }
                    .padding(.horizontal, 8)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 46)
                    .background(Color(uiColor: .secondarySystemBackground))
                    .cornerRadius(12)
                }
                .buttonStyle(.plain)

                if let shareUrl = URL(string: result.targetUrl) {
                    ShareLink(item: shareUrl, message: Text("\(result.title) – \(result.artist)")) {
                        HStack(spacing: 6) {
                            Image(systemName: "square.and.arrow.up")
                            Text(LocalizationManager.string(for: "listen_share_action", lang: lang))
                                .font(.subheadline.weight(.semibold))
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        .padding(.horizontal, 8)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 46)
                        .background(Color(uiColor: .secondarySystemBackground))
                        .cornerRadius(12)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Identify another song
            Button(action: {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                isFavorited = false
                recognizer.startListening(
                    targetPlatformKey: settings.targetPlatform,
                    isPro: proManager.isPro
                )
            }) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.clockwise")
                    Text(LocalizationManager.string(for: "listen_scan_again", lang: lang))
                        .font(.subheadline.weight(.semibold))
                }
                .foregroundColor(.green)
                .padding(.vertical, 8)
            }
            .buttonStyle(.plain)
        }
        .onAppear {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        }
    }

    // MARK: - Offline Club Mode Queued

    private func offlineSavedSection(pendingCount: Int) -> some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.green.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: "icloud.and.arrow.up.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.green)
            }

            Text(LocalizationManager.string(for: "listen_offline_saved_title", lang: lang))
                .font(.headline.weight(.bold))
                .multilineTextAlignment(.center)

            Text(String(
                format: LocalizationManager.string(for: "listen_offline_saved_desc", lang: lang),
                pendingCount
            ))
            .font(.subheadline)
            .foregroundColor(.secondary)
            .multilineTextAlignment(.center)

            Button(action: {
                dismiss()
            }) {
                Text(LocalizationManager.string(for: "btn_done", lang: lang))
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 48)
                    .background(Color.green)
                    .cornerRadius(14)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
    }

    // MARK: - No Match

    private var noMatchSection: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.orange.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: "waveform.slash")
                    .font(.system(size: 30))
                    .foregroundColor(.orange)
            }

            Text(LocalizationManager.string(for: "listen_no_match_title", lang: lang))
                .font(.headline.weight(.bold))
                .multilineTextAlignment(.center)

            Text(LocalizationManager.string(for: "listen_no_match_subtitle", lang: lang))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button(action: {
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                recognizer.startListening(
                    targetPlatformKey: settings.targetPlatform,
                    isPro: proManager.isPro
                )
            }) {
                HStack(spacing: 8) {
                    Image(systemName: "arrow.clockwise")
                    Text(LocalizationManager.string(for: "listen_try_again", lang: lang))
                        .fontWeight(.bold)
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Color.green)
                .cornerRadius(14)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
    }

    // MARK: - Permission Denied

    private var permissionDeniedSection: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.red.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: "mic.slash.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.red)
            }

            Text(LocalizationManager.string(for: "listen_permission_title", lang: lang))
                .font(.headline.weight(.bold))
                .multilineTextAlignment(.center)

            Text(LocalizationManager.string(for: "listen_permission_desc", lang: lang))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            Button(action: {
                if let settingsUrl = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(settingsUrl)
                }
            }) {
                Text(LocalizationManager.string(for: "listen_permission_grant", lang: lang))
                    .fontWeight(.bold)
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 48)
                    .background(Color.green)
                    .cornerRadius(14)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
    }

    // MARK: - Quota Reached

    private var quotaReachedSection: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.green.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: "lock.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.green)
            }

            Text(LocalizationManager.string(for: "listen_quota_reached_title", lang: lang))
                .font(.headline.weight(.bold))
                .multilineTextAlignment(.center)

            Text(String(
                format: LocalizationManager.string(for: "listen_quota_reached_desc", lang: lang),
                Int(AcousticQuotaManager.companion.MAX_FREE_SCANS_PER_MONTH)
            ))
            .font(.subheadline)
            .foregroundColor(.secondary)
            .multilineTextAlignment(.center)

            Button(action: {
                AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "listen_quota_reached"])
                dismiss()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    onOpenPaywall()
                }
            }) {
                HStack(spacing: 8) {
                    Text("💎")
                    Text(LocalizationManager.string(for: "listen_unlock_pro", lang: lang))
                        .fontWeight(.bold)
                }
                .foregroundColor(.black)
                .frame(maxWidth: .infinity)
                .frame(minHeight: 48)
                .background(Color.green)
                .cornerRadius(14)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 12)
    }

    // MARK: - Error

    private func errorSection(messageKey: String) -> some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.orange.opacity(0.15))
                    .frame(width: 72, height: 72)
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 30))
                    .foregroundColor(.orange)
            }

            Text(LocalizationManager.string(for: messageKey, lang: lang))
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)

            if messageKey == "listen_error_offline" && !proManager.isPro {
                Button(action: {
                    AptabaseClient.shared.trackEvent(eventName: "paywall_viewed", props: ["source": "listen_offline_club_mode"])
                    dismiss()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        onOpenPaywall()
                    }
                }) {
                    HStack(spacing: 8) {
                        Text("💎")
                        Text(LocalizationManager.string(for: "listen_unlock_pro", lang: lang))
                            .fontWeight(.bold)
                    }
                    .foregroundColor(.black)
                    .frame(maxWidth: .infinity)
                    .frame(minHeight: 48)
                    .background(Color.green)
                    .cornerRadius(14)
                }
                .buttonStyle(.plain)
            } else {
                Button(action: {
                    recognizer.startListening(
                        targetPlatformKey: settings.targetPlatform,
                        isPro: proManager.isPro
                    )
                }) {
                    Text(LocalizationManager.string(for: "listen_try_again", lang: lang))
                        .fontWeight(.bold)
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: 48)
                        .background(Color.green)
                        .cornerRadius(14)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 12)
    }
}
