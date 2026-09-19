package de.goork.songflip.core.cache

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidSharedPreferencesCacheStorage(
    private val prefsSupplier: () -> SharedPreferences? = { null }
) : CacheStorage {

    companion object {
        private const val PREFS_NAME = "songflip_link_cache"
        private const val CACHE_VERSION_KEY = "songflip_cache_version"
        private const val CURRENT_CACHE_VERSION = 5
        private const val MAX_PERSISTENT_ENTRIES = 500

        private var appContext: Context? = null
        private var sharedPrefs: SharedPreferences? = null

        fun init(context: Context) {
            appContext = context.applicationContext
            sharedPrefs = appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            checkCacheVersionMigration()
        }

        fun init(prefs: SharedPreferences) {
            sharedPrefs = prefs
            checkCacheVersionMigration()
        }

        private fun checkCacheVersionMigration() {
            val prefs = sharedPrefs ?: return
            val savedVersion = prefs.getInt(CACHE_VERSION_KEY, 1)
            if (savedVersion < CURRENT_CACHE_VERSION) {
                prefs.edit().clear().putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION).apply()
            }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun getPrefs(): SharedPreferences? = sharedPrefs ?: prefsSupplier()

    override fun get(key: String): CacheEntry? {
        val prefs = getPrefs() ?: return null
        val raw = prefs.getString(key, null) ?: return null
        return try {
            json.decodeFromString<CacheEntry>(raw)
        } catch (_: Throwable) {
            null
        }
    }

    override fun put(key: String, entry: CacheEntry) {
        val prefs = getPrefs() ?: return
        try {
            val encoded = json.encodeToString(entry)
            val editor = prefs.edit()
            editor.putString(key, encoded)
            val all = prefs.all
            if (all.size > MAX_PERSISTENT_ENTRIES) {
                pruneIfNeeded(editor, all)
            }
            editor.apply()
        } catch (_: Throwable) {}
    }

    private fun pruneIfNeeded(editor: SharedPreferences.Editor, allEntries: Map<String, *>) {
        try {
            val entriesWithTime = mutableListOf<Pair<String, Long>>()
            for ((k, v) in allEntries) {
                if (k == CACHE_VERSION_KEY) continue
                if (v is String) {
                    val entry = try { json.decodeFromString<CacheEntry>(v) } catch (_: Throwable) { null }
                    val time = entry?.timestamp ?: 0L
                    entriesWithTime.add(k to time)
                }
            }
            entriesWithTime.sortBy { it.second }
            val removeCount = entriesWithTime.size - (MAX_PERSISTENT_ENTRIES - 50)
            if (removeCount > 0) {
                for (i in 0 until removeCount) {
                    editor.remove(entriesWithTime[i].first)
                }
            }
        } catch (_: Throwable) {}
    }

    override fun remove(key: String) {
        getPrefs()?.edit()?.remove(key)?.apply()
    }

    override fun clear() {
        getPrefs()?.edit()?.clear()?.putInt(CACHE_VERSION_KEY, CURRENT_CACHE_VERSION)?.apply()
    }

    override fun loadAll(): Map<String, CacheEntry> {
        val prefs = getPrefs() ?: return emptyMap()
        val result = mutableMapOf<String, CacheEntry>()
        val all = prefs.all
        for ((k, v) in all) {
            if (k == CACHE_VERSION_KEY) continue
            if (v is String) {
                try {
                    val entry = json.decodeFromString<CacheEntry>(v)
                    result[k] = entry
                } catch (_: Throwable) {}
            }
        }
        return result
    }
}

actual fun createDefaultCacheStorage(): CacheStorage =
    AndroidSharedPreferencesCacheStorage()
