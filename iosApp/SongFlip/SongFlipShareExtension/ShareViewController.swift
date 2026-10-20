import UIKit
import Social
import MobileCoreServices
import UniformTypeIdentifiers
import SongFlipKit
import os

class ShareViewController: UIViewController {
    private static let logger = Logger(subsystem: "de.goork.songflip", category: "ShareExtension")

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
        TargetPlatformItem(key: "amazonMusic", name: "Amazon Music", iconSystemName: "cart.fill", color: UIColor(red: 0.15, green: 0.82, blue: 0.85, alpha: 1.0)),
        TargetPlatformItem(key: "soundcloud", name: "SoundCloud", iconSystemName: "cloud.fill", color: UIColor(red: 1.0, green: 0.33, blue: 0.0, alpha: 1.0)),
        TargetPlatformItem(key: "qobuz", name: "Qobuz", iconSystemName: "waveform.badge.magnifyingglass", color: UIColor(red: 0.0, green: 0.45, blue: 0.82, alpha: 1.0))
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
        let validTargets = ["youtubeMusic", "appleMusic", "spotify", "tidal", "deezer", "amazonMusic", "soundcloud", "qobuz"]
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

        let extractString: (NSSecureCoding?) -> String? = { item in
            if let url = item as? URL {
                return url.absoluteString
            } else if let str = item as? String {
                return str
            } else if let nsStr = item as? NSString {
                return nsStr as String
            } else if let data = item as? Data, let str = String(data: data, encoding: .utf8) {
                return str
            }
            return nil
        }

        for provider in attachments {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, _) in
                    if let extracted = extractString(item), !extracted.isEmpty {
                        handleUrl(extracted)
                    } else {
                        DispatchQueue.main.async {
                            self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                        }
                    }
                }
                return
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
                provider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] (item, _) in
                    if let extracted = extractString(item), !extracted.isEmpty {
                        handleUrl(extracted)
                    } else {
                        DispatchQueue.main.async {
                            self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                        }
                    }
                }
                return
            }
        }

        self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
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

    private func ensureAptabaseInitialized() {
        if AptabaseClient.shared.isInitialized { return }
        let isDebug: Bool = {
            #if DEBUG
            return true
            #else
            return false
            #endif
        }()
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.9.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "79"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        let aptabaseKey = Bundle.main.infoDictionary?["APTABASE_APP_KEY"] as? String ?? "A-SH-4092372492"
        let aptabaseHost = Bundle.main.infoDictionary?["APTABASE_HOST"] as? String ?? "https://telemetry-apps.goork.de"

        #if DEBUG
        if aptabaseKey.isEmpty {
            Self.logger.error("[ShareExtension] APTABASE_APP_KEY missing in Info.plist! Check Config.xcconfig")
        }
        #endif

        AptabaseClient.shared.doInit(
            appKey: aptabaseKey,
            host: aptabaseHost,
            osName: "iOS",
            osVersion: osVersion,
            locale: locale,
            appVersion: appVersion,
            appBuildNumber: buildNumber,
            isDebug: isDebug
        )
    }

    private func resolveAndOpen(
        inputUrl: String,
        targetPlatform: String,
        customUrl: String,
        customToken: String
    ) {
        ensureAptabaseInitialized()
        Task {
            let result: ResolutionResult
            var bridgeErrorDetail: String? = nil
            do {
                result = try await engine.resolveTargetUrl(
                    inputUrl: inputUrl,
                    targetPlatformKey: targetPlatform,
                    customApiUrl: customUrl,
                    customApiToken: customToken
                )
            } catch {
                Self.logger.error("[ShareExtension] Resolution threw error: \(error.localizedDescription)")
                let nsError = error as NSError
                let errorReason: String
                if nsError.domain == NSURLErrorDomain {
                    switch nsError.code {
                    case NSURLErrorTimedOut:
                        errorReason = "timeout"
                    case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost, NSURLErrorNotConnectedToInternet:
                        errorReason = "io_error"
                    default:
                        errorReason = "network_error"
                    }
                } else {
                    errorReason = "engine_error"
                }
                bridgeErrorDetail = error.localizedDescription
                result = ResolutionResult.Error(message: error.localizedDescription, isUnsupported: false, errorReason: errorReason)
            }

            if let success = result as? ResolutionResult.Success {
                // Save to shared SQLDelight history via App Group BEFORE terminating extension
                try? await self.engine.cache.addHistoryEntry(
                    sourceUrl: inputUrl,
                    targetPlatformKey: targetPlatform,
                    targetUrl: success.targetUrl,
                    title: success.title ?? LocalizationManager.string(for: "unknown_song"),
                    artist: success.artist,
                    isAlbum: success.isAlbum,
                    thumbnailUrl: success.thumbnailUrl,
                    currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                    isAcoustic: false
                )
            }

            await MainActor.run {
                if let success = result as? ResolutionResult.Success {
                    if let title = success.title {
                        self.statusLabel.text = "🎵 \(title)"
                    }

                    let srcKey = UrlUtils.shared.detectPlatform(url: inputUrl)?.key ?? "unknown"
                    AptabaseClient.shared.trackLinkFlipped(
                        target: targetPlatform,
                        isAlbum: success.isAlbum,
                        isSearch: success.isSearchFallback,
                        source: srcKey
                    )

                    let targetUri = success.nativeAppUri ?? success.targetUrl
                    let fallbackUri = success.nativeAppUri != nil ? success.targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)
                } else if let podcast = result as? ResolutionResult.Podcast {
                    self.statusLabel.text = self.localizedText(for: "podcast_badge", default: "Podcast")

                    let srcKey = UrlUtils.shared.detectPlatform(url: inputUrl)?.key ?? "unknown"
                    AptabaseClient.shared.trackPodcastFlipped(
                        source: srcKey,
                        target: targetPlatform,
                        isDeepSearch: podcast.isDeepSearch
                    )

                    let targetUri = podcast.nativeAppUri ?? podcast.targetUrl
                    let fallbackUri = podcast.nativeAppUri != nil ? podcast.targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)
                } else if let playlist = result as? ResolutionResult.Playlist {
                    self.statusLabel.text = self.localizedText(for: "playlist_share_opening", default: "Playlist: Opening original...")
                    AptabaseClient.shared.trackPlaylistRouted(target: targetPlatform)
                    self.openApp(urlString: playlist.originalUrl)
                } else if let podcast = result as? ResolutionResult.PodcastOrAudiobook {
                    self.statusLabel.text = self.localizedText(for: "podcast_share_opening", default: "Podcast: Opening original...")
                    if podcast.isAudiobook {
                        AptabaseClient.shared.trackAudiobookIntercepted(target: targetPlatform)
                    } else {
                        AptabaseClient.shared.trackPodcastIntercepted(target: targetPlatform)
                    }
                    self.openApp(urlString: podcast.originalUrl)
                } else if let unsupported = result as? ResolutionResult.UnsupportedEntity {
                    let srcDomain = UrlUtils.shared.extractDomain(url: unsupported.originalUrl)
                    AptabaseClient.shared.trackUnsupportedEntityIntercepted(
                        target: targetPlatform,
                        entityType: unsupported.entityType,
                        sourceDomain: srcDomain
                    )
                    if unsupported.entityType == "unsupported_domain" {
                        self.statusLabel.text = self.localizedText(
                            for: "unsupported_domain_toast",
                            default: "Not a supported streaming link – use Listen & Flip to identify music in videos."
                        )
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            self.openApp(urlString: unsupported.originalUrl)
                        }
                    } else {
                        self.openApp(urlString: unsupported.originalUrl)
                    }
                } else {
                    self.statusLabel.text = self.localizedText(for: "share_error_failed", default: "Could not redirect link.")

                    let srcKey = UrlUtils.shared.detectPlatform(url: inputUrl)?.key ?? "unknown"
                    let srcDomain = UrlUtils.shared.extractDomain(url: inputUrl)
                    let err = result as? ResolutionResult.Error
                    let reason = err?.message ?? self.localizedText(for: "share_error_failed", default: "Could not redirect link.")
                    let errorReason = err?.errorReason ?? "no_match_found"
                    AptabaseClient.shared.trackLinkFlipFailed(
                        target: targetPlatform,
                        reason: reason,
                        sourceDomain: srcDomain,
                        sourcePlatform: srcKey,
                        errorReason: errorReason,
                        errorDetail: bridgeErrorDetail
                    )

                    let trimmedInput = inputUrl.trimmingCharacters(in: .whitespacesAndNewlines)
                    if let fallbackUrl = URL(string: trimmedInput),
                       fallbackUrl.scheme == "http" || fallbackUrl.scheme == "https" {
                        self.openApp(urlString: fallbackUrl.absoluteString)
                    } else {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                            self.completeExtension()
                        }
                    }
                }
            }
        }
    }

    private func completeExtension() {
        Task {
            _ = try? await AptabaseClient.shared.flush(timeoutMs: 1500)
            await MainActor.run {
                self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
            }
        }
    }

    private func openApp(urlString: String, fallbackUrlString: String? = nil) {
        guard let url = URL(string: urlString) else {
            if let fallback = fallbackUrlString, let _ = URL(string: fallback) {
                openApp(urlString: fallback)
            } else {
                completeExtension()
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
            completeExtension()
            return
        }

        app.open(url, options: [:]) { [weak self] success in
            if !success, let fallback = fallbackUrlString, let fallbackUrl = URL(string: fallback), fallback != urlString {
                app.open(fallbackUrl, options: [:]) { _ in
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                        self?.completeExtension()
                    }
                }
            } else {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    self?.completeExtension()
                }
            }
        }
    }
}

extension SongLinkEngine {
    public nonisolated static let shared = SongLinkEngine.companion.shared
}
