package de.goork.songflip.core.cache

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import de.goork.songflip.core.db.SongFlipDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HistoryPersistenceTest {

    @Test
    fun testExistingHistoryWith60SongsPreservedOnStartup() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SongFlipDatabase.Schema.create(driver)

        // Seed 60 songs into the database
        val db = SongFlipDatabase(driver)
        for (i in 1..60) {
            db.songFlipDatabaseQueries.upsertHistory(
                cache_key = "https://spotify.com/track/$i|apple_music",
                canonical_url = "https://spotify.com/track/$i",
                target_platform_key = "apple_music",
                target_url = "https://music.apple.com/song/$i",
                platform = "apple_music",
                title = "Track $i",
                artist = "Artist $i",
                is_album = 0L,
                native_app_uri = null,
                timestamp = 1700000000000L + (i * 1000L),
                thumbnail_url = "https://img.com/$i.jpg",
                is_favorite = if (i % 5 == 0) 1L else 0L,
                is_acoustic = 0L
            )
        }

        // Verify count
        val seededCount = db.songFlipDatabaseQueries.countHistory().executeAsOne()
        assertEquals(60L, seededCount)

        // Wrap into SqlDelightCacheStorage as Android does
        val storage = SqlDelightCacheStorage(
            driverProvider = { driver }
        )

        val loadedEntries = storage.loadAll()
        val historyEntries = loadedEntries.filter { it.value.isHistory }
        assertEquals(60, historyEntries.size)

        val first = storage.get("https://spotify.com/track/1|apple_music")
        assertNotNull(first)
        assertEquals("Track 1", first?.title)
        assertEquals("Artist 1", first?.artist)
    }

    @Test
    fun testAndroidSqlitePragmaSyntaxMustUseQueryNotExecSQL() {
        val sourceFile = java.io.File("src/androidMain/kotlin/de/goork/songflip/core/cache/PlatformCacheStorage.android.kt")
        val content = sourceFile.readText()
        // Android FrameworkSQLiteDatabase.execSQL("PRAGMA busy_timeout = 5000;") throws
        // SQLiteException ("Queries can be performed using SQLiteDatabase query or rawQuery methods only.")
        // during onConfigure(), preventing the SQLite DB from opening and leaving Android history empty!
        org.junit.Assert.assertFalse(
            "Forbidden db.execSQL(\"PRAGMA ...\") found in PlatformCacheStorage.android.kt; use db.query(\"PRAGMA ...\").close() instead",
            content.contains("execSQL(\"PRAGMA")
        )
    }

    @Test
    fun testIosSwiftResolveTargetUrlCallsMustIncludeIsPrefetchWhenUsingFullSignature() {
        val iosDir = java.io.File("../iosApp")
        val swiftFiles = iosDir.walkTopDown().filter { it.isFile && it.extension == "swift" }.toList()
        org.junit.Assert.assertTrue("Expected Swift source files in ../iosApp", swiftFiles.isNotEmpty())

        val callBlockRegex = Regex("""\.resolveTargetUrl\s*\(([\s\S]*?)\n\s*\)""")
        for (file in swiftFiles) {
            val text = file.readText()
            for (match in callBlockRegex.findAll(text)) {
                val args = match.groupValues[1]
                // Kotlin/Native exports no default parameter values to Objective-C/Swift.
                // Any call passing isPro, authToken, or audiophileMode must pass all 9 parameters including isPrefetch:
                if (args.contains("isPro:") || args.contains("authToken:") || args.contains("audiophileMode:")) {
                    org.junit.Assert.assertTrue(
                        "Missing 'isPrefetch:' parameter in Kotlin/Native resolveTargetUrl call in ${file.name}: $args",
                        args.contains("isPrefetch:")
                    )
                }
            }
        }
    }
}
