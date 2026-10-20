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
        let title = LocalizationManager.string(for: "listen_and_flip_title", lang: lang)
        let subtitle = LocalizationManager.string(for: "listen_and_flip_subtitle", lang: lang)
        let icon = UIApplicationShortcutIcon(systemImageName: "waveform")
        let item = UIApplicationShortcutItem(
            type: listenShortcutType,
            localizedTitle: title,
            localizedSubtitle: subtitle,
            icon: icon,
            userInfo: nil
        )
        UIApplication.shared.shortcutItems = [item]
    }

    static func handleShortcutItem(_ shortcutItem: UIApplicationShortcutItem) -> Bool {
        guard shortcutItem.type == listenShortcutType else { return false }
        let defaults = UserDefaults(suiteName: "group.de.goork.songflip") ?? UserDefaults.standard
        defaults.set(true, forKey: "pending_open_listen_sheet")
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: .openListenAndFlip, object: nil)
        }
        return true
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

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.9.0"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "79"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        let aptabaseKey = Bundle.main.infoDictionary?["APTABASE_APP_KEY"] as? String ?? "A-SH-4092372492"
        let aptabaseHost = Bundle.main.infoDictionary?["APTABASE_HOST"] as? String ?? "https://telemetry-apps.goork.de"

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
                // Keep initial isDebug status
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
        }
    }
}
