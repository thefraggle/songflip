import SwiftUI
import SongFlipKit

struct PromoBannerView: View {
    let config: PromoBannerConfig
    let lang: String
    let onAction: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        let badge = config.getLocalizedBadge(locale: lang)
        let title = config.getLocalizedTitle(locale: lang)
        let subtitle = config.getLocalizedSubtitle(locale: lang)
        let buttonText = config.getLocalizedButtonText(locale: lang)

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
                                colors: [Color(red: 0.06, green: 0.73, blue: 0.51), Color(red: 0.02, green: 0.59, blue: 0.41)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(6)
                }

                Image(systemName: "tag.fill")
                    .foregroundColor(Color(red: 0.06, green: 0.73, blue: 0.51))
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
                .foregroundColor(.white)

            if !subtitle.isEmpty {
                Text(subtitle)
                    .font(.caption)
                    .foregroundColor(Color.secondary)
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
                    .foregroundColor(.black)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color(red: 0.06, green: 0.73, blue: 0.51))
                    .cornerRadius(8)
                }
            }
        }
        .padding(14)
        .background(Color(red: 0.05, green: 0.11, blue: 0.09))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(red: 0.06, green: 0.73, blue: 0.51).opacity(0.3), lineWidth: 1)
        )
    }
}
