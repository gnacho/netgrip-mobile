package dev.gnacho.netgrip.discovery

import java.io.IOException
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProbeTargetsTest {
    private val subnet = InetAddress.getByName("192.0.2.255")
    private val global = InetAddress.getByName("255.255.255.255")

    @Test
    fun `computes directed broadcast from address and prefix`() {
        val address = InetAddress.getByName("192.168.1.50") as java.net.Inet4Address
        assertEquals(InetAddress.getByName("192.168.1.255"), ipv4Broadcast(address, 24))
        assertEquals(global, ipv4Broadcast(address, 0))
        assertEquals(address, ipv4Broadcast(address, 32))
    }

    @Test
    fun `computes directed broadcast from DHCP values`() {
        fun addressInt(value: String): Int = InetAddress.getByName(value).address.fold(0) { result, octet ->
            (result shl 8) or (octet.toInt() and 0xFF)
        }
        assertEquals(
            InetAddress.getByName("192.168.1.255"),
            ipv4BroadcastFromDhcp(addressInt("192.168.1.50"), addressInt("255.255.255.0")),
        )
        assertEquals(
            InetAddress.getByName("10.0.2.255"),
            ipv4BroadcastFromDhcp(addressInt("10.0.2.16"), addressInt("255.255.255.0")),
        )
    }

    @Test
    fun `an interface broadcast failure does not prevent the global fallback`() {
        val attempted = mutableListOf<InetAddress>()
        sendProbeTargets(listOf(subnet, global)) { target ->
            attempted += target
            if (target == subnet) throw IOException("EPERM")
        }
        assertEquals(listOf(subnet, global), attempted)
    }

    @Test
    fun `all targets failing reports the last socket error`() {
        val error = assertFailsWith<IOException> {
            sendProbeTargets(listOf(subnet, global)) { target ->
                if (target == subnet) throw IOException("first failure")
                throw IOException("EPERM")
            }
        }
        assertEquals("EPERM", error.message)
    }

    @Test
    fun `one successful target is enough to listen for beacons`() {
        val attempted = mutableListOf<InetAddress>()
        sendProbeTargets(listOf(subnet, global)) { target ->
            attempted += target
            if (target == global) throw IOException("EPERM")
        }
        assertEquals(listOf(subnet, global), attempted)
    }
}
