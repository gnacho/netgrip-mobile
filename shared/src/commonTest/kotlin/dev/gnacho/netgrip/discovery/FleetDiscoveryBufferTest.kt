package dev.gnacho.netgrip.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun router(
    id: String = "rt2",
    name: String = "Router 2",
    address: String = "192.168.1.2",
    port: Int = 8080,
    version: String = "v1",
) = DiscoveredRouter(id, name, version, address, port)

class FleetDiscoveryBufferTest {

    @Test
    fun duplicateIdAddressAndPortReplacesOneEntry() {
        val buffer = FleetDiscoveryBuffer()
        assertTrue(buffer.offer(router(version = "v1")))
        assertTrue(buffer.offer(router(name = "Updated", version = "v2")))
        assertEquals(listOf("Updated"), buffer.snapshot().map { it.name })
        assertEquals(listOf("v2"), buffer.snapshot().map { it.version })
    }

    @Test
    fun sameIdAtAnotherEndpointRemainsSeparate() {
        val buffer = FleetDiscoveryBuffer()
        buffer.offer(router())
        buffer.offer(router(address = "192.168.1.22"))
        buffer.offer(router(port = 8090))
        assertEquals(3, buffer.snapshot().size)
    }

    @Test
    fun malformedPacketDoesNotChangeResults() {
        val buffer = FleetDiscoveryBuffer()
        buffer.offer(router())
        assertFalse(buffer.offer("broken", "192.168.1.2", 1_800_000_000L))
        assertEquals(1, buffer.snapshot().size)
    }

    @Test
    fun resultsAreSortedAndCapacityIsBounded() {
        val buffer = FleetDiscoveryBuffer(capacity = 2)
        buffer.offer(router(id = "z", name = "Zulu", address = "192.168.1.9"))
        buffer.offer(router(id = "a", name = "alpha", address = "192.168.1.3"))
        assertFalse(buffer.offer(router(id = "b", name = "Bravo")))
        assertEquals(listOf("alpha", "Zulu"), buffer.snapshot().map { it.name })
    }
}
