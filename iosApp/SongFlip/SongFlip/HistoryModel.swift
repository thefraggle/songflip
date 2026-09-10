import Foundation
import SwiftUI
import Combine
import SongFlipKit

struct HistoryItem: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    let timestamp: Date
    let title: String
    let artist: String?
    let sourceUrl: String
    let targetUrl: String
    let targetPlatform: String
    let isAlbum: Bool

    var formattedDate: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: timestamp, relativeTo: Date())
    }

    enum CodingKeys: String, CodingKey {
        case id, timestamp, title, artist, sourceUrl, targetUrl, targetPlatform, isAlbum
    }

    init(
        id: UUID = UUID(),
        timestamp: Date = Date(),
        title: String,
        artist: String?,
        sourceUrl: String,
        targetUrl: String,
        targetPlatform: String,
        isAlbum: Bool = false
    ) {
        self.id = id
        self.timestamp = timestamp
        self.title = title
        self.artist = artist
        self.sourceUrl = sourceUrl
        self.targetUrl = targetUrl
        self.targetPlatform = targetPlatform
        self.isAlbum = isAlbum
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        if let uuid = try? container.decode(UUID.self, forKey: .id) {
            self.id = uuid
        } else if let uuidStr = try? container.decode(String.self, forKey: .id), let uuid = UUID(uuidString: uuidStr) {
            self.id = uuid
        } else {
            self.id = UUID()
        }

        self.title = try container.decode(String.self, forKey: .title)
        self.artist = try? container.decodeIfPresent(String.self, forKey: .artist)
        self.sourceUrl = try container.decode(String.self, forKey: .sourceUrl)
        self.targetUrl = try container.decode(String.self, forKey: .targetUrl)
        self.targetPlatform = try container.decode(String.self, forKey: .targetPlatform)
        self.isAlbum = (try? container.decodeIfPresent(Bool.self, forKey: .isAlbum)) ?? false

        // Self-healing timestamp (Fix for Issue #21 / 2057 bug):
        // Apple reference date is 2001-01-01 00:00:00 UTC (978,307,200 seconds after 1970).
        // If secondsSince1970 (~1.7e9) was interpreted as reference date, timestamp becomes ~2057 (~2.7e9 epoch).
        // If reference date (~8e8) is interpreted as epoch, timestamp becomes ~1995.
        let now = Date()
        let refOffset = Date.timeIntervalBetween1970AndReferenceDate

        var parsedDate: Date? = nil
        if let d = try? container.decode(Date.self, forKey: .timestamp) {
            parsedDate = d
        } else if let dbl = try? container.decode(Double.self, forKey: .timestamp) {
            parsedDate = Date(timeIntervalSince1970: dbl)
        }

        if let date = parsedDate {
            let t = date.timeIntervalSince1970
            if t > now.timeIntervalSince1970 + 86400 {
                // Year 2057 bug healing: epoch was offset by reference date
                let healed = t - refOffset
                if healed > 0 && healed <= now.timeIntervalSince1970 + 86400 {
                    self.timestamp = Date(timeIntervalSince1970: healed)
                } else {
                    self.timestamp = now
                }
            } else if t < 1_000_000_000 {
                // Reference date (seconds since 2001) stored raw: add reference date offset
                let healed = t + refOffset
                if healed > 0 && healed <= now.timeIntervalSince1970 + 86400 {
                    self.timestamp = Date(timeIntervalSince1970: healed)
                } else {
                    self.timestamp = now
                }
            } else {
                self.timestamp = date
            }
        } else {
            self.timestamp = now
        }
    }
}

class HistoryModel: ObservableObject {
    static let shared = HistoryModel()
    private static let storageKey = "songflip_conversion_history"
    private static let appGroupId = "group.de.goork.songflip"

    @Published var items: [HistoryItem] = []

    private var defaults: UserDefaults {
        UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
    }

    init() {
        loadHistory()
    }

    func loadHistory() {
        guard let data = defaults.data(forKey: Self.storageKey) else {
            self.items = []
            return
        }

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970

        if let decoded = try? decoder.decode([HistoryItem].self, from: data) {
            self.items = decoded
            // Self-healing: persist cleaned timestamps so future decodes are clean
            saveHistory()
        } else if let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            // Resilient fallback: decode element-by-element so one bad item doesn't drop the entire history
            var recovered: [HistoryItem] = []
            for dict in array {
                if let itemData = try? JSONSerialization.data(withJSONObject: dict),
                   let item = try? decoder.decode(HistoryItem.self, from: itemData) {
                    recovered.append(item)
                }
            }
            if !recovered.isEmpty {
                self.items = recovered
                saveHistory()
            } else {
                self.items = []
            }
        } else {
            self.items = []
        }
    }

    func add(
        title: String,
        artist: String?,
        sourceUrl: String,
        targetUrl: String,
        targetPlatform: String,
        isAlbum: Bool = false
    ) {
        // Avoid duplicate consecutive identical items
        if let first = items.first, first.targetUrl == targetUrl {
            return
        }

        let newItem = HistoryItem(
            id: UUID(),
            timestamp: Date(),
            title: title,
            artist: artist,
            sourceUrl: sourceUrl,
            targetUrl: targetUrl,
            targetPlatform: targetPlatform,
            isAlbum: isAlbum
        )

        var current = items
        current.insert(newItem, at: 0)

        // Limit history to 50 items (Free version)
        if current.count > 50 {
            current = Array(current.prefix(50))
        }

        self.items = current
        saveHistory()
    }

    func updateItem(
        id: UUID,
        newTargetUrl: String,
        newTitle: String?,
        newArtist: String?,
        newTargetPlatform: String? = nil,
        isAlbum: Bool
    ) {
        if let idx = items.firstIndex(where: { $0.id == id }) {
            let old = items[idx]
            items[idx] = HistoryItem(
                id: old.id,
                timestamp: Date(),
                title: newTitle ?? old.title,
                artist: newArtist ?? old.artist,
                sourceUrl: old.sourceUrl,
                targetUrl: newTargetUrl,
                targetPlatform: newTargetPlatform ?? old.targetPlatform,
                isAlbum: isAlbum
            )
            saveHistory()
        }
    }

    func delete(at offsets: IndexSet) {
        items.remove(atOffsets: offsets)
        saveHistory()
    }

    func deleteItem(id: UUID) {
        items.removeAll { $0.id == id }
        saveHistory()
    }

    func clear() {
        items.removeAll()
        saveHistory()
    }

    private func saveHistory() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .secondsSince1970
        if let encoded = try? encoder.encode(items) {
            defaults.set(encoded, forKey: Self.storageKey)
        }
    }
}

extension SongLinkEngine {
    public static let shared = SongLinkEngine.companion.shared
}
