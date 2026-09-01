import SwiftUI
import SongFlipKit

@main
struct SongFlipApp: App {
    @StateObject private var settings = SettingsModel()

    init() {
        var isDebug = false
        #if DEBUG || targetEnvironment(simulator)
        isDebug = true
        #endif

        // Route TestFlight beta testers, Apple reviewers, and StoreKit sandbox to debug data source
        if Bundle.main.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt" {
            isDebug = true
        }

        // Route automated UI test harnesses to debug data source
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
            isDebug = true
        }

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.2.9"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "5"
        let osVersion = UIDevice.current.systemVersion
        let locale = Locale.current.identifier

        AptabaseClient.shared.doInit(
            appKey: "A-SH-4092372492",
            host: "https://telemetry-apps.goork.de",
            osName: "iOS",
            osVersion: osVersion,
            locale: locale,
            appVersion: appVersion,
            appBuildNumber: buildNumber,
            isDebug: isDebug
        )
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
