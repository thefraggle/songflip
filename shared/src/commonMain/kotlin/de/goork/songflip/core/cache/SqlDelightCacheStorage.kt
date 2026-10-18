package de.goork.songflip.core.cache

import app.cash.sqldelight.db.SqlDriver
import de.goork.songflip.core.db.SongFlipDatabase
import de.goork.songflip.core.engine.getCurrentTimeMillis
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class SqlDelightCacheStorage(
    private val driverProvider: () -> SqlDriver?,
    private val onDatabaseReady: ((SqlDelightCacheStorage, SongFlipDatabase) -> Unit)? = null
) : CacheStorage {

    companion object {
        private const val MAX_RESOLVER_CACHE_ENTRIES = 500L
        private const val APPLE_REF_OFFSET_SEC = 978_307_200.0

        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parseLegacyIosHistoryJson(
            rawJson: String,
            currentTimeMs: Long = getCurrentTimeMillis()
        ): Map<String, CacheEntry> {
            if (rawJson.isBlank()) return emptyMap()
            val array = try {
                lenientJson.parseToJsonElement(rawJson) as? JsonArray
            } catch (_: Throwable) {
                null
            } ?: return emptyMap()

            val nowSec = currentTimeMs / 1000.0
            val result = mutableMapOf<String, CacheEntry>()

            for (element in array) {
                val obj = element as? JsonObject ?: continue
                val sourceUrl = obj["sourceUrl"]?.jsonPrimitive?.content?.trim().orEmpty()
                val targetUrl = obj["targetUrl"]?.jsonPrimitive?.content?.trim().orEmpty()
                val targetPlatform = obj["targetPlatform"]?.jsonPrimitive?.content?.trim().orEmpty()
                if (sourceUrl.isEmpty() || targetUrl.isEmpty() || targetPlatform.isEmpty()) continue

                val title = obj["title"]?.jsonPrimitive?.content
                val artist = obj["artist"]?.jsonPrimitive?.content
                val isAlbum = obj["isAlbum"]?.jsonPrimitive?.booleanOrNull ?: false
                val thumbnailUrl = obj["thumbnailUrl"]?.jsonPrimitive?.content
                val isFavorite = obj["isFavorite"]?.jsonPrimitive?.booleanOrNull ?: false

                val rawTs = obj["timestamp"]?.jsonPrimitive?.doubleOrNull
                val timestampMs = if (rawTs != null) {
                    val asSec = if (rawTs > 100_000_000_000.0) rawTs / 1000.0 else rawTs
                    val healedSec = when {
                        asSec > nowSec + 86400 && (asSec - APPLE_REF_OFFSET_SEC) <= nowSec + 86400 ->
                            asSec - APPLE_REF_OFFSET_SEC
                        asSec < 1_000_000_000 && (asSec + APPLE_REF_OFFSET_SEC) <= nowSec + 86400 ->
                            asSec + APPLE_REF_OFFSET_SEC
                        else -> asSec
                    }
                    (healedSec * 1000.0).toLong()
                } else {
                    currentTimeMs
                }

                val canonicalUrl = sourceUrl.substringBefore("#").trimEnd('/')
                val key = "$canonicalUrl|$targetPlatform"
                val existing = result[key]
                if (existing == null || timestampMs >= existing.timestamp) {
                    result[key] = CacheEntry(
                        targetUrl = targetUrl,
                        platform = targetPlatform,
                        title = title,
                        artist = artist,
                        isAlbum = isAlbum,
                        nativeAppUri = null,
                        timestamp = timestampMs,
                        isHistory = true,
                        thumbnailUrl = thumbnailUrl,
                        isFavorite = isFavorite || (existing?.isFavorite == true)
                    )
                }
            }
            return result
        }
    }

    private var database: SongFlipDatabase? = null
    private var initAttempted = false

    fun getDatabase(): SongFlipDatabase? {
        if (database != null) return database
        if (initAttempted) return null
        val driver = try {
            driverProvider()
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to create SQL driver: ${t.message}")
            null
        } ?: return null

        initAttempted = true
        return try {
            val db = SongFlipDatabase(driver)
            database = db
            onDatabaseReady?.invoke(this, db)
            db
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to initialize database: ${t.message}")
            null
        }
    }

    override fun get(key: String): CacheEntry? {
        val db = getDatabase() ?: return null
        return try {
            val historyRow = db.songFlipDatabaseQueries.getHistoryByKey(key).executeAsOneOrNull()
            val resolverRow = db.songFlipDatabaseQueries.getResolverCacheByKey(key).executeAsOneOrNull()

            when {
                historyRow != null && resolverRow != null -> {
                    val useResolver = resolverRow.timestamp >= historyRow.timestamp
                    CacheEntry(
                        targetUrl = if (useResolver) resolverRow.target_url else historyRow.target_url,
                        platform = if (useResolver) resolverRow.platform else historyRow.platform,
                        title = (if (useResolver) resolverRow.title else historyRow.title) ?: historyRow.title,
                        artist = (if (useResolver) resolverRow.artist else historyRow.artist) ?: historyRow.artist,
                        isAlbum = (if (useResolver) resolverRow.is_album else historyRow.is_album) != 0L,
                        nativeAppUri = if (useResolver) resolverRow.native_app_uri else historyRow.native_app_uri,
                        timestamp = maxOf(historyRow.timestamp, resolverRow.timestamp),
                        isHistory = true,
                        thumbnailUrl = (if (useResolver) resolverRow.thumbnail_url else historyRow.thumbnail_url) ?: historyRow.thumbnail_url,
                        isFavorite = historyRow.is_favorite != 0L
                    )
                }
                historyRow != null -> CacheEntry(
                    targetUrl = historyRow.target_url,
                    platform = historyRow.platform,
                    title = historyRow.title,
                    artist = historyRow.artist,
                    isAlbum = historyRow.is_album != 0L,
                    nativeAppUri = historyRow.native_app_uri,
                    timestamp = historyRow.timestamp,
                    isHistory = true,
                    thumbnailUrl = historyRow.thumbnail_url,
                    isFavorite = historyRow.is_favorite != 0L
                )
                resolverRow != null -> CacheEntry(
                    targetUrl = resolverRow.target_url,
                    platform = resolverRow.platform,
                    title = resolverRow.title,
                    artist = resolverRow.artist,
                    isAlbum = resolverRow.is_album != 0L,
                    nativeAppUri = resolverRow.native_app_uri,
                    timestamp = resolverRow.timestamp,
                    isHistory = false,
                    thumbnailUrl = resolverRow.thumbnail_url,
                    isFavorite = false
                )
                else -> null
            }
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to get entry $key: ${t.message}")
            null
        }
    }

    override fun put(key: String, entry: CacheEntry) {
        val db = getDatabase() ?: return
        try {
            val canonicalUrl = key.substringBeforeLast("|")
            val targetPlatformKey = key.substringAfterLast("|", entry.platform)

            db.transaction {
                db.songFlipDatabaseQueries.upsertResolverCache(
                    cache_key = key,
                    target_url = entry.targetUrl,
                    platform = entry.platform,
                    title = entry.title,
                    artist = entry.artist,
                    is_album = if (entry.isAlbum) 1L else 0L,
                    native_app_uri = entry.nativeAppUri,
                    timestamp = entry.timestamp,
                    thumbnail_url = entry.thumbnailUrl
                )

                val existingHistory = db.songFlipDatabaseQueries.getHistoryByKey(key).executeAsOneOrNull()
                if (entry.isHistory || entry.isFavorite || existingHistory != null) {
                    db.songFlipDatabaseQueries.upsertHistory(
                        cache_key = key,
                        canonical_url = canonicalUrl,
                        target_platform_key = targetPlatformKey,
                        target_url = entry.targetUrl,
                        platform = entry.platform,
                        title = entry.title ?: existingHistory?.title,
                        artist = entry.artist ?: existingHistory?.artist,
                        is_album = if (entry.isAlbum) 1L else 0L,
                        native_app_uri = entry.nativeAppUri ?: existingHistory?.native_app_uri,
                        timestamp = entry.timestamp,
                        thumbnail_url = entry.thumbnailUrl ?: existingHistory?.thumbnail_url,
                        is_favorite = if (entry.isFavorite) 1L else 0L
                    )
                }

                val count = db.songFlipDatabaseQueries.countResolverCache().executeAsOne()
                if (count > MAX_RESOLVER_CACHE_ENTRIES) {
                    val removeCount = count - (MAX_RESOLVER_CACHE_ENTRIES - 50L)
                    db.songFlipDatabaseQueries.pruneOldestResolverCache(removeCount)
                }
            }
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to put entry $key: ${t.message}")
        }
    }

    override fun remove(key: String) {
        val db = getDatabase() ?: return
        try {
            db.transaction {
                db.songFlipDatabaseQueries.deleteHistoryByKey(key)
                db.songFlipDatabaseQueries.deleteResolverCacheByKey(key)
            }
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to remove entry $key: ${t.message}")
        }
    }

    override fun clear() {
        val db = getDatabase() ?: return
        try {
            db.transaction {
                db.songFlipDatabaseQueries.clearAllHistory()
                db.songFlipDatabaseQueries.clearAllResolverCache()
            }
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to clear storage: ${t.message}")
        }
    }

    override fun loadAll(): Map<String, CacheEntry> {
        val db = getDatabase() ?: return emptyMap()
        return try {
            val result = mutableMapOf<String, CacheEntry>()
            val resolverRows = db.songFlipDatabaseQueries.getAllResolverCache().executeAsList()
            for (row in resolverRows) {
                result[row.cache_key] = CacheEntry(
                    targetUrl = row.target_url,
                    platform = row.platform,
                    title = row.title,
                    artist = row.artist,
                    isAlbum = row.is_album != 0L,
                    nativeAppUri = row.native_app_uri,
                    timestamp = row.timestamp,
                    isHistory = false,
                    thumbnailUrl = row.thumbnail_url,
                    isFavorite = false
                )
            }

            val historyRows = db.songFlipDatabaseQueries.getAllHistoryOrdered().executeAsList()
            for (row in historyRows) {
                val newerResolver = result[row.cache_key]?.takeIf { it.timestamp > row.timestamp }
                result[row.cache_key] = CacheEntry(
                    targetUrl = newerResolver?.targetUrl ?: row.target_url,
                    platform = newerResolver?.platform ?: row.platform,
                    title = newerResolver?.title ?: row.title,
                    artist = newerResolver?.artist ?: row.artist,
                    isAlbum = newerResolver?.isAlbum ?: (row.is_album != 0L),
                    nativeAppUri = newerResolver?.nativeAppUri ?: row.native_app_uri,
                    timestamp = newerResolver?.timestamp ?: row.timestamp,
                    isHistory = true,
                    thumbnailUrl = newerResolver?.thumbnailUrl ?: row.thumbnail_url,
                    isFavorite = row.is_favorite != 0L
                )
            }
            result
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Failed to loadAll: ${t.message}")
            emptyMap()
        }
    }

    fun isMigrationDone(migrationKey: String): Boolean {
        val db = getDatabase() ?: return false
        return try {
            db.songFlipDatabaseQueries.getMetaValue(migrationKey).executeAsOneOrNull() == "done"
        } catch (_: Throwable) {
            false
        }
    }

    fun migrateLegacyEntries(legacyEntries: Map<String, CacheEntry>, migrationKey: String): Boolean {
        val db = getDatabase() ?: return false
        return try {
            if (db.songFlipDatabaseQueries.getMetaValue(migrationKey).executeAsOneOrNull() == "done") {
                return false
            }
            db.transaction {
                for ((key, entry) in legacyEntries) {
                    if (entry.targetUrl.isBlank()) continue
                    val canonicalUrl = key.substringBeforeLast("|")
                    val targetPlatformKey = key.substringAfterLast("|", entry.platform)

                    val existingResolver = db.songFlipDatabaseQueries.getResolverCacheByKey(key).executeAsOneOrNull()
                    if (existingResolver == null || entry.timestamp >= existingResolver.timestamp) {
                        db.songFlipDatabaseQueries.upsertResolverCache(
                            cache_key = key,
                            target_url = entry.targetUrl,
                            platform = entry.platform,
                            title = entry.title,
                            artist = entry.artist,
                            is_album = if (entry.isAlbum) 1L else 0L,
                            native_app_uri = entry.nativeAppUri,
                            timestamp = entry.timestamp,
                            thumbnail_url = entry.thumbnailUrl
                        )
                    }

                    if (entry.isHistory || entry.isFavorite) {
                        val existingHistory = db.songFlipDatabaseQueries.getHistoryByKey(key).executeAsOneOrNull()
                        val mergedFav = (existingHistory?.is_favorite == 1L) || entry.isFavorite
                        if (existingHistory == null || entry.timestamp >= existingHistory.timestamp || mergedFav != (existingHistory.is_favorite == 1L)) {
                            db.songFlipDatabaseQueries.upsertHistory(
                                cache_key = key,
                                canonical_url = canonicalUrl,
                                target_platform_key = targetPlatformKey,
                                target_url = entry.targetUrl,
                                platform = entry.platform,
                                title = entry.title ?: existingHistory?.title,
                                artist = entry.artist ?: existingHistory?.artist,
                                is_album = if (entry.isAlbum) 1L else 0L,
                                native_app_uri = entry.nativeAppUri ?: existingHistory?.native_app_uri,
                                timestamp = maxOf(entry.timestamp, existingHistory?.timestamp ?: 0L),
                                thumbnail_url = entry.thumbnailUrl ?: existingHistory?.thumbnail_url,
                                is_favorite = if (mergedFav) 1L else 0L
                            )
                        }
                    }
                }
                db.songFlipDatabaseQueries.setMetaValue(migrationKey, "done")
            }
            true
        } catch (t: Throwable) {
            println("SqlDelightCacheStorage: Migration $migrationKey failed: ${t.message}")
            false
        }
    }
}
