package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.RemoteAccessRequest
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
import kotlin.test.assertTrue

private const val REMOTE_BASE = "https://router.local:8080"

private const val TS_JSON = """{"installed":true,"running":false,"state":"NeedsLogin",
 "auth_url":"https://login.tailscale.com/a/abc","ips":[]}"""

private const val WG_JSON = """{"installed":true,"active":true,"running":true,"port":"51820",
 "address":"10.6.0.1/24","public_key":"PUB","peers":[{"section":"peer1","name":"movil",
 "allowed_ips":["10.6.0.2/32"],"admin":false}],"endpoint":"casa.example.org"}"""

private const val DDNS_JSON = """{"installed":true,"wan_ip":"203.0.113.9",
 "entries":[{"section":"casa","enabled":true,"running":true,"service_name":"duckdns",
 "domain":"casa.duckdns.org","registered_ip":"203.0.113.9","last_update":"hace 5 min"}]}"""

private const val EXPOSURE_JSON = """{"applicable":true,"ping_wan":true,
 "remote_https":false,"remote_ssh":false}"""

/** Acceso remoto: lecturas tolerantes y escrituras con su cuerpo exacto. */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteAccessViewModelTest {

    @Test
    fun cargaLosCincoTransportesAunqueAlgunoFalte() = runRemoteTest(openvpnStatus = HttpStatusCode.BadGateway) { probe, viewModel ->
        viewModel.loadRemote()
        probe.pump()

        val state = viewModel.remote.value
        assertTrue(state.tailscale?.installed == true)
        assertEquals("NeedsLogin", state.tailscale?.state)
        assertEquals(1, state.wireguard?.peers?.size)
        assertEquals(null, state.openvpn)
        assertEquals("casa.duckdns.org", state.ddns?.entries?.single()?.domain)
        assertTrue(state.exposure?.pingWan == true)
        assertEquals(null, state.error)
    }

    @Test
    fun conectarTailscaleEnviaSuCuerpoYRelee() = runRemoteTest { probe, viewModel ->
        viewModel.loadRemote()
        probe.pump()

        viewModel.setTailscale(true)
        probe.pump()

        val post = probe.calls.first { it.path == "/api/tailscale" && it.method == "POST" }
        assertEquals("""{"enabled":true}""", post.body)
        assertEquals("Tailscale conectado", viewModel.remote.value.actionMessage)
        assertFalse(viewModel.remote.value.busy)
    }

    @Test
    fun abrirElPanelEnviaSoloEsePermiso() = runRemoteTest { probe, viewModel ->
        viewModel.loadRemote()
        probe.pump()

        viewModel.setRemoteAccess(RemoteAccessRequest(remoteHttps = true))
        probe.pump()

        val post = probe.calls.first { it.path == "/api/remoteaccess" && it.method == "POST" }
        assertEquals("""{"remote_https":true}""", post.body)
        assertEquals("Permiso actualizado y verificado", viewModel.remote.value.actionMessage)
    }

    @Test
    fun forzarDdnsPideTodasLasEntradasYRelee() = runRemoteTest { probe, viewModel ->
        viewModel.loadRemote()
        probe.pump()

        viewModel.forceDdns()
        probe.pump()

        val post = probe.calls.first { it.path == "/api/ddns/force" }
        // La sección vacía se omite y el router la interpreta como "todas".
        assertEquals("{}", post.body)
        assertEquals("Actualización de DDNS pedida al router", viewModel.remote.value.actionMessage)
    }

    @Test
    fun unRollbackDeExposicionSeMuestraComoError() = runRemoteTest(exposureRolledBack = true) { probe, viewModel ->
        viewModel.loadRemote()
        probe.pump()

        viewModel.setRemoteAccess(RemoteAccessRequest(remoteSsh = true))
        probe.pump()

        assertEquals("El router deshizo el cambio", viewModel.remote.value.actionError)
        assertFalse(viewModel.remote.value.busy)
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

    private fun runRemoteTest(
        openvpnStatus: HttpStatusCode = HttpStatusCode.OK,
        exposureRolledBack: Boolean = false,
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
                    path == "/api/tailscale" && method == "POST" ->
                        respond("""{"status":"applied","state":$TS_JSON}""", HttpStatusCode.OK, json)
                    path == "/api/tailscale" -> respond(TS_JSON, HttpStatusCode.OK, json)
                    path == "/api/wireguard" -> respond(WG_JSON, HttpStatusCode.OK, json)
                    path == "/api/openvpn" -> respond(
                        if (openvpnStatus == HttpStatusCode.OK) """{"installed":false}""" else "",
                        openvpnStatus,
                        json,
                    )
                    path == "/api/ddns/force" -> respond(
                        """{"status":"applied","state":$DDNS_JSON}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    path == "/api/ddns" -> respond(DDNS_JSON, HttpStatusCode.OK, json)
                    path == "/api/remoteaccess" && method == "POST" -> respond(
                        """{"status":"applied","rolled_back":$exposureRolledBack,"state":$EXPOSURE_JSON}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    path == "/api/remoteaccess" -> respond(EXPOSURE_JSON, HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), REMOTE_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
