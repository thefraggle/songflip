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
}
