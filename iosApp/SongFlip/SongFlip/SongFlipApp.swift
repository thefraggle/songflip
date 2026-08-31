import SwiftUI
import SongFlipKit

@main
struct SongFlipApp: App {
    @StateObject private var settings = SettingsModel()

    init() {
        let isDebug: Bool
        #if DEBUG
        isDebug = true
        #else
        isDebug = false
        #endif

        let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.2.6"
        let buildNumber = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "2"
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
