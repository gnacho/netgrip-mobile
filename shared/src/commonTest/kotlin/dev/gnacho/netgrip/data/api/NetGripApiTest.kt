package dev.gnacho.netgrip.data.api

import dev.gnacho.netgrip.core.network.ApiResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val BASE = "http://router.local"

class NetGripApiTest {

    private fun apiWith(engine: MockEngine) = NetGripApi(engine, BASE)

    @Test
    fun loginOkGuardaLaCookieDeSesion() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(
                    HttpHeaders.SetCookie,
                    "netgrip_http_session=abc123; Path=/; HttpOnly; SameSite=Lax",
                ),
            )
        }
        val api = apiWith(engine)
        val result = api.login("", "secreto")
        assertIs<ApiResult.Ok<Unit>>(result)
        assertEquals("netgrip_http_session=abc123", api.sessionCookie)
    }

    @Test
    fun loginAceptaLaCookieSeguraDeHttps() = runTest {
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(
                    HttpHeaders.SetCookie,
                    "__Secure-netgrip_session=xyz789; Path=/; HttpOnly; Secure; SameSite=Lax",
                ),
            )
        }
        val api = apiWith(engine)
        assertIs<ApiResult.Ok<Unit>>(api.login("root", "secreto"))
        assertEquals("__Secure-netgrip_session=xyz789", api.sessionCookie)
    }

    @Test
    fun loginConCredencialesIncorrectasDevuelve401() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        assertEquals(ApiResult.Unauthorized, apiWith(engine).login("root", "mal"))
    }

    @Test
    fun loginConRateLimitDevuelveRetryAfter() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "30"))
        }
        val result = apiWith(engine).login("root", "x")
        assertIs<ApiResult.RateLimited>(result)
        assertEquals(30L, result.retryAfterSeconds)
    }

    @Test
    fun meSinSesionDevuelve401() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        assertEquals(ApiResult.Unauthorized, apiWith(engine).me())
    }

    @Test
    fun systemDevuelveJsonTipado() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"uptime":1234,"load":[0.1,0.2,0.3],"memory":{"total":100,"free":10,"available":90},"root":{"total":50,"free":25}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val api = apiWith(engine)
        api.restoreSessionCookie("netgrip_http_session=abc123")
        val result = api.systemInfo()
        assertIs<ApiResult.Ok<*>>(result)
        assertEquals(1234L, (result.value as dev.gnacho.netgrip.data.dto.SystemDto).uptime)
    }

    @Test
    fun routerInalcanzableSeMapeaAUnreachable() = runTest {
        val engine = MockEngine { throw IOException("sin red") }
        assertEquals(ApiResult.Unreachable, apiWith(engine).me())
    }

    @Test
    fun logoutLimpiaLaCookieLocal() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val api = apiWith(engine)
        api.restoreSessionCookie("netgrip_http_session=abc123")
        api.logout()
        assertEquals(null, api.sessionCookie)
    }
}
