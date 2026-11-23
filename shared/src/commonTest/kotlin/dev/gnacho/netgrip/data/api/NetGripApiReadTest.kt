package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.SystemDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val BASE = "https://router.local:8080"

/** Lecturas tipadas: 200 con cuerpo, 401 de sesion y 502 de ubus. */
class NetGripApiReadTest {

    private fun apiWith(handler: MockRequestHandler): NetGripApi =
        NetGripApi(MockEngine(handler), BASE).also {
            it.restoreSessionCookie("netgrip_http_session=abc123")
        }

    private fun jsonHandler(path: String, body: String): MockRequestHandler = { request ->
        assertEquals(path, request.url.encodedPath)
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun boardTipadoDevuelveOkConCampos() = runTest {
        val api = apiWith(jsonHandler("/api/board", """{"hostname":"casa","model":"AX6","kernel":"6.6.89"}"""))
        val result = api.board()
        assertIs<ApiResult.Ok<BoardDto>>(result)
        assertEquals("casa", result.value.hostname)
        assertEquals("AX6", result.value.model)
    }

    @Test
    fun systemTipadoDevuelveMemoriaEnBytes() = runTest {
        val api = apiWith(
            jsonHandler(
                "/api/system",
                """{"uptime":60,"load":[1.0,0.5,0.25],"memory":{"total":100,"free":10,"available":40},"root":{"total":200,"free":50}}""",
            ),
        )
        val result = api.systemInfo()
        assertIs<ApiResult.Ok<SystemDto>>(result)
        assertEquals(100L, result.value.memory.total)
        assertEquals(200L, result.value.root.total)
    }

    @Test
    fun wirelessAceptaArrayDesnudo() = runTest {
        val api = apiWith(jsonHandler("/api/wireless", """[{"name":"radio0","up":true,"band":"2g","channel":"6"}]"""))
        val result = api.wireless()
        assertIs<ApiResult.Ok<List<*>>>(result)
        assertEquals(1, result.value.size)
    }

    @Test
    fun unaSesionCaducadaDevuelveUnauthorized() = runTest {
        val api = apiWith { respond("", HttpStatusCode.Unauthorized) }
        assertEquals(ApiResult.Unauthorized, api.clients())
        assertEquals(ApiResult.Unauthorized, api.board())
        assertEquals(ApiResult.Unauthorized, api.ethPorts())
    }

    @Test
    fun unUbusCaidoDevuelveHttpError502() = runTest {
        val api = apiWith {
            respond(
                content = """{"error":"ubus failed"}""",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertEquals(ApiResult.HttpError(502), api.wifi())
        assertEquals(ApiResult.HttpError(502), api.mdns())
    }

    @Test
    fun unCuerpoInvalidoNoRompeLaApp() = runTest {
        val api = apiWith {
            respond(
                content = "esto no es json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        assertIs<ApiResult.Unknown>(api.access())
    }

    @Test
    fun lasLecturasLlevanLaCookieDeSesion() = runTest {
        val cookies = mutableListOf<String?>()
        val api = apiWith { request ->
            cookies += request.headers[HttpHeaders.Cookie]
            respond(
                content = """{"installed":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        api.mdns()
        assertTrue(cookies.single()?.contains("netgrip_http_session=abc123") == true)
    }
}
