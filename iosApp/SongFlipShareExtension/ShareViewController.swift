import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers
import SongFlipKit

class ShareViewController: UIViewController {

    private let engine = SongLinkEngine(client: HttpClientFactoryKt.createPlatformHttpClient(), cache: LinkCache(maxEntries: 200, ttlMs: 7 * 24 * 60 * 60 * 1000))
    private let activityIndicator = UIActivityIndicatorView(style: .large)
    private let statusLabel = UILabel()

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        processSharedItem()
    }

    private func setupUI() {
        view.backgroundColor = UIColor(white: 0.1, alpha: 0.95)

        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.color = .white
        activityIndicator.startAnimating()
        view.addSubview(activityIndicator)

        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        statusLabel.text = "SongFlip: Leite weiter..."
        statusLabel.textAlignment = .center
        view.addSubview(statusLabel)

        NSLayoutConstraint.activate([
            activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -20),
            statusLabel.topAnchor.constraint(equalTo: activityIndicator.bottomAnchor, constant: 16),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
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

        for provider in attachments {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, error) in
                    if let url = item as? URL {
                        self?.resolveAndOpen(inputUrl: url.absoluteString, targetPlatform: targetPlatform)
                    }
                }
                return
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] (item, error) in
                    if let text = item as? String {
                        self?.resolveAndOpen(inputUrl: text, targetPlatform: targetPlatform)
                    }
                }
                return
            }
        }
    }

    private func resolveAndOpen(inputUrl: String, targetPlatform: String) {
        Task {
            let result = try? await engine.resolveTargetUrl(
                inputUrl: inputUrl,
                targetPlatformKey: targetPlatform,
                customApiUrl: "",
                customApiToken: ""
            )

            await MainActor.run {
                if let success = result as? ResolutionResult.Success {
                    let targetUri = success.nativeAppUri ?? success.targetUrl
                    self.openApp(urlString: targetUri)
                } else {
                    self.statusLabel.text = "Fehler beim Auflösen des Links."
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                    }
                }
            }
        }
    }

    private func openApp(urlString: String) {
        guard let url = URL(string: urlString) else {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            return
        }

        // Open URL from Share Extension context
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
