package dev.gnacho.netgrip.domain.fleet

import dev.gnacho.netgrip.data.dto.FleetNodeDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FleetRulesTest {

    @Test
    fun hostPortComposesTheAddressThePanelExpects() {
        assertEquals("192.168.1.20:8080", FleetRules.hostPort("192.168.1.20", 8080))
        assertEquals("router.local:9000", FleetRules.hostPort(" router.local/ ", 9000))
        assertEquals("router.local", FleetRules.hostPort("router.local", 0))
        // Ya trae puerto: no se duplica.
        assertEquals("192.168.1.20:8080", FleetRules.hostPort("192.168.1.20:8080", 9000))
        // IPv6 sin corchetes se envuelve.
        assertEquals("[fd00::1]:8080", FleetRules.hostPort("fd00::1", 8080))
        assertEquals("[fd00::1]", FleetRules.hostPort("fd00::1", 0))
        assertEquals("[fd00::1]:8081", FleetRules.hostPort("[fd00::1]:8081", 9000))
        assertEquals("", FleetRules.hostPort("  ", 8080))
    }

    @Test
    fun summaryCountsWhatTheHeaderShows() {
        val nodes = listOf(
            FleetNodeDto(id = "a", reachable = true, currentVersion = "0.9", updateAvailable = true),
            FleetNodeDto(id = "b", reachable = true, currentVersion = "0.9"),
            FleetNodeDto(id = "c", reachable = false, error = "timeout"),
            FleetNodeDto(id = "d", reachable = false),
        )
        val summary = FleetRules.summary(nodes)
        assertEquals(4, summary.total)
        assertEquals(2, summary.reachable)
        assertEquals(1, summary.updates)
        assertEquals(1, summary.failing)
        // Un nodo sin comprobar no es un nodo que no responde.
        assertEquals(1, summary.unchecked)
    }

    @Test
    fun skipReasonsSayWhyADeviceStaysOutOfABatch() {
        assertEquals(
            "no responde: timeout",
            FleetRules.skipReason(FleetNodeDto(id = "a", reachable = false, error = "timeout")),
        )
        assertEquals("no responde", FleetRules.skipReason(FleetNodeDto(id = "a", reachable = false)))
        assertEquals(
            "ya está en la 0.9.1",
            FleetRules.skipReason(FleetNodeDto(id = "a", reachable = true, currentVersion = "0.9.1")),
        )
        assertTrue(
            FleetRules.skipReason(FleetNodeDto(id = "a", reachable = true))!!.contains("sin actualización"),
        )
        assertEquals(
            "sin identificador en el panel",
            FleetRules.skipReason(FleetNodeDto(id = "", reachable = true, updateAvailable = true)),
        )
        assertNull(
            FleetRules.skipReason(
                FleetNodeDto(id = "a", reachable = true, updateAvailable = true, currentVersion = "0.9", latestVersion = "0.9.2"),
            ),
        )
    }

    @Test
    fun onlyTheCandidatesEnterTheBatch() {
        val nodes = listOf(
            FleetNodeDto(id = "a", reachable = true, updateAvailable = true),
            FleetNodeDto(id = "b", reachable = false),
            FleetNodeDto(id = "c", reachable = true),
        )
        assertEquals(listOf("a"), FleetRules.updateCandidates(nodes).map { it.id })
    }
}
