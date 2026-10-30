package de.goork.songflip.core.cache

import de.goork.songflip.core.engine.getCurrentTimeMillis
import de.goork.songflip.core.model.ResolutionResult
import de.goork.songflip.core.util.UrlUtils
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
    val isFavorite: Boolean = false,
    val isAcoustic: Boolean = false
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
    val isFavorite: Boolean = false,
    val isAcoustic: Boolean = false
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

    /** Evicts the least recently used non-favorite entries from in-memory cache; history is preserved on disk. */
    private fun evictOverflow() {
        var evictable = entries.count { !it.value.isFavorite }
        while (evictable > maxEntries) {
            val oldest = entries.entries.firstOrNull { !it.value.isFavorite } ?: break
            val oldestKey = oldest.key
            val oldestEntry = oldest.value
            entries.remove(oldestKey)
            if (!oldestEntry.isHistory && !oldestEntry.isFavorite) {
                storage.remove(oldestKey)
            }
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
            isFavorite = existing?.isFavorite == true,
            isAcoustic = existing?.isAcoustic == true
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
            isFavorite = existing?.isFavorite == true,
            isAcoustic = existing?.isAcoustic == true
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
        for ((k, loadedEntry) in allLoaded.entries.sortedBy { it.value.timestamp }) {
            if (loadedEntry.isRetained(currentTimeMs)) {
                val memEntry = entries[k]
                if (memEntry == null || loadedEntry.timestamp >= memEntry.timestamp || loadedEntry.isFavorite != memEntry.isFavorite || loadedEntry.isAcoustic != memEntry.isAcoustic) {
                    entries.remove(k)
                    entries[k] = loadedEntry
                }
            }
        }
        return allLoaded
    }

    private fun isDuplicateSongItem(a: LinkHistoryItem, b: LinkHistoryItem): Boolean {
        if (a.targetPlatformKey != b.targetPlatformKey) return false
        val normA = UrlUtils.normalizeUrl(a.canonicalUrl).substringBefore("#").trimEnd('/')
        val normB = UrlUtils.normalizeUrl(b.canonicalUrl).substringBefore("#").trimEnd('/')
        if (normA.isNotBlank() && normA == normB) return true

        val cleanTargetA = a.targetUrl.trim().trimEnd('/')
        val cleanTargetB = b.targetUrl.trim().trimEnd('/')
        val isSearchTarget = cleanTargetA.contains("/search") || cleanTargetA.contains("?q=") || cleanTargetA.contains("?term=")
        if (!isSearchTarget && cleanTargetA.isNotBlank() && cleanTargetA == cleanTargetB) {
            return true
        }

        if (!a.isAlbum && !b.isAlbum) {
            val titleA = a.title?.trim().orEmpty()
            val titleB = b.title?.trim().orEmpty()
            val artistA = a.artist?.trim().orEmpty()
            val artistB = b.artist?.trim().orEmpty()
            if (titleA.isNotEmpty() && artistA.isNotEmpty() &&
                titleA.equals(titleB, ignoreCase = true) &&
                artistA.equals(artistB, ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    private fun collectDeduplicatedHistoryItems(currentTimeMs: Long): List<LinkHistoryItem> {
        val allLoaded = storage.loadAll()
        val combinedKeys = mutableSetOf<String>()
        combinedKeys.addAll(entries.keys)
        combinedKeys.addAll(allLoaded.keys)

        val rawItems = mutableListOf<LinkHistoryItem>()
        for (key in combinedKeys) {
            val entry = entries[key] ?: allLoaded[key]
            if (entry != null && entry.isHistory && entry.isRetained(currentTimeMs) && entry.targetUrl.isNotBlank()) {
                val canonicalUrl = key.substringBeforeLast("|")
                val targetPlatformKey = key.substringAfterLast("|", entry.platform)
                rawItems.add(
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
                        isFavorite = entry.isFavorite,
                        isAcoustic = entry.isAcoustic
                    )
                )
            }
        }

        val deduplicated = mutableListOf<LinkHistoryItem>()
        val urlIndex = mutableMapOf<String, Int>()
        val targetIndex = mutableMapOf<String, Int>()
        val trackIndex = mutableMapOf<String, Int>()

        for (candidate in rawItems) {
            val normUrl = UrlUtils.normalizeUrl(candidate.canonicalUrl).substringBefore("#").trimEnd('/')
            val urlKey = if (normUrl.isNotBlank()) "${candidate.targetPlatformKey}|$normUrl" else null

            val cleanTarget = candidate.targetUrl.trim().trimEnd('/')
            val isSearchTarget = cleanTarget.contains("/search") || cleanTarget.contains("?q=") || cleanTarget.contains("?term=")
            val targetKey = if (!isSearchTarget && cleanTarget.isNotBlank()) "${candidate.targetPlatformKey}|$cleanTarget" else null

            val title = candidate.title?.trim()?.lowercase().orEmpty()
            val artist = candidate.artist?.trim()?.lowercase().orEmpty()
            val trackKey = if (!candidate.isAlbum && title.isNotEmpty() && artist.isNotEmpty()) "${candidate.targetPlatformKey}|$artist|$title" else null

            val existingIdx = (urlKey?.let { urlIndex[it] })
                ?: (targetKey?.let { targetIndex[it] })
                ?: (trackKey?.let { trackIndex[it] })
                ?: -1

            if (existingIdx < 0) {
                val newIdx = deduplicated.size
                deduplicated.add(candidate)
                urlKey?.let { urlIndex[it] = newIdx }
                targetKey?.let { targetIndex[it] = newIdx }
                trackKey?.let { trackIndex[it] = newIdx }
            } else {
                val existing = deduplicated[existingIdx]
                // Prefer the normalized key or the acoustic/newer entry
                val canonicalCandidateKey = buildKey(UrlUtils.normalizeUrl(candidate.canonicalUrl), candidate.targetPlatformKey)
                val preferCandidateKey = (candidate.cacheKey == canonicalCandidateKey && existing.cacheKey != canonicalCandidateKey) ||
                    (candidate.isAcoustic && !existing.isAcoustic) ||
                    (candidate.timestamp > existing.timestamp && existing.cacheKey != canonicalCandidateKey)

                val primary = if (preferCandidateKey) candidate else existing
                val secondary = if (preferCandidateKey) existing else candidate

                val merged = primary.copy(
                    title = primary.title?.takeIf { it.isNotBlank() } ?: secondary.title,
                    artist = primary.artist?.takeIf { it.isNotBlank() } ?: secondary.artist,
                    thumbnailUrl = primary.thumbnailUrl?.takeIf { it.isNotBlank() } ?: secondary.thumbnailUrl,
                    timestamp = maxOf(primary.timestamp, secondary.timestamp),
                    isFavorite = primary.isFavorite || secondary.isFavorite,
                    isAcoustic = primary.isAcoustic || secondary.isAcoustic
                )
                deduplicated[existingIdx] = merged

                // Update indices for merged item
                urlKey?.let { urlIndex[it] = existingIdx }
                targetKey?.let { targetIndex[it] = existingIdx }
                trackKey?.let { trackIndex[it] = existingIdx }

                // Self-heal storage by removing the redundant duplicate key and persisting merged flags
                if (secondary.cacheKey != merged.cacheKey) {
                    entries.remove(secondary.cacheKey)
                    storage.remove(secondary.cacheKey)
                }
                val primaryEntry = entries[merged.cacheKey] ?: storage.get(merged.cacheKey)
                if (primaryEntry != null) {
                    val healedEntry = primaryEntry.copy(
                        title = merged.title,
                        artist = merged.artist,
                        thumbnailUrl = merged.thumbnailUrl,
                        timestamp = merged.timestamp,
                        isHistory = true,
                        isFavorite = merged.isFavorite,
                        isAcoustic = merged.isAcoustic
                    )
                    entries[merged.cacheKey] = healedEntry
                    storage.put(merged.cacheKey, healedEntry)
                }
            }
        }
        return deduplicated
    }

    suspend fun getHistoryEntries(limit: Int = 10, currentTimeMs: Long = getCurrentTimeMillis()): List<LinkHistoryItem> = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val allItems = collectDeduplicatedHistoryItems(currentTimeMs).toMutableList()

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
        var count = 0
        for ((_, entry) in entries) {
            if (entry.isFavorite && entry.targetUrl.isNotBlank()) {
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
            val updated = existing.copy(
                isFavorite = newFav,
                isHistory = if (newFav) true else existing.isHistory
            )
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
        maxFreeFavorites: Int = de.goork.songflip.core.engine.ProLimits.MAX_FREE_FAVORITES,
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
        maxFreeFavorites: Int = de.goork.songflip.core.engine.ProLimits.MAX_FREE_FAVORITES
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
            val updated = existing.copy(
                isFavorite = isFavorite,
                isHistory = if (isFavorite) true else existing.isHistory
            )
            entries[cacheKey] = updated
            storage.put(cacheKey, updated)
        }
    }

    suspend fun hasMatchingHistoryEntry(
        canonicalUrl: String,
        targetPlatformKey: String,
        targetUrl: String?,
        title: String?,
        artist: String?,
        currentTimeMs: Long = getCurrentTimeMillis()
    ): Boolean = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val items = collectDeduplicatedHistoryItems(currentTimeMs)
        val probe = LinkHistoryItem(
            cacheKey = buildKey(canonicalUrl, targetPlatformKey),
            canonicalUrl = UrlUtils.normalizeUrl(canonicalUrl).substringBefore("#").trimEnd('/'),
            targetPlatformKey = targetPlatformKey,
            targetUrl = targetUrl.orEmpty(),
            platform = targetPlatformKey,
            title = title,
            artist = artist,
            isAlbum = false,
            timestamp = currentTimeMs,
            thumbnailUrl = null,
            isFavorite = false,
            isAcoustic = true
        )
        items.any { isDuplicateSongItem(it, probe) }
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
        isFavorite: Boolean? = null,
        isAcoustic: Boolean? = null
    ): String = mutex.withLock {
        ensureLoaded(timestamp)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val existing = entries[key] ?: storage.get(key)
        val resolvedFav = isFavorite ?: (existing?.isFavorite == true)
        val resolvedAcoustic = isAcoustic ?: (existing?.isAcoustic == true)

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
            isFavorite = resolvedFav,
            isAcoustic = resolvedAcoustic
        )

        entries.remove(key)
        entries[key] = entry
        storage.put(key, entry)
        // Run deduplication so any unnormalized/duplicate entry for the same track is merged immediately
        collectDeduplicatedHistoryItems(timestamp)
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
        currentTimeMs: Long = getCurrentTimeMillis(),
        isAcoustic: Boolean = false
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
        isFavorite = null,
        isAcoustic = isAcoustic
    )

    suspend fun addHistoryEntry(
        sourceUrl: String,
        targetPlatformKey: String,
        targetUrl: String,
        title: String?,
        artist: String?,
        isAlbum: Boolean,
        thumbnailUrl: String?,
        isAcoustic: Boolean
    ): String = addHistoryEntry(
        sourceUrl = sourceUrl,
        targetPlatformKey = targetPlatformKey,
        targetUrl = targetUrl,
        title = title,
        artist = artist,
        isAlbum = isAlbum,
        thumbnailUrl = thumbnailUrl,
        currentTimeMs = getCurrentTimeMillis(),
        isAcoustic = isAcoustic
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
        currentTimeMs = getCurrentTimeMillis(),
        isAcoustic = false
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
        collectDeduplicatedHistoryItems(currentTimeMs).size
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
