package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.DiscoveredPeerDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FLEET_BASE = "https://router.local:8080"

private const val FLEET_JSON = """{"nodes":[
 {"id":"zeta","name":"Zeta","address":"192.168.1.30","reachable":true,
  "current_version":"0.82.0","latest_version":"0.82.0","update_available":false},
 {"id":"alfa","name":"Alfa","address":"192.168.1.20:8080","reachable":false,
  "error":"timeout"}]}"""

private const val CHECK_JSON = """{"id":"alfa","name":"Alfa","address":"192.168.1.20:8080",
 "reachable":true,"current_version":"0.82.0","latest_version":"0.83.0","update_available":true}"""

private const val CHECK_ALL_JSON = """{"nodes":[
 {"id":"zeta","name":"Zeta","address":"192.168.1.30","reachable":true,
  "current_version":"0.82.0","latest_version":"0.82.0","update_available":false},
 {"id":"alfa","name":"Alfa","address":"192.168.1.20:8080","reachable":true,
  "current_version":"0.82.0","latest_version":"0.83.0","update_available":true}]}"""

private const val PEERS_JSON = """{"peers":[{"id":"nuevo","name":"Salon","version":"0.82.0",
 "address":"192.168.1.77","port":8080,"seen_at":"2026-11-26T20:10:00Z"}]}"""

/** Tras reiniciar el panel la cache de la flota llega vacia: sin estado. */
private const val NO_STATE_FLEET = """{"nodes":[
 {"id":"rt3","name":"rt3","address":"192.168.1.3:8080"}]}"""

private const val RT3_CHECKED = """{"nodes":[
 {"id":"rt3","name":"rt3","address":"192.168.1.3:8080","reachable":true,
  "current_version":"v0.82.0","latest_version":"v0.82.0","update_available":false}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class FleetViewModelTest {

    @Test
    fun loadsTheFleetPeersAndDiscoverySwitch() = runFleetTest { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        val state = viewModel.fleet.value
        assertTrue(state.loaded)
        assertNull(state.error)
        // La lista se ordena por nombre en la app, no se confía en el orden del panel.
        assertEquals(listOf("Alfa", "Zeta"), state.nodes.map { it.name })
        assertEquals(1, state.discovered.size)
        assertEquals(true, state.discoveryEnabled)
        assertEquals(
            listOf("/api/fleet", "/api/fleet/discovered", "/api/fleet/discovery-config"),
            engine.reads,
        )
    }

    @Test
    fun checkingOneNodeReplacesOnlyThatNode() = runFleetTest { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        viewModel.checkFleetNode("alfa")
        advanceUntilIdle()
        val state = viewModel.fleet.value
        val alfa = state.nodes.first { it.id == "alfa" }
        assertEquals(true, alfa.reachable)
        assertEquals(true, alfa.updateAvailable)
        assertEquals("Alfa puede pasar a 0.83.0", state.actionMessage)
        assertEquals("""{"id":"alfa"}""", engine.writes.single().body)
        // Zeta sigue con el estado que tenía.
        assertEquals("0.82.0", state.nodes.first { it.id == "zeta" }.currentVersion)
    }

    @Test
    fun checkingAllSortsAndSummarises() = runFleetTest { _, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        viewModel.checkAllFleet()
        advanceUntilIdle()
        val state = viewModel.fleet.value
        assertEquals(listOf("Alfa", "Zeta"), state.nodes.map { it.name })
        assertEquals("1 con actualización disponible", state.actionMessage)
    }

    @Test
    fun adoptingComposesTheAddressAndReloadsTheFleet() = runFleetTest { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        val peer = DiscoveredPeerDto(id = "nuevo", name = "Salon", address = "192.168.1.77", port = 8080)
        viewModel.adoptFleetPeer(peer, "  Salón  ", "clave")
        advanceUntilIdle()
        val state = viewModel.fleet.value
        assertEquals("Salón añadido a la flota", state.actionMessage)
        assertNull(state.actionError)
        assertEquals(
            """{"id":"nuevo","name":"Salón","address":"192.168.1.77:8080","password":"clave"}""",
            engine.writes.single().body,
        )
        // Tras adoptar se relee la flota y los peers.
        assertEquals(6, engine.reads.size)
    }

    @Test
    fun adoptionRejectedByThePanelKeepsItsMessage() = runFleetTest(failAdopt = true) { _, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        viewModel.adoptFleetPeer(
            DiscoveredPeerDto(id = "nuevo", name = "Salon", address = "192.168.1.77", port = 8080),
            "Salon",
            "mala",
        )
        advanceUntilIdle()
        val state = viewModel.fleet.value
        assertNull(state.actionMessage)
        assertTrue(state.actionError != null)
        assertNull(state.adopting)
    }

    @Test
    fun loadingTheFleetChecksItWhenNoNodeHasAState() = runFleetTest(
        fleet = NO_STATE_FLEET,
        checkAll = RT3_CHECKED,
    ) { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        // La cache del panel vive en memoria: tras su reinicio llega vacia y
        // la app pregunta una vez en vez de dejar todo en "sin datos".
        assertEquals(listOf("/api/fleet/check-all"), engine.writes.map { it.path })
        assertEquals("rt3", viewModel.fleet.value.nodes.single().id)
        assertEquals(true, viewModel.fleet.value.nodes.single().reachable)
        assertEquals("v0.82.0", viewModel.fleet.value.nodes.single().currentVersion)
    }

    @Test
    fun aCheckedFleetIsNotCheckedAgainOnLoad() = runFleetTest { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        assertTrue(engine.writes.isEmpty())
    }

    @Test
    fun aSuccessfulAdoptionHandsTheRequestToTheCaller() = runFleetTest { _, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        var adopted: dev.gnacho.netgrip.data.dto.FleetAdoptRequest? = null
        viewModel.adoptFleetPeer(
            DiscoveredPeerDto(id = "nuevo", name = "Salon", address = "192.168.1.77", port = 8080),
            "Salon",
            "clave",
        ) { adopted = it }
        advanceUntilIdle()
        assertEquals("nuevo", adopted?.id)
        assertEquals("192.168.1.77:8080", adopted?.address)
    }

    @Test
    fun discoverySwitchPostsAndKeepsTheServerAnswer() = runFleetTest { engine, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        viewModel.setFleetDiscovery(false)
        advanceUntilIdle()
        assertEquals("""{"enabled":false}""", engine.writes.single().body)
        assertEquals(false, viewModel.fleet.value.discoveryEnabled)
        assertEquals("Descubrimiento apagado", viewModel.fleet.value.actionMessage)
    }

    @Test
    fun unauthorizedMarksTheSessionExpired() = runFleetTest(fleetStatus = HttpStatusCode.Unauthorized) { _, viewModel ->
        viewModel.loadFleet()
        advanceUntilIdle()
        assertTrue(viewModel.sessionExpired.value)
        assertEquals(1, viewModel.fleet.value.discovered.size)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Recorded(val method: HttpMethod, val path: String, val body: String)

    private class Probe {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Recorded>()
    }

    private fun runFleetTest(
        failAdopt: Boolean = false,
        fleetStatus: HttpStatusCode = HttpStatusCode.OK,
        fleet: String = FLEET_JSON,
        checkAll: String = CHECK_ALL_JSON,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe()
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                if (request.method == HttpMethod.Post) {
                    val body = try {
                        request.body.toByteArray().decodeToString()
                    } catch (_: Throwable) {
                        ""
                    }
                    probe.writes += Recorded(HttpMethod.Post, path, body)
                    val discoveryAnswer = """{"enabled":${!body.contains("\"enabled\":false")}}"""
                    val (payload, status) = when (path) {
                        "/api/fleet/check" -> CHECK_JSON to HttpStatusCode.OK
                        "/api/fleet/check-all" -> checkAll to HttpStatusCode.OK
                        "/api/fleet/adopt" ->
                            """{"status":"adopted"}""" to
                                if (failAdopt) HttpStatusCode.BadRequest else HttpStatusCode.OK
                        "/api/fleet/discovery-config" -> discoveryAnswer to HttpStatusCode.OK
                        else -> """{"error":"not found"}""" to HttpStatusCode.NotFound
                    }
                    respond(payload, status, json)
                } else {
                    probe.reads += path
                    val (payload, status) = when (path) {
                        "/api/fleet" -> fleet to fleetStatus
                        "/api/fleet/discovered" -> PEERS_JSON to HttpStatusCode.OK
                        "/api/fleet/discovery-config" -> """{"enabled":true}""" to HttpStatusCode.OK
                        else -> null to HttpStatusCode.NotFound
                    }
                    if (payload == null) respond("", status) else respond(payload, status, json)
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), FLEET_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
