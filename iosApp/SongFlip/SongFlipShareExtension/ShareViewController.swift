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
    private let pickerContainerView = UIView()
    private var isPickerVisible = false

    private struct TargetPlatformItem {
        let key: String
        let name: String
        let iconSystemName: String
        let color: UIColor
    }

    private let targetPlatforms: [TargetPlatformItem] = [
        TargetPlatformItem(key: "appleMusic", name: "Apple Music", iconSystemName: "music.note", color: UIColor(red: 0.99, green: 0.24, blue: 0.27, alpha: 1.0)),
        TargetPlatformItem(key: "spotify", name: "Spotify", iconSystemName: "dot.radiowaves.left.and.right", color: UIColor(red: 0.11, green: 0.73, blue: 0.33, alpha: 1.0)),
        TargetPlatformItem(key: "youtubeMusic", name: "YouTube Music", iconSystemName: "play.rectangle.fill", color: UIColor(red: 1.0, green: 0.0, blue: 0.0, alpha: 1.0)),
        TargetPlatformItem(key: "tidal", name: "Tidal", iconSystemName: "waveform", color: UIColor(red: 0.0, green: 0.85, blue: 0.9, alpha: 1.0)),
        TargetPlatformItem(key: "deezer", name: "Deezer", iconSystemName: "music.quarternote.3", color: UIColor(red: 0.64, green: 0.22, blue: 1.0, alpha: 1.0)),
        TargetPlatformItem(key: "amazonMusic", name: "Amazon Music", iconSystemName: "cart.fill", color: UIColor(red: 0.15, green: 0.82, blue: 0.85, alpha: 1.0))
    ]

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

        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(handleBackdropTap(_:)))
        tapGesture.cancelsTouchesInView = false
        view.addGestureRecognizer(tapGesture)

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

    @objc private func handleBackdropTap(_ gesture: UITapGestureRecognizer) {
        guard isPickerVisible else { return }
        let location = gesture.location(in: view)
        if !pickerContainerView.frame.contains(location) {
            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }
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
        let askEveryTime = defaults.bool(forKey: "ask_every_time")
        let customUrl = defaults.string(forKey: "custom_api_url") ?? ""
        let customToken = defaults.string(forKey: "custom_api_token") ?? ""

        let handleUrl: (String) -> Void = { [weak self] urlString in
            guard let self = self else { return }
            if askEveryTime {
                DispatchQueue.main.async {
                    self.showQuickPicker(
                        inputUrl: urlString,
                        customUrl: customUrl,
                        customToken: customToken
                    )
                }
            } else {
                self.resolveAndOpen(
                    inputUrl: urlString,
                    targetPlatform: targetPlatform,
                    customUrl: customUrl,
                    customToken: customToken
                )
            }
        }

        for provider in attachments {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { (item, error) in
                    if let url = item as? URL {
                        handleUrl(url.absoluteString)
                    }
                }
                return
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { (item, error) in
                    if let text = item as? String {
                        handleUrl(text)
                    }
                }
                return
            }
        }
    }

    private func showQuickPicker(
        inputUrl: String,
        customUrl: String,
        customToken: String
    ) {
        isPickerVisible = true
        iconImageView.isHidden = true
        activityIndicator.stopAnimating()
        statusLabel.isHidden = true

        pickerContainerView.subviews.forEach { $0.removeFromSuperview() }
        pickerContainerView.translatesAutoresizingMaskIntoConstraints = false
        pickerContainerView.backgroundColor = UIColor(red: 0.12, green: 0.12, blue: 0.15, alpha: 0.98)
        pickerContainerView.layer.cornerRadius = 20
        pickerContainerView.layer.borderWidth = 1
        pickerContainerView.layer.borderColor = UIColor(white: 1.0, alpha: 0.12).cgColor
        pickerContainerView.clipsToBounds = true
        view.addSubview(pickerContainerView)

        let contentStack = UIStackView()
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 10
        contentStack.alignment = .fill
        contentStack.isLayoutMarginsRelativeArrangement = true
        contentStack.layoutMargins = UIEdgeInsets(top: 18, left: 16, bottom: 12, right: 16)
        pickerContainerView.addSubview(contentStack)

        // Header Title
        let titleLabel = UILabel()
        titleLabel.text = localizedText(for: "quick_picker_title", default: "Open with …")
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.textAlignment = .center
        contentStack.addArrangedSubview(titleLabel)

        // Preview snippet
        let urlSnippetLabel = UILabel()
        urlSnippetLabel.text = inputUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        urlSnippetLabel.font = .systemFont(ofSize: 11, weight: .regular)
        urlSnippetLabel.textColor = .lightGray
        urlSnippetLabel.textAlignment = .center
        urlSnippetLabel.lineBreakMode = .byTruncatingMiddle
        contentStack.addArrangedSubview(urlSnippetLabel)

        contentStack.setCustomSpacing(14, after: urlSnippetLabel)

        // Service Buttons
        for platform in targetPlatforms {
            let button = UIButton(type: .system)
            button.translatesAutoresizingMaskIntoConstraints = false
            button.heightAnchor.constraint(equalToConstant: 44).isActive = true
            button.backgroundColor = UIColor(white: 0.20, alpha: 1.0)
            button.layer.cornerRadius = 12
            button.layer.borderWidth = 1
            button.layer.borderColor = UIColor(white: 1.0, alpha: 0.08).cgColor

            let rowStack = UIStackView()
            rowStack.translatesAutoresizingMaskIntoConstraints = false
            rowStack.axis = .horizontal
            rowStack.spacing = 12
            rowStack.alignment = .center
            rowStack.isUserInteractionEnabled = false

            // Icon container
            let iconContainer = UIView()
            iconContainer.translatesAutoresizingMaskIntoConstraints = false
            iconContainer.backgroundColor = platform.color.withAlphaComponent(0.2)
            iconContainer.layer.cornerRadius = 8
            iconContainer.widthAnchor.constraint(equalToConstant: 32).isActive = true
            iconContainer.heightAnchor.constraint(equalToConstant: 32).isActive = true

            let iconView = UIImageView(image: UIImage(systemName: platform.iconSystemName))
            iconView.translatesAutoresizingMaskIntoConstraints = false
            iconView.tintColor = platform.color
            iconView.contentMode = .scaleAspectFit
            iconContainer.addSubview(iconView)
            NSLayoutConstraint.activate([
                iconView.centerXAnchor.constraint(equalTo: iconContainer.centerXAnchor),
                iconView.centerYAnchor.constraint(equalTo: iconContainer.centerYAnchor),
                iconView.widthAnchor.constraint(equalToConstant: 18),
                iconView.heightAnchor.constraint(equalToConstant: 18)
            ])

            rowStack.addArrangedSubview(iconContainer)

            let nameLabel = UILabel()
            nameLabel.text = platform.name
            nameLabel.font = .systemFont(ofSize: 15, weight: .semibold)
            nameLabel.textColor = .white
            rowStack.addArrangedSubview(nameLabel)

            let spacer = UIView()
            spacer.isUserInteractionEnabled = false
            rowStack.addArrangedSubview(spacer)

            let arrowView = UIImageView(image: UIImage(systemName: "arrow.up.right"))
            arrowView.translatesAutoresizingMaskIntoConstraints = false
            arrowView.tintColor = .systemGray
            arrowView.contentMode = .scaleAspectFit
            arrowView.widthAnchor.constraint(equalToConstant: 14).isActive = true
            arrowView.heightAnchor.constraint(equalToConstant: 14).isActive = true
            rowStack.addArrangedSubview(arrowView)

            button.addSubview(rowStack)
            NSLayoutConstraint.activate([
                rowStack.leadingAnchor.constraint(equalTo: button.leadingAnchor, constant: 12),
                rowStack.trailingAnchor.constraint(equalTo: button.trailingAnchor, constant: -12),
                rowStack.centerYAnchor.constraint(equalTo: button.centerYAnchor)
            ])

            button.addAction(UIAction { [weak self] _ in
                guard let self = self else { return }
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                self.isPickerVisible = false
                self.pickerContainerView.removeFromSuperview()
                self.iconImageView.isHidden = false
                self.activityIndicator.startAnimating()
                self.statusLabel.isHidden = false
                self.statusLabel.text = self.localizedText(for: "share_redirecting", default: "SongFlip: Redirecting...")
                self.resolveAndOpen(
                    inputUrl: inputUrl,
                    targetPlatform: platform.key,
                    customUrl: customUrl,
                    customToken: customToken
                )
            }, for: .touchUpInside)

            contentStack.addArrangedSubview(button)
        }

        // Cancel Button
        let cancelButton = UIButton(type: .system)
        cancelButton.translatesAutoresizingMaskIntoConstraints = false
        cancelButton.heightAnchor.constraint(equalToConstant: 38).isActive = true
        cancelButton.setTitle(localizedText(for: "btn_cancel", default: "Cancel"), for: .normal)
        cancelButton.setTitleColor(.systemGray, for: .normal)
        cancelButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .medium)
        cancelButton.addAction(UIAction { [weak self] _ in
            self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
        }, for: .touchUpInside)
        contentStack.addArrangedSubview(cancelButton)

        NSLayoutConstraint.activate([
            pickerContainerView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            pickerContainerView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            pickerContainerView.widthAnchor.constraint(equalTo: view.widthAnchor, constant: -36),
            pickerContainerView.widthAnchor.constraint(lessThanOrEqualToConstant: 380),

            contentStack.topAnchor.constraint(equalTo: pickerContainerView.topAnchor),
            contentStack.leadingAnchor.constraint(equalTo: pickerContainerView.leadingAnchor),
            contentStack.trailingAnchor.constraint(equalTo: pickerContainerView.trailingAnchor),
            contentStack.bottomAnchor.constraint(equalTo: pickerContainerView.bottomAnchor)
        ])
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
                        isAlbum: success.isAlbum,
                        thumbnailUrl: success.thumbnailUrl
                    )

                    let targetUri = success.nativeAppUri ?? success.targetUrl
                    let fallbackUri = success.nativeAppUri != nil ? success.targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)
                } else if let podcast = result as? ResolutionResult.Podcast {
                    self.statusLabel.text = self.localizedText(for: "podcast_badge", default: "Podcast")
                    let targetUri = podcast.nativeAppUri ?? podcast.targetUrl
                    let fallbackUri = podcast.nativeAppUri != nil ? podcast.targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)
                } else if let playlist = result as? ResolutionResult.Playlist {
                    self.statusLabel.text = self.localizedText(for: "playlist_share_opening", default: "Playlist: Opening original...")
                    self.openApp(urlString: playlist.originalUrl)
                } else if let podcast = result as? ResolutionResult.PodcastOrAudiobook {
                    self.statusLabel.text = self.localizedText(for: "podcast_share_opening", default: "Podcast: Opening original...")
                    self.openApp(urlString: podcast.originalUrl)
                } else {
                    self.statusLabel.text = self.localizedText(for: "share_error_failed", default: "Could not redirect link.")
                    let trimmedInput = inputUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                    if let fallbackUrl = URL(string: trimmedInput),
                       fallbackUrl.scheme == "http" || fallbackUrl.scheme == "https" {
                        self.openApp(urlString: fallbackUrl.absoluteString)
                    } else {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                        }
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
        isAlbum: Bool,
        thumbnailUrl: String? = nil
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

        var item: [String: Any] = [
            "id": UUID().uuidString,
            "timestamp": Date().timeIntervalSince1970,
            "title": title,
            "artist": artist ?? "",
            "sourceUrl": sourceUrl,
            "targetUrl": targetUrl,
            "targetPlatform": targetPlatform,
            "isAlbum": isAlbum
        ]
        if let thumb = thumbnailUrl, !thumb.isEmpty {
            item["thumbnailUrl"] = thumb
        }

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
    public nonisolated static let shared = SongLinkEngine.companion.shared
}
