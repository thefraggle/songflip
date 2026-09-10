import SwiftUI

struct PlaylistNoticeSheetView: View {
    let playlistUrl: String
    let platformName: String
    var dismissAction: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.orange.opacity(0.15))
                        .frame(width: 72, height: 72)

                    Image(systemName: "music.note.list")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.orange)
                }

                Text(LocalizationManager.string(for: "playlist_dialog_title"))
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text(LocalizationManager.string(for: "playlist_dialog_body"))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)

                // URL badge snippet
                HStack(spacing: 8) {
                    Text(platformName)
                        .font(.caption2)
                        .fontWeight(.bold)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.orange.opacity(0.2))
                        .foregroundColor(.orange)
                        .cornerRadius(6)

                    Text(playlistUrl)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(10)
                .frame(maxWidth: .infinity)
                .background(Color(uiColor: .secondarySystemBackground))
                .cornerRadius(12)
                .padding(.horizontal, 20)

                Spacer()

                VStack(spacing: 12) {
                    // Open in original
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        if let url = URL(string: playlistUrl) {
                            UIApplication.shared.open(url)
                        }
                        dismissAction()
                        dismiss()
                    }) {
                        HStack {
                            Image(systemName: "arrow.up.right")
                            Text(String(format: LocalizationManager.string(for: "playlist_dialog_action_open"), platformName))
                                .fontWeight(.bold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.orange)
                        .foregroundColor(.white)
                        .cornerRadius(14)
                    }

                    // Copy Link
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        UIPasteboard.general.string = playlistUrl
                        dismissAction()
                        dismiss()
                    }) {
                        HStack {
                            Image(systemName: "doc.on.doc")
                            Text(LocalizationManager.string(for: "playlist_dialog_action_copy"))
                                .fontWeight(.semibold)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(uiColor: .secondarySystemBackground))
                        .foregroundColor(.primary)
                        .cornerRadius(14)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LocalizationManager.string(for: "playlist_dialog_action_close")) {
                        dismissAction()
                        dismiss()
                    }
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
