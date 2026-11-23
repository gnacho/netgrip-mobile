package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.CertificateChangedException
import dev.gnacho.netgrip.core.network.SelfSignedCertificatesUnsupportedException
import dev.gnacho.netgrip.core.network.UnknownCertificateException
import dev.gnacho.netgrip.core.network.certificateValidationException
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
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
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Cliente de la API REST de NetGrip para la app movil de solo lectura.
 *
 * Verificado en internal/server/server.go:
 *  - POST /api/login devuelve 204 y la sesion via Set-Cookie; 401 con
 *    credenciales incorrectas; 429 con Retry-After bajo rate limit; 502 si
 *    rpcd no responde.
 *  - GET /api/me devuelve 204 con sesion valida y 401 en caso contrario.
 *  - POST /api/logout revoca la sesion.
 *  - El resto de lecturas exigen sesion y responden 502 cuando ubus falla.
 *
 * Todos los GET son de solo lectura. Los DTO viven en data/dto y son
 * tolerantes: claves de mas se ignoran y los campos opcionales del router
 * tienen valor por defecto.
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
