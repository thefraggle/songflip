import SwiftUI

struct ProNudgeBannerCardView: View {
    let milestone: Int
    let lang: String
    let onLearnMore: () -> Void
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    private var accentColor: Color {
        Color.green
    }

    private var titleText: String {
        String(format: LocalizationManager.string(for: "pro_nudge_title", lang: lang), milestone)
    }

    private var subtitleText: String {
        LocalizationManager.string(for: "pro_nudge_subtitle", lang: lang)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header Row: Sparkles Icon + Title + Close Button
            HStack(alignment: .center, spacing: 8) {
                Image(systemName: "sparkles")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(accentColor)

                Text(titleText)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)

                Spacer()

                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onDismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(6)
                        .background(Color(uiColor: .tertiarySystemFill))
                        .clipShape(Circle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
            }

            // Subtitle Description
            Text(subtitleText)
                .font(.system(size: 12, weight: .regular))
                .foregroundColor(.secondary)
                .lineSpacing(2)
                .fixedSize(horizontal: false, vertical: true)

            // Action Button Row
            HStack(spacing: 8) {
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onLearnMore()
                }) {
                    Text(LocalizationManager.string(for: "pro_nudge_action_more", lang: lang))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .frame(maxWidth: .infinity)
                        .background(accentColor)
                        .cornerRadius(8)
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 2)
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.green.opacity(colorScheme == .dark ? 0.12 : 0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.green.opacity(colorScheme == .dark ? 0.35 : 0.25), lineWidth: 1)
        )
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}
