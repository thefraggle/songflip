import SwiftUI
import SongFlipKit

/**
 * Smart clipboard detected music/playlist/podcast link banner (#241).
 */
struct ClipboardSmartBannerView: View {
    let detectedUrl: String
    let lang: String
    let targetPlatform: String
    let isResolving: Bool
    let askEveryTime: Bool
    let onDismiss: () -> Void
    let onOpenPlaylist: (String) -> Void
    let onOpenAudiobook: (String) -> Void
    let onConvert: (String) -> Void

    var body: some View {
        let isPlaylist = UrlUtils.shared.isPlaylistUrl(url: detectedUrl)
        let isPodcast = UrlUtils.shared.isPodcastUrl(url: detectedUrl)
        let isAudiobook = UrlUtils.shared.isAudiobookUrl(url: detectedUrl)
        let isPodcastOrAudiobook = isPodcast || isAudiobook

        let iconName: String = isPlaylist ? "music.note.list" : (isAudiobook ? "book.closed.fill" : (isPodcast ? "mic.fill" : "doc.on.clipboard.fill"))
        let accentColor: Color = isPlaylist ? Color.orange : (isPodcastOrAudiobook ? Color.purple : Color.green)
        let titleKey: String = isPlaylist ? "playlist_dialog_title" : (isAudiobook ? "audiobook_dialog_title" : (isPodcast ? "podcast_dialog_title" : "clipboard_banner_title"))

        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: iconName)
                    .foregroundColor(accentColor)
                    .font(.system(size: 16))
                Text(LocalizationManager.string(for: titleKey, lang: lang))
                    .font(.subheadline)
                    .fontWeight(.bold)
                    .foregroundColor(.primary)
                Spacer()
                Button(action: {
                    onDismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(4)
                }
                .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
            }

            HStack(spacing: 8) {
                Text(UrlUtils.shared.detectPlatformName(url: detectedUrl))
                    .font(.caption2)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(accentColor.opacity(0.15))
                    .foregroundColor(accentColor)
                    .cornerRadius(6)

                if isPlaylist {
                    Text(LocalizationManager.string(for: "playlist_badge", lang: lang))
                        .font(.caption2)
                        .fontWeight(.bold)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.orange.opacity(0.2))
                        .foregroundColor(.orange)
                        .cornerRadius(6)
                } else if isPodcastOrAudiobook {
                    Text(LocalizationManager.string(for: isAudiobook ? "audiobook_badge" : "podcast_badge", lang: lang))
                        .font(.caption2)
                        .fontWeight(.bold)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color.purple.opacity(0.2))
                        .foregroundColor(.purple)
                        .cornerRadius(6)
                }

                Text(detectedUrl)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                    .truncationMode(.tail)
            }

            if isPlaylist {
                let sourceName = UrlUtils.shared.detectPlatformName(url: detectedUrl)
                let targetChoice = PlatformChoice.allCases.first { $0.rawValue == targetPlatform }
                let targetName = targetChoice?.displayName ?? "Player"
                let isSamePlaylistPlatform = sourceName.caseInsensitiveCompare(targetName) == .orderedSame
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onOpenPlaylist(detectedUrl)
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: isSamePlaylistPlatform ? "arrow.up.right" : "music.note.list")
                            .font(.system(size: 12))
                        Text(isSamePlaylistPlatform
                            ? String(format: LocalizationManager.string(for: "playlist_banner_action_open", lang: lang), sourceName)
                            : String(format: LocalizationManager.string(for: "clipboard_banner_action_open", lang: lang), targetName))
                            .font(.system(size: 13, weight: .bold))
                            .lineLimit(1)
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity)
                    .background(Color.orange)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            } else if isAudiobook {
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onOpenAudiobook(detectedUrl)
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.up.right")
                            .font(.system(size: 12))
                        Text(String(format: LocalizationManager.string(for: "playlist_banner_action_open", lang: lang), UrlUtils.shared.detectPlatformName(url: detectedUrl)))
                            .font(.system(size: 13, weight: .bold))
                            .lineLimit(1)
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity)
                    .background(Color.purple)
                    .foregroundColor(.white)
                    .cornerRadius(10)
                }
            } else {
                let targetChoice = PlatformChoice.allCases.first { $0.rawValue == targetPlatform }
                let targetName = targetChoice?.displayName ?? "Player"
                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onConvert(detectedUrl)
                }) {
                    HStack(spacing: 6) {
                        if isResolving {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .black))
                                .scaleEffect(0.8)
                        } else {
                            Image(systemName: askEveryTime ? "arrow.triangle.branch" : "play.fill")
                                .font(.system(size: 12))
                        }
                        Text(askEveryTime 
                            ? LocalizationManager.string(for: "quick_picker_title", lang: lang) 
                            : String(format: LocalizationManager.string(for: "clipboard_banner_action_open", lang: lang), targetName))
                            .font(.system(size: 13, weight: .bold))
                            .lineLimit(1)
                    }
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
                    .frame(maxWidth: .infinity)
                    .background(isResolving ? Color.green.opacity(0.6) : Color.green)
                    .foregroundColor(.black)
                    .cornerRadius(10)
                }
                .disabled(isResolving)
            }
        }
        .padding(14)
        .background(accentColor.opacity(0.12))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(accentColor.opacity(0.35), lineWidth: 1)
        )
        .cornerRadius(16)
        .transition(.opacity.combined(with: .scale(scale: 0.95)))
    }
}
