package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Los cuerpos son los que sirve el panel (internal/server handleFleetGet y
 * handleFleetDiscovered, y los structs de internal/modules/fleet.go y
 * fleet_discovery.go).
 */
class FleetDtoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun fleetListCarriesTheLastKnownStatus() {
        val fleet = json.decodeFromString<FleetDto>(
            """{"nodes":[
              {"id":"casa","name":"Casa","address":"192.168.1.1:8080","reachable":true,
               "current_version":"0.82.0","latest_version":"0.82.0","update_available":false},
              {"id":"ofi","name":"Oficina","address":"10.0.0.2","reachable":false,
               "error":"timeout","password":"no-viaja"}]}""",
        )
        assertEquals(2, fleet.nodes.size)
        assertEquals("Casa", fleet.nodes.first().name)
        assertEquals("0.82.0", fleet.nodes.first().currentVersion)
        assertEquals(false, fleet.nodes.first().updateAvailable)
        assertEquals("timeout", fleet.nodes[1].error)
        assertEquals(false, fleet.nodes[1].reachable)
    }

    @Test
    fun nodesWithOnlyTheEssentialsParse() {
        val fleet = json.decodeFromString<FleetDto>("""{"nodes":[{"id":"a","name":"A","address":"h"}]}""")
        assertEquals(1, fleet.nodes.size)
        assertEquals(false, fleet.nodes.single().reachable)
        assertNull(fleet.nodes.single().error)
    }

    @Test
    fun discoveredPeersParseWithTheirSeenAt() {
        val peers = json.decodeFromString<FleetPeersDto>(
            """{"peers":[{"id":"nuevo","name":"Salon","version":"0.82.0",
              "address":"192.168.1.77","port":8080,"seen_at":"2026-11-26T20:10:00Z"}]}""",
        )
        val peer = peers.peers.single()
        assertEquals("nuevo", peer.id)
        assertEquals(8080, peer.port)
        assertEquals("2026-11-26T20:10:00Z", peer.seenAt)
    }

    @Test
    fun emptyDiscoveryAndConfigParse() {
        assertTrue(json.decodeFromString<FleetPeersDto>("""{"peers":[]}""").peers.isEmpty())
        assertEquals(false, json.decodeFromString<FleetDiscoveryConfigDto>("""{"enabled":false}""").enabled)
        assertEquals("adopted", json.decodeFromString<FleetStatusResponse>("""{"status":"adopted"}""").status)
    }

    @Test
    fun adoptRequestSendsEveryFieldEvenEmptyPassword() {
        // El servidor exige contrasena, pero el DTO no lleva defaults para que
        // kotlinx no omita nada de lo que se construye.
        val body = Json.encodeToString(
            FleetAdoptRequest.serializer(),
            FleetAdoptRequest(id = "nuevo", name = "Salon", address = "192.168.1.77:8080", password = ""),
        )
        assertEquals(
            """{"id":"nuevo","name":"Salon","address":"192.168.1.77:8080","password":""}""",
            body,
        )
    }
}
