package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.GuestConfigRequest
import dev.gnacho.netgrip.data.dto.IoTConfigRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NET_BASE = "https://router.local:8080"

private const val GUEST_JSON = """{"gateway":true,"active":true,"ssid":"Invitados","subnet":"192.168.9.1",
 "isolate":true,"ifaces":["phy0-ap1"],"clients":2,"gl_conflict":false}"""

private const val IOT_JSON = """{"active":false,"ssid":"Cosa","band":"2g","isolated":true,
 "ifaces":[],"clients":0}"""

/** Escrituras de invitados e IoT: cuerpo, relectura y rollback. */
@OptIn(ExperimentalCoroutinesApi::class)
class NetworkWriteViewModelTest {

    @Test
    fun laRedDeInvitadosSeEnviaCompletaYSeRelee() = runNetworkTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyGuest(
            GuestConfigRequest(
                enabled = true,
                ssid = "Invitados",
                key = "clave-larga",
                band = "both",
                subnet = "",
                isolate = true,
            ),
        )
        probe.pump()

        val post = probe.calls.last { it.path == "/api/guestwifi" && it.method == "POST" }
        assertEquals(
            "{\"enabled\":true,\"ssid\":\"Invitados\",\"key\":\"clave-larga\"," +
                "\"band\":\"both\",\"isolate\":true}",
            post.body,
        )
        assertEquals("Red de invitados aplicada y verificada", viewModel.wifi.value.actionMessage)
        assertFalse(viewModel.wifi.value.busy)
        assertTrue(viewModel.wifi.value.guest?.active == true)
        // Lectura inicial, POST y relectura de comprobacion.
        assertEquals(3, probe.calls.count { it.path == "/api/guestwifi" })
    }

    @Test
    fun elRollbackDeInvitadosSeMuestraComoError() = runNetworkTest(guestRolledBack = true) { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyGuest(GuestConfigRequest(true, "Invitados", "clave-larga", "2g"))
        probe.pump()

        assertEquals(
            "Red de invitados: el router deshizo el cambio y dejó la configuración como estaba",
            viewModel.wifi.value.actionError,
        )
        assertNull(viewModel.wifi.value.actionMessage)
    }

    @Test
    fun iotEnviaSuCuerpoYReleeSuPropioEstado() = runNetworkTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyIoT(IoTConfigRequest(enabled = false, ssid = "Cosa", key = "", band = "2g"))
        probe.pump()

        val post = probe.calls.last { it.path == "/api/iotwifi" && it.method == "POST" }
        assertEquals("""{"enabled":false,"ssid":"Cosa","key":"","band":"2g"}""", post.body)
        assertEquals("Red IoT aplicada y verificada", viewModel.wifi.value.actionMessage)
        assertTrue(probe.calls.any { it.path == "/api/guestwifi" })
    }

    @Test
    fun elErrorDelRouterLlegaTalCual() = runNetworkTest(guestError = """{"error":"healthcheck failed, rolled back"}""") { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applyGuest(GuestConfigRequest(true, "Invitados", "clave-larga", "both"))
        probe.pump()

        assertEquals("healthcheck failed, rolled back", viewModel.wifi.value.actionError)
        assertFalse(viewModel.wifi.value.busy)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Call(val path: String, val method: String, val body: String)

    private class Probe(val scope: TestScope) {
        val calls = mutableListOf<Call>()

        fun pump() {
            repeat(12) {
                scope.runCurrent()
                scope.advanceTimeBy(1)
            }
        }
    }

    private fun runNetworkTest(
        guestRolledBack: Boolean = false,
        guestError: String? = null,
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
                probe.calls += Call(path, method, body)
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                when {
                    path == "/api/wifi" -> respond("""{"interfaces":[]}""", HttpStatusCode.OK, json)
                    path == "/api/wireless" -> respond("[]", HttpStatusCode.OK, json)
                    path == "/api/mode" -> respond("""{"has_wifi":true}""", HttpStatusCode.OK, json)
                    path == "/api/config/confirm" -> respond("""{"pending":false}""", HttpStatusCode.OK, json)
                    path == "/api/guestwifi" && method == "GET" -> respond(GUEST_JSON, HttpStatusCode.OK, json)
                    path == "/api/iotwifi" && method == "GET" -> respond(IOT_JSON, HttpStatusCode.OK, json)
                    guestError != null && path == "/api/guestwifi" ->
                        respond(guestError, HttpStatusCode.InternalServerError, json)
                    path == "/api/guestwifi" -> respond(
                        """{"status":"applied","rolled_back":$guestRolledBack,"state":$GUEST_JSON}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    path == "/api/iotwifi" -> respond(
                        """{"status":"applied","rolled_back":false,"state":$IOT_JSON}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), NET_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
