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

class LinkCache(
    private val maxEntries: Int = 200,
    private val ttlMs: Long = 7 * 24 * 60 * 60 * 1000L
) {
    private val mutex = Mutex()
    private val entries = LinkedHashMap<String, CacheEntry>()

    suspend fun get(canonicalUrl: String, targetPlatformKey: String, currentTimeMs: Long): ResolutionResult.Success? = mutex.withLock {
        val key = buildKey(canonicalUrl, targetPlatformKey)
        val entry = entries[key] ?: return@withLock null

        if ((currentTimeMs - entry.timestamp) > ttlMs) {
            entries.remove(key)
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

        if (entries.size > maxEntries) {
            val oldestKey = entries.keys.firstOrNull()
            if (oldestKey != null) {
                entries.remove(oldestKey)
            }
        }
    }

    suspend fun clear() = mutex.withLock {
        entries.clear()
    }

    suspend fun size(): Int = mutex.withLock {
        entries.size
    }

    private fun buildKey(url: String, targetPlatformKey: String): String {
        val normalized = url.trim().substringBefore("#").trimEnd('/')
        return "$normalized|$targetPlatformKey"
    }
}
