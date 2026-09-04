package de.goork.songflip

import de.goork.songflip.data.PauseHelper
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class PauseHelperTest {

    @Test
    fun testTomorrowMorningTimestampIsFutureAndAt6AM() {
        val timestamp = PauseHelper.getTomorrowMorningTimestamp()
        val now = System.currentTimeMillis()
        assertTrue("Timestamp must be in the future", timestamp > now)

        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
    }

    @Test
    fun testTomorrowMorningDifferenceIsWithinExpectedHours() {
        val timestamp = PauseHelper.getTomorrowMorningTimestamp()
        val now = System.currentTimeMillis()
        val diffHours = (timestamp - now) / (1000 * 60 * 60)
        assertTrue("Difference should be between 0 and 30 hours", diffHours in 0..30)
    }
}
