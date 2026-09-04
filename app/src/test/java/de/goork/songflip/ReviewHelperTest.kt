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
        val installTime = now - (4 * dayMs) // Installed 4 days ago (needs >= 3 days)
        val lastPrompt = 0L // Never prompted before (needs >= 60 days)
        val flips = 5 // Has 5 flips (needs >= 5)

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertTrue("Expected eligible when all criteria are met", eligible)
    }

    @Test
    fun testNotEligibleWhenFlipsBelowThreshold() {
        val installTime = now - (10 * dayMs)
        val lastPrompt = 0L
        val flips = 4 // Only 4 flips, needs 5

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt if flip count is below 5", eligible)
    }

    @Test
    fun testNotEligibleWhenInstalledTooRecently() {
        val installTime = now - (1 * dayMs) // Only 1 day installed, needs >= 3 days
        val lastPrompt = 0L
        val flips = 10

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt if installed for less than 3 days", eligible)
    }

    @Test
    fun testNotEligibleDuringCooldown() {
        val installTime = now - (90 * dayMs)
        val lastPrompt = now - (20 * dayMs) // Prompted 20 days ago (cooldown is 60 days)
        val flips = 25

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertFalse("Should not prompt during 60-day cooldown", eligible)
    }

    @Test
    fun testEligibleAfterCooldownPassed() {
        val installTime = now - (90 * dayMs)
        val lastPrompt = now - (61 * dayMs) // 61 days ago (cooldown expired)
        val flips = 25

        val eligible = ReviewHelper.isEligibleForReview(now, flips, installTime, lastPrompt)
        assertTrue("Should prompt after 60-day cooldown expires", eligible)
    }
}
