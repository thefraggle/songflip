import SwiftUI
import SongFlipKit

@main
struct SongFlipApp: App {
    @StateObject private var settings = SettingsModel()

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
