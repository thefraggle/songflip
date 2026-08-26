import Foundation
import SwiftUI
import Combine

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
        guard let data = defaults.data(forKey: Self.storageKey),
              let decoded = try? JSONDecoder().decode([HistoryItem].self, from: data) else {
            self.items = []
            return
        }
        self.items = decoded
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
        if let encoded = try? JSONEncoder().encode(items) {
            defaults.set(encoded, forKey: Self.storageKey)
        }
    }
}
