import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers
import SongFlipKit

class ShareViewController: UIViewController {

    private let engine = SongLinkEngine(client: HttpClientFactoryKt.createPlatformHttpClient(), cache: LinkCache(maxEntries: 200, ttlMs: 7 * 24 * 60 * 60 * 1000))
    private let activityIndicator = UIActivityIndicatorView(style: .large)
    private let statusLabel = UILabel()
    private let iconImageView = UIImageView()

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
        statusLabel.text = "SongFlip: Leite weiter..."
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
        let targetPlatform = defaults.string(forKey: "target_platform") ?? "youtubeMusic"
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
                        title: success.title ?? "Song",
                        artist: success.artist,
                        sourceUrl: inputUrl,
                        targetUrl: success.targetUrl,
                        targetPlatform: targetPlatform,
                        isAlbum: success.isAlbum
                    )

                    let targetUri = success.nativeAppUri ?? success.targetUrl
                    self.openApp(urlString: targetUri)
                } else {
                    self.statusLabel.text = "Konnte Link nicht weiterleiten."
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
            history = list
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

    private func openApp(urlString: String) {
        guard let url = URL(string: urlString) else {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }

        var responder: UIResponder? = self
        while responder != nil {
            if let application = responder as? UIApplication {
                application.open(url, options: [:], completionHandler: nil)
                break
            }
            responder = responder?.next
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
    }
}
