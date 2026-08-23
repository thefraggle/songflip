import SwiftUI
import SongFlipKit

struct ContentView: View {
    @EnvironmentObject var settings: SettingsModel
    @State private var inputUrl: String = ""
    @State private var statusMessage: String? = nil
    @State private var showingSettingsSheet = false

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor", bundle: nil)
                    .background(Color.black)
                    .ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Header
                        VStack(spacing: 6) {
                            Image(systemName: "music.note.list")
                                .resizable()
                                .scaledToFit()
                                .frame(width: 48, height: 48)
                                .foregroundColor(.green)
                                .padding(.top, 16)

                            Text("SongFlip")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)

                            Text("Universeller Musik-Link Redirector")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                        }

                        // Target Selector Card with symmetric buttons
                        VStack(alignment: .leading, spacing: 14) {
                            Text("STANDARD-ZIELDIENST")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.gray)

                            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                                ForEach(PlatformChoice.allCases) { platform in
                                    let isSelected = settings.targetPlatform == platform.rawValue
                                    Button(action: {
                                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                        settings.targetPlatform = platform.rawValue
                                    }) {
                                        HStack(spacing: 10) {
                                            Image(systemName: platform.iconName)
                                                .font(.system(size: 18))
                                                .frame(width: 24, height: 24)
                                                .foregroundColor(isSelected ? .green : .white)

                                            Text(platform.displayName)
                                                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                                .lineLimit(1)
                                                .minimumScaleFactor(0.8)
                                                .foregroundColor(.white)

                                            Spacer(minLength: 0)

                                            if isSelected {
                                                Image(systemName: "checkmark.circle.fill")
                                                    .font(.system(size: 15))
                                                    .foregroundColor(.green)
                                            }
                                        }
                                        .padding(.horizontal, 12)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 54)
                                        .background(isSelected ? Color.green.opacity(0.15) : Color.white.opacity(0.06))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(isSelected ? Color.green.opacity(0.5) : Color.clear, lineWidth: 1)
                                        )
                                        .cornerRadius(12)
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

                        // Footer Section
                        VStack(spacing: 8) {
                            HStack(spacing: 12) {
                                Link("Website", destination: URL(string: "https://songflip.link")!)
                                Text("•").foregroundColor(.gray)
                                Link("Datenschutz", destination: URL(string: "https://songflip.link/privacy-policy.html")!)
                                Text("•").foregroundColor(.gray)
                                Link("Impressum", destination: URL(string: "https://songflip.link/imprint.html")!)
                            }
                            .font(.caption)
                            .foregroundColor(.gray)

                            Text("SongFlip v1.1.2 • © 2026 Daniel Notthoff")
                                .font(.caption2)
                                .foregroundColor(Color(white: 0.4))
                        }
                        .padding(.top, 10)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .navigationTitle("SongFlip")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { showingSettingsSheet = true }) {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.white)
                    }
                }
            }
            .sheet(isPresented: $showingSettingsSheet) {
                SettingsSheetView()
                    .environmentObject(settings)
            }
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
