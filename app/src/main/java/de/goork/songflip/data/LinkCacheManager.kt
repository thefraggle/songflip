package de.goork.songflip.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

data class CachedLinkEntry(
    val targetUrl: String,
    val platform: String,
    val title: String? = null,
    val artist: String? = null,
    val isAlbum: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class HistoryItem(
    val cacheKey: String,
    val canonicalUrl: String,
    val targetPlatformKey: String,
    val targetUrl: String,
    val platform: String,
    val title: String? = null,
    val artist: String? = null,
    val isAlbum: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Two-tier L1 cache for fast music link resolutions:
 * 1. Thread-safe in-memory LRU map (< 5ms access time)
 * 2. Persistent SharedPreferences storage across app restarts
 */
object LinkCacheManager {
    private const val PREFS_NAME = "songflip_link_cache"
    private const val CACHE_VERSION_KEY = "songflip_cache_version"
    private const val CURRENT_CACHE_VERSION = 4
    private const val MAX_MEMORY_ENTRIES = 200
    private const val CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L // 7 days

    private var sharedPreferences: SharedPreferences? = null

    // Thread-safe in-memory LRU cache
    private val memoryCache = object : LinkedHashMap<String, CachedLinkEntry>(MAX_MEMORY_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedLinkEntry>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            checkCacheVersionMigration()
            loadInitialFromPrefs()
        }
    }

    @Synchronized
    private fun checkCacheVersionMigration() {
        val prefs = sharedPreferences ?: return
        val savedVersion = prefs.getInt(CACHE_VERSION_KEY, 1)
        if (savedVersion < CURRENT_CACHE_VERSION) {
            // Flush all legacy/corrupt cache entries from earlier beta test versions
            prefs.edit().clear().putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION).apply()
            memoryCache.clear()
        }
    }

    @Synchronized
    private fun loadInitialFromPrefs() {
        val prefs = sharedPreferences ?: return
        try {
            val all = prefs.all
            val now = System.currentTimeMillis()
            for ((key, value) in all) {
                if (key == CACHE_VERSION_KEY) continue
                if (value is String) {
                    val entry = parseEntry(value)
                    if (entry != null && (now - entry.timestamp) < CACHE_TTL_MS && isValidTargetUrl(entry.targetUrl)) {
                        memoryCache[key] = entry
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    @Synchronized
    fun get(canonicalUrl: String, targetPlatformKey: String): CachedLinkEntry? {
        val cacheKey = buildCacheKey(canonicalUrl, targetPlatformKey)
        val now = System.currentTimeMillis()

        // 1. Check in-memory LRU cache
        val memEntry = memoryCache[cacheKey]
        if (memEntry != null) {
            if (now - memEntry.timestamp < CACHE_TTL_MS && memEntry.targetUrl.isNotBlank() && (memEntry.targetUrl.startsWith("http") || memEntry.targetUrl.contains(":"))) {
                if (memEntry.targetUrl.contains("music.music.youtube.com")) {
                    val fixed = memEntry.copy(targetUrl = memEntry.targetUrl.replace("music.music.youtube.com", "music.youtube.com"))
                    memoryCache[cacheKey] = fixed
                    savePersistent(cacheKey, fixed)
                    return fixed
                }
                return memEntry
            } else {
                memoryCache.remove(cacheKey)
                removePersistent(cacheKey)
                return null
            }
        }

        // 2. Check persistent SharedPreferences if available
        val prefs = sharedPreferences
        if (prefs != null && prefs.contains(cacheKey)) {
            val jsonStr = prefs.getString(cacheKey, null)
            if (jsonStr != null) {
                val entry = parseEntry(jsonStr)
                if (entry != null) {
                    if (now - entry.timestamp < CACHE_TTL_MS && entry.targetUrl.isNotBlank() && (entry.targetUrl.startsWith("http") || entry.targetUrl.contains(":"))) {
                        memoryCache[cacheKey] = entry
                        return entry
                    } else {
                        removePersistent(cacheKey)
                    }
                }
            }
        }

        return null
    }

    @Synchronized
    fun put(
        canonicalUrl: String,
        targetPlatformKey: String,
        targetUrl: String,
        platform: String,
        title: String? = null,
        artist: String? = null,
        isAlbum: Boolean = false
    ) {
        if (!isValidTargetUrl(targetUrl)) return
        val cacheKey = buildCacheKey(canonicalUrl, targetPlatformKey)
        val entry = CachedLinkEntry(
            targetUrl = targetUrl,
            platform = platform,
            title = title,
            artist = artist,
            isAlbum = isAlbum,
            timestamp = System.currentTimeMillis()
        )

        memoryCache[cacheKey] = entry
        savePersistent(cacheKey, entry)
    }

    @Synchronized
    fun remove(canonicalUrl: String, targetPlatformKey: String) {
        val cacheKey = buildCacheKey(canonicalUrl, targetPlatformKey)
        memoryCache.remove(cacheKey)
        removePersistent(cacheKey)
    }

    @Synchronized
    fun removeByCacheKey(cacheKey: String) {
        memoryCache.remove(cacheKey)
        removePersistent(cacheKey)
    }

    @Synchronized
    fun clear() {
        memoryCache.clear()
        sharedPreferences?.edit()?.clear()?.apply()
    }

    @Synchronized
    fun clearHistoryAndCache() {
        clear()
    }

    @Synchronized
    fun getHistoryEntries(limit: Int = 10): List<HistoryItem> {
        val now = System.currentTimeMillis()
        val allItems = mutableListOf<HistoryItem>()
        val combinedKeys = mutableSetOf<String>()
        combinedKeys.addAll(memoryCache.keys)

        val prefs = sharedPreferences
        if (prefs != null) {
            combinedKeys.addAll(prefs.all.keys.filter { it != CACHE_VERSION_KEY })
        }

        for (key in combinedKeys) {
            val entry = memoryCache[key] ?: (prefs?.getString(key, null)?.let { parseEntry(it) })
            if (entry != null && (now - entry.timestamp) < CACHE_TTL_MS && isValidTargetUrl(entry.targetUrl)) {
                val parts = key.split("|")
                val canonicalUrl = parts.getOrNull(0) ?: ""
                val targetPlatformKey = parts.getOrNull(1) ?: entry.platform

                allItems.add(
                    HistoryItem(
                        cacheKey = key,
                        canonicalUrl = canonicalUrl,
                        targetPlatformKey = targetPlatformKey,
                        targetUrl = entry.targetUrl,
                        platform = entry.platform,
                        title = entry.title,
                        artist = entry.artist,
                        isAlbum = entry.isAlbum,
                        timestamp = entry.timestamp
                    )
                )
            }
        }

        // Newest entries first
        allItems.sortByDescending { it.timestamp }

        return if (limit > 0 && allItems.size > limit) {
            allItems.subList(0, limit)
        } else {
            allItems
        }
    }

    @Synchronized
    fun getTotalCachedCount(): Int {
        val prefs = sharedPreferences
        val keys = mutableSetOf<String>()
        keys.addAll(memoryCache.keys)
        if (prefs != null) {
            keys.addAll(prefs.all.keys.filter { it != CACHE_VERSION_KEY })
        }
        return keys.size
    }

    fun isValidTargetUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (url.contains("music.music.youtube.com")) return false
        if (url.contains("spotify.link") || url.contains("deezer.page.link") || url.contains("://apple.co") || url.contains("amzn.to")) return false
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("spotify:") || url.startsWith("deezer://") || url.startsWith("tidal://")
    }

    @Synchronized
    fun size(): Int = memoryCache.size

    private fun buildCacheKey(url: String, targetPlatformKey: String): String {
        val normalized = url.trim()
            .substringBefore("#")
            .trimEnd('/')
        return "$normalized|$targetPlatformKey"
    }

    private const val MAX_PERSISTENT_ENTRIES = 500

    private fun savePersistent(key: String, entry: CachedLinkEntry) {
        val prefs = sharedPreferences ?: return
        try {
            val json = JSONObject().apply {
                put("targetUrl", entry.targetUrl)
                put("platform", entry.platform)
                put("title", entry.title)
                put("artist", entry.artist)
                put("isAlbum", entry.isAlbum)
                put("timestamp", entry.timestamp)
            }
            val editor = prefs.edit()
            editor.putString(key, json.toString())
            val all = prefs.all
            if (all.size > MAX_PERSISTENT_ENTRIES) {
                prunePersistentIfNeeded(editor, all)
            }
            editor.apply()
        } catch (ignored: Exception) {}
    }

    private fun prunePersistentIfNeeded(editor: SharedPreferences.Editor, allEntries: Map<String, *>) {
        try {
            val entriesWithTime = mutableListOf<Pair<String, Long>>()
            for ((k, v) in allEntries) {
                if (v is String) {
                    val entry = parseEntry(v)
                    val time = entry?.timestamp ?: 0L
                    entriesWithTime.add(k to time)
                }
            }
            // Sort ascending by timestamp (oldest first)
            entriesWithTime.sortBy { it.second }
            val removeCount = entriesWithTime.size - (MAX_PERSISTENT_ENTRIES - 50)
            if (removeCount > 0) {
                for (i in 0 until removeCount) {
                    val (k, _) = entriesWithTime[i]
                    editor.remove(k)
                    memoryCache.remove(k)
                }
            }
        } catch (ignored: Exception) {}
    }

    private fun removePersistent(key: String) {
        sharedPreferences?.edit()?.remove(key)?.apply()
    }

    private fun parseEntry(jsonString: String): CachedLinkEntry? {
        return try {
            val obj = JSONObject(jsonString)
            var target = obj.getString("targetUrl")
            if (target.contains("music.music.youtube.com")) {
                target = target.replace("music.music.youtube.com", "music.youtube.com")
            }
            CachedLinkEntry(
                targetUrl = target,
                platform = obj.optString("platform", ""),
                title = if (obj.has("title") && !obj.isNull("title")) obj.getString("title") else null,
                artist = if (obj.has("artist") && !obj.isNull("artist")) obj.getString("artist") else null,
                isAlbum = obj.optBoolean("isAlbum", false),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            null
        }
    }
}
