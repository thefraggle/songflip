import SwiftUI

struct PromoBannerCardView: View {
    let config: PromoBannerConfig
    let lang: String
    let onClick: () -> Void
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var isPressed: Bool = false
    @State private var isHovered: Bool = false

    private var accentColor: Color {
        Color(red: 0.95, green: 0.65, blue: 0.15)
    }

    private var badgeText: String {
        config.getLocalizedBadge(locale: lang)
    }

    private var titleText: String {
        config.getLocalizedTitle(locale: lang)
    }

    private var subtitleText: String {
        config.getLocalizedSubtitle(locale: lang)
    }

    private var buttonText: String {
        config.getLocalizedButtonText(locale: lang)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Badge + Sparkles + Close Button
            HStack(alignment: .center, spacing: 8) {
                if !badgeText.isEmpty {
                    Text(badgeText)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(accentColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(accentColor.opacity(colorScheme == .dark ? 0.22 : 0.16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(accentColor.opacity(0.45), lineWidth: 1)
                        )
                        .cornerRadius(6)
                }

                Image(systemName: "sparkles")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(accentColor)

                Spacer()

                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onDismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color.secondary.opacity(0.8))
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(Text("Close"))
            }

            // Title
            Text(titleText)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(.primary)

            // Subtitle
            if !subtitleText.isEmpty {
                Text(subtitleText)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(.secondary)
                    .lineLimit(3)
                    .fixedSize(horizontal: false, vertical: true)
            }

            // Action Button (Trailing)
            HStack {
                Spacer()

                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onClick()
                }) {
                    HStack(spacing: 6) {
                        Text(buttonText.isEmpty ? "View Offer" : buttonText)
                            .font(.system(size: 13, weight: .bold))

                        Image(systemName: "arrow.right")
                            .font(.system(size: 11, weight: .bold))
                    }
                    .foregroundColor(accentColor)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                    .background(accentColor.opacity(colorScheme == .dark ? 0.25 : 0.18))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(accentColor.opacity(0.5), lineWidth: 1)
                    )
                    .cornerRadius(10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(accentColor.opacity(colorScheme == .dark ? 0.12 : 0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(
                    accentColor.opacity(isPressed ? 0.85 : (isHovered ? 0.65 : (colorScheme == .dark ? 0.35 : 0.40))),
                    lineWidth: 1
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .onTapGesture {
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onClick()
        }
        .onHover { hovering in
            isHovered = hovering
        }
    }
}
