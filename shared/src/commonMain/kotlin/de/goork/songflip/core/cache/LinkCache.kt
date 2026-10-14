package de.goork.songflip.core.cache

import de.goork.songflip.core.engine.getCurrentTimeMillis
import de.goork.songflip.core.model.ResolutionResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

@Serializable
data class CacheEntry(
    val targetUrl: String,
    val platform: String,
    val title: String? = null,
    val artist: String? = null,
    val isAlbum: Boolean = false,
    val nativeAppUri: String? = null,
    val timestamp: Long,
    val isHistory: Boolean = true,
    val thumbnailUrl: String? = null,
    val isFavorite: Boolean = false
)

data class LinkHistoryItem(
    val cacheKey: String,
    val canonicalUrl: String,
    val targetPlatformKey: String,
    val targetUrl: String,
    val platform: String,
    val title: String? = null,
    val artist: String? = null,
    val isAlbum: Boolean = false,
    val timestamp: Long,
    val thumbnailUrl: String? = null,
    val isFavorite: Boolean = false
)

interface CacheStorage {
    fun get(key: String): CacheEntry?
    fun put(key: String, entry: CacheEntry)
    fun remove(key: String)
    fun clear()
    fun loadAll(): Map<String, CacheEntry>
}

class NoOpCacheStorage : CacheStorage {
    override fun get(key: String): CacheEntry? = null
    override fun put(key: String, entry: CacheEntry) {}
    override fun remove(key: String) {}
    override fun clear() {}
    override fun loadAll(): Map<String, CacheEntry> = emptyMap()
}

expect fun createDefaultCacheStorage(): CacheStorage

class LinkCache(
    private val maxEntries: Int = 200,
    private val ttlMs: Long = 7 * 24 * 60 * 60 * 1000L,
    private val storage: CacheStorage = NoOpCacheStorage(),
    // History is user data and retained indefinitely by default (#105)
    private val historyRetentionMs: Long = Long.MAX_VALUE
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CacheEntry>()
    private var initialized = false

    /** Whether a cached resolution may be reused without hitting the network. */
    private fun CacheEntry.isFresh(now: Long) = (now - timestamp) <= ttlMs

    /** Whether the entry must be kept on disk. Favorites and history are kept indefinitely. */
    private fun CacheEntry.isRetained(now: Long) = when {
        isFavorite -> true
        isHistory -> historyRetentionMs == Long.MAX_VALUE || (now - timestamp) <= historyRetentionMs
        else -> isFresh(now)
    }

    /** Evicts the least recently used non-favorite entries; favorites never count against the limit. */
    private fun evictOverflow() {
        var evictable = entries.count { !it.value.isFavorite }
        while (evictable > maxEntries) {
            val oldestKey = entries.entries.firstOrNull { !it.value.isFavorite }?.key ?: break
            entries.remove(oldestKey)
            storage.remove(oldestKey)
            evictable--
        }
    }

    private fun ensureLoaded(currentTimeMs: Long) {
        if (!initialized) {
            initialized = true
            val loaded = storage.loadAll()
            for ((k, v) in loaded) {
                if (!v.isRetained(currentTimeMs)) {
                    storage.remove(k)
                }
            }

            loaded.entries
                .filter { it.value.isRetained(currentTimeMs) }
                .sortedBy { it.value.timestamp }
                .forEach { (k, v) -> entries[k] = v }

            evictOverflow()
        }
    }

    suspend fun get(canonicalUrl: String, targetPlatformKey: String, currentTimeMs: Long = getCurrentTimeMillis()): ResolutionResult.Success? = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        var entry = entries[key]
        if (entry == null) {
            val stored = storage.get(key)
            if (stored != null) {
                if (stored.isRetained(currentTimeMs)) {
                    entries[key] = stored
                    entry = stored
                    evictOverflow()
                } else {
                    storage.remove(key)
                }
            }
        }

        if (entry == null) return@withLock null

        if (!entry.isFresh(currentTimeMs)) {
            // Stale resolution: force a re-resolve, but keep history/favorites intact
            if (!entry.isRetained(currentTimeMs)) {
                entries.remove(key)
                storage.remove(key)
            }
            return@withLock null
        }

        // Refresh LRU order
        entries.remove(key)
        entries[key] = entry

        ResolutionResult.Success(
            targetUrl = entry.targetUrl,
            platform = entry.platform,
            title = entry.title,
            artist = entry.artist,
            isAlbum = entry.isAlbum,
            nativeAppUri = entry.nativeAppUri,
            thumbnailUrl = entry.thumbnailUrl
        )
    }

    suspend fun put(
        canonicalUrl: String,
        targetPlatformKey: String,
        result: ResolutionResult.Success,
        currentTimeMs: Long = getCurrentTimeMillis(),
        isHistory: Boolean = true
    ) = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val existing = entries[key] ?: storage.get(key)
        val finalIsHistory = (existing?.isHistory == true) || isHistory

        val entry = CacheEntry(
            targetUrl = result.targetUrl,
            platform = result.platform,
            title = result.title,
            artist = result.artist,
            isAlbum = result.isAlbum,
            nativeAppUri = result.nativeAppUri,
            timestamp = currentTimeMs,
            isHistory = finalIsHistory,
            thumbnailUrl = result.thumbnailUrl ?: existing?.thumbnailUrl,
            // Re-flipping a favorite must not silently unpin it (#105)
            isFavorite = existing?.isFavorite == true
        )

        entries.remove(key)
        entries[key] = entry
        storage.put(key, entry)

        evictOverflow()
    }

    suspend fun putPodcast(
        canonicalUrl: String,
        targetPlatformKey: String,
        result: ResolutionResult.Podcast,
        currentTimeMs: Long = getCurrentTimeMillis(),
        isHistory: Boolean = true
    ) = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val existing = entries[key] ?: storage.get(key)
        val finalIsHistory = (existing?.isHistory == true) || isHistory

        val entry = CacheEntry(
            targetUrl = result.targetUrl,
            platform = result.platform,
            title = result.episodeTitle ?: result.showTitle,
            artist = if (result.episodeTitle != null) result.showTitle else null,
            isAlbum = false,
            nativeAppUri = result.nativeAppUri,
            timestamp = currentTimeMs,
            isHistory = finalIsHistory,
            thumbnailUrl = result.thumbnailUrl ?: existing?.thumbnailUrl,
            isFavorite = existing?.isFavorite == true
        )

        entries.remove(key)
        entries[key] = entry
        storage.put(key, entry)

        evictOverflow()
    }

    suspend fun markAsHistory(canonicalUrl: String, targetPlatformKey: String, currentTimeMs: Long = getCurrentTimeMillis()) = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val existing = entries[key] ?: storage.get(key)
        if (existing != null) {
            val updated = existing.copy(isHistory = true, timestamp = currentTimeMs)
            entries.remove(key)
            entries[key] = updated
            storage.put(key, updated)
        }
    }

    suspend fun remove(canonicalUrl: String, targetPlatformKey: String) = mutex.withLock {
        val key = buildKey(canonicalUrl, targetPlatformKey)
        entries.remove(key)
        storage.remove(key)
    }

    suspend fun removeByCacheKey(cacheKey: String) = mutex.withLock {
        entries.remove(cacheKey)
        storage.remove(cacheKey)
    }

    suspend fun clear() = mutex.withLock {
        initialized = true
        entries.clear()
        storage.clear()
    }

    suspend fun clearHistoryAndCache() = clear()

    suspend fun size(): Int = mutex.withLock {
        entries.size
    }

    suspend fun syncFromStorage(): Unit = mutex.withLock {
        ensureLoaded(getCurrentTimeMillis())
        syncFromStorage(getCurrentTimeMillis())
    }

    private fun syncFromStorage(currentTimeMs: Long): Map<String, CacheEntry> {
        val allLoaded = storage.loadAll()
        for ((k, loadedEntry) in allLoaded) {
            if (loadedEntry.isRetained(currentTimeMs)) {
                val memEntry = entries[k]
                if (memEntry == null || loadedEntry.timestamp >= memEntry.timestamp || loadedEntry.isFavorite != memEntry.isFavorite) {
                    entries[k] = loadedEntry
                }
            }
        }
        return allLoaded
    }

    suspend fun getHistoryEntries(limit: Int = 10, currentTimeMs: Long = getCurrentTimeMillis()): List<LinkHistoryItem> = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val allLoaded = syncFromStorage(currentTimeMs)
        val combinedKeys = mutableSetOf<String>()
        combinedKeys.addAll(entries.keys)
        combinedKeys.addAll(allLoaded.keys)

        val allItems = mutableListOf<LinkHistoryItem>()
        for (key in combinedKeys) {
            val entry = entries[key] ?: allLoaded[key]
            if (entry != null && entry.isHistory && entry.isRetained(currentTimeMs) && entry.targetUrl.isNotBlank()) {
                val canonicalUrl = key.substringBeforeLast("|")
                val targetPlatformKey = key.substringAfterLast("|", entry.platform)
                allItems.add(
                    LinkHistoryItem(
                        cacheKey = key,
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = entry.targetUrl,
                        platform = entry.platform,
                        title = entry.title,
                        artist = entry.artist,
                        isAlbum = entry.isAlbum,
                        timestamp = entry.timestamp,
                        thumbnailUrl = entry.thumbnailUrl,
                        isFavorite = entry.isFavorite
                    )
                )
            }
        }

        // Favorites pinned to the top, then sorted by timestamp descending
        allItems.sortWith(compareByDescending<LinkHistoryItem> { it.isFavorite }.thenByDescending { it.timestamp })
        if (limit > 0 && allItems.size > limit) {
            allItems.subList(0, limit)
        } else {
            allItems
        }
    }

    suspend fun getHistoryEntries(limit: Int): List<LinkHistoryItem> =
        getHistoryEntries(limit = limit, currentTimeMs = getCurrentTimeMillis())

    suspend fun getHistoryEntries(): List<LinkHistoryItem> =
        getHistoryEntries(limit = 10, currentTimeMs = getCurrentTimeMillis())

    private fun countFavoritesInternal(currentTimeMs: Long): Int {
        val allLoaded = syncFromStorage(currentTimeMs)
        val combinedKeys = mutableSetOf<String>()
        combinedKeys.addAll(entries.keys)
        combinedKeys.addAll(allLoaded.keys)

        var count = 0
        for (key in combinedKeys) {
            val entry = entries[key] ?: allLoaded[key]
            if (entry != null && entry.isFavorite && entry.targetUrl.isNotBlank()) {
                count++
            }
        }
        return count
    }

    suspend fun getFavoriteCount(currentTimeMs: Long = getCurrentTimeMillis()): Int = mutex.withLock {
        ensureLoaded(currentTimeMs)
        countFavoritesInternal(currentTimeMs)
    }

    suspend fun getFavoriteCount(): Int =
        getFavoriteCount(getCurrentTimeMillis())

    suspend fun toggleFavorite(cacheKey: String, currentTimeMs: Long = getCurrentTimeMillis()): Boolean = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val existing = entries[cacheKey] ?: storage.get(cacheKey)
        if (existing != null) {
            val newFav = !existing.isFavorite
            val updated = existing.copy(isFavorite = newFav)
            entries[cacheKey] = updated
            storage.put(cacheKey, updated)
            newFav
        } else {
            false
        }
    }

    suspend fun toggleFavorite(cacheKey: String): Boolean =
        toggleFavorite(cacheKey = cacheKey, currentTimeMs = getCurrentTimeMillis())

    suspend fun toggleFavoriteWithLimit(
        cacheKey: String,
        isPro: Boolean,
        maxFreeFavorites: Int = 10,
        currentTimeMs: Long = getCurrentTimeMillis()
    ): FavoriteToggleResult = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val existing = entries[cacheKey] ?: storage.get(cacheKey) ?: return@withLock FavoriteToggleResult.NOT_FOUND
        if (existing.isFavorite) {
            val updated = existing.copy(isFavorite = false)
            entries[cacheKey] = updated
            storage.put(cacheKey, updated)
            return@withLock FavoriteToggleResult.REMOVED
        }

        if (!isPro && maxFreeFavorites > 0) {
            val currentFavCount = countFavoritesInternal(currentTimeMs)
            if (currentFavCount >= maxFreeFavorites) {
                return@withLock FavoriteToggleResult.LIMIT_REACHED
            }
        }

        val updated = existing.copy(isFavorite = true, isHistory = true)
        entries[cacheKey] = updated
        storage.put(cacheKey, updated)
        FavoriteToggleResult.ADDED
    }

    suspend fun toggleFavoriteWithLimit(
        cacheKey: String,
        isPro: Boolean,
        maxFreeFavorites: Int = 10
    ): FavoriteToggleResult = toggleFavoriteWithLimit(
        cacheKey = cacheKey,
        isPro = isPro,
        maxFreeFavorites = maxFreeFavorites,
        currentTimeMs = getCurrentTimeMillis()
    )

    suspend fun setFavorite(cacheKey: String, isFavorite: Boolean, currentTimeMs: Long = getCurrentTimeMillis()) = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val existing = entries[cacheKey] ?: storage.get(cacheKey)
        if (existing != null) {
            val updated = existing.copy(isFavorite = isFavorite)
            entries[cacheKey] = updated
            storage.put(cacheKey, updated)
        }
    }

    suspend fun addHistoryEntry(
        canonicalUrl: String,
        targetPlatformKey: String,
        targetUrl: String,
        platform: String,
        title: String? = null,
        artist: String? = null,
        isAlbum: Boolean = false,
        thumbnailUrl: String? = null,
        timestamp: Long = getCurrentTimeMillis(),
        isFavorite: Boolean? = null
    ): String = mutex.withLock {
        ensureLoaded(timestamp)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val existing = entries[key] ?: storage.get(key)
        val resolvedFav = isFavorite ?: (existing?.isFavorite == true)

        val entry = CacheEntry(
            targetUrl = targetUrl,
            platform = platform,
            title = title ?: existing?.title,
            artist = artist ?: existing?.artist,
            isAlbum = isAlbum,
            nativeAppUri = existing?.nativeAppUri,
            timestamp = timestamp,
            isHistory = true,
            thumbnailUrl = thumbnailUrl ?: existing?.thumbnailUrl,
            isFavorite = resolvedFav
        )

        entries.remove(key)
        entries[key] = entry
        storage.put(key, entry)
        evictOverflow()
        key
    }

    suspend fun addHistoryEntry(
        sourceUrl: String,
        targetPlatformKey: String,
        targetUrl: String,
        title: String? = null,
        artist: String? = null,
        isAlbum: Boolean = false,
        thumbnailUrl: String? = null,
        currentTimeMs: Long = getCurrentTimeMillis()
    ): String = addHistoryEntry(
        canonicalUrl = sourceUrl,
        targetPlatformKey = targetPlatformKey,
        targetUrl = targetUrl,
        platform = targetPlatformKey,
        title = title,
        artist = artist,
        isAlbum = isAlbum,
        thumbnailUrl = thumbnailUrl,
        timestamp = currentTimeMs,
        isFavorite = null
    )

    suspend fun addHistoryEntry(
        sourceUrl: String,
        targetPlatformKey: String,
        targetUrl: String,
        title: String?,
        artist: String?,
        isAlbum: Boolean,
        thumbnailUrl: String?
    ): String = addHistoryEntry(
        sourceUrl = sourceUrl,
        targetPlatformKey = targetPlatformKey,
        targetUrl = targetUrl,
        title = title,
        artist = artist,
        isAlbum = isAlbum,
        thumbnailUrl = thumbnailUrl,
        currentTimeMs = getCurrentTimeMillis()
    )

    suspend fun updateHistoryEntry(
        cacheKey: String,
        newTargetUrl: String,
        newTitle: String? = null,
        newArtist: String? = null,
        newPlatform: String? = null,
        isAlbum: Boolean? = null,
        thumbnailUrl: String? = null,
        currentTimeMs: Long = getCurrentTimeMillis()
    ): Boolean = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val existing = entries[cacheKey] ?: storage.get(cacheKey) ?: return@withLock false
        val updated = existing.copy(
            targetUrl = newTargetUrl,
            platform = newPlatform ?: existing.platform,
            title = newTitle ?: existing.title,
            artist = newArtist ?: existing.artist,
            isAlbum = isAlbum ?: existing.isAlbum,
            thumbnailUrl = thumbnailUrl ?: existing.thumbnailUrl,
            timestamp = currentTimeMs,
            isHistory = true
        )
        entries[cacheKey] = updated
        storage.put(cacheKey, updated)
        true
    }

    suspend fun updateHistoryEntry(
        cacheKey: String,
        newTargetUrl: String,
        newTitle: String?,
        newArtist: String?,
        newPlatform: String?,
        isAlbum: Boolean?,
        thumbnailUrl: String?
    ): Boolean = updateHistoryEntry(
        cacheKey = cacheKey,
        newTargetUrl = newTargetUrl,
        newTitle = newTitle,
        newArtist = newArtist,
        newPlatform = newPlatform,
        isAlbum = isAlbum,
        thumbnailUrl = thumbnailUrl,
        currentTimeMs = getCurrentTimeMillis()
    )

    suspend fun updateTargetUrl(
        cacheKey: String,
        newTargetUrl: String,
        newNativeAppUri: String? = null,
        currentTimeMs: Long = getCurrentTimeMillis()
    ): Boolean = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val existing = entries[cacheKey] ?: storage.get(cacheKey)
        if (existing != null) {
            val updated = existing.copy(
                targetUrl = newTargetUrl,
                nativeAppUri = newNativeAppUri,
                timestamp = currentTimeMs
            )
            entries[cacheKey] = updated
            storage.put(cacheKey, updated)
            true
        } else {
            false
        }
    }

    suspend fun updateTargetUrl(
        cacheKey: String,
        newTargetUrl: String,
        newNativeAppUri: String?
    ): Boolean = updateTargetUrl(
        cacheKey = cacheKey,
        newTargetUrl = newTargetUrl,
        newNativeAppUri = newNativeAppUri,
        currentTimeMs = getCurrentTimeMillis()
    )

    suspend fun getHistoryCount(currentTimeMs: Long = getCurrentTimeMillis()): Int = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val allLoaded = syncFromStorage(currentTimeMs)
        val combinedKeys = mutableSetOf<String>()
        combinedKeys.addAll(entries.keys)
        combinedKeys.addAll(allLoaded.keys)

        var count = 0
        for (key in combinedKeys) {
            val entry = entries[key] ?: allLoaded[key]
            if (entry != null && entry.isHistory && entry.isRetained(currentTimeMs) && entry.targetUrl.isNotBlank()) {
                count++
            }
        }
        count
    }

    suspend fun getHistoryCount(): Int =
        getHistoryCount(getCurrentTimeMillis())

    fun buildKey(url: String, targetPlatformKey: String): String {
        val normalized = url.trim().substringBefore("#").trimEnd('/')
        return "$normalized|$targetPlatformKey"
    }

    suspend fun setFavoriteForUrl(url: String, targetPlatformKey: String, isFavorite: Boolean) {
        setFavorite(buildKey(url, targetPlatformKey), isFavorite)
    }
}

enum class FavoriteToggleResult {
    ADDED,
    REMOVED,
    LIMIT_REACHED,
    NOT_FOUND
}
