package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.CertificateChangedException
import dev.gnacho.netgrip.core.network.SelfSignedCertificatesUnsupportedException
import dev.gnacho.netgrip.core.network.UnknownCertificateException
import dev.gnacho.netgrip.core.network.certificateValidationException
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientBlockRequest
import dev.gnacho.netgrip.data.dto.ClientMetaDto
import dev.gnacho.netgrip.data.dto.ClientMetaRequest
import dev.gnacho.netgrip.data.dto.ClientReserveRequest
import dev.gnacho.netgrip.data.dto.ClientWriteResponseDto
import dev.gnacho.netgrip.data.dto.ClientsDto
import dev.gnacho.netgrip.data.dto.EthPortsDto
import dev.gnacho.netgrip.data.dto.LoginRequest
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.WifiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import dev.gnacho.netgrip.data.dto.WolRequest
import dev.gnacho.netgrip.data.dto.WolResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cliente de la API REST de NetGrip para la app movil.
 *
 * Verificado en internal/server/server.go:
 *  - POST /api/login devuelve 204 y la sesion via Set-Cookie; 401 con
 *    credenciales incorrectas; 429 con Retry-After bajo rate limit; 502 si
 *    rpcd no responde.
 *  - GET /api/me devuelve 204 con sesion valida y 401 en caso contrario.
 *  - POST /api/logout revoca la sesion.
 *  - El resto de lecturas exigen sesion y responden 502 cuando ubus falla.
 *
 * Las escrituras se limitan a /api/clients/meta, /api/clients/block,
 * /api/clients/reserve y /api/wol (verificadas en internal/server/server.go y
 * internal/modules). Un non-2xx puede traer {"error":"..."}, que se conserva
 * en [ApiResult.HttpError.message] para mostrarlo.
 *
 * Los DTO viven en data/dto y son tolerantes: claves de mas se ignoran y los
 * campos opcionales del router tienen valor por defecto.
 */
class NetGripApi(
    engine: HttpClientEngine,
    private val baseUrl: String,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** Cookie de sesion en formato "nombre=valor". Se persiste cifrada. */
    var sessionCookie: String? = null
        private set

    fun restoreSessionCookie(cookie: String) {
        sessionCookie = cookie
    }

    suspend fun login(username: String, password: String): ApiResult<Unit> = safeCall {
        val response = client.post("$baseUrl/api/login") {
            contentType(ContentType.Application.Json)
            // NetGrip usa root si el usuario va vacio
            setBody(LoginRequest(username.ifBlank { "root" }, password))
        }
        when (response.status) {
            HttpStatusCode.NoContent -> {
                val cookie = extractSessionCookie(response)
                if (cookie == null) {
                    ApiResult.Unknown("login correcto pero sin cookie de sesion")
                } else {
                    sessionCookie = cookie
                    ApiResult.Ok(Unit)
                }
            }
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value)
        }
    }

    /** Probe de sesion: 204 si la cookie sigue siendo valida. */
    suspend fun me(): ApiResult<Unit> = safeCall {
        val response = client.get("$baseUrl/api/me") { sessionHeader() }
        when (response.status) {
            HttpStatusCode.NoContent -> ApiResult.Ok(Unit)
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            else -> ApiResult.HttpError(response.status.value)
        }
    }

    suspend fun board(): ApiResult<BoardDto> =
        getData("/api/board") { json.decodeFromJsonElement(it) }

    suspend fun systemInfo(): ApiResult<SystemDto> =
        getData("/api/system") { json.decodeFromJsonElement(it) }

    suspend fun wan(): ApiResult<WanDto> =
        getData("/api/wan") { json.decodeFromJsonElement(it) }

    suspend fun mode(): ApiResult<ModeDto> =
        getData("/api/mode") { json.decodeFromJsonElement(it) }

    suspend fun clients(): ApiResult<ClientsDto> =
        getData("/api/clients") { json.decodeFromJsonElement(it) }

    suspend fun ethPorts(): ApiResult<EthPortsDto> =
        getData("/api/ethports") { json.decodeFromJsonElement(it) }

    suspend fun wifi(): ApiResult<WifiDto> =
        getData("/api/wifi") { json.decodeFromJsonElement(it) }

    /** /api/wireless responde un array desnudo, sin envoltorio. */
    suspend fun wireless(): ApiResult<List<WirelessRadioDto>> =
        getData("/api/wireless") { json.decodeFromJsonElement(it) }

    suspend fun access(): ApiResult<AccessDto> =
        getData("/api/access") { json.decodeFromJsonElement(it) }

    suspend fun mdns(): ApiResult<MdnsDto> =
        getData("/api/mdns") { json.decodeFromJsonElement(it) }

    suspend fun selfUpdate(): ApiResult<SelfUpdateDto> =
        getData("/api/selfupdate") { json.decodeFromJsonElement(it) }

    /** GET /api/clients/meta: nombres y tipos asignados a mano. */
    suspend fun clientMeta(): ApiResult<ClientMetaDto> =
        getData("/api/clients/meta") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/clients/meta. Reemplaza la ficha completa del cliente: con
     * nombre y tipo vacios se borra el override (el servidor elimina la MAC
     * del mapa). Devuelve el mapa actualizado.
     */
    suspend fun setClientMeta(mac: String, name: String, deviceType: String): ApiResult<ClientMetaDto> =
        postJson("/api/clients/meta", ClientMetaRequest(mac, name, deviceType)) {
            json.decodeFromJsonElement(it)
        }

    /**
     * POST /api/clients/block. [type] es "wifi" o "cable" y [band] "" o una
     * banda del payload /api/clients. Solo se llama desde acciones
     * confirmadas y nunca con un cliente self o no blockable.
     */
    suspend fun blockClient(
        mac: String,
        type: String,
        band: String,
        blocked: Boolean,
    ): ApiResult<ClientWriteResponseDto> =
        postJson("/api/clients/block", ClientBlockRequest(mac, type, band, blocked)) {
            json.decodeFromJsonElement(it)
        }

    /**
     * POST /api/clients/reserve. Solo para fijar la IP actual del cliente o
     * para quitar una reserva existente.
     */
    suspend fun reserveClient(mac: String, ip: String, reserved: Boolean): ApiResult<ClientWriteResponseDto> =
        postJson("/api/clients/reserve", ClientReserveRequest(mac, ip, reserved)) {
            json.decodeFromJsonElement(it)
        }

    /** POST /api/wol: paquete magico, no persiste nada en el router. */
    suspend fun wakeOnLan(mac: String): ApiResult<WolResponseDto> =
        postJson("/api/wol", WolRequest(mac)) { json.decodeFromJsonElement(it) }

    suspend fun logout(): ApiResult<Unit> = safeCall {
        try {
            client.post("$baseUrl/api/logout") { sessionHeader() }
        } finally {
            sessionCookie = null
        }
        ApiResult.Ok(Unit)
    }

    private suspend fun <T> getData(path: String, decode: (JsonElement) -> T): ApiResult<T> =
        safeCall {
            val response = client.get("$baseUrl$path") { sessionHeader() }
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
                HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
                else -> ApiResult.HttpError(response.status.value)
            }
        }

    private fun HttpRequestBuilder.sessionHeader() {
        sessionCookie?.let { header(HttpHeaders.Cookie, it) }
    }

    /**
     * POST con cuerpo JSON y envelope de error. En un non-2xx se intenta
     * leer {"error":"..."} para poder mostrarlo tal cual.
     */
    private suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
        crossinline decode: (JsonElement) -> T,
    ): ApiResult<T> = safeCall {
        val response = client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            sessionHeader()
            setBody(body)
        }
        when (response.status) {
            HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value, errorMessage(response))
        }
    }

    /** Texto de {"error":"..."} del router, o null si el cuerpo no lo trae. */
    private suspend fun errorMessage(response: HttpResponse): String? =
        try {
            val element = response.body<JsonElement>()
            (element as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private fun extractSessionCookie(response: HttpResponse): String? {
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        for (name in SESSION_COOKIE_NAMES) {
            val prefix = "$name="
            setCookies.firstOrNull { it.startsWith(prefix) }
                ?.substringBefore(';')
                ?.let { return it }
        }
        return null
    }

    private fun retryAfter(response: HttpResponse): Long? =
        response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            when (val certificateError = t.certificateValidationException()) {
                is CertificateChangedException -> ApiResult.CertificateChanged(
                    host = certificateError.host,
                    expectedFingerprint = certificateError.expectedFingerprint,
                    actualFingerprint = certificateError.actualFingerprint,
                )
                is SelfSignedCertificatesUnsupportedException ->
                    ApiResult.CertificateUnsupported(certificateError.message ?: "Certificado no soportado")
                is UnknownCertificateException ->
                    ApiResult.Unknown(certificateError.message)
                null -> if (t is IOException) ApiResult.Unreachable else ApiResult.Unknown(t.message)
            }
        }

    companion object {
        // NetGrip emite nombres distintos segun sirva HTTP o HTTPS, y acepta la legacy
        private val SESSION_COOKIE_NAMES = listOf(
            "__Secure-netgrip_session",
            "netgrip_http_session",
            "netgrip_session",
        )
    }
}
