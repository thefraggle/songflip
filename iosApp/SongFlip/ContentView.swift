import SwiftUI
import SongFlipKit

struct ContentView: View {
    @EnvironmentObject var settings: SettingsModel
    @State private var inputUrl: String = ""
    @State private var statusMessage: String? = nil
    @State private var showingCopiedAlert = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 6) {
                            Image(systemName: "music.note.list")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 48, height: 48)
                                .foregroundColor(.accentColor)
                                .padding(.top, 16)

                            Text("SongFlip")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)

                            Text("Universeller Musik-Link Redirector")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                        }

                        // Target Selector Card
                        VStack(alignment: .leading, spacing: 14) {
                            Text("STANDARD-ZIELDIENST")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.gray)

                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                                ForEach(PlatformChoice.allCases) { platform in
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        settings.targetPlatform = platform.rawValue
                                    }) {
                                        HStack {
                                            Image(systemName: platform.iconName)
                                            Text(platform.displayName)
                                                .font(.system(size: 14, weight: .medium))
                                            Spacer()
                                            if settings.targetPlatform == platform.rawValue {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .foregroundColor(.green)
                                            }
                                        }
                                        .padding(12)
                                        .background(settings.targetPlatform == platform.rawValue ? Color.white.opacity(0.15) : Color.white.opacity(0.06))
                                        .cornerRadius(12)
                                        .foregroundColor(.white)
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        // Manual Test & Convert Box
                        VStack(alignment: .leading, spacing: 12) {
                            Text("TEST-STUDIO")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.gray)

                            HStack {
                                TextField("Musik-Link einfügen...", text: $inputUrl)
                                    .textFieldStyle(PlainTextFieldStyle())
                                    .padding(12)
                                    .background(Color.white.opacity(0.08))
                                    .cornerRadius(10)
                                    .foregroundColor(.white)
                                    .autocapitalization(.none)
                                    .disableAutocorrection(true)

                                Button(action: convertLink) {
                                    if settings.isResolving {
                                        ProgressView()
                                            .progressViewStyle(CircularProgressViewStyle(tint: .black))
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 12)
                                            .background(Color.white)
                                            .cornerRadius(10)
                                    } else {
                                        Text("Flip")
                                            .fontWeight(.bold)
                                            .foregroundColor(.black)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 12)
                                            .background(Color.white)
                                            .cornerRadius(10)
                                    }
                                }
                                .disabled(inputUrl.trimmingCharacters(in: .whitespaces).isEmpty || settings.isResolving)
                            }

                            if let status = statusMessage {
                                Text(status)
                                    .font(.footnote)
                                    .foregroundColor(.yellow)
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        // iOS How-to & Shortcuts Card
                        VStack(alignment: .leading, spacing: 12) {
                            Text("WIE FUNKTIONIERT ES UNTER IOS?")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.gray)

                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "square.and.arrow.up")
                                    .font(.title2)
                                    .foregroundColor(.blue)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("1. Über das Teilen-Menü")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.white)
                                    Text("Tippe in Spotify, Apple Music oder YouTube auf Teilen ➔ SongFlip für den sofortigen Sprung.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }

                            Divider().background(Color.white.opacity(0.1))

                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "button.programmable")
                                    .font(.title2)
                                    .foregroundColor(.orange)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("2. Action Button & Kurzbefehle")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.white)
                                    Text("Lege den SongFlip Kurzbefehl auf deinen iPhone 15/16 Action Button.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        Spacer(minLength: 30)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .navigationTitle("SongFlip")
            .navigationBarTitleDisplayMode(.inline)
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                checkClipboard()
            }
        }
    }

    private func checkClipboard() {
        guard settings.autoClipboardDetect, let clip = UIPasteboard.general.string else { return }
        let clean = UrlUtils.shared.extractCleanUrl(rawInput: clip)
        if let clean = clean, clean != inputUrl, clean.contains("spotify.com") || clean.contains("apple.com") || clean.contains("youtube.com") || clean.contains("deezer.com") || clean.contains("tidal.com") || clean.contains("amazon.com") {
            inputUrl = clean
            statusMessage = "Link aus Zwischenablage erkannt!"
        }
    }

    private func convertLink() {
        guard !inputUrl.isEmpty else { return }
        settings.isResolving = true
        statusMessage = "Löse Link auf..."

        Task {
            let res = try? await settings.engine.resolveTargetUrl(
                inputUrl: inputUrl,
                targetPlatformKey: settings.targetPlatform,
                customApiUrl: settings.customApiUrl,
                customApiToken: settings.customApiToken
            )

            await MainActor.run {
                settings.isResolving = false
                if let success = res as? ResolutionResult.Success {
                    statusMessage = "Gefunden: \(success.title ?? "Song") (\(success.artist ?? ""))"
                    let target = success.nativeAppUri ?? success.targetUrl
                    if let url = URL(string: target) {
                        UIApplication.shared.open(url)
                    }
                } else if let error = res as? ResolutionResult.Error {
                    statusMessage = "Fehler: \(error.message)"
                } else {
                    statusMessage = "Konnte Link nicht auflösen."
                }
            }
        }
    }
}
