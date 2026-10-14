package de.goork.songflip.core.cache

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import de.goork.songflip.core.db.SongFlipDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create

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

object IosAppGroupSqlDelightStorage {
    private const val APP_GROUP_ID = "group.de.goork.songflip"
    private const val DB_NAME = "songflip_store.db"
    private const val MIGRATION_KEY_IOS_L1 = "ios_l1_cache_v1"
    private const val MIGRATION_KEY_IOS_HISTORY = "ios_history_v1"
    private const val LEGACY_HISTORY_KEY = "songflip_conversion_history"

    @OptIn(ExperimentalForeignApi::class)
    private fun resolveAppGroupBasePath(): String? {
        val fm = NSFileManager.defaultManager
        val containerUrl = fm.containerURLForSecurityApplicationGroupIdentifier(APP_GROUP_ID)
        val baseDirUrl = containerUrl?.URLByAppendingPathComponent("Library/Application Support")
        val path = baseDirUrl?.path ?: return null
        if (!fm.fileExistsAtPath(path)) {
            fm.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        }
        return path
    }

    val shared: SqlDelightCacheStorage by lazy {
        SqlDelightCacheStorage(
            driverProvider = {
                val basePath = resolveAppGroupBasePath()
                if (basePath != null) {
                    NativeSqliteDriver(
                        schema = SongFlipDatabase.Schema,
                        name = DB_NAME,
                        onConfiguration = { config: DatabaseConfiguration ->
                            config.copy(
                                extendedConfig = config.extendedConfig.copy(basePath = basePath)
                            )
                        }
                    )
                } else {
                    NativeSqliteDriver(SongFlipDatabase.Schema, DB_NAME)
                }
            },
            onDatabaseReady = { storage, _ ->
                // 1. Migrate and clean up legacy songflip_l1_cache:* keys from App Group UserDefaults (#115)
                if (!storage.isMigrationDone(MIGRATION_KEY_IOS_L1)) {
                    val legacyL1 = IosUserDefaultsCacheStorage(suiteName = APP_GROUP_ID)
                    val loadedL1 = legacyL1.loadAll()
                    if (storage.migrateLegacyEntries(loadedL1, MIGRATION_KEY_IOS_L1)) {
                        legacyL1.clear()
                    }
                }
                // 2. Migrate legacy songflip_conversion_history JSON from App Group UserDefaults (#107)
                if (!storage.isMigrationDone(MIGRATION_KEY_IOS_HISTORY)) {
                    val defaults = NSUserDefaults(suiteName = APP_GROUP_ID)
                    val rawData = defaults.dataForKey(LEGACY_HISTORY_KEY)
                    val rawJson = if (rawData != null) {
                        NSString.create(data = rawData, encoding = NSUTF8StringEncoding)?.toString().orEmpty()
                    } else {
                        defaults.stringForKey(LEGACY_HISTORY_KEY).orEmpty()
                    }
                    val parsedHistory = SqlDelightCacheStorage.parseLegacyIosHistoryJson(rawJson)
                    storage.migrateLegacyEntries(parsedHistory, MIGRATION_KEY_IOS_HISTORY)
                }
            }
        )
    }
}

actual fun createDefaultCacheStorage(): CacheStorage = IosAppGroupSqlDelightStorage.shared

