package de.goork.songflip.core.cache

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
    val timestamp: Long
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
    private val storage: CacheStorage = NoOpCacheStorage()
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CacheEntry>()
    private var initialized = false

    private fun ensureLoaded(currentTimeMs: Long) {
        if (!initialized) {
            initialized = true
            val loaded = storage.loadAll()
            val valid = loaded.entries
                .filter { (currentTimeMs - it.value.timestamp) <= ttlMs }
                .sortedBy { it.value.timestamp }

            for ((k, v) in loaded) {
                if ((currentTimeMs - v.timestamp) > ttlMs) {
                    storage.remove(k)
                }
            }

            val toKeep = if (valid.size > maxEntries) {
                val excess = valid.take(valid.size - maxEntries)
                for ((k, _) in excess) {
                    storage.remove(k)
                }
                valid.drop(valid.size - maxEntries)
            } else {
                valid
            }

            for ((k, v) in toKeep) {
                entries[k] = v
            }
        }
    }

    suspend fun get(canonicalUrl: String, targetPlatformKey: String, currentTimeMs: Long): ResolutionResult.Success? = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        var entry = entries[key]
        if (entry == null) {
            val stored = storage.get(key)
            if (stored != null) {
                if ((currentTimeMs - stored.timestamp) <= ttlMs) {
                    entries[key] = stored
                    entry = stored
                    while (entries.size > maxEntries) {
                        val oldestKey = entries.keys.firstOrNull() ?: break
                        entries.remove(oldestKey)
                        storage.remove(oldestKey)
                    }
                } else {
                    storage.remove(key)
                }
            }
        }

        if (entry == null) return@withLock null

        if ((currentTimeMs - entry.timestamp) > ttlMs) {
            entries.remove(key)
            storage.remove(key)
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
            nativeAppUri = entry.nativeAppUri
        )
    }

    suspend fun put(
        canonicalUrl: String,
        targetPlatformKey: String,
        result: ResolutionResult.Success,
        currentTimeMs: Long
    ) = mutex.withLock {
        ensureLoaded(currentTimeMs)
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val entry = CacheEntry(
            targetUrl = result.targetUrl,
            platform = result.platform,
            title = result.title,
            artist = result.artist,
            isAlbum = result.isAlbum,
            nativeAppUri = result.nativeAppUri,
            timestamp = currentTimeMs
        )

        entries.remove(key)
        entries[key] = entry
        storage.put(key, entry)

        while (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull() ?: break
            entries.remove(oldestKey)
            storage.remove(oldestKey)
        }
    }

    suspend fun remove(canonicalUrl: String, targetPlatformKey: String) = mutex.withLock {
        val key = buildKey(canonicalUrl, targetPlatformKey)
        entries.remove(key)
        storage.remove(key)
    }

    suspend fun clear() = mutex.withLock {
        initialized = true
        entries.clear()
        storage.clear()
    }

    suspend fun size(): Int = mutex.withLock {
        entries.size
    }

    private fun buildKey(url: String, targetPlatformKey: String): String {
        val normalized = url.trim().substringBefore("#").trimEnd('/')
        return "$normalized|$targetPlatformKey"
    }
}
