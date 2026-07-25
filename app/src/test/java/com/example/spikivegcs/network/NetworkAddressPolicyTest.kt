package com.example.spikivegcs.network

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkAddressPolicyTest {
    @Test
    fun acceptsNormalIpv4WithoutAssumingASubnet() {
        assertTrue(address("192.168.111.5").isUsableForGroundStation())
        assertTrue(address("10.42.7.9").isUsableForGroundStation())
        assertTrue(address("172.31.200.18").isUsableForGroundStation())
    }

    @Test
    fun acceptsRoutableIpv6() {
        assertTrue(address("fd12:3456:789a::20").isUsableForGroundStation())
    }

    @Test
    fun rejectsAddressesThatDoNotIndicateAUsableInterface() {
        assertFalse(address("0.0.0.0").isUsableForGroundStation())
        assertFalse(address("127.0.0.1").isUsableForGroundStation())
        assertFalse(address("::1").isUsableForGroundStation())
        assertFalse(address("fe80::1234").isUsableForGroundStation())
        assertFalse(address("224.0.0.1").isUsableForGroundStation())
    }

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
