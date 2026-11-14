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
    private var delayTimer: DispatchWorkItem?

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
        TargetPlatformItem(key: "qobuz", name: "Qobuz", iconSystemName: "waveform.badge.magnifyingglass", color: UIColor(red: 0.0, green: 0.45, blue: 0.82, alpha: 1.0)),
        TargetPlatformItem(key: "audiomack", name: "Audiomack", iconSystemName: "waveform.and.mic", color: UIColor(red: 1.0, green: 0.635, blue: 0.0, alpha: 1.0)),
        TargetPlatformItem(key: "idagio", name: "Idagio", iconSystemName: "tuningfork", color: UIColor(red: 0.106, green: 0.212, blue: 0.365, alpha: 1.0)),
        TargetPlatformItem(key: "anghami", name: "Anghami", iconSystemName: "play.circle.fill", color: UIColor(red: 0.58, green: 0.16, blue: 0.83, alpha: 1.0))
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
        iconImageView.alpha = 0
        view.addSubview(iconImageView)

        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.color = .white
        activityIndicator.startAnimating()
        activityIndicator.alpha = 0
        view.addSubview(activityIndicator)

        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.textColor = .white
        statusLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        statusLabel.text = localizedText(for: "share_redirecting", default: "SongFlip: Redirecting...")
        statusLabel.textAlignment = .center
        statusLabel.numberOfLines = 2
        statusLabel.alpha = 0
        view.addSubview(statusLabel)

        // Issue #210: 350ms threshold prevents UI flicker for instantaneous cache hits
        let workItem = DispatchWorkItem { [weak self] in
            guard let self = self, !self.isPickerVisible else { return }
            UIView.animate(withDuration: 0.2) {
                self.iconImageView.alpha = 1.0
                self.activityIndicator.alpha = 1.0
                self.statusLabel.alpha = 1.0
            }
        }
        delayTimer = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35, execute: workItem)

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
        let validTargets = ["youtubeMusic", "appleMusic", "spotify", "tidal", "deezer", "amazonMusic", "soundcloud", "qobuz", "audiomack", "idagio", "anghami"]
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

        // 1. Prefer an explicit URL provider across all attachments
        if let urlProvider = attachments.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.url.identifier) }) {
            urlProvider.loadItem(forTypeIdentifier: UTType.url.identifier, options: nil) { [weak self] (item, _) in
                if let extracted = extractString(item), !extracted.isEmpty {
                    let clean = UrlUtils.shared.extractCleanUrl(rawInput: extracted) ?? extracted
                    handleUrl(clean)
                } else {
                    DispatchQueue.main.async {
                        self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                    }
                }
            }
            return
        }

        // 2. Fall back to plain text provider
        if let textProvider = attachments.first(where: { $0.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) }) {
            textProvider.loadItem(forTypeIdentifier: UTType.plainText.identifier, options: nil) { [weak self] (item, _) in
                if let extracted = extractString(item), !extracted.isEmpty {
                    let clean = UrlUtils.shared.extractCleanUrl(rawInput: extracted) ?? extracted
                    handleUrl(clean)
                } else {
                    DispatchQueue.main.async {
                        self?.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
                    }
                }
            }
            return
        }

        self.extensionContext?.completeRequest(returningItems: nil, completionHandler: nil)
    }

    private func showQuickPicker(
        inputUrl: String,
        customUrl: String,
        customToken: String
    ) {
        delayTimer?.cancel()
        delayTimer = nil
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

        // Scrollable Service Buttons container (prevents overflow on smaller screens / landscape)
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.showsVerticalScrollIndicator = true
        let buttonsStack = UIStackView()
        buttonsStack.translatesAutoresizingMaskIntoConstraints = false
        buttonsStack.axis = .vertical
        buttonsStack.spacing = 10
        buttonsStack.alignment = .fill
        scrollView.addSubview(buttonsStack)
        NSLayoutConstraint.activate([
            buttonsStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            buttonsStack.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            buttonsStack.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            buttonsStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            buttonsStack.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor)
        ])
        let maxScrollHeight = scrollView.heightAnchor.constraint(lessThanOrEqualToConstant: 440)
        maxScrollHeight.priority = .defaultHigh
        maxScrollHeight.isActive = true
        let contentHeightConstraint = scrollView.heightAnchor.constraint(equalTo: buttonsStack.heightAnchor)
        contentHeightConstraint.priority = .defaultLow
        contentHeightConstraint.isActive = true
        contentStack.addArrangedSubview(scrollView)

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

            buttonsStack.addArrangedSubview(button)
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
            pickerContainerView.heightAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.heightAnchor, constant: -32),

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
        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "0"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        let aptabaseKey = Bundle.main.infoDictionary?["APTABASE_APP_KEY"] as? String ?? ""
        let rawHost = (Bundle.main.infoDictionary?["APTABASE_HOST"] as? String)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "\\/", with: "/") ?? ""
        let aptabaseHost: String
        if rawHost.hasPrefix("https://") || rawHost.hasPrefix("http://") {
            aptabaseHost = rawHost
        } else if rawHost == "https:" || rawHost.isEmpty {
            aptabaseHost = "https://telemetry-apps.goork.de"
        } else {
            aptabaseHost = "https://api.aptabase.com"
        }

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

    private func isProActive(defaults: UserDefaults) -> Bool {
        let isPro = defaults.bool(forKey: "is_pro_cached")
        guard isPro else { return false }

        let expMs = defaults.object(forKey: "pro_cache_exp") as? Int64 ?? (defaults.object(forKey: "pro_cache_exp") as? NSNumber)?.int64Value ?? 0
        if expMs > 0 {
            let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
            if nowMs > (expMs + Int64(15 * 60 * 1000)) {
                return false
            }
        }
        return true
    }

    private func resolveAndOpen(
        inputUrl: String,
        targetPlatform: String,
        customUrl: String,
        customToken: String
    ) {
        ensureAptabaseInitialized()
        Task {
            let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
            let isProCached = isProActive(defaults: defaults)
            let audiophileMode = isProCached && defaults.bool(forKey: "audiophile_mode")
            let result: ResolutionResult
            var bridgeErrorDetail: String? = nil
            do {
                result = try await engine.resolveTargetUrl(
                    inputUrl: inputUrl,
                    targetPlatformKey: targetPlatform,
                    customApiUrl: customUrl,
                    customApiToken: customToken,
                    isPro: isProCached,
                    authToken: defaults.string(forKey: "pro_auth_token") ?? "",
                    isPrefetch: false,
                    forceRefresh: false,
                    audiophileMode: audiophileMode
                )
            } catch {
                Self.logger.error("[ShareExtension] Resolution threw error: \(error.localizedDescription)")
                let errInfo = FlipCoordinator.mapError(error)
                bridgeErrorDetail = errInfo.detail
                result = ResolutionResult.Error(message: errInfo.reason, isUnsupported: false, errorReason: errInfo.errorReason)
            }

            let action = FlipCoordinator.resolveAction(result: result, targetPlatform: targetPlatform)

            if case .openResolved(let targetUrl, _, let platformKey, let title, let artist, let isAlbum, _, _, let thumb) = action {
                FlipCoordinator.incrementSuccessfulFlipCount(defaults: defaults)
                _ = try? await self.engine.cache.addHistoryEntry(
                    sourceUrl: inputUrl,
                    targetPlatformKey: platformKey,
                    targetUrl: targetUrl,
                    title: title ?? LocalizationManager.string(for: "unknown_song"),
                    artist: artist,
                    isAlbum: isAlbum,
                    thumbnailUrl: thumb,
                    currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                    isAcoustic: false
                )
            }

            await MainActor.run {
                self.delayTimer?.cancel()
                self.delayTimer = nil

                FlipCoordinator.trackAction(action, inputUrl: inputUrl, targetPlatform: targetPlatform, errorDetail: bridgeErrorDetail)

                switch action {
                case .openResolved(let targetUrl, let nativeAppUri, let platformKey, let title, let artist, _, _, _, _):
                    if let title = title {
                        self.statusLabel.text = "🎵 \(title)"
                    }
                    let urls = FlipCoordinator.computeLaunchUrls(
                        targetUrl: targetUrl,
                        nativeAppUri: nativeAppUri,
                        platformKey: platformKey,
                        title: title,
                        artist: artist,
                        defaults: defaults
                    )
                    self.openApp(
                        urlString: urls.primaryUrl?.absoluteString ?? targetUrl,
                        fallbackUrlString: urls.fallbackUrl?.absoluteString
                    )

                case .openPodcast(let targetUrl, let nativeAppUri, _):
                    self.statusLabel.text = self.localizedText(for: "podcast_badge", default: "Podcast")
                    let targetUri = nativeAppUri ?? targetUrl
                    let fallbackUri = nativeAppUri != nil ? targetUrl : nil
                    self.openApp(urlString: targetUri, fallbackUrlString: fallbackUri)

                case .showPlaylistNotice(let originalUrl):
                    self.statusLabel.text = self.localizedText(for: "playlist_share_opening", default: "Playlist: Opening original...")
                    self.openApp(urlString: originalUrl)

                case .showPodcastNotice(let originalUrl, _):
                    self.statusLabel.text = self.localizedText(for: "podcast_share_opening", default: "Podcast: Opening original...")
                    self.openApp(urlString: originalUrl)

                case .unsupportedEntity(let originalUrl, let entityType):
                    if entityType == "unsupported_music_service",
                       let service = UrlUtils.shared.detectUnsupportedKnownMusicService(url: originalUrl.isEmpty ? inputUrl : originalUrl) {
                        let isGerman = Locale.preferredLanguages.first?.hasPrefix("de") == true
                        self.statusLabel.text = isGerman ? "\(service) wird noch nicht unterstützt." : "\(service) is not supported yet."
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            self.openApp(urlString: originalUrl)
                        }
                    } else if entityType == "unsupported_domain" {
                        self.statusLabel.text = self.localizedText(
                            for: "unsupported_domain_toast",
                            default: "Not a supported streaming link – use Listen & Flip to identify music in videos."
                        )
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                            self.openApp(urlString: originalUrl)
                        }
                    } else {
                        self.openApp(urlString: originalUrl)
                    }

                case .failure:
                    self.statusLabel.text = self.localizedText(for: "share_error_failed", default: "Could not redirect link.")
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

    private func isSafeUrlToOpen(_ url: URL) -> Bool {
        let scheme = url.scheme?.lowercased() ?? ""
        if scheme == "http" || scheme == "https" {
            return true
        }
        let allowedNativeSchemes: Set<String> = [
            "music", "youtubemusic", "spotify", "tidal", "deezer",
            "amznmp3", "soundcloud", "bandcamp", "qobuz", "qobuzapp", "beatport",
            "audiomack", "idagio", "anghami", "demus", "yattee",
            "marvispro", "marvis", "soor", "joitunes"
        ]
        return allowedNativeSchemes.contains(scheme)
    }

    private func openApp(urlString: String, fallbackUrlString: String? = nil) {
        guard let url = URL(string: urlString), isSafeUrlToOpen(url) else {
            if let fallback = fallbackUrlString, let fallbackUrl = URL(string: fallback), isSafeUrlToOpen(fallbackUrl) {
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
