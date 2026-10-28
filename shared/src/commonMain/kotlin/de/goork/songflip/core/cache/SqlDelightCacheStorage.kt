package de.goork.songflip.core.cache

import app.cash.sqldelight.db.SqlDriver
import de.goork.songflip.core.db.SongFlipDatabase
import de.goork.songflip.core.engine.getCurrentTimeMillis
import de.goork.songflip.core.util.PlatformLock
import de.goork.songflip.core.util.SongFlipLogger
import kotlin.concurrent.Volatile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

@Serializable
data class PendingAcousticScan(
    val id: String,
    val signatureBase64: String,
    val durationMs: Long,
    val createdAt: Long
)

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
                val isAcoustic = obj["isAcoustic"]?.jsonPrimitive?.booleanOrNull ?: false

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
                        isFavorite = isFavorite || (existing?.isFavorite == true),
                        isAcoustic = isAcoustic || (existing?.isAcoustic == true)
                    )
                }
            }
            return result
        }
    }

    @Volatile
    private var database: SongFlipDatabase? = null
    @Volatile
    private var initAttempted = false
    private val initLock = PlatformLock()

    private fun ensureSchemaUpgrades(driver: SqlDriver) {
        try {
            driver.execute(
                identifier = null,
                sql = "ALTER TABLE history ADD COLUMN is_acoustic INTEGER NOT NULL DEFAULT 0;",
                parameters = 0
            )
        } catch (_: Throwable) {
            // Column already exists on fresh or already-migrated databases
        }
        try {
            driver.execute(
                identifier = null,
                sql = """
                    CREATE TABLE IF NOT EXISTS pending_acoustic_scans (
                        id TEXT NOT NULL PRIMARY KEY,
                        signature_base64 TEXT NOT NULL,
                        duration_ms INTEGER NOT NULL DEFAULT 0,
                        created_at INTEGER NOT NULL
                    );
                """.trimIndent(),
                parameters = 0
            )
        } catch (t: Throwable) {
            SongFlipLogger.e("SqlDelightCacheStorage", "Failed to ensure pending_acoustic_scans table", t)
        }
    }

    fun getDatabase(): SongFlipDatabase? {
        val existing = database
        if (existing != null) return existing
        if (initAttempted) return null

        return initLock.withLock {
            val doubleCheck = database
            if (doubleCheck != null) return@withLock doubleCheck
            if (initAttempted) return@withLock null

            val driver = try {
                driverProvider()
            } catch (t: Throwable) {
                SongFlipLogger.e("SqlDelightCacheStorage", "Failed to create SQL driver", t)
                null
            }
            initAttempted = true
            if (driver == null) return@withLock null

            try {
                ensureSchemaUpgrades(driver)
                val db = SongFlipDatabase(driver)
                database = db
                onDatabaseReady?.invoke(this, db)
                db
            } catch (t: Throwable) {
                SongFlipLogger.e("SqlDelightCacheStorage", "Failed to initialize database", t)
                null
            }
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
                        timestamp = historyRow.timestamp,
                        isHistory = true,
                        thumbnailUrl = (if (useResolver) resolverRow.thumbnail_url else historyRow.thumbnail_url) ?: historyRow.thumbnail_url,
                        isFavorite = historyRow.is_favorite != 0L,
                        isAcoustic = historyRow.is_acoustic != 0L
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
                    isFavorite = historyRow.is_favorite != 0L,
                    isAcoustic = historyRow.is_acoustic != 0L
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
                    isFavorite = false,
                    isAcoustic = false
                )
                else -> null
            }
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to get entry $key", t)
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
                    val isAcousticFlag = entry.isAcoustic || (existingHistory?.is_acoustic == 1L)
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
                        is_favorite = if (entry.isFavorite) 1L else 0L,
                        is_acoustic = if (isAcousticFlag) 1L else 0L
                    )
                }

                val count = db.songFlipDatabaseQueries.countResolverCache().executeAsOne()
                if (count > MAX_RESOLVER_CACHE_ENTRIES) {
                    val removeCount = count - (MAX_RESOLVER_CACHE_ENTRIES - 50L)
                    db.songFlipDatabaseQueries.pruneOldestResolverCache(removeCount)
                }
            }
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to put entry $key", t)
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
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to remove entry $key", t)
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
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to clear storage", t)
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
                    isFavorite = false,
                    isAcoustic = false
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
                    timestamp = row.timestamp,
                    isHistory = true,
                    thumbnailUrl = newerResolver?.thumbnailUrl ?: row.thumbnail_url,
                    isFavorite = row.is_favorite != 0L,
                    isAcoustic = row.is_acoustic != 0L
                )
            }
            result
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to loadAll", t)
            emptyMap()
        }
    }

    fun getMetaValue(key: String): String? {
        val db = getDatabase() ?: return null
        return try {
            db.songFlipDatabaseQueries.getMetaValue(key).executeAsOneOrNull()
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to get meta $key", t)
            null
        }
    }

    fun setMetaValue(key: String, value: String) {
        val db = getDatabase() ?: return
        try {
            db.songFlipDatabaseQueries.setMetaValue(key, value)
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to set meta $key", t)
        }
    }

    fun enqueuePendingAcousticScan(id: String, signatureBase64: String, durationMs: Long, createdAt: Long = getCurrentTimeMillis()) {
        val db = getDatabase() ?: return
        try {
            db.songFlipDatabaseQueries.insertPendingAcousticScan(id, signatureBase64, durationMs, createdAt)
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to enqueue pending acoustic scan $id", t)
        }
    }

    fun getPendingAcousticScans(): List<PendingAcousticScan> {
        val db = getDatabase() ?: return emptyList()
        return try {
            db.songFlipDatabaseQueries.getAllPendingAcousticScans().executeAsList().map { row ->
                PendingAcousticScan(
                    id = row.id,
                    signatureBase64 = row.signature_base64,
                    durationMs = row.duration_ms,
                    createdAt = row.created_at
                )
            }
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to getPendingAcousticScans", t)
            emptyList()
        }
    }

    fun deletePendingAcousticScan(id: String) {
        val db = getDatabase() ?: return
        try {
            db.songFlipDatabaseQueries.deletePendingAcousticScanById(id)
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to deletePendingAcousticScan $id", t)
        }
    }

    fun countPendingAcousticScans(): Int {
        val db = getDatabase() ?: return 0
        return try {
            db.songFlipDatabaseQueries.countPendingAcousticScans().executeAsOne().toInt()
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Failed to countPendingAcousticScans", t)
            0
        }
    }

    fun isMigrationDone(migrationKey: String): Boolean {
        return getMetaValue(migrationKey) == "done"
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
                        val mergedAcoustic = (existingHistory?.is_acoustic == 1L) || entry.isAcoustic
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
                                is_favorite = if (mergedFav) 1L else 0L,
                                is_acoustic = if (mergedAcoustic) 1L else 0L
                            )
                        }
                    }
                }
                db.songFlipDatabaseQueries.setMetaValue(migrationKey, "done")
            }
            true
        } catch (t: Throwable) {
            SongFlipLogger.w("SqlDelightCacheStorage", "Migration $migrationKey failed", t)
            false
        }
    }
}
