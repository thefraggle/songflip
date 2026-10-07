import SwiftUI
import SongFlipKit

struct QuickTargetPickerSheetView: View {
    let url: String
    let lang: String
    var onSelectTarget: (String) -> Void
    var onDismiss: () -> Void

    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                // Header snippet
                HStack(spacing: 8) {
                    Image(systemName: "link")
                        .font(.caption)
                        .foregroundColor(.secondary)

                    Text(url)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(uiColor: .secondarySystemBackground))
                .cornerRadius(10)
                .padding(.horizontal, 20)
                .padding(.top, 12)

                // List of Target Services
                ScrollView {
                    VStack(spacing: 10) {
                        ForEach(PlatformChoice.allCases) { platform in
                            Button(action: {
                                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                onSelectTarget(platform.rawValue)
                                dismiss()
                            }) {
                                HStack(spacing: 14) {
                                    ZStack {
                                        RoundedRectangle(cornerRadius: 10)
                                            .fill(platform.brandColor.opacity(0.15))
                                            .frame(width: 44, height: 44)

                                        Image(systemName: platform.iconName)
                                            .font(.system(size: 20, weight: .semibold))
                                            .foregroundColor(platform.brandColor)
                                    }

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(platform.displayName)
                                            .font(.body)
                                            .fontWeight(.semibold)
                                            .foregroundColor(.primary)

                                        Text(String(format: LocalizationManager.string(for: "clipboard_banner_action_open", lang: lang), platform.displayName))
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }

                                    Spacer()

                                    Image(systemName: "arrow.up.right")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundColor(.secondary)
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(Color(uiColor: .secondarySystemGroupedBackground))
                                .cornerRadius(14)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(Color.primary.opacity(0.06), lineWidth: 1)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle(LocalizationManager.string(for: "quick_picker_title", lang: lang))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LocalizationManager.string(for: "btn_cancel", lang: lang)) {
                        onDismiss()
                        dismiss()
                    }
                    .foregroundColor(.primary)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}
