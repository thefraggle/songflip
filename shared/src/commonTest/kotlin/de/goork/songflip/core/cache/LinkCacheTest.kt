package de.goork.songflip.core.cache

import de.goork.songflip.core.model.ResolutionResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkCacheTest {

    @Test
    fun testPutAndGet() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 1000L)
        val result = ResolutionResult.Success(
            targetUrl = "https://music.youtube.com/watch?v=123",
            platform = "youtubeMusic",
            title = "Test Song",
            artist = "Test Artist"
        )

        cache.put("https://open.spotify.com/track/123", "youtubeMusic", result, currentTimeMs = 1000L)
        assertEquals(1, cache.size())

        val retrieved = cache.get("https://open.spotify.com/track/123", "youtubeMusic", currentTimeMs = 1500L)
        assertNotNull(retrieved)
        assertEquals("https://music.youtube.com/watch?v=123", retrieved.targetUrl)
        assertEquals("Test Song", retrieved.title)
    }

    @Test
    fun testTtlExpiry() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 1000L)
        val result = ResolutionResult.Success(
            targetUrl = "https://music.youtube.com/watch?v=123",
            platform = "youtubeMusic"
        )

        // Pure cache entry (prefetch). History entries are retained longer by design (#105),
        // see testHistoryOutlivesResolverTtl.
        cache.put("https://open.spotify.com/track/123", "youtubeMusic", result, currentTimeMs = 1000L, isHistory = false)

        // After 1001ms, it should be expired
        val expired = cache.get("https://open.spotify.com/track/123", "youtubeMusic", currentTimeMs = 2001L)
        assertNull(expired)
        assertEquals(0, cache.size())
    }

    @Test
    fun testMaxEntriesEviction() = runTest {
        val cache = LinkCache(maxEntries = 3, ttlMs = 100000L)

        for (i in 1..4) {
            val result = ResolutionResult.Success(
                targetUrl = "https://music.youtube.com/watch?v=$i",
                platform = "youtubeMusic"
            )
            cache.put("https://open.spotify.com/track/$i", "youtubeMusic", result, currentTimeMs = i.toLong())
        }

        assertEquals(3, cache.size())
        // Oldest item (1) should have been evicted
        val item1 = cache.get("https://open.spotify.com/track/1", "youtubeMusic", currentTimeMs = 10L)
        assertNull(item1)

        // Items 2, 3, 4 should still exist
        assertNotNull(cache.get("https://open.spotify.com/track/2", "youtubeMusic", currentTimeMs = 10L))
        assertNotNull(cache.get("https://open.spotify.com/track/3", "youtubeMusic", currentTimeMs = 10L))
        assertNotNull(cache.get("https://open.spotify.com/track/4", "youtubeMusic", currentTimeMs = 10L))
    }

    @Test
    fun testClear() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 100000L)
        cache.put(
            "https://open.spotify.com/track/1",
            "youtubeMusic",
            ResolutionResult.Success(targetUrl = "url", platform = "yt"),
            currentTimeMs = 100L
        )
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun testStoragePersistenceAcrossInstances() = runTest {
        // In-memory fake storage simulating persistent shared storage (e.g. App Group UserDefaults)
        val sharedStorage = object : CacheStorage {
            val map = mutableMapOf<String, CacheEntry>()
            override fun get(key: String): CacheEntry? = map[key]
            override fun put(key: String, entry: CacheEntry) { map[key] = entry }
            override fun remove(key: String) { map.remove(key) }
            override fun clear() { map.clear() }
            override fun loadAll(): Map<String, CacheEntry> = map.toMap()
        }

        // Process 1 (e.g. ShareExtension): resolves and caches song
        val cache1 = LinkCache(maxEntries = 10, ttlMs = 100000L, storage = sharedStorage)
        cache1.put(
            canonicalUrl = "https://open.spotify.com/track/shared123",
            targetPlatformKey = "appleMusic",
            result = ResolutionResult.Success(targetUrl = "https://music.apple.com/song/shared123", platform = "appleMusic", title = "Shared Song"),
            currentTimeMs = 1000L
        )

        // Process 2 (e.g. Main App or App Intent): new LinkCache instance accessing the same storage
        val cache2 = LinkCache(maxEntries = 10, ttlMs = 100000L, storage = sharedStorage)
        val resolvedInProcess2 = cache2.get("https://open.spotify.com/track/shared123", "appleMusic", currentTimeMs = 2000L)

        assertNotNull(resolvedInProcess2, "Item cached in Process 1 must be retrievable in Process 2 from shared storage")
        assertEquals("https://music.apple.com/song/shared123", resolvedInProcess2.targetUrl)
        assertEquals("Shared Song", resolvedInProcess2.title)
    }

    @Test
    fun testHistoryTrackingAndPrefetch() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 100000L)
        val now = 5000L

        // 1. Put a prefetch item (isHistory = false)
        cache.put(
            canonicalUrl = "https://open.spotify.com/track/prefetch1",
            targetPlatformKey = "youtubeMusic",
            result = ResolutionResult.Success(targetUrl = "https://music.youtube.com/watch?v=pf1", platform = "youtubeMusic", title = "Prefetch Song"),
            currentTimeMs = now,
            isHistory = false
        )

        // History count should be 0 because it was a prefetch
        assertEquals(0, cache.getHistoryCount(currentTimeMs = now))
        assertTrue(cache.getHistoryEntries(limit = 10, currentTimeMs = now).isEmpty())

        // Cache get still works
        val cached = cache.get("https://open.spotify.com/track/prefetch1", "youtubeMusic", currentTimeMs = now)
        assertNotNull(cached)

        // 2. Mark as history (when user actually clicks / shares)
        cache.markAsHistory("https://open.spotify.com/track/prefetch1", "youtubeMusic", currentTimeMs = now + 100L)
        assertEquals(1, cache.getHistoryCount(currentTimeMs = now + 100L))
        val historyList = cache.getHistoryEntries(limit = 10, currentTimeMs = now + 100L)
        assertEquals(1, historyList.size)
        assertEquals("Prefetch Song", historyList[0].title)

        // 3. Put normal item (isHistory = true)
        cache.put(
            canonicalUrl = "https://open.spotify.com/track/normal2",
            targetPlatformKey = "youtubeMusic",
            result = ResolutionResult.Success(targetUrl = "https://music.youtube.com/watch?v=n2", platform = "youtubeMusic", title = "Normal Song"),
            currentTimeMs = now + 200L,
            isHistory = true
        )

        assertEquals(2, cache.getHistoryCount(currentTimeMs = now + 200L))
        val updatedHistory = cache.getHistoryEntries(limit = 10, currentTimeMs = now + 200L)
        assertEquals(2, updatedHistory.size)
        assertEquals("Normal Song", updatedHistory[0].title) // Newer first

        // 4. Clear history
        cache.clearHistoryAndCache()
        assertEquals(0, cache.getHistoryCount(currentTimeMs = now + 300L))
        assertEquals(0, cache.size())
    }

    // --- #105: favorites & history are user data, not disposable cache ---

    private fun inMemoryStorage() = object : CacheStorage {
        val map = mutableMapOf<String, CacheEntry>()
        override fun get(key: String): CacheEntry? = map[key]
        override fun put(key: String, entry: CacheEntry) { map[key] = entry }
        override fun remove(key: String) { map.remove(key) }
        override fun clear() { map.clear() }
        override fun loadAll(): Map<String, CacheEntry> = map.toMap()
    }

    private fun song(i: Int) = ResolutionResult.Success(
        targetUrl = "https://music.youtube.com/watch?v=$i",
        platform = "youtubeMusic",
        title = "Song $i"
    )

    @Test
    fun testFavoriteSurvivesTtlAndRestart() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val storage = inMemoryStorage()
        val cache = LinkCache(maxEntries = 10, ttlMs = 7 * day, storage = storage)
        cache.put("https://open.spotify.com/track/fav", "youtubeMusic", song(1), currentTimeMs = 0L)
        cache.setFavoriteForUrl("https://open.spotify.com/track/fav", "youtubeMusic", true)

        // Fresh instance a year later simulates an app restart
        val later = 365 * day
        val restarted = LinkCache(maxEntries = 10, ttlMs = 7 * day, storage = storage)
        val history = restarted.getHistoryEntries(limit = 10, currentTimeMs = later)

        assertEquals(1, history.size, "Favorite must not expire with the resolver TTL")
        assertTrue(history[0].isFavorite)
        // Stale resolution is not reused, but the favorite itself stays on disk
        assertNull(restarted.get("https://open.spotify.com/track/fav", "youtubeMusic", currentTimeMs = later))
        assertEquals(1, restarted.getHistoryCount(currentTimeMs = later))
    }

    @Test
    fun testFavoriteIsNeverEvictedByLru() = runTest {
        val cache = LinkCache(maxEntries = 3, ttlMs = 100_000L)
        cache.put("https://open.spotify.com/track/0", "youtubeMusic", song(0), currentTimeMs = 0L)
        cache.setFavoriteForUrl("https://open.spotify.com/track/0", "youtubeMusic", true)

        for (i in 1..10) {
            cache.put("https://open.spotify.com/track/$i", "youtubeMusic", song(i), currentTimeMs = i.toLong())
        }

        val history = cache.getHistoryEntries(limit = 0, currentTimeMs = 20L)
        assertTrue(history.any { it.isFavorite && it.title == "Song 0" }, "Favorite must survive LRU eviction")
        assertEquals(3, history.count { !it.isFavorite }, "Non-favorites are still capped at maxEntries")
    }

    @Test
    fun testReFlipKeepsFavorite() = runTest {
        val cache = LinkCache(maxEntries = 10, ttlMs = 100_000L)
        cache.put("https://open.spotify.com/track/1", "youtubeMusic", song(1), currentTimeMs = 0L)
        cache.setFavoriteForUrl("https://open.spotify.com/track/1", "youtubeMusic", true)

        // Resolving the same link again must not unpin it
        cache.put("https://open.spotify.com/track/1", "youtubeMusic", song(1), currentTimeMs = 50L)

        val history = cache.getHistoryEntries(limit = 10, currentTimeMs = 60L)
        assertTrue(history.single().isFavorite)
    }

    @Test
    fun testHistoryOutlivesResolverTtl() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val cache = LinkCache(maxEntries = 10, ttlMs = 7 * day, historyRetentionMs = 90 * day)
        cache.put("https://open.spotify.com/track/h", "youtubeMusic", song(1), currentTimeMs = 0L)

        assertEquals(1, cache.getHistoryCount(currentTimeMs = 30 * day), "History stays visible after cache TTL")
        assertEquals(0, cache.getHistoryCount(currentTimeMs = 91 * day), "History expires after retention window")
    }

    @Test
    fun testHistoryIsRetainedIndefinitelyByDefault() = runTest {
        val day = 24 * 60 * 60 * 1000L
        val cache = LinkCache(maxEntries = 10, ttlMs = 7 * day) // default retention: unlimited
        cache.put("https://open.spotify.com/track/h", "youtubeMusic", song(1), currentTimeMs = 0L)

        assertEquals(1, cache.getHistoryCount(currentTimeMs = 365 * day), "History stays visible indefinitely by default")
    }

    // --- #107 & #115: SQLDelight Store, Legacy Migration, and 10-Favorite Free Limit ---

    @Test
    fun testSqlDelightPlatformStorageSeparatesHistoryAndResolverCache() = runTest {
        val storage = createDefaultCacheStorage()
        storage.clear()

        val cache = LinkCache(maxEntries = 10, ttlMs = 1000L, storage = storage)
        cache.put("https://open.spotify.com/track/prefetch_only", "youtubeMusic", song(1), currentTimeMs = 100L, isHistory = false)
        cache.put("https://open.spotify.com/track/history_item", "youtubeMusic", song(2), currentTimeMs = 200L, isHistory = true)
        cache.setFavoriteForUrl("https://open.spotify.com/track/history_item", "youtubeMusic", true)

        val reloaded = LinkCache(maxEntries = 10, ttlMs = 1000L, storage = storage)
        assertEquals(1, reloaded.getHistoryCount(currentTimeMs = 5000L), "Only history item should appear in history count")
        val historyEntries = reloaded.getHistoryEntries(limit = 10, currentTimeMs = 5000L)
        assertEquals(1, historyEntries.size)
        assertEquals("Song 2", historyEntries[0].title)
        assertTrue(historyEntries[0].isFavorite)

        // Prefetch item expired after TTL (1000ms)
        assertNull(reloaded.get("https://open.spotify.com/track/prefetch_only", "youtubeMusic", currentTimeMs = 5000L))
        storage.clear()
    }

    @Test
    fun testLegacyIosHistoryJsonMigrationPreservesDataAndHealsTimestamps() = runTest {
        val storage = createDefaultCacheStorage() as? SqlDelightCacheStorage
        assertNotNull(storage, "Platform storage should be SqlDelightCacheStorage")
        storage.clear()

        val legacyIosJson = """
            [
                {
                    "id": "11111111-2222-3333-4444-555555555555",
                    "timestamp": 1710000000.0,
                    "title": "Legacy iOS Song",
                    "artist": "Legacy Artist",
                    "sourceUrl": "https://open.spotify.com/track/ios123?si=abc",
                    "targetUrl": "https://music.apple.com/us/album/ios123",
                    "targetPlatform": "appleMusic",
                    "isAlbum": false,
                    "thumbnailUrl": "https://example.com/cover.jpg",
                    "isFavorite": true
                },
                {
                    "id": "22222222-2222-3333-4444-555555555555",
                    "timestamp": 2688307200.0,
                    "title": "Year 2055 Bug Song",
                    "artist": "Healed Artist",
                    "sourceUrl": "https://open.spotify.com/track/ios2055",
                    "targetUrl": "https://music.apple.com/us/album/ios2055",
                    "targetPlatform": "appleMusic",
                    "isAlbum": true
                }
            ]
        """.trimIndent()

        val parsed = SqlDelightCacheStorage.parseLegacyIosHistoryJson(legacyIosJson, currentTimeMs = 1720000000000L)
        assertEquals(2, parsed.size)

        val migrated = storage.migrateLegacyEntries(parsed, "test_ios_migration_v1")
        assertTrue(migrated, "First migration should execute and return true")

        // Second call with same migration key must be idempotent no-op
        val secondRun = storage.migrateLegacyEntries(parsed, "test_ios_migration_v1")
        assertTrue(!secondRun, "Second migration call with same key should be skipped")

        val cache = LinkCache(maxEntries = 20, storage = storage)
        val entries = cache.getHistoryEntries(limit = 10)
        assertEquals(2, entries.size)
        val favItem = entries.first { it.title == "Legacy iOS Song" }
        assertTrue(favItem.isFavorite)
        assertEquals("Legacy Artist", favItem.artist)
        assertEquals("https://open.spotify.com/track/ios123?si=abc", favItem.canonicalUrl)

        val healedItem = entries.first { it.title == "Year 2055 Bug Song" }
        assertEquals(1710000000000L, healedItem.timestamp, "2057/2055 reference date offset should be healed to 1710000000000ms")
        storage.clear()
    }

    @Test
    fun testFreeFavoriteLimitEnforcedAt10AndUnlimitedForPro() = runTest {
        val cache = LinkCache(maxEntries = 50, ttlMs = 100_000L)
        for (i in 1..12) {
            cache.put("https://open.spotify.com/track/$i", "youtubeMusic", song(i), currentTimeMs = i.toLong())
        }

        // Free user can favorite 10 items
        for (i in 1..10) {
            val key = "https://open.spotify.com/track/$i|youtubeMusic"
            val res = cache.toggleFavoriteWithLimit(key, isPro = false, maxFreeFavorites = 10)
            assertEquals(FavoriteToggleResult.ADDED, res, "Item $i should be added as favorite for Free user")
        }
        assertEquals(10, cache.getFavoriteCount())

        // 11th favorite for Free user must return LIMIT_REACHED and not be added
        val key11 = "https://open.spotify.com/track/11|youtubeMusic"
        val res11 = cache.toggleFavoriteWithLimit(key11, isPro = false, maxFreeFavorites = 10)
        assertEquals(FavoriteToggleResult.LIMIT_REACHED, res11, "11th favorite must be blocked for Free user")
        assertEquals(10, cache.getFavoriteCount())

        // Unfavoriting an existing favorite is always allowed for Free user
        val key1 = "https://open.spotify.com/track/1|youtubeMusic"
        val resRemove = cache.toggleFavoriteWithLimit(key1, isPro = false, maxFreeFavorites = 10)
        assertEquals(FavoriteToggleResult.REMOVED, resRemove)
        assertEquals(9, cache.getFavoriteCount())

        // Now Free user can add 11th since count is 9
        val res11Retry = cache.toggleFavoriteWithLimit(key11, isPro = false, maxFreeFavorites = 10)
        assertEquals(FavoriteToggleResult.ADDED, res11Retry)
        assertEquals(10, cache.getFavoriteCount())

        // Pro user can add 11th and 12th without limit
        val res1Pro = cache.toggleFavoriteWithLimit(key1, isPro = true, maxFreeFavorites = 10)
        val res12Pro = cache.toggleFavoriteWithLimit("https://open.spotify.com/track/12|youtubeMusic", isPro = true, maxFreeFavorites = 10)
        assertEquals(FavoriteToggleResult.ADDED, res1Pro)
        assertEquals(FavoriteToggleResult.ADDED, res12Pro)
        assertEquals(12, cache.getFavoriteCount())
    }

    @Test
    fun testSwiftConvenienceOverloads() = runTest {
        val cache = LinkCache(maxEntries = 10)
        val key = cache.addHistoryEntry(
            sourceUrl = "https://open.spotify.com/track/swift_overload",
            targetPlatformKey = "appleMusic",
            targetUrl = "https://music.apple.com/song",
            title = "Swift Title",
            artist = "Swift Artist",
            isAlbum = false,
            thumbnailUrl = "https://img.com/art.jpg",
            currentTimeMs = 123456L
        )

        assertEquals("https://open.spotify.com/track/swift_overload|appleMusic", key)
        assertEquals(1, cache.getHistoryCount())
        val entries = cache.getHistoryEntries(limit = 5)
        assertEquals(1, entries.size)
        assertEquals("Swift Title", entries[0].title)

        cache.syncFromStorage()
        val toggleRes = cache.toggleFavoriteWithLimit(key, isPro = false, maxFreeFavorites = 10)
        assertEquals(FavoriteToggleResult.ADDED, toggleRes)
        assertFalse(cache.toggleFavorite(key)) // toggled from true to false
        assertTrue(cache.toggleFavorite(key))  // toggled from false to true

        val updated = cache.updateHistoryEntry(
            cacheKey = key,
            newTargetUrl = "https://music.apple.com/updated",
            newTitle = "Updated Title",
            newArtist = "Updated Artist",
            newPlatform = "appleMusic",
            isAlbum = false,
            thumbnailUrl = null
        )
        assertTrue(updated)

        val targetUpdated = cache.updateTargetUrl(
            cacheKey = key,
            newTargetUrl = "https://music.apple.com/target2",
            newNativeAppUri = "music://target2"
        )
        assertTrue(targetUpdated)
    }

    @Test
    fun testEvictOverflowPreservesHistoryInStorageAndEvictsOldestAfterSync() = runTest {
        val storage = createDefaultCacheStorage()
        storage.clear()

        val cache = LinkCache(maxEntries = 3, ttlMs = 100_000L, storage = storage)
        for (i in 1..4) {
            cache.put("https://open.spotify.com/track/hist_$i", "youtubeMusic", song(i), currentTimeMs = (i * 1000).toLong(), isHistory = true)
        }

        // In-memory cache is capped at maxEntries = 3, but persistent history retains all 4 items
        assertEquals(3, cache.size())
        assertEquals(4, cache.getHistoryCount(currentTimeMs = 5000L))

        // Syncing from storage must keep oldest entries first in LinkedHashMap so subsequent eviction removes the oldest
        val reloaded = LinkCache(maxEntries = 4, ttlMs = 100_000L, storage = storage)
        reloaded.syncFromStorage()
        reloaded.put("https://open.spotify.com/track/hist_5", "youtubeMusic", song(5), currentTimeMs = 5000L, isHistory = true)
        assertEquals(4, reloaded.size())
        assertEquals(5, reloaded.getHistoryCount(currentTimeMs = 6000L))

        // Favoriting a prefetched (isHistory = false) item via toggleFavorite or setFavorite promotes it to history
        cache.put("https://open.spotify.com/track/pref_fav", "youtubeMusic", song(99), currentTimeMs = 6000L, isHistory = false)
        cache.toggleFavorite("https://open.spotify.com/track/pref_fav|youtubeMusic", currentTimeMs = 6000L)
        val entriesAfterFav = cache.getHistoryEntries(limit = 10, currentTimeMs = 6000L)
        assertTrue(entriesAfterFav.any { it.cacheKey == "https://open.spotify.com/track/pref_fav|youtubeMusic" && it.isFavorite })

        storage.clear()
    }
}



