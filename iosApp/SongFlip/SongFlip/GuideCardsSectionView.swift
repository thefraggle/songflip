import SwiftUI

/**
 * Onboarding guidance section cards for iOS Shortcuts & Share Sheet (#241).
 */
struct GuideCardsSectionView: View {
    let lang: String
    let onOpenShareGuide: () -> Void
    let onOpenShortcutsGuide: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(LocalizationManager.string(for: "how_it_works_ios", lang: lang))
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(.secondary)

            // 1. Share Sheet Action
            Button(action: {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onOpenShareGuide()
            }) {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "square.and.arrow.up.fill")
                        .font(.title3)
                        .foregroundColor(.blue)
                        .frame(width: 28)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(LocalizationManager.string(for: "step1_title", lang: lang))
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                        Text(LocalizationManager.string(for: "step1_desc", lang: lang))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }

                    Spacer(minLength: 4)

                    Image(systemName: "info.circle")
                        .font(.system(size: 18))
                        .foregroundColor(.blue)
                }
            }
            .buttonStyle(.plain)

            Divider()

            // 2. Action Button & Shortcuts Action
            Button(action: {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                onOpenShortcutsGuide()
            }) {
                HStack(alignment: .center, spacing: 12) {
                    Image(systemName: "button.programmable")
                        .font(.title3)
                        .foregroundColor(.orange)
                        .frame(width: 28)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(LocalizationManager.string(for: "step2_title", lang: lang))
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(.primary)
                        Text(LocalizationManager.string(for: "step2_desc", lang: lang))
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.leading)
                    }

                    Spacer(minLength: 4)

                    Image(systemName: "info.circle")
                        .font(.system(size: 18))
                        .foregroundColor(.orange)
                }
            }
            .buttonStyle(.plain)

            Divider()

            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "doc.on.clipboard.fill")
                    .font(.title3)
                    .foregroundColor(.green)
                    .frame(width: 28)

                VStack(alignment: .leading, spacing: 3) {
                    Text(LocalizationManager.string(for: "step3_title", lang: lang))
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(.primary)
                    Text(LocalizationManager.string(for: "step3_desc", lang: lang))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
        }
        .padding(16)
        .background(Color("CardBackgroundColor"))
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(uiColor: .separator).opacity(0.25), lineWidth: 1)
        )
    }
}
