package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SYS_BASE = "https://router.local:8080"

/** Reinicio y contrasena: confirmacion, sondeo de vuelta y sesion perdida. */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemActionsViewModelTest {

    @Test
    fun elReinicioSeConfirmaCuandoElRouterVuelve() = runSystemTest(meFailures = 2) { probe, viewModel ->
        viewModel.rebootRouter()
        probe.pumpUntilIdle()

        assertEquals("El router ha vuelto a responder", viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.rebooting)
        assertFalse(viewModel.settings.value.actionBusy)
        assertEquals("{}", probe.calls.first { it.path == "/api/reboot" }.body)
        assertTrue(probe.calls.count { it.path == "/api/me" } >= 3)
    }

    @Test
    fun siElRouterNoVuelveSeDiceSinFingirExito() = runSystemTest(alwaysDown = true) { probe, viewModel ->
        viewModel.rebootRouter()
        probe.pumpUntilIdle()

        assertTrue(viewModel.settings.value.actionError?.contains("no responde todavía") == true)
        assertNull(viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.rebooting)
        assertEquals(60, probe.calls.count { it.path == "/api/me" })
    }

    @Test
    fun siLaSesionNoSobreviveAlReinicioSeVuelveAlLogin() = runSystemTest(unauthorizedAfterReboot = true) { probe, viewModel ->
        viewModel.rebootRouter()
        probe.pumpUntilIdle()

        assertTrue(viewModel.sessionExpired.value)
        assertFalse(viewModel.settings.value.rebooting)
    }

    @Test
    fun cambiarLaContrasenaCierraLaSesionDeLaApp() = runSystemTest(passwordStatus = HttpStatusCode.NoContent) { probe, viewModel ->
        viewModel.changePassword("vieja-clave", "nueva-clave")
        probe.pumpUntilIdle()

        assertTrue(viewModel.passwordChanged.value)
        val post = probe.calls.first { it.path == "/api/password" }
        assertEquals("""{"current":"vieja-clave","next":"nueva-clave"}""", post.body)
        assertNull(viewModel.settings.value.actionError)
    }

    @Test
    fun unaContrasenaActualIncorrectaSeMuestraTalCual() = runSystemTest(
        passwordStatus = HttpStatusCode.BadRequest,
        passwordError = """{"error":"current password is wrong"}""",
    ) { probe, viewModel ->
        viewModel.changePassword("mala", "nueva-clave")
        probe.pumpUntilIdle()

        assertFalse(viewModel.passwordChanged.value)
        assertEquals("current password is wrong", viewModel.settings.value.actionError)
        assertFalse(viewModel.settings.value.actionBusy)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Call(val path: String, val method: String, val body: String)

    private class Probe(val scope: TestScope) {
        val calls = mutableListOf<Call>()

        fun pumpUntilIdle() {
            repeat(200) {
                scope.runCurrent()
                scope.advanceUntilIdle()
            }
        }
    }

    private fun runSystemTest(
        meFailures: Int = 0,
        alwaysDown: Boolean = false,
        unauthorizedAfterReboot: Boolean = false,
        passwordStatus: HttpStatusCode = HttpStatusCode.NoContent,
        passwordError: String = "",
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe(this)
            var meCount = 0
            var rebooted = false
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val method = request.method.value
                val body = request.body.toByteArray().decodeToString()
                probe.calls += Call(path, method, body)
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                when {
                    path == "/api/reboot" -> {
                        rebooted = true
                        respond("""{"started":true}""", HttpStatusCode.OK, json)
                    }
                    path == "/api/me" -> {
                        meCount += 1
                        when {
                            !rebooted -> respond("", HttpStatusCode.NoContent)
                            unauthorizedAfterReboot -> respond("", HttpStatusCode.Unauthorized)
                            alwaysDown -> respond("", HttpStatusCode.BadGateway)
                            meCount > meFailures -> respond("", HttpStatusCode.NoContent)
                            else -> respond("", HttpStatusCode.BadGateway)
                        }
                    }
                    path == "/api/password" ->
                        respond(passwordError, passwordStatus, json)
                    path == "/api/board" -> respond("{}", HttpStatusCode.OK, json)
                    path == "/api/mode" -> respond("{}", HttpStatusCode.OK, json)
                    path == "/api/access" -> respond("{}", HttpStatusCode.OK, json)
                    path == "/api/mdns" -> respond("{}", HttpStatusCode.OK, json)
                    path == "/api/selfupdate" -> respond("{}", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), SYS_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
