import XCTest
import SwiftUI
@testable import SongFlip

final class HistoryModelTests: XCTestCase {

    override func setUp() {
        super.setUp()
        if let defaults = UserDefaults(suiteName: "group.de.goork.songflip") {
            defaults.removePersistentDomain(forName: "group.de.goork.songflip")
        }
        HistoryModel.shared.clear()
    }

    override func tearDown() {
        if let defaults = UserDefaults(suiteName: "group.de.goork.songflip") {
            defaults.removePersistentDomain(forName: "group.de.goork.songflip")
        }
        HistoryModel.shared.clear()
        super.tearDown()
    }

    func testHistoryItemCodableRoundtrip() throws {
        let originalItem = HistoryItem(
            id: UUID(),
            timestamp: Date(),
            title: "Bohemian Rhapsody",
            artist: "Queen",
            sourceUrl: "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv",
            targetUrl: "https://music.apple.com/album/1440807241?i=1440807242",
            targetPlatform: "appleMusic",
            isAlbum: false
        )

        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        let data = try encoder.encode(originalItem)

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        let decodedItem = try decoder.decode(HistoryItem.self, from: data)

        XCTAssertEqual(originalItem.id, decodedItem.id)
        XCTAssertEqual(originalItem.title, decodedItem.title)
        XCTAssertEqual(originalItem.artist, decodedItem.artist)
        XCTAssertEqual(originalItem.sourceUrl, decodedItem.sourceUrl)
        XCTAssertEqual(originalItem.targetUrl, decodedItem.targetUrl)
        XCTAssertEqual(originalItem.targetPlatform, decodedItem.targetPlatform)
        XCTAssertEqual(originalItem.isAlbum, decodedItem.isAlbum)
        XCTAssertEqual(originalItem.timestamp.timeIntervalSince1970, decodedItem.timestamp.timeIntervalSince1970, accuracy: 1.0)
    }

    func testTimestampHealing2057Bug() throws {
        // Year 2057 timestamp simulation: ~2.7e9 epoch seconds (caused by adding reference date to 1970 epoch)
        let year2057Seconds: Double = 2_750_000_000.0
        let json = """
        {
            "id": "\(UUID().uuidString)",
            "timestamp": \(year2057Seconds),
            "title": "Future Song",
            "artist": "Time Traveler",
            "sourceUrl": "https://open.spotify.com/track/123",
            "targetUrl": "https://music.apple.com/track/123",
            "targetPlatform": "appleMusic",
            "isAlbum": false
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        let item = try decoder.decode(HistoryItem.self, from: json)

        let now = Date().timeIntervalSince1970
        // Healed timestamp must be in reasonable present time (< now + 86400)
        XCTAssertLessThanOrEqual(item.timestamp.timeIntervalSince1970, now + 86400)
        XCTAssertGreaterThan(item.timestamp.timeIntervalSince1970, 1_000_000_000)
    }

    func testTimestampHealingPre2001ReferenceDate() throws {
        // Stored raw reference date seconds (e.g. 700_000_000)
        let rawRefSeconds: Double = 700_000_000.0
        let json = """
        {
            "id": "\(UUID().uuidString)",
            "timestamp": \(rawRefSeconds),
            "title": "Ref Date Song",
            "artist": "Classic Artist",
            "sourceUrl": "https://open.spotify.com/track/456",
            "targetUrl": "https://music.apple.com/track/456",
            "targetPlatform": "appleMusic",
            "isAlbum": false
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        let item = try decoder.decode(HistoryItem.self, from: json)

        let now = Date().timeIntervalSince1970
        XCTAssertLessThanOrEqual(item.timestamp.timeIntervalSince1970, now + 86400)
        XCTAssertGreaterThan(item.timestamp.timeIntervalSince1970, 1_000_000_000)
    }

    func testAddAndConsecutiveDuplicatePrevention() {
        let history = HistoryModel.shared
        XCTAssertEqual(history.items.count, 0)

        history.add(
            title: "Track 1",
            artist: "Artist 1",
            sourceUrl: "https://spotify.com/1",
            targetUrl: "https://apple.com/1",
            targetPlatform: "appleMusic"
        )
        XCTAssertEqual(history.items.count, 1)

        // Attempt to add duplicate consecutive item with same targetUrl
        history.add(
            title: "Track 1 Duplicate",
            artist: "Artist 1",
            sourceUrl: "https://spotify.com/1",
            targetUrl: "https://apple.com/1",
            targetPlatform: "appleMusic"
        )
        XCTAssertEqual(history.items.count, 1, "Duplicate consecutive item must not be inserted")

        // Adding distinct item succeeds
        history.add(
            title: "Track 2",
            artist: "Artist 2",
            sourceUrl: "https://spotify.com/2",
            targetUrl: "https://apple.com/2",
            targetPlatform: "appleMusic"
        )
        XCTAssertEqual(history.items.count, 2)
        XCTAssertEqual(history.items.first?.title, "Track 2")
    }

    func testHistoryLimitFiftyItems() {
        let history = HistoryModel.shared

        for i in 1...60 {
            history.add(
                title: "Track \(i)",
                artist: "Artist \(i)",
                sourceUrl: "https://spotify.com/\(i)",
                targetUrl: "https://apple.com/\(i)",
                targetPlatform: "appleMusic"
            )
        }

        XCTAssertEqual(history.items.count, 50, "History should be capped at 50 items")
        XCTAssertEqual(history.items.first?.title, "Track 60")
    }

    func testUpdateItem() {
        let history = HistoryModel.shared
        history.add(
            title: "Original Title",
            artist: "Original Artist",
            sourceUrl: "https://spotify.com/orig",
            targetUrl: "https://apple.com/orig",
            targetPlatform: "appleMusic"
        )

        guard let firstItem = history.items.first else {
            XCTFail("Item should exist")
            return
        }

        history.updateItem(
            id: firstItem.id,
            newTargetUrl: "https://deezer.com/updated",
            newTitle: "Updated Title",
            newArtist: "Updated Artist",
            newTargetPlatform: "deezer",
            isAlbum: true
        )

        let updated = history.items.first
        XCTAssertEqual(updated?.id, firstItem.id)
        XCTAssertEqual(updated?.title, "Updated Title")
        XCTAssertEqual(updated?.artist, "Updated Artist")
        XCTAssertEqual(updated?.targetUrl, "https://deezer.com/updated")
        XCTAssertEqual(updated?.targetPlatform, "deezer")
        XCTAssertEqual(updated?.isAlbum, true)
    }

    func testDeleteAndClear() {
        let history = HistoryModel.shared
        history.add(title: "Track 1", artist: "Artist 1", sourceUrl: "s1", targetUrl: "t1", targetPlatform: "appleMusic")
        history.add(title: "Track 2", artist: "Artist 2", sourceUrl: "s2", targetUrl: "t2", targetPlatform: "appleMusic")

        XCTAssertEqual(history.items.count, 2)
        let itemToDelete = history.items[0]

        history.deleteItem(id: itemToDelete.id)
        XCTAssertEqual(history.items.count, 1)
        XCTAssertEqual(history.items[0].title, "Track 1")

        history.clear()
        XCTAssertEqual(history.items.count, 0)
    }
}
