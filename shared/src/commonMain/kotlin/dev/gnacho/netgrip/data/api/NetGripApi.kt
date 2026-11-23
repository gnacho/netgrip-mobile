package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.CertificateChangedException
import dev.gnacho.netgrip.core.network.SelfSignedCertificatesUnsupportedException
import dev.gnacho.netgrip.core.network.UnknownCertificateException
import dev.gnacho.netgrip.core.network.certificateValidationException
import dev.gnacho.netgrip.data.dto.LoginRequest
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

/**
 * Cliente minimo de la API REST de NetGrip para el spike de la fase 1.
 *
 * Verificado en internal/server/server.go:
 *  - POST /api/login devuelve 204 y la sesion via Set-Cookie; 401 con
 *    credenciales incorrectas; 429 con Retry-After bajo rate limit; 502 si
 *    rpcd no responde.
 *  - GET /api/me devuelve 204 con sesion valida y 401 en caso contrario.
 *  - POST /api/logout revoca la sesion.
 *
 * Los cuerpos de /api/system y /api/clients no estaban verificados al crear
 * este scaffold, por eso se leen como JsonElement hasta capturar respuestas
 * reales y definir DTOs tipados (ver docs/fase-1-spike.md).
 */
class NetGripApi(
    engine: HttpClientEngine,
    private val baseUrl: String,
) {

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
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

    suspend fun system(): ApiResult<JsonElement> = getJson("/api/system")

    suspend fun clients(): ApiResult<JsonElement> = getJson("/api/clients")

    suspend fun logout(): ApiResult<Unit> = safeCall {
        try {
            client.post("$baseUrl/api/logout") { sessionHeader() }
        } finally {
            sessionCookie = null
        }
        ApiResult.Ok(Unit)
    }

    private suspend fun getJson(path: String): ApiResult<JsonElement> = safeCall {
        val response = client.get("$baseUrl$path") { sessionHeader() }
        when (response.status) {
            HttpStatusCode.OK -> ApiResult.Ok(response.body<JsonElement>())
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
