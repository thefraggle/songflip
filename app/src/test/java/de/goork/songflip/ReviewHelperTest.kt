package de.goork.songflip

import de.goork.songflip.data.ReviewHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewHelperTest {

    private val now = 1700000000000L
    private val dayMs = 24 * 60 * 60 * 1000L

    @Test
    fun testEligibleWhenAllCriteriaMet() {
        val installTime = now - (1 * dayMs) // Installed 1 day ago (needs >= 1 day)
        val lastPrompt = 0L // Never prompted before
        val flips = 3 // Has 3 flips (needs >= 3)

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertTrue("Expected eligible when all criteria are met", eligible)
    }

    @Test
    fun testNotEligibleWhenFlipsBelowThreshold() {
        val installTime = now - (10 * dayMs)
        val lastPrompt = 0L
        val flips = 2 // Only 2 flips, needs 3

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt if flip count is below 3", eligible)
    }

    @Test
    fun testNotEligibleWhenInstalledTooRecently() {
        val installTime = now - (12 * 60 * 60 * 1000L) // Only 12h installed, needs >= 1 day
        val lastPrompt = 0L
        val flips = 10

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt if installed for less than 1 day", eligible)
    }

    @Test
    fun testNotEligibleDuringCooldown() {
        val installTime = now - (90 * dayMs)
        val lastPrompt = now - (15 * dayMs) // Prompted 15 days ago (cooldown is 21 days)
        val flips = 25

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt during 21-day cooldown", eligible)
    }

    @Test
    fun testEligibleAfterCooldownPassed() {
        val installTime = now - (90 * dayMs)
        val lastPrompt = now - (22 * dayMs) // 22 days ago (cooldown expired)
        val flips = 25

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertTrue("Should prompt after 21-day cooldown expires", eligible)
    }
}
