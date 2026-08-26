import SwiftUI
import SongFlipKit

struct ContentView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared

    @State private var inputUrl: String = ""
    @State private var statusMessage: String? = nil
    @State private var statusSuccess: Bool = false
    @State private var showingSettingsSheet = false
    @State private var showingHistorySheet = false

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

                            Text("SongFlip")
                                .font(.system(size: 28, weight: .bold))
                                .foregroundColor(.white)

                            Text("Universeller Musik-Link Redirector")
                                .font(.subheadline)
                                .foregroundColor(.gray)
                        }

                        // 2. Preferred Target Player Card (2x3 Grid with Brand Accents)
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("STANDARD-ZIELDIENST")
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .foregroundColor(.gray)

                                Spacer()

                                Text("1-Tap Auswahl")
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
                                    }) {
                                        HStack(spacing: 10) {
                                            ZStack {
                                                RoundedRectangle(cornerRadius: 8)
                                                    .fill(platform.brandColor.opacity(isSelected ? 0.3 : 0.15))
                                                    .frame(width: 32, height: 32)

                                                Image(systemName: platform.iconName)
                                                    .font(.system(size: 15))
                                                    .foregroundColor(platform.brandColor)
                                            }

                                            Text(platform.displayName)
                                                .font(.system(size: 13, weight: isSelected ? .bold : .medium))
                                                .lineLimit(1)
                                                .minimumScaleFactor(0.8)
                                                .foregroundColor(.white)

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
                                        .background(isSelected ? Color.white.opacity(0.12) : Color.white.opacity(0.05))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12)
                                                .stroke(isSelected ? Color.green.opacity(0.6) : Color.white.opacity(0.06), lineWidth: isSelected ? 1.5 : 1)
                                        )
                                        .cornerRadius(12)
                                    }
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        // 3. iOS Setup & How-To Card
                        VStack(alignment: .leading, spacing: 12) {
                            Text("WIE FUNKTIONIERT ES UNTER IOS?")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.gray)

                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "square.and.arrow.up.fill")
                                    .font(.title3)
                                    .foregroundColor(.blue)
                                    .frame(width: 28)

                                VStack(alignment: .leading, spacing: 3) {
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
                                    .font(.title3)
                                    .foregroundColor(.orange)
                                    .frame(width: 28)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("2. Action Button & Kurzbefehle")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.white)
                                    Text("Lege den SongFlip Kurzbefehl auf deinen iPhone 15/16 Action Button für 1-Klick Konvertierung.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }

                            Divider().background(Color.white.opacity(0.1))

                            HStack(alignment: .top, spacing: 12) {
                                Image(systemName: "doc.on.clipboard.fill")
                                    .font(.title3)
                                    .foregroundColor(.green)
                                    .frame(width: 28)

                                VStack(alignment: .leading, spacing: 3) {
                                    Text("3. Automatische Zwischenablage")
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                        .foregroundColor(.white)
                                    Text("Kopierte Musik-Links werden beim Öffnen der App automatisch erkannt.")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        // 4. Test Studio Card
                        VStack(alignment: .leading, spacing: 12) {
                            Text("TEST-STUDIO")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(.gray)

                            HStack(spacing: 8) {
                                HStack {
                                    TextField("Musik-Link einfügen...", text: $inputUrl)
                                        .textFieldStyle(PlainTextFieldStyle())
                                        .foregroundColor(.white)
                                        .autocapitalization(.none)
                                        .disableAutocorrection(true)

                                    if !inputUrl.isEmpty {
                                        Button(action: { inputUrl = ""; statusMessage = nil }) {
                                            Image(systemName: "xmark.circle.fill")
                                                .foregroundColor(.gray)
                                        }
                                    } else {
                                        Button(action: pasteFromClipboard) {
                                            Image(systemName: "doc.on.clipboard")
                                                .foregroundColor(.green)
                                        }
                                    }
                                }
                                .padding(12)
                                .background(Color.white.opacity(0.08))
                                .cornerRadius(10)

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
                                            .padding(.horizontal, 18)
                                            .padding(.vertical, 12)
                                            .background(Color.green)
                                            .cornerRadius(10)
                                    }
                                }
                                .disabled(inputUrl.trimmingCharacters(in: .whitespaces).isEmpty || settings.isResolving)
                            }

                            if let status = statusMessage {
                                HStack(spacing: 6) {
                                    Image(systemName: statusSuccess ? "checkmark.circle.fill" : "info.circle.fill")
                                        .foregroundColor(statusSuccess ? .green : .orange)
                                    Text(status)
                                        .font(.footnote)
                                        .foregroundColor(statusSuccess ? .green : .orange)
                                        .lineLimit(2)
                                }
                                .padding(.top, 4)
                            }
                        }
                        .padding(16)
                        .background(Color(white: 0.1))
                        .cornerRadius(16)

                        // 5. Recent History Preview Card (if history exists)
                        if !history.items.isEmpty {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    Text("LETZTE SONGS")
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.gray)

                                    Spacer()

                                    Button(action: { showingHistorySheet = true }) {
                                        HStack(spacing: 4) {
                                            Text("Alle anzeigen")
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
                                                    .foregroundColor(.white)
                                                    .lineLimit(1)

                                                if let artist = item.artist, !artist.isEmpty {
                                                    Text(artist)
                                                        .font(.caption2)
                                                        .foregroundColor(.gray)
                                                        .lineLimit(1)
                                                }
                                            }

                                            Spacer()

                                            Image(systemName: "arrow.up.forward.app")
                                                .font(.caption)
                                                .foregroundColor(.gray.opacity(0.7))
                                        }
                                        .padding(.vertical, 4)
                                    }
                                }
                            }
                            .padding(16)
                            .background(Color(white: 0.1))
                            .cornerRadius(16)
                        }

                        // 6. Footer Section
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

                            Text("SongFlip v1.2.0 (iOS) • Open Source (GPLv3)")
                                .font(.caption2)
                                .foregroundColor(Color(white: 0.4))
                        }
                        .padding(.top, 8)
                        .padding(.bottom, 24)
                    }
                    .padding(.horizontal, 16)
                }
            }
            .navigationTitle("SongFlip")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showingHistorySheet = true }) {
                        Image(systemName: "clock.arrow.circlepath")
                            .foregroundColor(.white)
                    }
                }

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
            .sheet(isPresented: $showingHistorySheet) {
                HistorySheetView()
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                checkClipboard()
            }
        }
    }

    private func pasteFromClipboard() {
        if let clip = UIPasteboard.general.string {
            let clean = UrlUtils.shared.extractCleanUrl(rawInput: clip) ?? clip
            inputUrl = clean
            statusMessage = "Link aus Zwischenablage eingefügt!"
            statusSuccess = true
        }
    }

    private func checkClipboard() {
        guard settings.autoClipboardDetect, let clip = UIPasteboard.general.string else { return }
        let clean = UrlUtils.shared.extractCleanUrl(rawInput: clip)
        if let clean = clean, clean != inputUrl, clean.contains("spotify.com") || clean.contains("apple.com") || clean.contains("youtube.com") || clean.contains("deezer.com") || clean.contains("tidal.com") || clean.contains("amazon.com") {
            inputUrl = clean
            statusMessage = "Link aus Zwischenablage erkannt!"
            statusSuccess = true
        }
    }

    private func convertLink() {
        guard !inputUrl.isEmpty else { return }
        settings.isResolving = true
        statusMessage = "Löse Link auf..."
        statusSuccess = false

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
                    statusSuccess = true

                    // Add to shared History
                    HistoryModel.shared.add(
                        title: success.title ?? "Unbekannter Song",
                        artist: success.artist,
                        sourceUrl: inputUrl,
                        targetUrl: success.targetUrl,
                        targetPlatform: settings.targetPlatform,
                        isAlbum: success.isAlbum
                    )

                    let target = success.nativeAppUri ?? success.targetUrl
                    if let url = URL(string: target) {
                        UIApplication.shared.open(url)
                    }
                } else if let error = res as? ResolutionResult.Error {
                    statusMessage = "Fehler: \(error.message)"
                    statusSuccess = false
                } else {
                    statusMessage = "Konnte Link nicht auflösen."
                    statusSuccess = false
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
        default: return .green
        }
    }
}
