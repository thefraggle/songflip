package de.goork.songflip

import de.goork.songflip.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Test

class ProNudgeMilestoneTest {

    @Test
    fun testMilestonesBelow5() {
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 0, lastDismissed = 0))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 4, lastDismissed = 0))
    }

    @Test
    fun testFirstMilestoneAt5() {
        assertEquals(5, SettingsRepository.calculateActiveMilestone(flips = 5, lastDismissed = 0))
        assertEquals(5, SettingsRepository.calculateActiveMilestone(flips = 19, lastDismissed = 0))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 19, lastDismissed = 5))
    }

    @Test
    fun testMilestoneAt20() {
        assertEquals(20, SettingsRepository.calculateActiveMilestone(flips = 20, lastDismissed = 5))
        assertEquals(20, SettingsRepository.calculateActiveMilestone(flips = 25, lastDismissed = 5))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 25, lastDismissed = 20))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 49, lastDismissed = 20))
    }

    @Test
    fun testPostFlipPaywallTriggerRules() {
        assertEquals(false, SettingsRepository.shouldTriggerPostFlipPaywall(isPro = false, flips = 1, hasShown = false))
        assertEquals(true, SettingsRepository.shouldTriggerPostFlipPaywall(isPro = false, flips = 2, hasShown = false))
        assertEquals(false, SettingsRepository.shouldTriggerPostFlipPaywall(isPro = false, flips = 2, hasShown = true))
        assertEquals(false, SettingsRepository.shouldTriggerPostFlipPaywall(isPro = true, flips = 2, hasShown = false))
        assertEquals(false, SettingsRepository.shouldTriggerPostFlipPaywall(isPro = false, flips = 3, hasShown = false))
    }

    @Test
    fun testSecondMilestoneAt50() {
        assertEquals(50, SettingsRepository.calculateActiveMilestone(flips = 50, lastDismissed = 20))
        assertEquals(50, SettingsRepository.calculateActiveMilestone(flips = 60, lastDismissed = 20))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 50, lastDismissed = 50))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 99, lastDismissed = 50))
    }

    @Test
    fun testThirdMilestoneAt100() {
        assertEquals(100, SettingsRepository.calculateActiveMilestone(flips = 100, lastDismissed = 50))
        assertEquals(100, SettingsRepository.calculateActiveMilestone(flips = 120, lastDismissed = 50))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 100, lastDismissed = 100))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 149, lastDismissed = 100))
    }

    @Test
    fun testEvery50Thereafter() {
        // 150
        assertEquals(150, SettingsRepository.calculateActiveMilestone(flips = 150, lastDismissed = 100))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 150, lastDismissed = 150))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 199, lastDismissed = 150))

        // 200
        assertEquals(200, SettingsRepository.calculateActiveMilestone(flips = 200, lastDismissed = 150))
        assertEquals(0, SettingsRepository.calculateActiveMilestone(flips = 200, lastDismissed = 200))

        // 250
        assertEquals(250, SettingsRepository.calculateActiveMilestone(flips = 250, lastDismissed = 200))
    }
}
