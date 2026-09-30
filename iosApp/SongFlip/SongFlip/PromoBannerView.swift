import SwiftUI
import SongFlipKit

struct PromoBannerView: View {
    @Environment(\.colorScheme) var colorScheme
    let config: PromoBannerConfig
    let lang: String
    let onAction: () -> Void
    let onDismiss: () -> Void

    var isDark: Boolean {
        colorScheme == .dark
    }

    var body: some View {
        let badge = config.getLocalizedBadge(locale: lang)
        let title = config.getLocalizedTitle(locale: lang)
        let subtitle = config.getLocalizedSubtitle(locale: lang)
        let buttonText = config.getLocalizedButtonText(locale: lang)

        let goldPrimary = Color(red: 0.96, green: 0.62, blue: 0.04) // #F59E0B
        let goldDark = Color(red: 0.85, green: 0.47, blue: 0.02)    // #D97706

        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center) {
                if !badge.isEmpty {
                    Text(badge)
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(
                            LinearGradient(
                                colors: [goldPrimary, goldDark],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(6)
                }

                Image(systemName: "sparkles")
                    .foregroundColor(goldPrimary)
                    .font(.system(size: 13))

                Spacer()

                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onDismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(6)
                }
            }

            Text(title)
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(isDark ? .white : Color(red: 0.06, green: 0.09, blue: 0.16))

            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(isDark ? Color.secondary : Color(red: 0.28, green: 0.33, blue: 0.41))
                    .lineLimit(2)
            }

            HStack {
                Spacer()
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onAction()
                }) {
                    HStack(spacing: 6) {
                        Text(buttonText)
                            .font(.caption)
                            .fontWeight(.bold)
                        Image(systemName: "arrow.right")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(isDark ? Color(red: 0.11, green: 0.10, blue: 0.09) : .white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(isDark ? goldPrimary : goldDark)
                    .cornerRadius(8)
                }
            }
        }
        .padding(14)
        .background(
            isDark
                ? LinearGradient(
                    colors: [Color(red: 0.13, green: 0.10, blue: 0.06), Color(red: 0.08, green: 0.10, blue: 0.14)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                : LinearGradient(
                    colors: [Color(red: 1.0, green: 0.98, blue: 0.92), Color(red: 0.99, green: 0.95, blue: 0.78)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
        )
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(goldPrimary.opacity(isDark ? 0.35 : 0.45), lineWidth: 1)
        )
    }
}
