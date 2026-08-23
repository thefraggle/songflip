package de.goork.songflip

import de.goork.songflip.data.NetworkUtils
import org.junit.Assert.assertNotNull
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun testNetworkUtilsObjectExists() {
        assertNotNull(NetworkUtils)
    }
}
