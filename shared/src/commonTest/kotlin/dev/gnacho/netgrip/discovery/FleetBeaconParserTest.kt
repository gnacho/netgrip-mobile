package dev.gnacho.netgrip.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val NOW = 1_800_000_000L
private const val SOURCE = "192.168.1.50"

private fun beacon(
    v: Int = 1,
    type: String = "netgrip-fleet-beacon",
    id: String = "flint2",
    name: String = "Flint 2",
    version: String = "v0.79.0",
    address: String = "192.168.1.1",
    port: Int = 8080,
    ts: Long = NOW,
): String = """{"v":$v,"type":"$type","id":"$id","name":"$name","version":"$version","address":"$address","port":$port,"ts":$ts}"""

class FleetBeaconParserTest {

    @Test
    fun validBeaconReturnsRouterAndHttpUrl() {
        val router = FleetBeaconParser.parse(beacon(), SOURCE, NOW)
        assertEquals(
            DiscoveredRouter("flint2", "Flint 2", "v0.79.0", "192.168.1.1", 8080),
            router,
        )
        assertEquals("http://192.168.1.1:8080", router?.baseUrl)
    }

    @Test
    fun emptyAddressFallsBackToDatagramSource() {
        val router = FleetBeaconParser.parse(beacon(address = ""), SOURCE, NOW)
        assertEquals(SOURCE, router?.address)
        assertEquals("http://$SOURCE:8080", router?.baseUrl)
    }

    @Test
    fun missingAddressUsesFallback() {
        val payload = """{"v":1,"type":"netgrip-fleet-beacon","id":"rt2","name":"RT2","version":"v1","port":8090,"ts":$NOW}"""
        assertEquals(SOURCE, FleetBeaconParser.parse(payload, SOURCE, NOW)?.address)
    }

    @Test
    fun wrongTypeIsRejected() {
        assertNull(FleetBeaconParser.parse(beacon(type = "netgrip-fleet-probe"), SOURCE, NOW))
    }

    @Test
    fun wrongProtocolVersionIsRejected() {
        assertNull(FleetBeaconParser.parse(beacon(v = 2), SOURCE, NOW))
    }

    @Test
    fun staleBeaconIsRejected() {
        assertNull(FleetBeaconParser.parse(beacon(ts = NOW - 61), SOURCE, NOW))
    }

    @Test
    fun futureBeaconIsRejected() {
        assertNull(FleetBeaconParser.parse(beacon(ts = NOW + 61), SOURCE, NOW))
    }

    @Test
    fun freshnessBoundariesAreAccepted() {
        listOf(NOW - 60, NOW + 60).forEach { timestamp ->
            assertEquals("flint2", FleetBeaconParser.parse(beacon(ts = timestamp), SOURCE, NOW)?.id)
        }
    }

    @Test
    fun invalidPortsAreRejected() {
        listOf(-1, 0, 65536).forEach { port ->
            assertNull(FleetBeaconParser.parse(beacon(port = port), SOURCE, NOW))
        }
    }

    @Test
    fun malformedJsonIsRejected() {
        assertNull(FleetBeaconParser.parse("{not-json", SOURCE, NOW))
    }

    @Test
    fun invalidIdsAndBlankNamesAreRejected() {
        listOf("", "Flint 2", "-flint2", "a".repeat(65)).forEach { id ->
            assertNull(FleetBeaconParser.parse(beacon(id = id), SOURCE, NOW))
        }
        assertNull(FleetBeaconParser.parse(beacon(name = "  "), SOURCE, NOW))
    }

    @Test
    fun invalidAdvertisedAndSourceAddressesAreRejected() {
        assertNull(FleetBeaconParser.parse(beacon(address = "router.local"), SOURCE, NOW))
        assertNull(FleetBeaconParser.parse(beacon(address = "999.1.1.1"), SOURCE, NOW))
        assertNull(FleetBeaconParser.parse(beacon(address = ""), "not-an-ip", NOW))
    }
}
