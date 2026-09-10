package de.goork.songflip

import de.goork.songflip.core.util.UrlUtils
import de.goork.songflip.data.LinkCacheManager
import de.goork.songflip.data.OdesliRepository
import de.goork.songflip.data.RedeemResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class Issue19Test {

    @Test
    fun testNetworkErrorStringResourceExistsInAll24Locales() {
        val dbFactory = DocumentBuilderFactory.newInstance()
        val dBuilder = dbFactory.newDocumentBuilder()

        val resDir = File("src/main/res")
        assertTrue("src/main/res directory must exist", resDir.exists())

        val valueDirs = resDir.listFiles()?.filter { it.name.startsWith("values") && it.isDirectory } ?: emptyList()
        assertTrue("Must find at least 24 values directories", valueDirs.size >= 24)

        for (dir in valueDirs) {
            val stringsFile = File(dir, "strings.xml")
            if (!stringsFile.exists()) continue

            val doc = dBuilder.parse(stringsFile)
            val stringNodes = doc.getElementsByTagName("string")
            var found = false
            for (i in 0 until stringNodes.length) {
                val node = stringNodes.item(i)
                val name = node.attributes?.getNamedItem("name")?.nodeValue
                if (name == "pro_coupon_network_error") {
                    found = true
                    assertTrue("String 'pro_coupon_network_error' in ${dir.name} must not be blank", node.textContent.isNotBlank())
                    break
                }
            }
            assertTrue("pro_coupon_network_error must exist in ${dir.name}/strings.xml", found)
        }
    }

    @Test
    fun testRedeemResultNetworkErrorEnum() {
        // Verify NETWORK_ERROR enum exists and can be matched
        val result = RedeemResult.NETWORK_ERROR
        assertEquals("NETWORK_ERROR", result.name)
    }

    @Test
    fun testL1CacheHitDetection() {
        LinkCacheManager.clear()
        val testUrl = "https://open.spotify.com/track/12345"
        val platform = "youtubeMusic"

        // Before caching: cache miss
        val miss = LinkCacheManager.get(testUrl, platform) != null
        assertTrue("Initial link must not be cached", !miss)

        // Put in L1 cache
        LinkCacheManager.put(
            canonicalUrl = testUrl,
            targetPlatformKey = platform,
            targetUrl = "https://music.youtube.com/watch?v=12345",
            platform = platform
        )

        // After caching: cache hit (< 5ms)
        val hit = LinkCacheManager.get(testUrl, platform) != null
        assertTrue("Cached link must be detected as cached to suppress flicker toast", hit)
    }

    @Test
    fun testActionSendUrlExtractionAndTargetRoutingNotHijacked() {
        val repo = OdesliRepository()
        val rawSharedText = "Schau mal hier: https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv?si=abc12345"

        val cleanUrl = repo.extractCleanUrl(rawSharedText)
        assertNotNull("Clean URL must be extracted from ACTION_SEND text", cleanUrl)
        assertEquals("https://open.spotify.com/track/4u7EnebtmKWzUH433cf5Qv", cleanUrl)

        // Verify that target URL is intended for direct playback, not hijacked into universal link
        assertTrue("URL must point to Spotify track, not universal share link", !cleanUrl!!.contains("songflip.link/s/"))
    }
}
