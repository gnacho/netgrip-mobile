package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.data.dto.ClientMetaDto
import dev.gnacho.netgrip.data.dto.ConfirmStatusDto
import dev.gnacho.netgrip.data.dto.RadioEditRequest
import dev.gnacho.netgrip.data.dto.WifiEditRequest
import dev.gnacho.netgrip.data.dto.ClientWriteResponseDto
import dev.gnacho.netgrip.data.dto.WolResponseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val BASE = "https://router.local:8080"
private const val MAC = "aa:bb:cc:dd:ee:ff"

/** Escrituras: metodo, ruta, cuerpo exacto, respuesta 200 y error JSON. */
class NetGripApiWriteTest {

    private class Recorded(val method: HttpMethod, val path: String, val body: String, val cookie: String?)

    private fun apiWith(
        status: HttpStatusCode = HttpStatusCode.OK,
        responseBody: String = "{}",
        recorded: MutableList<Recorded>,
    ): NetGripApi {
        val engine = MockEngine { request ->
            recorded += Recorded(
                method = request.method,
                path = request.url.encodedPath,
                body = request.body.toByteArray().decodeToString(),
                cookie = request.headers[HttpHeaders.Cookie],
            )
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        return NetGripApi(engine, BASE).also { it.restoreSessionCookie("netgrip_http_session=abc123") }
    }

    @Test
    fun metaHacePostConElCuerpoCompletoYDevuelveElMapa() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            responseBody = """{"meta":{"$MAC":{"name":"Pixel","device_type":"phone"}}}""",
            recorded = recorded,
        )
        val result = api.setClientMeta(MAC, "Pixel", "phone")

        assertIs<ApiResult.Ok<ClientMetaDto>>(result)
        assertEquals("Pixel", result.value.meta.getValue(MAC).name)
        assertEquals("phone", result.value.meta.getValue(MAC).deviceType)
        val request = recorded.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/clients/meta", request.path)
        assertEquals("""{"mac":"$MAC","name":"Pixel","device_type":"phone"}""", request.body)
        assertTrue(request.cookie?.contains("netgrip_http_session=abc123") == true)
    }

    @Test
    fun metaVaciaElCuerpoCuandoSeBorraElOverride() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"meta":{}}""", recorded = recorded)

        api.setClientMeta(MAC, "", "")

        assertEquals("""{"mac":"$MAC","name":"","device_type":""}""", recorded.single().body)
    }

    @Test
    fun blockHacePostConTipoYBandasExactos() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"rolled_back":false,"status":"applied"}""", recorded = recorded)

        val result = api.blockClient(MAC, "wifi", "5g", blocked = true)

        assertIs<ApiResult.Ok<ClientWriteResponseDto>>(result)
        assertEquals("applied", result.value.status)
        assertEquals(false, result.value.rolledBack)
        val request = recorded.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/clients/block", request.path)
        assertEquals("""{"mac":"$MAC","type":"wifi","band":"5g","blocked":true}""", request.body)
    }

    @Test
    fun blockConTodasLasBandasEnviaLaBandaVacia() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"rolled_back":false,"status":"applied"}""", recorded = recorded)

        api.blockClient(MAC, "cable", "", blocked = false)

        assertEquals("""{"mac":"$MAC","type":"cable","band":"","blocked":false}""", recorded.single().body)
    }

    @Test
    fun reserveHacePostConLaIpYLaMarca() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"rolled_back":false,"status":"applied"}""", recorded = recorded)

        val result = api.reserveClient(MAC, "192.168.8.50", reserved = true)

        assertIs<ApiResult.Ok<ClientWriteResponseDto>>(result)
        assertEquals(HttpMethod.Post, recorded.single().method)
        assertEquals("/api/clients/reserve", recorded.single().path)
        assertEquals("""{"mac":"$MAC","ip":"192.168.8.50","reserved":true}""", recorded.single().body)
    }

    @Test
    fun wolHacePostSoloConLaMac() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"status":"sent"}""", recorded = recorded)

        val result = api.wakeOnLan(MAC)

        assertIs<ApiResult.Ok<WolResponseDto>>(result)
        assertEquals("sent", result.value.status)
        assertEquals("/api/wol", recorded.single().path)
        assertEquals("""{"mac":"$MAC"}""", recorded.single().body)
    }

    @Test
    fun unErrorJsonSeConservaParaMostrarlo() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            status = HttpStatusCode.BadRequest,
            responseBody = """{"error":"invalid mac/ip"}""",
            recorded = recorded,
        )

        assertEquals(ApiResult.HttpError(400, "invalid mac/ip"), api.reserveClient(MAC, "", true))
        assertEquals(ApiResult.HttpError(400, "invalid mac/ip"), api.blockClient(MAC, "wifi", "", true))
        assertEquals(ApiResult.HttpError(400, "invalid mac/ip"), api.setClientMeta(MAC, "x", "phone"))
        assertEquals(ApiResult.HttpError(400, "invalid mac/ip"), api.wakeOnLan(MAC))
    }

    @Test
    fun unCuerpoDeErrorSinJsonNoRompeLaApp() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            status = HttpStatusCode.BadGateway,
            responseBody = "ubus caido",
            recorded = recorded,
        )

        assertEquals(ApiResult.HttpError(502, null), api.wakeOnLan(MAC))
    }

    @Test
    fun unCuatrocientosUnoCaducaLaSesion() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            status = HttpStatusCode.Unauthorized,
            responseBody = """{"error":"unauthorized"}""",
            recorded = recorded,
        )

        assertEquals(ApiResult.Unauthorized, api.setClientMeta(MAC, "x", "phone"))
        assertEquals(ApiResult.Unauthorized, api.blockClient(MAC, "wifi", "", true))
        assertEquals(ApiResult.Unauthorized, api.reserveClient(MAC, "192.168.8.50", true))
        assertEquals(ApiResult.Unauthorized, api.wakeOnLan(MAC))
    }

    @Test
    fun laRespuestaConRollbackSeExponeTalCual() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"rolled_back":true,"status":"applied"}""", recorded = recorded)

        val result = api.blockClient(MAC, "wifi", "", true)

        assertIs<ApiResult.Ok<ClientWriteResponseDto>>(result)
        assertEquals(true, result.value.rolledBack)
    }

    @Test
    fun elEditorWifiEnviaSoloLoQueCambia() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            responseBody = """{"status":"applied","state":{},"confirm":{"id":"abc","expires":1790000000}}""",
            recorded = recorded,
        )

        val result = api.setWifi(
            WifiEditRequest(
                section = "default_radio0",
                sections = listOf("default_radio0", "default_radio1"),
                ssid = "casa",
                key = "clave-larga",
                encryption = "sae",
                hidden = false,
                disabled = false,
                confirm = true,
            ),
        )

        assertIs<ApiResult.Ok<*>>(result)
        val request = recorded.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/wifi", request.path)
        assertEquals(
            """{"section":"default_radio0","sections":["default_radio0","default_radio1"],""" +
                """"ssid":"casa","key":"clave-larga","encryption":"sae","hidden":false,""" +
                """"disabled":false,"confirm":true}""",
            request.body,
        )
        assertTrue(request.body.contains("\"key\":\"clave-larga\""))
    }

    @Test
    fun apagarUnaRedEnviaDisabledYNoEnviaClave() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"status":"applied","state":{}}""", recorded = recorded)

        api.setWifi(
            WifiEditRequest(
                section = "default_radio0",
                sections = listOf("default_radio0", "default_radio1"),
                ssid = "casa",
                encryption = "psk2",
                hidden = false,
                disabled = true,
                confirm = true,
            ),
        )

        val body = recorded.single().body
        assertTrue(body.contains("\"disabled\":true"))
        assertTrue(!body.contains("\"key\""))
    }

    @Test
    fun losCamposNulosNoViajanEnElCuerpo() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"status":"applied","state":{}}""", recorded = recorded)

        api.setWifi(WifiEditRequest(section = "default_radio0", ssid = "casa"))

        assertEquals("""{"section":"default_radio0","ssid":"casa"}""", recorded.single().body)
    }

    @Test
    fun laRadioEnviaCanalAnchoYPotencia() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(responseBody = """{"status":"applied","state":{"name":"radio0"}}""", recorded = recorded)

        api.setWifiRadio(
            RadioEditRequest(radio = "radio0", channel = "36", htmode = "HE80", txpower = 20, confirm = true),
        )

        assertEquals("/api/wifi/radio", recorded.single().path)
        assertEquals(
            """{"radio":"radio0","channel":"36","htmode":"HE80","txpower":20,"confirm":true}""",
            recorded.single().body,
        )
    }

    @Test
    fun laVentanaDeConfirmacionSeLeeMantieneYDeshace() = runTest {
        val recorded = mutableListOf<Recorded>()
        val api = apiWith(
            responseBody = """{"pending":true,"id":"abc","expires":1790000000}""",
            recorded = recorded,
        )

        val status = api.confirmStatus()
        assertIs<ApiResult.Ok<ConfirmStatusDto>>(status)
        assertTrue(status.value.pending)
        assertEquals("abc", status.value.id)

        api.keepConfirm("abc")
        api.revertConfirm("abc")

        assertEquals(listOf("/api/config/confirm", "/api/config/confirm", "/api/config/confirm/rollback"), recorded.map { it.path })
        assertEquals(HttpMethod.Post, recorded[1].method)
        assertEquals("""{"id":"abc"}""", recorded[1].body)
        assertEquals("""{"id":"abc"}""", recorded[2].body)
    }
}
