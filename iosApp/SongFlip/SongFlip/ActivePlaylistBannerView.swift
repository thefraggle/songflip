import SwiftUI
import SongFlipKit

/**
 * Floating active playlist conversion progress / completion banner (#241).
 */
struct ActivePlaylistBannerView: View {
    let activePlaylist: ActivePlaylistData
    let lang: String
    let isPro: Bool
    let onDismiss: () -> Void
    let onOpenDetails: (String) -> Void

    var body: some View {
        let sourceName = detectSourcePlatformName(url: activePlaylist.playlistUrl)
        let targetChoice = PlatformChoice(rawValue: activePlaylist.targetPlatformKey)
        let targetName = targetChoice?.displayName ?? activePlaylist.targetPlatformKey

        VStack(alignment: .leading, spacing: 10) {
            // Header: Queue Icon + Platform chips + Dismiss button
            HStack(spacing: 8) {
                Image(systemName: "music.note.list")
                    .foregroundColor(.orange)
                    .font(.subheadline)

                Text(sourceName)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.secondary.opacity(0.15))
                    .cornerRadius(6)

                Text("➔")
                    .font(.caption2)
                    .foregroundColor(.secondary)

                Text(targetName)
                    .font(.caption2)
                    .fontWeight(.bold)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.green.opacity(0.18))
                    .foregroundColor(.green)
                    .cornerRadius(6)

                Spacer()

                Button(action: {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    onDismiss()
                }) {
                    Image(systemName: "xmark")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(4)
                }
                .accessibilityLabel(Text(LocalizationManager.string(for: "action_close", lang: lang)))
            }

            switch activePlaylist.state {
            case .loading:
                HStack(spacing: 12) {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                        .scaleEffect(0.85)

                    VStack(alignment: .leading, spacing: 2) {
                        Text(LocalizationManager.string(for: "playlist_converting", lang: lang))
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.primary)

                        Text(String(format: LocalizationManager.string(for: "playlist_searching_tracks", lang: lang), targetName))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }

                    Spacer()

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onOpenDetails(activePlaylist.playlistUrl)
                    }) {
                        Text(LocalizationManager.string(for: "playlist_active_banner_details", lang: lang))
                            .font(.caption.weight(.bold))
                            .padding(.vertical, 6)
                            .padding(.horizontal, 10)
                            .background(Color.secondary.opacity(0.15))
                            .cornerRadius(8)
                    }
                }

            case .success(let res):
                let title = res.title?.isEmpty == false ? res.title! : LocalizationManager.string(for: "playlist_converter_title", lang: lang)

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .lineLimit(1)
                        .foregroundColor(.primary)

                    HStack(spacing: 6) {
                        let displayTotal = max(res.totalTracks, res.originalTotalTracks ?? 0)
                        Text(String(format: LocalizationManager.string(for: "playlist_matched_count", lang: lang), res.matchedCount, displayTotal))
                            .font(.caption)
                            .foregroundColor(.secondary)

                        if !isPro && res.isLimited {
                            Text(LocalizationManager.string(for: "playlist_badge_free_limit", lang: lang))
                                .font(.system(size: 10, weight: .bold))
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color.orange.opacity(0.2))
                                .foregroundColor(.orange)
                                .cornerRadius(4)
                        }
                    }
                }

                HStack(spacing: 8) {
                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        let targetLink = res.zeroOAuthUrl ?? res.webShareUrl ?? activePlaylist.playlistUrl
                        if let url = URL(string: targetLink) {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "play.fill")
                                .font(.caption2)
                            Text(String(format: LocalizationManager.string(for: "playlist_active_banner_open", lang: lang), targetName))
                                .font(.caption.weight(.bold))
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color(red: 4/255, green: 120/255, blue: 87/255))
                        .foregroundColor(.white)
                        .cornerRadius(10)
                    }

                    Button(action: {
                        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                        onOpenDetails(activePlaylist.playlistUrl)
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "info.circle")
                                .font(.caption2)
                            Text(LocalizationManager.string(for: "playlist_active_banner_details", lang: lang))
                                .font(.caption.weight(.bold))
                        }
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(Color.secondary.opacity(0.15))
                        .foregroundColor(.primary)
                        .cornerRadius(10)
                    }
                }

            case .error(_, let msg):
                VStack(alignment: .leading, spacing: 4) {
                    Text(LocalizationManager.string(for: "playlist_error_title", lang: lang))
                        .font(.subheadline)
                        .fontWeight(.bold)
                        .foregroundColor(.red)

                    Text(msg)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }

                Button(action: {
                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                    onOpenDetails(activePlaylist.playlistUrl)
                }) {
                    Text(LocalizationManager.string(for: "playlist_active_banner_details", lang: lang))
                        .font(.caption.weight(.bold))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(Color.secondary.opacity(0.15))
                        .foregroundColor(.primary)
                        .cornerRadius(10)
                }
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(uiColor: .secondarySystemBackground))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.orange.opacity(0.35), lineWidth: 1)
        )
        .transition(.opacity.combined(with: .scale(scale: 0.96)))
    }
}
