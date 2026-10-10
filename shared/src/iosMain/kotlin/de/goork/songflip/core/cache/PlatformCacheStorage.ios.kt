package de.goork.songflip.core.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

class IosUserDefaultsCacheStorage(
    suiteName: String = "group.de.goork.songflip",
    private val keyPrefix: String = "songflip_l1_cache:"
) : CacheStorage {
    private val defaults: NSUserDefaults = NSUserDefaults(suiteName = suiteName)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun get(key: String): CacheEntry? {
        val raw = defaults.stringForKey("$keyPrefix$key") ?: return null
        return try {
            json.decodeFromString<CacheEntry>(raw)
        } catch (_: Throwable) {
            null
        }
    }

    override fun put(key: String, entry: CacheEntry) {
        try {
            val encoded = json.encodeToString(entry)
            defaults.setObject(encoded, forKey = "$keyPrefix$key")
        } catch (t: Throwable) {
            println("IosCacheStorage: Failed to put entry $key: ${t.message}")
        }
    }

    override fun remove(key: String) {
        defaults.removeObjectForKey("$keyPrefix$key")
    }

    override fun clear() {
        val dict = defaults.dictionaryRepresentation()
        for ((k, _) in dict) {
            val strKey = k as? String ?: continue
            if (strKey.startsWith(keyPrefix)) {
                defaults.removeObjectForKey(strKey)
            }
        }
    }

    override fun loadAll(): Map<String, CacheEntry> {
        val result = mutableMapOf<String, CacheEntry>()
        val dict = defaults.dictionaryRepresentation()
        for ((k, v) in dict) {
            val strKey = k as? String ?: continue
            if (strKey.startsWith(keyPrefix)) {
                val raw = v as? String ?: continue
                try {
                    val entry = json.decodeFromString<CacheEntry>(raw)
                    val realKey = strKey.removePrefix(keyPrefix)
                    result[realKey] = entry
                } catch (t: Throwable) {
                    println("IosCacheStorage: Failed to decode entry $strKey: ${t.message}")
                }
            }
        }
        return result
    }
}

actual fun createDefaultCacheStorage(): CacheStorage = IosUserDefaultsCacheStorage()
