package com.example.spikivegcs.network

import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkDiscoveryStateTest {
    @Test
    fun missingNetworksDoNotFallbackToCellular() {
        val state = NetworkDiscoveryState()

        assertNull(state.candidate(NetworkKind.USB_ETHERNET))
        assertNull(state.candidate(NetworkKind.WIFI))
        assertNull(state.selected)
    }

    @Test
    fun onlyEthernetAndWifiKindsExist() {
        assertEquals(
            setOf(NetworkKind.USB_ETHERNET, NetworkKind.WIFI),
            NetworkKind.entries.toSet(),
        )
    }
}
