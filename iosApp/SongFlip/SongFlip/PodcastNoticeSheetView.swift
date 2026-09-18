import SwiftUI

struct PodcastNoticeSheetView: View {
    let url: String
    let platformName: String
    var isAudiobook: Bool = false
    var dismissAction: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Spacer()

                ZStack {
                    Circle()
                        .fill(Color.purple.opacity(0.15))
                        .frame(width: 72, height: 72)

                    Image(systemName: isAudiobook ? "book.closed.fill" : "mic.fill")
                        .font(.system(size: 36, weight: .bold))
                        .foregroundColor(.purple)
                }

                Text(LocalizationManager.string(for: isAudiobook ? "audiobook_dialog_title" : "podcast_dialog_title"))
                    .font(.title2)
                    .fontWeight(.bold)
                    .multilineTextAlignment(.center)

                Text(LocalizationManager.string(for: "podcast_dialog_body"))
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
                        .background(Color.purple.opacity(0.2))
                        .foregroundColor(.purple)
                        .cornerRadius(6)

                    Text(url)
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
                        if let targetUrl = URL(string: url) {
                            UIApplication.shared.open(targetUrl)
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
                        .background(Color.purple)
                        .foregroundColor(.white)
                        .cornerRadius(14)
                    }

                    // Copy Link
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        UIPasteboard.general.string = url
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
