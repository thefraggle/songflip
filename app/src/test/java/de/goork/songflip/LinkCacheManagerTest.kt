package de.goork.songflip

import de.goork.songflip.data.LinkCacheManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LinkCacheManagerTest {

    @Before
    fun setUp() {
        LinkCacheManager.clear()
    }

    @Test
    fun testCachePutAndGet() {
        val inputUrl = "https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv?si=abc"
        val targetPlatform = "youtubeMusic"
        val targetUrl = "https://music.youtube.com/watch?v=fJ9rUzIMcZQ"

        LinkCacheManager.put(
            canonicalUrl = inputUrl,
            targetPlatformKey = targetPlatform,
            targetUrl = targetUrl,
            platform = targetPlatform,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            isAlbum = false
        )

        val cached = LinkCacheManager.get(inputUrl, targetPlatform)
        assertNotNull(cached)
        assertEquals(targetUrl, cached?.targetUrl)
        assertEquals("Bohemian Rhapsody", cached?.title)
        assertEquals("Queen", cached?.artist)
        assertFalse(cached?.isAlbum ?: true)
    }

    @Test
    fun testCacheMiss() {
        val nonExistent = LinkCacheManager.get("https://music.apple.com/song/123", "spotify")
        assertNull(nonExistent)
    }

    @Test
    fun testCacheClear() {
        LinkCacheManager.put(
            canonicalUrl = "https://song.link/s/123",
            targetPlatformKey = "appleMusic",
            targetUrl = "https://music.apple.com/song/123",
            platform = "appleMusic"
        )
        assertEquals(1, LinkCacheManager.size())
        LinkCacheManager.clear()
        assertEquals(0, LinkCacheManager.size())
    }

    @Test
    fun testHistoryRetrievalAndLimit() {
        for (i in 1..15) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic",
                title = "Song $i",
                artist = "Artist $i"
            )
            // Small sleep to ensure distinct timestamps
            Thread.sleep(2)
        }

        val freeHistory = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, freeHistory.size)
        // Newest should be Song 15
        assertEquals("Song 15", freeHistory.first().title)
        assertEquals("Song 6", freeHistory.last().title)

        val proHistory = LinkCacheManager.getHistoryEntries(limit = 100)
        assertEquals(15, proHistory.size)
        assertEquals("Song 15", proHistory.first().title)
        assertEquals("Song 1", proHistory.last().title)
    }

    @Test
    fun testHistoryItemDeletionAndRefill() {
        for (i in 1..15) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic",
                title = "Song $i",
                artist = "Artist $i"
            )
            Thread.sleep(2)
        }

        var history = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, history.size)
        assertEquals("Song 15", history.first().title)

        // Delete top item (Song 15)
        val topKey = history.first().cacheKey
        LinkCacheManager.removeByCacheKey(topKey)

        // New history should still have 10 items because Song 5 refills the 10th spot!
        history = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals(10, history.size)
        assertEquals("Song 14", history.first().title)
        assertEquals("Song 5", history.last().title)

        // Song 15 should no longer be in cache
        val checkDeleted = LinkCacheManager.get("https://open.spotify.com/track/15", "youtubeMusic")
        assertNull(checkDeleted)
    }

    @Test
    fun testClearHistoryAndCache() {
        for (i in 1..5) {
            LinkCacheManager.put(
                canonicalUrl = "https://open.spotify.com/track/$i",
                targetPlatformKey = "youtubeMusic",
                targetUrl = "https://music.youtube.com/watch?v=video$i",
                platform = "youtubeMusic"
            )
        }
        assertEquals(5, LinkCacheManager.getHistoryEntries(limit = 10).size)
        LinkCacheManager.clearHistoryAndCache()
        assertEquals(0, LinkCacheManager.getHistoryEntries(limit = 10).size)
        assertEquals(0, LinkCacheManager.getTotalCachedCount())
    }

    @Test
    fun testPrefetchedEntryDoesNotPoisonHistoryUntilMarked() {
        val prefetchUrl = "https://open.spotify.com/track/prefetch123"
        val targetPlatform = "youtubeMusic"
        val targetUrl = "https://music.youtube.com/watch?v=prefetched"

        // Prefetch writes with isHistory = false
        LinkCacheManager.put(
            canonicalUrl = prefetchUrl,
            targetPlatformKey = targetPlatform,
            targetUrl = targetUrl,
            platform = targetPlatform,
            title = "Prefetched Song",
            artist = "Silent Artist",
            isHistory = false
        )

        // Item is in L1 cache
        val cached = LinkCacheManager.get(prefetchUrl, targetPlatform)
        assertNotNull("Prefetched item must be present in L1 cache", cached)
        assertEquals(targetUrl, cached?.targetUrl)

        // Item MUST NOT appear in user history
        val historyBefore = LinkCacheManager.getHistoryEntries(limit = 10)
        assertTrue("Prefetched song without user click must not appear in history", historyBefore.isEmpty())

        // User actually clicks / redirects: mark as history
        LinkCacheManager.markAsHistory(prefetchUrl, targetPlatform)

        val historyAfter = LinkCacheManager.getHistoryEntries(limit = 10)
        assertEquals("Song must now appear in history after being marked", 1, historyAfter.size)
        assertEquals("Prefetched Song", historyAfter.first().title)
    }

    @Test
    fun testCacheClearPreservesCacheVersion() {
        val prefs = FakeSharedPreferences()
        LinkCacheManager.init(prefs)

        // Add an item
        LinkCacheManager.put(
            canonicalUrl = "https://open.spotify.com/track/versionTest",
            targetPlatformKey = "youtubeMusic",
            targetUrl = "https://music.youtube.com/watch?v=v1",
            platform = "youtubeMusic"
        )

        // Clear cache
        LinkCacheManager.clear()

        // Cache version must still be 5 (CURRENT_CACHE_VERSION) and not reset or deleted
        val savedVersion = prefs.getInt("songflip_cache_version", -1)
        assertEquals(5, savedVersion)
    }

    @Test
    fun testPrefetchDoesNotDemoteExistingHistory() {
        val songUrl = "https://open.spotify.com/track/alreadyFlipped"
        val targetPlatform = "youtubeMusic"

        // Step 1: User flipped song previously (isHistory = true)
        LinkCacheManager.put(
            canonicalUrl = songUrl,
            targetPlatformKey = targetPlatform,
            targetUrl = "https://music.youtube.com/watch?v=alreadyFlipped",
            platform = "youtubeMusic",
            title = "Existing History Song",
            isHistory = true
        )
        assertEquals(1, LinkCacheManager.getHistoryEntries().size)

        // Step 2: Predictive prefetch runs later for same song (isHistory = false)
        LinkCacheManager.put(
            canonicalUrl = songUrl,
            targetPlatformKey = targetPlatform,
            targetUrl = "https://music.youtube.com/watch?v=alreadyFlipped",
            platform = "youtubeMusic",
            title = "Existing History Song",
            isHistory = false
        )

        // Step 3: Entry MUST STILL be in history and not demoted
        val history = LinkCacheManager.getHistoryEntries()
        assertEquals(1, history.size)
        assertEquals("Existing History Song", history.first().title)
    }

    private class FakeSharedPreferences : android.content.SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = HashMap(data)
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        private class FakeEditor(private val backing: MutableMap<String, Any?>) : android.content.SharedPreferences.Editor {
            private val temp = mutableMapOf<String, Any?>()
            private var clearCalled = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) temp[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) {
                    temp.remove(key)
                    backing.remove(key)
                }
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                clearCalled = true
                temp.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                if (clearCalled) {
                    backing.clear()
                }
                backing.putAll(temp)
            }
        }
    }
}
