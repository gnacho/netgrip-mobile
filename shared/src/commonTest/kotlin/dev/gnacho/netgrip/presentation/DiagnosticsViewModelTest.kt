package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DIAG_BASE = "https://router.local:8080"

private val LOGS_JSON = """{"entries":[
 {"ts":"Thu Nov 26 17:33:14 2026","facility":"daemon","level":"err","tag":"netgrip[24843]","message":"listening on http://0.0.0.0:8090"},
 {"ts":"Thu Nov 26 17:33:15 2026","facility":"daemon","level":"info","tag":"dnsmasq[1]","message":"query[A] example.org"}
],"total":1207}"""

private val LOGS_FILTERED_JSON = """{"entries":[
 {"ts":"Thu Nov 26 17:33:15 2026","facility":"daemon","level":"info","tag":"dnsmasq[1]","message":"query[A] example.org"}
],"total":1207}"""

private val LEDS_JSON = """{"applicable":true,"leds":[
 {"section":"led_wan_port_link","name":"WAN-PORT-LINK","sysfs":"90000.mdio-1:18:green:wan","trigger":"netdev","dev":"wan","brightness":1,"on":true},
 {"section":"led_led_broken","name":"STATUS","sysfs":"blue:status","brightness":-1}
]}"""

private val LEDS_EMPTY_JSON = """{"applicable":false,"leds":[]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    @Test
    fun loadsLogsAndLedsInOnePass() = runDiagnosticsTest { engine, viewModel ->
        viewModel.loadDiagnostics()
        advanceUntilIdle()
        val state = viewModel.diagnostics.value
        assertEquals(2, state.logs.size)
        assertEquals(1207, state.logTotal)
        assertEquals("netgrip[24843]", state.logs.first().tag)
        assertTrue(state.ledsApplicable)
        assertEquals(2, state.leds.size)
        assertEquals(true, state.leds.first().on)
        // Un estado que el panel no pudo leer llega como null, no como false.
        assertNull(state.leds[1].on)
        assertEquals(-1, state.leds[1].brightness)
        assertNull(state.error)
        assertEquals(listOf("/api/logs", "/api/leds"), engine.requests)
    }

    @Test
    fun filterTravelsAsAQueryParameter() = runDiagnosticsTest(engineBody = { path, query ->
        if (path == "/api/logs" && query.contains("filter=dnsmasq")) LOGS_FILTERED_JSON else null
    }) { engine, viewModel ->
        viewModel.loadDiagnostics("dnsmasq")
        advanceUntilIdle()
        assertEquals(1, viewModel.diagnostics.value.logs.size)
        assertTrue(engine.requests[0].startsWith("/api/logs"))
        assertTrue(
            engine.calls.any { it.first == "/api/logs" && it.second.contains("filter=dnsmasq") },
            "calls were ${engine.calls}",
        )
    }

    @Test
    fun emptyInventoryIsNotAnError() = runDiagnosticsTest(leds = LEDS_EMPTY_JSON) { _, viewModel ->
        viewModel.loadDiagnostics()
        advanceUntilIdle()
        assertFalse(viewModel.diagnostics.value.ledsApplicable)
        assertTrue(viewModel.diagnostics.value.leds.isEmpty())
        assertNull(viewModel.diagnostics.value.error)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Probe {
        val requests = mutableListOf<String>()
        /** Cada peticion con su query, para no perderla cuando llega la siguiente. */
        val calls = mutableListOf<Pair<String, String>>()
    }

    private fun runDiagnosticsTest(
        leds: String = LEDS_JSON,
        engineBody: ((path: String, query: String) -> String?)? = null,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe()
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val query = request.url.encodedQuery
                probe.requests += path
                probe.calls += path to query
                val override = engineBody?.invoke(path, query)
                val body = when {
                    override != null -> override
                    path == "/api/logs" -> LOGS_JSON
                    path == "/api/leds" -> leds
                    else -> null
                }
                if (body == null) {
                    respond("", HttpStatusCode.NotFound)
                } else {
                    respond(
                        content = body,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), DIAG_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
