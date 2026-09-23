package de.goork.songflip

import de.goork.songflip.data.ContactHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactHelperTest {

    @Test
    fun testSupportEmailAddress() {
        assertEquals("songflip@goork.de", ContactHelper.SUPPORT_EMAIL)
    }

    @Test
    fun testMailtoUriFormatting() {
        val subject = "SongFlip Feedback (v1.4.15)"
        val encodedSubject = java.net.URLEncoder.encode(subject, "UTF-8")
        val mailtoUri = "mailto:${ContactHelper.SUPPORT_EMAIL}?subject=$encodedSubject"

        assertTrue(mailtoUri.startsWith("mailto:songflip@goork.de?subject="))
        assertTrue(mailtoUri.contains("SongFlip"))
    }
}
