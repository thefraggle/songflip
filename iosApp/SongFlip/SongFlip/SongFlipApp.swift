import SwiftUI
import SongFlipKit
import StoreKit

@main
struct SongFlipApp: App {
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

        // Route automated UI test harnesses to debug data source
        if ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil {
            isDebug = true
        }

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.2.10"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "6"
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
