package de.goork.songflip

import de.goork.songflip.data.DomainStatusInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainVerificationUtilsTest {

    @Test
    fun testDomainStatusInfoFullyEnabled() {
        val status = DomainStatusInfo(
            totalHosts = 10,
            enabledHosts = 10,
            unverifiedHosts = emptyList(),
            isFullyEnabled = true,
            isPartiallyEnabled = false
        )
        assertEquals(10, status.totalHosts)
        assertEquals(10, status.enabledHosts)
        assertTrue(status.unverifiedHosts.isEmpty())
        assertTrue(status.isFullyEnabled)
        assertFalse(status.isPartiallyEnabled)
    }

    @Test
    fun testDomainStatusInfoPartiallyEnabled() {
        val status = DomainStatusInfo(
            totalHosts = 10,
            enabledHosts = 6,
            unverifiedHosts = listOf("open.spotify.com", "deezer.com", "tidal.com", "music.amazon.com"),
            isFullyEnabled = false,
            isPartiallyEnabled = true
        )
        assertEquals(10, status.totalHosts)
        assertEquals(6, status.enabledHosts)
        assertEquals(4, status.unverifiedHosts.size)
        assertFalse(status.isFullyEnabled)
        assertTrue(status.isPartiallyEnabled)
    }

    @Test
    fun testDomainStatusInfoDisabled() {
        val status = DomainStatusInfo(
            totalHosts = 5,
            enabledHosts = 0,
            unverifiedHosts = listOf("a", "b", "c", "d", "e"),
            isFullyEnabled = false,
            isPartiallyEnabled = false
        )
        assertEquals(5, status.totalHosts)
        assertEquals(0, status.enabledHosts)
        assertFalse(status.isFullyEnabled)
        assertFalse(status.isPartiallyEnabled)
    }
}
