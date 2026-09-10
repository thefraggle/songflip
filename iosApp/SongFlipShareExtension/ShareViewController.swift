import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers
import SongFlipKit

class ShareViewController: UIViewController {

    private let engine = SongLinkEngine.shared
    private let activityIndicator = UIActivityIndicatorView(style: .large)
    private let statusLabel = UILabel()
    private let iconImageView = UIImageView()

    private func localizedText(for key: String, default defaultText: String) -> String {
        let val = LocalizationManager.string(for: key)
        return val == key ? defaultText : val
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        processSharedItem()
    }

    private func setupUI() {
        view.backgroundColor = UIColor(white: 0.08, alpha: 0.95)

        iconImageView.translatesAutoresizingMaskIntoConstraints = false
        iconImageView.image = UIImage(systemName: "music.note.list")
        iconImageView.tintColor = UIColor(red: 0.11, green: 0.73, blue: 0.33, alpha: 1.0)
        iconImageView.contentMode = .scaleAspectFit
        view.addSubview(iconImageView)

        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.color = .white
        activityIndicator.startAnimating()
        view.addSubview(activityIndicator)

        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        statusLabel.text = localizedText(for: "share_redirecting", default: "SongFlip: Redirecting...")
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
        view.addSubview(statusLabel)

        NSLayoutConstraint.activate([
            iconImageView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            iconImageView.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -50),
            iconImageView.widthAnchor.constraint(equalToConstant: 44),
            iconImageView.heightAnchor.constraint(equalToConstant: 44),

            activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            activityIndicator.topAnchor.constraint(equalTo: iconImageView.bottomAnchor, constant: 16),

            statusLabel.topAnchor.constraint(equalTo: activityIndicator.bottomAnchor, constant: 16),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24)
        ])
    }

    private func processSharedItem() {
        guard let item = extensionContext?.inputItems.first as? NSExtensionItem,
              let attachments = item.attachments else {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }

        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        let rawTarget = defaults.string(forKey: "target_platform") ?? "appleMusic"
        let validTargets = ["youtubeMusic", "appleMusic", "spotify", "tidal", "deezer", "amazonMusic"]
        let targetPlatform = validTargets.contains(rawTarget) ? rawTarget : "appleMusic"
        let customUrl = defaults.string(forKey: "custom_api_url") ?? ""
        let customToken = defaults.string(forKey: "custom_api_token") ?? ""

        for provider in attachments {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
                    if let url = item as? URL {
                        self?.resolveAndOpen(
                            inputUrl: url.absoluteString,
                            targetPlatform: targetPlatform,
                            customUrl: customUrl,
                            customToken: customToken
                        )
                    }
                }
                return
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] (item, error) in
                    if let text = item as? String {
                        self?.resolveAndOpen(
                            inputUrl: text,
                            targetPlatform: targetPlatform,
                            customUrl: customUrl,
                            customToken: customToken
                        )
                    }
                }
                return
            }
        }
    }

    private func resolveAndOpen(
        inputUrl: String,
        targetPlatform: String,
        customUrl: String,
        customToken: String
    ) {
        Task {
            let result = try? await engine.resolveTargetUrl(
                inputUrl: inputUrl,
                targetPlatformKey: targetPlatform,
                customApiUrl: customUrl,
                customApiToken: customToken
            )

            await MainActor.run {
                if let success = result as? ResolutionResult.Success {
                    if let title = success.title {
                        self.statusLabel.text = "🎵 \(title)"
                    }

                    // Save to shared history via App Group
                    self.saveToSharedHistory(
                        title: success.title ?? LocalizationManager.string(for: "unknown_song"),
                        artist: success.artist,
                        sourceUrl: inputUrl,
                        targetUrl: success.targetUrl,
                        targetPlatform: targetPlatform,
                        isAlbum: success.isAlbum
                    )

                    let targetUri = success.nativeAppUri ?? success.targetUrl
                    let fallbackUri = success.nativeAppUri != nil ? success.targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)
                } else if let playlist = result as? ResolutionResult.Playlist {
                    self.statusLabel.text = self.localizedText(for: "playlist_share_opening", default: "Playlist: Opening original...")
                    self.openApp(urlString: playlist.originalUrl)
                } else {
                    self.statusLabel.text = self.localizedText(for: "share_error_failed", default: "Could not redirect link.")
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                    }
                }
            }
        }
    }

    private func saveToSharedHistory(
        title: String,
        artist: String?,
        sourceUrl: String,
        targetUrl: String,
        targetPlatform: String,
        isAlbum: Bool
    ) {
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        let storageKey = "songflip_conversion_history"

        var history: [[String: Any]] = []
        if let data = defaults.data(forKey: storageKey),
           let list = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            let now = Date().timeIntervalSince1970
            let refOffset = Date.timeIntervalBetween1970AndReferenceDate
            history = list.map { rawItem in
                var item = rawItem
                if let t = item["timestamp"] as? Double {
                    if t > now + 86400 {
                        let healed = t - refOffset
                        item["timestamp"] = (healed > 0 && healed <= now + 86400) ? healed : now
                    } else if t < 1_000_000_000 {
                        let healed = t + refOffset
                        item["timestamp"] = (healed > 0 && healed <= now + 86400) ? healed : now
                    }
                }
                return item
            }
        }

        let item: [String: Any] = [
            "id": UUID().uuidString,
            "timestamp": Date().timeIntervalSince1970,
            "title": title,
            "artist": artist ?? "",
            "sourceUrl": sourceUrl,
            "targetUrl": targetUrl,
            "targetPlatform": targetPlatform,
            "isAlbum": isAlbum
        ]

        history.insert(item, at: 0)
        if history.count > 50 {
            history = Array(history.prefix(50))
        }

        if let encoded = try? JSONSerialization.data(withJSONObject: history) {
            defaults.set(encoded, forKey: storageKey)
        }
    }

    private func openApp(urlString: String, fallbackUrlString: String? = nil) {
        guard let url = URL(string: urlString) else {
            if let fallback = fallbackUrlString, let _ = URL(string: fallback) {
                openApp(urlString: fallback)
            } else {
                self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            }
            return
        }

        var responder: UIResponder? = self
        var application: UIApplication?
        while responder != nil {
            if let app = responder as? UIApplication {
                application = app
                break
            }
            responder = responder?.next
        }

        guard let app = application else {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }

        app.open(url, options: [:]) { [weak self] success in
            if !success, let fallback = fallbackUrlString, let fallbackUrl = URL(string: fallback), fallback != urlString {
                app.open(fallbackUrl, options: [:]) { _ in
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                    }
                }
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                }
            }
        }
    }
}

extension SongLinkEngine {
    public static let shared = SongLinkEngine.companion.shared
}
