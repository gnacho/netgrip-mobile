package dev.gnacho.netgrip.session

import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSecureStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

private const val BASE = "http://router.local"

class SessionManagerTest {

    @Test
    fun restoreSinCookieGuardadaDevuelveFalse() = runTest {
        val storage = FakeSecureStorage()
        val api = NetGripApi(MockEngine { respond("", HttpStatusCode.NoContent) }, BASE)
        assertFalse(SessionManager(storage).restore(api))
    }

    @Test
    fun restoreConCookieValidaDevuelveTrue() = runTest {
        val storage = FakeSecureStorage()
        storage.save("session.default", "netgrip_http_session=abc123")
        val api = NetGripApi(MockEngine { respond("", HttpStatusCode.NoContent) }, BASE)
        assertTrue(SessionManager(storage).restore(api))
    }

    @Test
    fun restoreConCookieCaducadaLaBorraYDevuelveFalse() = runTest {
        val storage = FakeSecureStorage()
        storage.save("session.default", "netgrip_http_session=vieja")
        val api = NetGripApi(MockEngine { respond("", HttpStatusCode.Unauthorized) }, BASE)
        assertFalse(SessionManager(storage).restore(api))
        assertFalse(storage.data.containsKey("session.default"))
    }

    @Test
    fun onLoginOkGuardaCookieYUltimaUrl() = runTest {
        val storage = FakeSecureStorage()
        val engine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.SetCookie, "netgrip_http_session=abc123; Path=/"),
            )
        }
        val api = NetGripApi(engine, BASE)
        api.login("root", "secreto")
        val manager = SessionManager(storage)
        manager.onLoginOk(api, BASE)
        assertEquals("netgrip_http_session=abc123", storage.data["session.default"])
        assertEquals(BASE, manager.lastUrl())
    }

    @Test
    fun logoutBorraCookieYUltimaUrl() = runTest {
        val storage = FakeSecureStorage()
        storage.save("session.default", "netgrip_http_session=abc123")
        storage.save("last.url", BASE)
        val manager = SessionManager(storage)
        val api = NetGripApi(MockEngine { respond("", HttpStatusCode.NoContent) }, BASE)
        api.restoreSessionCookie("netgrip_http_session=abc123")
        manager.logout(api)
        assertFalse(storage.data.containsKey("session.default"))
        assertNull(manager.lastUrl())
    }
}
