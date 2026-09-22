import XCTest
import SwiftUI
@testable import SongFlip

final class SettingsModelTests: XCTestCase {

    override func setUp() {
        super.setUp()
        // Clean UserDefaults suite before each test
        if let defaults = UserDefaults(suiteName: SettingsModel.appGroupId) {
            defaults.removePersistentDomain(forName: SettingsModel.appGroupId)
        }
    }

    override func tearDown() {
        if let defaults = UserDefaults(suiteName: SettingsModel.appGroupId) {
            defaults.removePersistentDomain(forName: SettingsModel.appGroupId)
        }
        super.tearDown()
    }

    func testDefaultValues() {
        let model = SettingsModel()
        XCTAssertEqual(model.targetPlatform, "appleMusic")
        XCTAssertTrue(model.autoClipboardDetect)
        XCTAssertFalse(model.askEveryTime)
        XCTAssertEqual(model.themeMode, "system")
        XCTAssertEqual(model.customApiUrl, "")
        XCTAssertEqual(model.customApiToken, "")
        XCTAssertNil(model.colorScheme)
    }

    func testPlatformChoiceEnumCoverage() {
        let platforms = PlatformChoice.allCases
        XCTAssertEqual(platforms.count, 6)

        let expectedKeys: Set<String> = ["youtubeMusic", "appleMusic", "spotify", "tidal", "deezer", "amazonMusic"]
        let actualKeys = Set(platforms.map { $0.rawValue })
        XCTAssertEqual(expectedKeys, actualKeys)

        for platform in platforms {
            XCTAssertFalse(platform.displayName.isEmpty)
            XCTAssertFalse(platform.iconName.isEmpty)
            _ = platform.brandColor
        }
    }

    func testSupportedLanguagesList() {
        let langs = SettingsModel.supportedLanguages
        XCTAssertEqual(langs.count, 22)

        let codes = Set(langs.map { $0.code })
        XCTAssertTrue(codes.contains("de"))
        XCTAssertTrue(codes.contains("en"))
        XCTAssertTrue(codes.contains("es"))
        XCTAssertTrue(codes.contains("fr"))
        XCTAssertTrue(codes.contains("ja"))
        XCTAssertTrue(codes.contains("zh"))

        for lang in langs {
            XCTAssertFalse(lang.name.isEmpty)
            XCTAssertFalse(lang.flag.isEmpty)
            XCTAssertEqual(lang.id, lang.code)
        }
    }

    func testPersistenceInUserDefaults() {
        let model = SettingsModel()
        model.targetPlatform = "spotify"
        model.autoClipboardDetect = false
        model.askEveryTime = true
        model.selectedLanguage = "de"
        model.themeMode = "dark"
        model.customApiUrl = "https://custom.api.songflip.link"
        model.customApiToken = "secret-token-123"

        // Create new instance to test reading persisted values
        let reloaded = SettingsModel()
        XCTAssertEqual(reloaded.targetPlatform, "spotify")
        XCTAssertFalse(reloaded.autoClipboardDetect)
        XCTAssertTrue(reloaded.askEveryTime)
        XCTAssertEqual(reloaded.selectedLanguage, "de")
        XCTAssertEqual(reloaded.themeMode, "dark")
        XCTAssertEqual(reloaded.colorScheme, .dark)
        XCTAssertEqual(reloaded.customApiUrl, "https://custom.api.songflip.link")
        XCTAssertEqual(reloaded.customApiToken, "secret-token-123")
    }

    func testInvalidTargetPlatformFallback() {
        let defaults = UserDefaults(suiteName: SettingsModel.appGroupId) ?? UserDefaults.standard
        defaults.set("invalid_unknown_platform", forKey: "target_platform")

        let model = SettingsModel()
        XCTAssertEqual(model.targetPlatform, "appleMusic")
    }

    func testThemeModeColorSchemeMapping() {
        let model = SettingsModel()

        model.themeMode = "light"
        XCTAssertEqual(model.colorScheme, .light)

        model.themeMode = "dark"
        XCTAssertEqual(model.colorScheme, .dark)

        model.themeMode = "system"
        XCTAssertNil(model.colorScheme)
    }
}
