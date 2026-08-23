import SwiftUI
import SongFlipKit

@main
struct SongFlipApp: App {
    @StateObject private var settings = SettingsModel()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(settings)
                .preferredColorScheme(.dark)
        }
    }
}
