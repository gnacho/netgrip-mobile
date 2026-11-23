package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.ClientDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BASE = "https://router.local:8080"
private const val PIXEL = "9c:b6:d0:12:ab:77"
private const val SELF = "00:11:22:33:44:55"

private fun clientsJson(blocked: Boolean = false, reserved: Boolean = false, includePixel: Boolean = true): String {
    val pixel = if (includePixel) {
        """{"name":"Pixel","ip":"192.168.8.112","mac":"$PIXEL","type":"wifi5",
            "device_type":"phone","blockable":true,"reservable":true,
            "blocked":$blocked,"blocked_on":${if (blocked) """["5g"]""" else "[]"},
            "reserved":$reserved}"""
    } else {
        null
    }
    val self = """{"name":"Portátil","ip":"192.168.8.2","mac":"$SELF","type":"cable",
        "self":true,"blockable":false,"reservable":true}"""
    val all = listOfNotNull(pixel, self).joinToString(",")
    return """{"ts":1732300000,"bands":["2g","5g"],"clients":[$all]}"""
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShellViewModelWriteTest {

    @Test
    fun guardarMetaEnviaElCuerpoYRecargaClientesYMeta() = runPanelTest {
        var metaBody = """{"meta":{}}"""
        val posts = mutableListOf<String>()
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> if (request.method == HttpMethod.Post) {
                    posts += request.body.toByteArray().decodeToString()
                    metaBody = """{"meta":{"$PIXEL":{"name":"Pixel 8","device_type":"phone"}}}"""
                    ok(metaBody)
                } else {
                    ok(metaBody)
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.updateClientMeta(PIXEL, "  Pixel 8  ", "PHONE")
        testScheduler.advanceUntilIdle()

        val state = viewModel.clients.value
        assertEquals("Nombre y tipo guardados", state.actionMessage)
        assertNull(state.actionError)
        assertNull(state.busyMac)
        assertEquals("Pixel 8", state.meta.getValue(PIXEL).name)
        assertEquals("""{"mac":"$PIXEL","name":"Pixel 8","device_type":"phone"}""", posts.single())
        assertEquals(2, countRequests(engine, "/api/clients", HttpMethod.Get))
        assertEquals(2, countRequests(engine, "/api/clients/meta", HttpMethod.Get))
    }

    @Test
    fun borrarSinOverridePrevioNoEnviaNada() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.updateClientMeta(PIXEL, "", "   ")
        testScheduler.advanceUntilIdle()

        assertEquals("Este cliente no tenía nombre ni tipo guardados", viewModel.clients.value.actionMessage)
        assertEquals(0, countRequests(engine, "/api/clients/meta", HttpMethod.Post))
    }

    @Test
    fun bloquearEnviaTipoWifiYLaBandaElegida() = runPanelTest {
        var blocked = false
        val posts = mutableListOf<String>()
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson(blocked = blocked))
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> {
                    posts += request.body.toByteArray().decodeToString()
                    blocked = true
                    ok("""{"rolled_back":false,"status":"applied"}""")
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "5g")
        testScheduler.advanceUntilIdle()

        assertEquals("""{"mac":"$PIXEL","type":"wifi","band":"5g","blocked":true}""", posts.single())
        assertEquals("Cliente bloqueado en 5 GHz", viewModel.clients.value.actionMessage)
        assertTrue(viewModel.clients.value.clients.first { it.mac == PIXEL }.blocked)
    }

    @Test
    fun bloquearElPropioDispositivoSeRechazaSinPeticiones() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(selfOf(viewModel), "")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.clients.value.actionError?.contains("propio dispositivo") == true)
        assertEquals(0, countRequests(engine, "/api/clients/block", HttpMethod.Post))
    }

    @Test
    fun unClienteNoBloqueableSeRechazaSinPeticiones() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(
                    """{"bands":[],"clients":[{"name":"TV","ip":"192.168.8.131","mac":"70:2a:d5:44:09:e1",
                        "type":"cable","blockable":false}]}""",
                )
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(viewModel.clients.value.clients.single(), "")
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.clients.value.actionError?.contains("no permite bloquear") == true)
        assertEquals(0, countRequests(engine, "/api/clients/block", HttpMethod.Post))
    }

    @Test
    fun unRollbackSeInformaSinFingirExito() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> ok("""{"rolled_back":true,"status":"failed"}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "")
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.clients.value.actionMessage)
        assertTrue(viewModel.clients.value.actionError?.contains("revirtió") == true)
    }

    @Test
    fun laRelecturaReintentaUnaVezSiElEstadoNoCambio() = runPanelTest {
        var clientsCalls = 0
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> {
                    clientsCalls++
                    ok(clientsJson(blocked = clientsCalls >= 3))
                }
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> ok("""{"rolled_back":false,"status":"applied"}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "5g")
        testScheduler.advanceUntilIdle()

        assertEquals(3, clientsCalls)
        assertEquals("Cliente bloqueado en 5 GHz", viewModel.clients.value.actionMessage)
    }

    @Test
    fun siLaRelecturaNuncaCambiaLoDiceSinFingirExito() = runPanelTest {
        var clientsCalls = 0
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> {
                    clientsCalls++
                    ok(clientsJson(blocked = false))
                }
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> ok("""{"rolled_back":false,"status":"applied"}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "5g")
        testScheduler.advanceUntilIdle()

        assertEquals(3, clientsCalls)
        assertNull(viewModel.clients.value.actionMessage)
        assertEquals(dev.gnacho.netgrip.domain.clients.ClientActionMessages.stateUnchanged(), viewModel.clients.value.actionError)
    }

    @Test
    fun unClienteQueDesapareceTrasElBloqueoSeInformaSinError() = runPanelTest {
        var blocked = false
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson(blocked = blocked, includePixel = !blocked))
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> {
                    blocked = true
                    ok("""{"rolled_back":false,"status":"applied"}""")
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "")
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.clients.value.actionError)
        assertEquals(
            dev.gnacho.netgrip.domain.clients.ClientActionMessages.clientAbsent(),
            viewModel.clients.value.actionMessage,
        )
    }

    @Test
    fun reservarFijaLaIpActualDelCliente() = runPanelTest {
        var reserved = false
        val posts = mutableListOf<String>()
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson(reserved = reserved))
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/reserve" -> {
                    posts += request.body.toByteArray().decodeToString()
                    reserved = true
                    ok("""{"rolled_back":false,"status":"applied"}""")
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.toggleReservation(pixelOf(viewModel))
        testScheduler.advanceUntilIdle()

        assertEquals("""{"mac":"$PIXEL","ip":"192.168.8.112","reserved":true}""", posts.single())
        assertEquals("IP 192.168.8.112 fijada para este cliente", viewModel.clients.value.actionMessage)
        assertTrue(viewModel.clients.value.clients.first { it.mac == PIXEL }.reserved)
    }

    @Test
    fun quitarLaReservaEnviaReservedFalse() = runPanelTest {
        var reserved = true
        val posts = mutableListOf<String>()
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson(reserved = reserved))
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/reserve" -> {
                    posts += request.body.toByteArray().decodeToString()
                    reserved = false
                    ok("""{"rolled_back":false,"status":"applied"}""")
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.toggleReservation(pixelOf(viewModel))
        testScheduler.advanceUntilIdle()

        assertEquals("""{"mac":"$PIXEL","ip":"192.168.8.112","reserved":false}""", posts.single())
        assertEquals("Reserva DHCP eliminada", viewModel.clients.value.actionMessage)
    }

    @Test
    fun unaReservaQueNoApareceAplicadaSeAvisaComoPosibleReservaManual() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson(reserved = false))
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/reserve" -> ok("""{"rolled_back":false,"status":"applied"}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.toggleReservation(pixelOf(viewModel))
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.clients.value.actionMessage)
        assertTrue(viewModel.clients.value.actionError?.contains("reserva manual previa") == true)
    }

    @Test
    fun unClienteNoReservableSeRechazaSinPeticiones() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(
                    """{"bands":[],"clients":[{"name":"Tab","ip":"192.168.9.108","mac":"fa:22:9c:71:08:55",
                        "type":"wifi24","reservable":false,"blockable":true}]}""",
                )
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.toggleReservation(viewModel.clients.value.clients.single())
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.clients.value.actionError?.contains("no se puede reservar") == true)
        assertEquals(0, countRequests(engine, "/api/clients/reserve", HttpMethod.Post))
    }

    @Test
    fun elErrorDelServidorSeMuestraTalCual() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/clients/block" -> response(
                    HttpStatusCode.BadRequest,
                    """{"error":"blocking wired clients needs the firewall (gateway)"}""",
                )
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.blockClient(pixelOf(viewModel), "")
        testScheduler.advanceUntilIdle()

        assertEquals(
            "blocking wired clients needs the firewall (gateway)",
            viewModel.clients.value.actionError,
        )
    }

    @Test
    fun unCuatrocientosUnoEnUnaEscrituraCaducaLaSesion() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/wol" -> response(HttpStatusCode.Unauthorized, "")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.wakeOnLan(pixelOf(viewModel))
        testScheduler.advanceUntilIdle()

        assertTrue(viewModel.sessionExpired.value)
        assertEquals("La sesión ha caducado", viewModel.clients.value.actionError)
    }

    @Test
    fun despertarEnviaLaMacYNoRecargaLaLista() = runPanelTest {
        val posts = mutableListOf<String>()
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                "/api/wol" -> {
                    posts += request.body.toByteArray().decodeToString()
                    ok("""{"status":"sent"}""")
                }
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.wakeOnLan(pixelOf(viewModel))
        testScheduler.advanceUntilIdle()

        assertEquals("""{"mac":"$PIXEL"}""", posts.single())
        assertEquals("Paquete de despertar enviado", viewModel.clients.value.actionMessage)
        assertEquals(1, countRequests(engine, "/api/clients", HttpMethod.Get))
    }

    @Test
    fun unaMacInvalidaNoLlegaAlRouter() = runPanelTest {
        val engine = routingEngine { request ->
            when (request.url.encodedPath) {
                "/api/clients" -> ok(clientsJson())
                "/api/clients/meta" -> ok("""{"meta":{}}""")
                else -> notFound()
            }
        }
        val viewModel = shell(engine)
        viewModel.loadClients()
        testScheduler.advanceUntilIdle()

        viewModel.updateClientMeta("no-es-una-mac", "x", "phone")
        viewModel.blockClient(pixelOf(viewModel).copy(mac = "mal"), "")
        testScheduler.advanceUntilIdle()

        assertEquals(0, countRequests(engine, "/api/clients/meta", HttpMethod.Post))
        assertEquals(0, countRequests(engine, "/api/clients/block", HttpMethod.Post))
        assertNotNull(viewModel.clients.value.actionError)
    }

    private fun pixelOf(viewModel: ShellViewModel): ClientDto =
        viewModel.clients.value.clients.first { it.mac == PIXEL }

    private fun selfOf(viewModel: ShellViewModel): ClientDto =
        viewModel.clients.value.clients.first { it.mac == SELF }

    private fun countRequests(engine: MockEngine, path: String, method: HttpMethod): Int =
        engine.requestHistory.count { it.url.encodedPath == path && it.method == method }

    private fun shell(engine: MockEngine) = ShellViewModel(NetGripApi(engine, BASE))

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
        route: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): MockEngine {
        val config = MockEngineConfig()
        config.dispatcher = StandardTestDispatcher(testScheduler)
        config.addHandler { request: HttpRequestData -> route(request) }
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
