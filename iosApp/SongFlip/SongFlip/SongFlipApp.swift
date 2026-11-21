import SwiftUI
import UIKit
import SongFlipKit
import StoreKit
import os

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(
        _ windowScene: UIWindowScene,
        performActionFor shortcutItem: UIApplicationShortcutItem,
        completionHandler: @escaping (Bool) -> Void
    ) {
        completionHandler(AppDelegate.handleShortcutItem(shortcutItem))
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    static let listenShortcutType = "de.goork.songflip.listen"
    static let clipboardShortcutType = "de.goork.songflip.clipboard"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Self.updateQuickActions(lang: LocalizationManager.currentLanguage())
        if let shortcutItem = launchOptions?[.shortcutItem] as? UIApplicationShortcutItem {
            _ = Self.handleShortcutItem(shortcutItem)
        }
        return true
    }

    func application(
        _ application: UIApplication,
        configurationForConnecting connectingSceneSession: UISceneSession,
        options: UIScene.ConnectionOptions
    ) -> UISceneConfiguration {
        if let shortcutItem = options.shortcutItem {
            _ = Self.handleShortcutItem(shortcutItem)
        }
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }

    static func updateQuickActions(lang: String) {
        let listenItem = UIApplicationShortcutItem(
            type: listenShortcutType,
            localizedTitle: LocalizationManager.string(for: "listen_and_flip_title", lang: lang),
            localizedSubtitle: LocalizationManager.string(for: "listen_and_flip_subtitle", lang: lang),
            icon: UIApplicationShortcutIcon(systemImageName: "waveform"),
            userInfo: nil
        )
        let clipboardItem = UIApplicationShortcutItem(
            type: clipboardShortcutType,
            localizedTitle: LocalizationManager.string(for: "shortcut_flip_clipboard_short", lang: lang),
            localizedSubtitle: LocalizationManager.string(for: "shortcut_flip_clipboard_long", lang: lang),
            icon: UIApplicationShortcutIcon(systemImageName: "doc.on.clipboard"),
            userInfo: nil
        )
        UIApplication.shared.shortcutItems = [listenItem, clipboardItem]
    }

    static func handleShortcutItem(_ shortcutItem: UIApplicationShortcutItem) -> Bool {
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        if shortcutItem.type == listenShortcutType {
            defaults.set(true, forKey: "pending_open_listen_sheet")
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .openListenAndFlip, object: nil)
            }
            return true
        }
        if shortcutItem.type == clipboardShortcutType {
            defaults.set(true, forKey: "pending_flip_clipboard")
            DispatchQueue.main.async {
                NotificationCenter.default.post(name: .openFlipClipboard, object: nil)
            }
            return true
        }
        return false
    }
}

@main
struct SongFlipApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    private static let logger = Logger(subsystem: "de.goork.songflip", category: "App")
    @StateObject private var settings = SettingsModel()

    init() {
        var isDebug = false
        #if DEBUG || targetEnvironment(simulator)
        isDebug = true
        #endif

        // TestFlight & Ad-Hoc builds contain embedded.mobileprovision; App Store production releases do not
        if Bundle.main.path(forResource: "embedded", ofType: "mobileprovision") != nil {
            isDebug = true
        }

        var isAutomatedTestBot = false
        #if targetEnvironment(simulator)
        isAutomatedTestBot = true
        #endif

        // Route automated UI test harnesses to debug data source and suppress impression noise (#134)
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
            isDebug = true
            isAutomatedTestBot = true
        }

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
        } else if rawHost == "https:" || rawHost == "http:" || rawHost.hasPrefix("$(") || rawHost.isEmpty {
            aptabaseHost = "https://api.aptabase.com"
        } else {
            aptabaseHost = rawHost
        }

        let normalizeUrl: (String) -> String = { key in
            let raw = (Bundle.main.infoDictionary?[key] as? String)?
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .replacingOccurrences(of: "\\/", with: "/") ?? ""
            guard (raw.hasPrefix("https://") || raw.hasPrefix("http://")), !raw.hasPrefix("$(") else {
                return ""
            }
            return raw
        }
        EngineConfig.shared.configureBackend(
            cacheBaseUrl: normalizeUrl("SONGFLIP_BACKEND_CACHE_URL"),
            webBaseUrl: normalizeUrl("SONGFLIP_BACKEND_WEB_URL")
        )

        #if DEBUG
        if aptabaseKey.isEmpty {
            Self.logger.error("[SongFlipApp] APTABASE_APP_KEY missing in Info.plist! Check Config.xcconfig")
        }
        #endif

        if !isAutomatedTestBot {
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

        ProManager.shared.configure(isDebug: isDebug, isAutomatedTest: isAutomatedTestBot)

        // StoreKit 2: Asynchronously verify sandbox environment (e.g. Apple App Store Reviewers)
        Task {
            do {
                if case .verified(let appTransaction) = try await AppTransaction.shared {
                    if appTransaction.environment == .sandbox {
                        AptabaseClient.shared.setIsDebug(isDebug: true)
                    }
                }
            } catch {
                os.Logger(subsystem: "de.goork.songflip", category: "AppTransaction")
                    .debug("AppTransaction check skipped: \(error.localizedDescription)")
            }
        }
    }

    var colorScheme: ColorScheme? {
        switch settings.themeMode {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                .preferredColorScheme(colorScheme)
                .onOpenURL { url in
                    guard url.scheme?.lowercased() == "songflip" else { return }
                    let host = url.host?.lowercased() ?? ""
                    let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
                    switch host {
                    case "listen":
                        defaults.set(true, forKey: "pending_open_listen_sheet")
                        NotificationCenter.default.post(name: .openListenAndFlip, object: nil)
                    case "clipboard":
                        defaults.set(true, forKey: "pending_flip_clipboard")
                        NotificationCenter.default.post(name: .openFlipClipboard, object: nil)
                    case "paywall":
                        defaults.set(true, forKey: "pending_open_paywall")
                        NotificationCenter.default.post(name: .openProPaywall, object: nil)
                    default:
                        break
                    }
                }
        }
    }
}
