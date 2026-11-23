package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE = "https://router.local:8080"

private val DEMO_BOARD = """{"hostname":"casa","model":"Redmi AX6","kernel":"6.6.89",
    "release":{"distribution":"OpenWrt","version":"25.12.5"}}"""

private val DEMO_SYSTEM = """{"uptime":1080000,"load":[0.42,0.38,0.31],
    "memory":{"total":414187520,"free":100663296,"available":259000000},
    "root":{"total":131072,"free":98304}}"""

private val DEMO_WAN = """{"present":true,"up":true,"uptime":270000,
    "ipv4":["192.0.2.10"],"gateway":"192.0.2.1","dns":["1.1.1.1"]}"""

private val DEMO_MODE = """{"mode":"router","role":"router","has_wifi":true,"port_count":5,
    "dnsmasq_on":true,"firewall_on":true}"""

private val DEMO_CLIENTS = """{"ts":1732300000,"bands":["2g","5g"],"clients":[
    {"name":"Pixel","ip":"192.168.8.112","mac":"9C:B6:D0:12:AB:77","type":"wifi5"},
    {"name":"NAS","ip":"192.168.8.10","mac":"00:11:32:9A:BC:10","type":"cable"}]}"""

private val DEMO_PORTS = """{"ports":[
    {"name":"wan","wan":true,"up":true,"speed_mbps":1000,"devices":[]},
    {"name":"lan4","wan":false,"up":true,"speed_mbps":100,"devices":[]}]}"""

private val DEMO_WIFI = """{"interfaces":[
    {"section":"wifinet0","radio":"radio0","ifname":"phy0-ap0","band":"2g","ssid":"CasaGarcia",
     "encryption":"sae-mixed","has_key":true,"hidden":false,"mac":"","bssid":"8C:53:C3:11:22:30",
     "disabled":false,"clients":[{"mac":"3A:7B:1C:44:20:11","signal":-58}]}]}"""

private val DEMO_WIRELESS = """[{"name":"radio0","up":true,"band":"2g","channel":"6",
    "htmode":"HE40","txpower":20,"interfaces":[]}]"""

private val DEMO_ACCESS = """{"panel":{"http_port":8090,"https_enabled":true,"force_https":false,"session_ttl":"60m"},
    "luci":{"http_port":80,"https_port":443,"force_https":false,"enabled":true},
    "ssh":{"enabled":true,"port":"22"}}"""

private val DEMO_MDNS = """{"installed":true,"enabled":true,"running":true,"domain":"casa.local"}"""

private val DEMO_SELFUPDATE = """{"current":"v0.79.0","latest":"v0.79.0","available":false,"notes":""}"""

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelTest {

    @Test
    fun elPanelCargaTodasLasLecturas() = runPanelTest {
        val engine = routingEngine { path ->
            when (path) {
                "/api/board" -> ok(DEMO_BOARD)
                "/api/system" -> ok(DEMO_SYSTEM)
                "/api/wan" -> ok(DEMO_WAN)
                "/api/mode" -> ok(DEMO_MODE)
                "/api/clients" -> ok(DEMO_CLIENTS)
                "/api/ethports" -> ok(DEMO_PORTS)
                else -> notFound()
            }
        }
        val viewModel = shell(engine)

        viewModel.loadPanel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.panel.value
        assertTrue(state.loaded)
        assertFalse(state.loading)
        assertNull(state.error)
        assertEquals("casa", state.board?.hostname)
        assertEquals(1080000L, state.system?.uptime)
        assertEquals("192.0.2.10", state.wan?.ipv4?.single())
        assertEquals("router", state.mode?.effectiveRole)
        assertEquals(2, state.clients.size)
        assertEquals("lan4", state.ports.last().name)
        assertEquals(6, engine.requestHistory.size)
    }

    @Test
    fun unUnoUnoEnElPanelVuelveAlLoginSinReintentar() = runPanelTest {
        val engine = routingEngine { path ->
            if (path == "/api/clients") response(HttpStatusCode.Unauthorized, "") else ok(DEMO_BOARD)
        }
        val viewModel = shell(engine)

        viewModel.loadPanel()
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.sessionExpired.value)
        assertEquals("La sesión ha caducado", viewModel.panel.value.error)
        assertEquals(6, engine.requestHistory.size)
    }

    @Test
    fun unFalloParcialConservaElRestoDeDatos() = runPanelTest {
        val engine = routingEngine { path ->
            if (path == "/api/board") response(HttpStatusCode.BadGateway, """{"error":"ubus"}""") else ok(DEMO_SYSTEM)
        }
        val viewModel = shell(engine)

        viewModel.loadPanel()
        testScheduler.advanceUntilIdle()

        val state = viewModel.panel.value
        assertEquals("El router respondió con error HTTP 502", state.error)
        assertNull(state.board)
        assertNotNull(state.system)
        assertFalse(viewModel.sessionExpired.value)
    }

    @Test
    fun elPanelNoSeRecargaSiYaEstabaCargado() = runPanelTest {
        val engine = routingEngine { ok("{}") }
        val viewModel = shell(engine)

        viewModel.ensurePanel()
        testScheduler.advanceUntilIdle()
        viewModel.ensurePanel()
        testScheduler.advanceUntilIdle()

        assertEquals(6, engine.requestHistory.size)
    }

    @Test
    fun clientesConErrorQuedaEnEstadoDeError() = runPanelTest {
        val engine = routingEngine { response(HttpStatusCode.BadGateway, """{"error":"ubus"}""") }
        val viewModel = shell(engine)

        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        val state = viewModel.clients.value
        assertTrue(state.loaded)
        assertEquals("El router respondió con error HTTP 502", state.error)
        assertTrue(state.clients.isEmpty())
    }

    @Test
    fun wifiSinRadiosSeMarcaComoEquipoSinWifi() = runPanelTest {
        val engine = routingEngine { path ->
            when (path) {
                "/api/wifi" -> ok("""{"interfaces":[]}""")
                "/api/wireless" -> ok("[]")
                "/api/mode" -> ok("""{"mode":"router","has_wifi":false,"port_count":5}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)

        viewModel.loadWifi()
        testScheduler.advanceUntilIdle()

        val state = viewModel.wifi.value
        assertTrue(state.loaded)
        assertFalse(state.hasWifi)
        assertNull(state.error)
    }

    @Test
    fun wifiConRadiosYRedesAgrupaLosDatos() = runPanelTest {
        val engine = routingEngine { path ->
            when (path) {
                "/api/wifi" -> ok(DEMO_WIFI)
                "/api/wireless" -> ok(DEMO_WIRELESS)
                "/api/mode" -> ok(DEMO_MODE)
                else -> notFound()
            }
        }
        val viewModel = shell(engine)

        viewModel.loadWifi()
        testScheduler.advanceUntilIdle()

        val state = viewModel.wifi.value
        assertTrue(state.hasWifi)
        assertEquals("CasaGarcia", state.interfaces.single().ssid)
        assertEquals("6", state.radios.single().channel)
    }

    @Test
    fun ajustesCargaAccesoMdnsYActualizacion() = runPanelTest {
        val engine = routingEngine { path ->
            when (path) {
                "/api/board" -> ok(DEMO_BOARD)
                "/api/mode" -> ok(DEMO_MODE)
                "/api/access" -> ok(DEMO_ACCESS)
                "/api/mdns" -> ok(DEMO_MDNS)
                "/api/selfupdate" -> ok(DEMO_SELFUPDATE)
                else -> notFound()
            }
        }
        val viewModel = shell(engine)

        viewModel.loadSettings()
        testScheduler.advanceUntilIdle()

        val state = viewModel.settings.value
        assertEquals(8090, state.access?.panel?.httpPort)
        assertTrue(state.mdns?.running == true)
        assertEquals("v0.79.0", state.selfUpdate?.current)
        assertNull(state.error)
    }

    @Test
    fun losPuertosVaciosNoSonUnError() = runPanelTest {
        val engine = routingEngine { ok("""{"ports":[]}""") }
        val viewModel = shell(engine)

        viewModel.loadPorts()
        testScheduler.advanceUntilIdle()

        val state = viewModel.ports.value
        assertTrue(state.loaded)
        assertNull(state.error)
        assertTrue(state.ports.isEmpty())
    }

    private fun shell(engine: MockEngine) =
        ShellViewModel(NetGripApi(engine, BASE))

    private fun MockRequestHandleScope.ok(body: String): HttpResponseData = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun MockRequestHandleScope.response(status: HttpStatusCode, body: String): HttpResponseData =
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

    private fun MockRequestHandleScope.notFound(): HttpResponseData =
        response(HttpStatusCode.NotFound, "")

    private fun TestScope.routingEngine(
        route: suspend MockRequestHandleScope.(String) -> HttpResponseData,
    ): MockEngine {
        val config = MockEngineConfig()
        config.dispatcher = StandardTestDispatcher(testScheduler)
        config.addHandler { request: HttpRequestData -> route(request.url.encodedPath) }
        return MockEngine(config)
    }

    private fun runPanelTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
