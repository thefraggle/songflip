import SwiftUI

struct HistorySheetView: View {
    @EnvironmentObject var settings: SettingsModel
    @ObservedObject var history = HistoryModel.shared
    @Environment(\.dismiss) var dismiss
    @State private var showingClearConfirmation = false

    var lang: String { settings.selectedLanguage }

    var body: some View {
        NavigationStack {
            ZStack {
                Color("BackgroundColor")
                    .ignoresSafeArea()

                if history.items.isEmpty {
                    VStack(spacing: 16) {
                        Image(systemName: "clock.arrow.circlepath")
                            .font(.system(size: 52))
                            .foregroundColor(.secondary.opacity(0.6))

                        Text(LocalizationManager.string(for: "history_empty_title", lang: lang))
                            .font(.title3)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)

                        Text(LocalizationManager.string(for: "history_empty", lang: lang))
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                } else {
                    List {
                        ForEach(history.items) { item in
                            Button(action: {
                                if let url = URL(string: item.targetUrl) {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    UIApplication.shared.open(url)
                                }
                            }) {
                                HStack(spacing: 14) {
                                    // Platform Icon
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(platformColor(for: item.targetPlatform).opacity(0.2))
                                            .frame(width: 44, height: 44)

                                        Image(systemName: platformIcon(for: item.targetPlatform))
                                            .font(.system(size: 20))
                                            .foregroundColor(platformColor(for: item.targetPlatform))
                                    }

                                    // Song & Artist Info
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(item.title)
                                            .font(.system(size: 15, weight: .bold))
                                            .foregroundColor(.primary)
                                            .lineLimit(1)

                                        HStack(spacing: 6) {
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

                                    Image(systemName: "arrow.up.forward.app")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(.secondary)
                                }
                                .padding(.vertical, 4)
                            }
                            .listRowBackground(Color("CardBackgroundColor"))
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                Button(role: .destructive) {
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
