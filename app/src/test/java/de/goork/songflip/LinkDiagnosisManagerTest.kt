package de.goork.songflip

import de.goork.songflip.data.LinkDiagnosisManager
import de.goork.songflip.data.PackageUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkDiagnosisManagerTest {

    @Test
    fun testSupportedPlatformsCompleteness() {
        val platforms = LinkDiagnosisManager.supportedPlatforms
        assertEquals(6, platforms.size)

        val keys = platforms.map { it.key }
        assertTrue(keys.contains("spotify"))
        assertTrue(keys.contains("youtubeMusic"))
        assertTrue(keys.contains("appleMusic"))
        assertTrue(keys.contains("deezer"))
        assertTrue(keys.contains("tidal"))
        assertTrue(keys.contains("amazonMusic"))
    }

    @Test
    fun testPlatformPackagesMatchPackageUtils() {
        LinkDiagnosisManager.supportedPlatforms.forEach { platform ->
            val expectedPkg = PackageUtils.packageMap[platform.key]
            assertEquals("Package for ${platform.key} must match PackageUtils", expectedPkg, platform.packageName)
        }
    }

    @Test
    fun testSampleUrlsAreValidHttps() {
        LinkDiagnosisManager.supportedPlatforms.forEach { platform ->
            assertTrue("Sample URL for ${platform.key} must be valid HTTPS", platform.sampleUrl.startsWith("https://"))
        }
    }
}
