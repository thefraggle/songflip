import Foundation
import SwiftUI
import Combine
import os
import SongFlipKit

struct HistoryItem: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var cacheKey: String
    let timestamp: Date
    let title: String
    let artist: String?
    let sourceUrl: String
    let targetUrl: String
    let targetPlatform: String
    let isAlbum: Bool
    let thumbnailUrl: String?
    var isFavorite: Bool
    var isAcoustic: Bool

    var formattedDate: String {
        let formatter = RelativeDateTimeFormatter()
        formatter.unitsStyle = .short
        return formatter.localizedString(for: timestamp, relativeTo: Date())
    }

    enum CodingKeys: String, CodingKey {
        case id, cacheKey, timestamp, title, artist, sourceUrl, targetUrl, targetPlatform, isAlbum, thumbnailUrl, isFavorite, isAcoustic
    }

    init(
        id: UUID = UUID(),
        cacheKey: String? = nil,
        timestamp: Date = Date(),
        title: String,
        artist: String?,
        sourceUrl: String,
        targetUrl: String,
        targetPlatform: String,
        isAlbum: Bool = false,
        thumbnailUrl: String? = nil,
        isFavorite: Bool = false,
        isAcoustic: Bool = false
    ) {
        self.id = id
        let cleanSource = UrlUtils.shared.extractCleanUrl(rawInput: sourceUrl) ?? sourceUrl
        let canonical = UrlUtils.shared.normalizeUrl(url: cleanSource)
        self.cacheKey = cacheKey ?? "\(canonical)|\(targetPlatform)"
        self.timestamp = timestamp
        self.title = title
        self.artist = artist
        self.sourceUrl = sourceUrl
        self.targetUrl = targetUrl
        self.targetPlatform = targetPlatform
        self.isAlbum = isAlbum
        self.thumbnailUrl = thumbnailUrl
        self.isFavorite = isFavorite
        self.isAcoustic = isAcoustic
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
        self.thumbnailUrl = try? container.decodeIfPresent(String.self, forKey: .thumbnailUrl)
        self.isFavorite = (try? container.decodeIfPresent(Bool.self, forKey: .isFavorite)) ?? false
        self.isAcoustic = (try? container.decodeIfPresent(Bool.self, forKey: .isAcoustic)) ?? false

        if let explicitKey = try? container.decodeIfPresent(String.self, forKey: .cacheKey), !explicitKey.isEmpty {
            self.cacheKey = explicitKey
        } else {
            let cleanSource = UrlUtils.shared.extractCleanUrl(rawInput: self.sourceUrl) ?? self.sourceUrl
            let canonical = UrlUtils.shared.normalizeUrl(url: cleanSource)
            self.cacheKey = "\(canonical)|\(self.targetPlatform)"
        }

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
                let healed = t - refOffset
                if healed > 0 && healed <= now.timeIntervalSince1970 + 86400 {
                    self.timestamp = Date(timeIntervalSince1970: healed)
                } else {
                    self.timestamp = now
                }
            } else if t < 1_000_000_000 {
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

    init(from kmpItem: LinkHistoryItem) {
        self.id = Self.deterministicUUID(from: kmpItem.cacheKey)
        self.cacheKey = kmpItem.cacheKey
        self.timestamp = Date(timeIntervalSince1970: Double(kmpItem.timestamp) / 1000.0)
        self.title = kmpItem.title?.isEmpty == false ? kmpItem.title! : kmpItem.canonicalUrl
        self.artist = kmpItem.artist
        self.sourceUrl = kmpItem.canonicalUrl
        self.targetUrl = kmpItem.targetUrl
        self.targetPlatform = kmpItem.targetPlatformKey
        self.isAlbum = kmpItem.isAlbum
        self.thumbnailUrl = kmpItem.thumbnailUrl
        self.isFavorite = kmpItem.isFavorite
        self.isAcoustic = kmpItem.isAcoustic
    }

    static func deterministicUUID(from key: String) -> UUID {
        var bytes = [UInt8](repeating: 0, count: 16)
        let utf8 = Array(key.utf8)
        for (i, b) in utf8.enumerated() {
            bytes[i % 16] = bytes[i % 16] ^ b &+ UInt8((i * 31) & 0xFF)
        }
        bytes[6] = (bytes[6] & 0x0F) | 0x40
        bytes[8] = (bytes[8] & 0x3F) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }
}

class HistoryModel: ObservableObject {
    static let shared = HistoryModel()
    private static let storageKey = "songflip_conversion_history"
    private static let appGroupId = "group.de.goork.songflip"
    private static let logger = Logger(subsystem: "de.goork.songflip", category: "History")

    @Published var items: [HistoryItem] = []
    @Published var totalHistoryCount: Int = 0

    private var defaults: UserDefaults {
        UserDefaults(suiteName: Self.appGroupId) ?? UserDefaults.standard
    }

    init() {
        loadHistory()
    }

    func loadHistory() {
        Task { [weak self] in
            guard let self = self else { return }
            await self.migrateRemainingUserDefaultsIfNeeded()
            await self.syncFromKmpStore()
            await self.hydrateMissingThumbnails()
        }
    }

    private func migrateRemainingUserDefaultsIfNeeded() async {
        guard let data = defaults.data(forKey: Self.storageKey) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .secondsSince1970
        var legacyItems: [HistoryItem] = []

        if let decoded = try? decoder.decode([HistoryItem].self, from: data) {
            legacyItems = decoded
        } else if let array = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] {
            for dict in array {
                if let itemData = try? JSONSerialization.data(withJSONObject: dict),
                   let item = try? decoder.decode(HistoryItem.self, from: itemData) {
                    legacyItems.append(item)
                }
            }
        }

        if !legacyItems.isEmpty {
            for item in legacyItems.reversed() {
                try? await SongLinkEngine.shared.cache.addHistoryEntry(
                    sourceUrl: item.sourceUrl,
                    targetPlatformKey: item.targetPlatform,
                    targetUrl: item.targetUrl,
                    title: item.title,
                    artist: item.artist,
                    isAlbum: item.isAlbum,
                    thumbnailUrl: item.thumbnailUrl,
                    currentTimeMs: Int64(item.timestamp.timeIntervalSince1970 * 1000),
                    isAcoustic: item.isAcoustic
                )
            }
        }
        defaults.removeObject(forKey: Self.storageKey)
    }

    @MainActor
    func syncFromKmpStore() async {
        do {
            try await SongLinkEngine.shared.cache.syncFromStorage()
            let maxLimit: Int32 = ProManager.shared.isPro ? 100 : 10
            let kmpEntries = try await SongLinkEngine.shared.cache.getHistoryEntries(limit: maxLimit)
            let count = try await SongLinkEngine.shared.cache.getHistoryCount()
            self.items = kmpEntries.map { HistoryItem(from: $0) }
            self.totalHistoryCount = Int(count)
        } catch {
            Self.logger.error("Failed to sync history from KMP SQLDelight store: \(error.localizedDescription, privacy: .public)")
        }
    }

    func hydrateMissingThumbnails() async {
        let currentItems = await MainActor.run { self.items }
        let missing = currentItems.filter { $0.thumbnailUrl == nil || $0.thumbnailUrl?.isEmpty == true }
        guard !missing.isEmpty else { return }

        var hasUpdates = false
        for item in missing {
            if item.targetPlatform.contains("_playlist") { continue }
            do {
                let res = try await SongLinkEngine.shared.resolveTargetUrl(
                    inputUrl: item.sourceUrl,
                    targetPlatformKey: item.targetPlatform,
                    customApiUrl: "",
                    customApiToken: "",
                    forceRefresh: true
                )
                if let success = res as? ResolutionResult.Success, let thumb = success.thumbnailUrl, !thumb.isEmpty {
                    hasUpdates = true
                }
            } catch {
                Self.logger.debug("Thumbnail hydration failed: \(error.localizedDescription, privacy: .public)")
            }
        }
        if hasUpdates {
            await syncFromKmpStore()
        }
    }

    func add(
        title: String,
        artist: String?,
        sourceUrl: String,
        targetUrl: String,
        targetPlatform: String,
        isAlbum: Bool = false,
        thumbnailUrl: String? = nil,
        isAcoustic: Bool = false
    ) {
        Task { [weak self] in
            do {
                try await SongLinkEngine.shared.cache.addHistoryEntry(
                    sourceUrl: sourceUrl,
                    targetPlatformKey: targetPlatform,
                    targetUrl: targetUrl,
                    title: title,
                    artist: artist,
                    isAlbum: isAlbum,
                    thumbnailUrl: thumbnailUrl,
                    currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                    isAcoustic: isAcoustic
                )
                await self?.syncFromKmpStore()
            } catch {
                Self.logger.error("Failed to add history entry: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func updateItem(
        id: UUID,
        newTargetUrl: String,
        newTitle: String?,
        newArtist: String?,
        newTargetPlatform: String? = nil,
        isAlbum: Bool,
        thumbnailUrl: String? = nil
    ) {
        guard let existing = items.first(where: { $0.id == id }) else { return }
        let cacheKey = existing.cacheKey
        Task { [weak self] in
            do {
                try await SongLinkEngine.shared.cache.updateHistoryEntry(
                    cacheKey: cacheKey,
                    newTargetUrl: newTargetUrl,
                    newTitle: newTitle,
                    newArtist: newArtist,
                    newPlatform: newTargetPlatform,
                    isAlbum: KotlinBoolean(bool: isAlbum),
                    thumbnailUrl: thumbnailUrl,
                    currentTimeMs: Int64(Date().timeIntervalSince1970 * 1000)
                )
                await self?.syncFromKmpStore()
            } catch {
                Self.logger.error("Failed to update history entry: \(error.localizedDescription, privacy: .public)")
            }
        }
    }

    func toggleFavorite(item: HistoryItem, isPro: Bool) async -> FavoriteToggleResult {
        do {
            let result = try await SongLinkEngine.shared.cache.toggleFavoriteWithLimit(
                cacheKey: item.cacheKey,
                isPro: isPro,
                maxFreeFavorites: 10
            )
            await syncFromKmpStore()
            return result
        } catch {
            Self.logger.error("Failed to toggle favorite: \(error.localizedDescription, privacy: .public)")
            return .notFound
        }
    }

    func delete(at offsets: IndexSet) {
        let keysToDelete = offsets.compactMap { idx -> String? in
            guard idx < items.count else { return nil }
            return items[idx].cacheKey
        }
        items.remove(atOffsets: offsets)
        Task { [weak self] in
            for key in keysToDelete {
                try? await SongLinkEngine.shared.cache.removeByCacheKey(cacheKey: key)
            }
            await self?.syncFromKmpStore()
        }
    }

    func deleteItem(id: UUID) {
        guard let existing = items.first(where: { $0.id == id }) else { return }
        let key = existing.cacheKey
        items.removeAll { $0.id == id }
        Task { [weak self] in
            try? await SongLinkEngine.shared.cache.removeByCacheKey(cacheKey: key)
            await self?.syncFromKmpStore()
        }
    }

    func clear() {
        items.removeAll()
        totalHistoryCount = 0
        Task { [weak self] in
            try? await SongLinkEngine.shared.cache.clearHistoryAndCache()
            await self?.syncFromKmpStore()
        }
    }
}

extension SongLinkEngine {
    public nonisolated static let shared = SongLinkEngine.companion.shared
}
