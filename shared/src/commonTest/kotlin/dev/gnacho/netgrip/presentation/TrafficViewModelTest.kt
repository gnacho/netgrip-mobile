package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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

private const val TRAFFIC_BASE = "https://router.local:8080"

private val NLBWMON_JSON = """{"installed":true,"running":true,"generations":2,
 "commit_interval":30,"prealloc_days":7,"protocol_database":true}"""

private val NLBWMON_TOP_JSON = """{"devices":[
 {"key":"aa:bb:cc:dd:ee:01","ip":"192.168.1.20","conns":12,"down_bytes":1024,"up_bytes":256},
 {"key":"aa:bb:cc:dd:ee:02","ip":"192.168.1.21","conns":2,"down_bytes":10,"up_bytes":5}
],"apps":[
 {"key":"HTTPS","conns":9,"down_bytes":900,"up_bytes":90},
 {"key":"other","conns":1,"down_bytes":50,"up_bytes":10}
]}"""

private val DPI_JSON = """{"applicable":true,"total_bytes":2048,"total_flows":4,
 "protocols":[{"name":"HTTPS","bytes":2000,"flows":3,"category":"web"}]}"""

private val DPI_APPS_JSON = """{"apps":[
 {"name":"Spotify","category":"streaming","bytes":2048,"local_bytes":1024,
  "other_bytes":1024,"packets":40,"flows":3}]}"""

private val DPI_TIMELINE_JSON = """{"buckets":[
 {"time":"2026-11-26T17:30:00Z","apps":{"Spotify":{"local":1,"other":2,"total":3}}}],
 "top":[{"name":"Spotify","category":"streaming","bytes":3}],
 "totals":{"local":1,"other":2,"total":3}}"""

private val CLIENTS_JSON = """{"clients":[
 {"mac":"AA:BB:CC:DD:EE:01","name":"Portatil","ip":"192.168.1.20","type":"wifi5"},
 {"mac":"aa:bb:cc:dd:ee:02","name":"Tele","ip":"192.168.1.21","type":"cable"}
],"bands":["2g","5g"]}"""

private val META_JSON = """{"meta":{"aa:bb:cc:dd:ee:01":{"name":"Portatil de Nacho"}}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class TrafficViewModelTest {

    @Test
    fun loadsAccountingAndDpiInOnePass() = runTrafficTest { engine, viewModel ->
        viewModel.loadTraffic()
        advanceUntilIdle()
        val state = viewModel.traffic.value
        assertTrue(state.loaded)
        assertNull(state.error)
        assertEquals(true, state.nlbwmon?.running)
        assertEquals(2, state.devices.size)
        assertEquals(2, state.apps.size)
        assertEquals(true, state.dpi?.applicable)
        assertEquals(1, state.dpiApps.size)
        assertEquals(3, state.timeline?.totals?.total)
        // El nombre guardado gana al que anuncia el cliente.
        assertEquals("Portatil de Nacho", state.names["aa:bb:cc:dd:ee:01"])
        assertEquals("Tele", state.names["aa:bb:cc:dd:ee:02"])
        assertEquals(
            listOf(
                "/api/nlbwmon",
                "/api/nlbwmon/top",
                "/api/dpi",
                "/api/dpi/apps",
                "/api/dpi/timeline",
                "/api/clients",
                "/api/clients/meta",
            ),
            engine.requests,
        )
    }

    @Test
    fun oneFailingReadingKeepsTheRest() = runTrafficTest(override = { path ->
        if (path == "/api/dpi") "" to HttpStatusCode.InternalServerError else null
    }) { _, viewModel ->
        viewModel.loadTraffic()
        advanceUntilIdle()
        val state = viewModel.traffic.value
        assertEquals(2, state.devices.size)
        assertNull(state.dpi)
        assertEquals(1, state.dpiApps.size)
        assertTrue(state.error != null)
    }

    @Test
    fun unauthorizedMarksTheSessionExpired() = runTrafficTest(override = { path ->
        if (path == "/api/nlbwmon/top") "" to HttpStatusCode.Unauthorized else null
    }) { _, viewModel ->
        viewModel.loadTraffic()
        advanceUntilIdle()
        assertTrue(viewModel.sessionExpired.value)
        // La lectura que fallo deja su parte vacia, pero el resto se pinta.
        assertTrue(viewModel.traffic.value.devices.isEmpty())
        assertEquals(true, viewModel.traffic.value.dpi?.applicable)
    }

    @Test
    fun failingNameLookupDoesNotBreakTheScreen() = runTrafficTest(override = { path ->
        if (path == "/api/clients") "" to HttpStatusCode.BadGateway else null
    }) { _, viewModel ->
        viewModel.loadTraffic()
        advanceUntilIdle()
        val state = viewModel.traffic.value
        assertTrue(state.names.isEmpty())
        assertNull(state.error)
        assertEquals(2, state.devices.size)
    }

    @Test
    fun ensureLoadsOnlyOnce() = runTrafficTest { engine, viewModel ->
        viewModel.ensureTraffic()
        advanceUntilIdle()
        viewModel.ensureTraffic()
        advanceUntilIdle()
        assertEquals(7, engine.requests.size)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Probe {
        val requests = mutableListOf<String>()
    }

    private fun runTrafficTest(
        override: ((path: String) -> Pair<String, HttpStatusCode>?)? = null,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe()
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                probe.requests += path
                val forced = override?.invoke(path)
                val body = forced?.first ?: when (path) {
                    "/api/nlbwmon" -> NLBWMON_JSON
                    "/api/nlbwmon/top" -> NLBWMON_TOP_JSON
                    "/api/dpi" -> DPI_JSON
                    "/api/dpi/apps" -> DPI_APPS_JSON
                    "/api/dpi/timeline" -> DPI_TIMELINE_JSON
                    "/api/clients" -> CLIENTS_JSON
                    "/api/clients/meta" -> META_JSON
                    else -> null
                }
                val status = forced?.second ?: HttpStatusCode.OK
                if (body == null) {
                    respond("", HttpStatusCode.NotFound)
                } else {
                    respond(
                        content = body,
                        status = status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), TRAFFIC_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
