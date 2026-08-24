package de.goork.songflip

import de.goork.songflip.data.ProManager
import de.goork.songflip.data.RedeemResult
import org.junit.Assert.*
import org.junit.Test

class ProManagerTest {

    @Test
    fun testInvalidCodeReturnsInvalid() {
        val result = ProManager.redeemCoupon("RANDOM_INVALID_CODE_123")
        assertEquals(RedeemResult.INVALID, result)
    }

    @Test
    fun testEmptyCodeReturnsInvalid() {
        val result = ProManager.redeemCoupon("")
        assertEquals(RedeemResult.INVALID, result)
    }
}
