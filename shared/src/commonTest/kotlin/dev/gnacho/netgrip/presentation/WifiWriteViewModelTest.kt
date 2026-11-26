package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.RadioEditRequest
import dev.gnacho.netgrip.data.dto.WifiEditRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val WIFI_BASE = "https://router.local:8080"

private val WIFI_JSON = """{"interfaces":[
 {"section":"default_radio0","radio":"radio0","band":"2g","ssid":"casa","encryption":"psk2",
  "has_key":true,"hidden":false,"disabled":false,"clients":[]}
]}"""

private val WIRELESS_JSON = """[{"name":"radio0","up":true,"band":"2g","channel":"6","htmode":"HE20","txpower":20}]"""

/** Escrituras WiFi: ventana de reversion, relectura y errores del router. */
@OptIn(ExperimentalCoroutinesApi::class)
class WifiWriteViewModelTest {

    @Test
    fun aplicarUnCambioArmaLaVentanaYReleeElEstado() = runWifiTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()
        probe.pump()

        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()

        val state = viewModel.wifi.value
        assertEquals("abc", state.pendingConfirm?.id)
        assertEquals(120, state.pendingConfirm?.secondsLeft)
        assertFalse(state.busy)
        assertEquals("Cambio aplicado. Confírmalo antes de que el router lo deshaga.", state.actionMessage)
        assertNull(state.actionError)
        // Una lectura inicial y una relectura tras la escritura.
        assertEquals(2, probe.calls.count { it.path == "/api/wifi" && it.method == "GET" })
        assertTrue(probe.requests.contains("/api/wifi/radio") || probe.calls.any { it.path == "/api/wireless" })
    }

    @Test
    fun mantenerLosCambiosCierraLaVentanaSinRevertir() = runWifiTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()
        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()

        viewModel.keepWifiChanges()
        probe.pump()

        assertNull(viewModel.wifi.value.pendingConfirm)
        assertEquals("Cambios confirmados", viewModel.wifi.value.actionMessage)
        val keep = probe.calls.last { it.path == "/api/config/confirm" }
        assertEquals("POST", keep.method)
        assertEquals("""{"id":"abc"}""", keep.body)
        assertFalse(probe.requests.contains("/api/config/confirm/rollback"))
    }

    @Test
    fun deshacerRevierteYVuelveALeer() = runWifiTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()
        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()
        val readsBefore = probe.calls.count { it.path == "/api/wifi" }

        viewModel.revertWifiChanges()
        probe.pump()

        assertNull(viewModel.wifi.value.pendingConfirm)
        assertEquals("Cambios deshechos", viewModel.wifi.value.actionMessage)
        assertTrue(probe.requests.contains("/api/config/confirm/rollback"))
        assertTrue(probe.calls.count { it.path == "/api/wifi" } > readsBefore)
    }

    @Test
    fun siLaVentanaCaducaSeAvisaYNoSeDaElCambioPorBueno() = runWifiTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()
        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()
        assertEquals(120, viewModel.wifi.value.pendingConfirm?.secondsLeft)

        probe.clock += 200
        probe.schedulerAdvance(1_000)
        probe.pump()

        assertNull(viewModel.wifi.value.pendingConfirm)
        assertEquals(
            "El router deshizo el cambio al no confirmarse a tiempo",
            viewModel.wifi.value.actionError,
        )
        assertTrue(probe.requests.contains("/api/config/confirm/rollback").not())
    }

    @Test
    fun unRollbackDelRouterSeMuestraComoErrorTrasReleer() = runWifiTest(rolledBack = true) { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()

        assertNull(viewModel.wifi.value.pendingConfirm)
        assertEquals("El router deshizo el cambio y dejó la configuración como estaba", viewModel.wifi.value.actionError)
    }

    @Test
    fun elErrorDelRouterLlegaTalCualYNoSePintaElCambio() = runWifiTest(httpError = """{"error":"wifi healthcheck failed, rolled back"}""") { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyWifi(WifiEditRequest(section = "default_radio0", ssid = "casa-nueva", confirm = true))
        probe.pump()

        assertEquals("wifi healthcheck failed, rolled back", viewModel.wifi.value.actionError)
        assertNull(viewModel.wifi.value.pendingConfirm)
        assertFalse(viewModel.wifi.value.busy)
        assertEquals("casa", viewModel.wifi.value.interfaces.single().ssid)
    }

    @Test
    fun sinVentanaElCambioSeDaPorAplicadoTrasLaRelectura() = runWifiTest(confirmWindow = false) { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyWifiRadio(RadioEditRequest(radio = "radio0", channel = "36", confirm = true))
        probe.pump()

        assertNull(viewModel.wifi.value.pendingConfirm)
        assertEquals("Cambios aplicados y verificados", viewModel.wifi.value.actionMessage)
        val radio = probe.calls.last { it.path == "/api/wifi/radio" }
        assertEquals("""{"radio":"radio0","channel":"36","confirm":true}""", radio.body)
    }

    @Test
    fun laVentanaPendienteDelRouterSeReanudaAlCargarLaPestana() = runWifiTest(pendingOnLoad = true) { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        assertEquals("abc", viewModel.wifi.value.pendingConfirm?.id)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Call(val path: String, val method: String, val body: String)

    private class Probe(val scheduler: TestScope) {
        val requests = mutableListOf<String>()
        val calls = mutableListOf<Call>()
        var clock = 1_000L

        fun pump() {
            repeat(12) {
                scheduler.runCurrent()
                scheduler.advanceTimeBy(1)
            }
        }

        fun schedulerAdvance(millis: Long) {
            scheduler.advanceTimeBy(millis)
        }
    }

    private fun runWifiTest(
        rolledBack: Boolean = false,
        httpError: String? = null,
        confirmWindow: Boolean = true,
        pendingOnLoad: Boolean = false,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe(this)
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val method = request.method.value
                val body = request.body.toByteArray().decodeToString()
                probe.requests += path
                probe.calls += Call(path, method, body)
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                when {
                    path == "/api/wifi" && method == "GET" -> respond(WIFI_JSON, HttpStatusCode.OK, json)
                    path == "/api/wireless" -> respond(WIRELESS_JSON, HttpStatusCode.OK, json)
                    path == "/api/mode" -> respond("""{"has_wifi":true}""", HttpStatusCode.OK, json)
                    path == "/api/config/confirm" && method == "GET" ->
                        if (pendingOnLoad) {
                            respond(
                                """{"pending":true,"id":"abc","expires":${probe.clock + 120}}""",
                                HttpStatusCode.OK,
                                json,
                            )
                        } else {
                            respond("""{"pending":false}""", HttpStatusCode.OK, json)
                        }
                    httpError != null && (path == "/api/wifi" || path == "/api/wifi/radio") ->
                        respond(httpError, HttpStatusCode.InternalServerError, json)
                    path == "/api/wifi" && method == "POST" -> {
                        val window = if (rolledBack || !confirmWindow) {
                            ""
                        } else {
                            ""","confirm":{"id":"abc","expires":${probe.clock + 120}}"""
                        }
                        respond(
                            """{"status":"applied","rolled_back":$rolledBack,"state":{}""" + window + "}",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    path == "/api/wifi/radio" -> {
                        val window = if (!confirmWindow) {
                            ""
                        } else {
                            ""","confirm":{"id":"abc","expires":${probe.clock + 120}}"""
                        }
                        respond(
                            """{"status":"applied","state":{"name":"radio0"}""" + window + "}",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    path == "/api/config/confirm" -> respond("""{"status":"kept"}""", HttpStatusCode.OK, json)
                    path == "/api/config/confirm/rollback" -> respond("""{"status":"reverted"}""", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(
                api = NetGripApi(MockEngine(config), WIFI_BASE),
                now = { probe.clock },
            )
            try {
                block(probe, viewModel)
            } finally {
                // La cuenta atras deja una corrutina esperando: se cancela
                // antes de devolver el dispatcher principal.
                viewModel.viewModelScope.cancel()
            }
        } finally {
            Dispatchers.resetMain()
        }
    }
}
