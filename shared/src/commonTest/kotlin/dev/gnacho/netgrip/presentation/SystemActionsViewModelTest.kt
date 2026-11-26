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

    @Test
    fun elFirmwareSeConfirmaCuandoElRouterVuelve() = runSystemTest(meFailures = 1, firmware = true) { probe, viewModel ->
        viewModel.installFirmware()
        probe.pumpUntilIdle()

        val post = probe.calls.first { it.path == "/api/update" && it.method == "POST" }
        assertEquals("""{"confirm":true}""", post.body)
        assertEquals("El router ha vuelto tras el firmware", viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.firmwareRunning)
    }

    @Test
    fun laActualizacionDelPanelUsaSuPropioEndpointYVuelve() = runSystemTest(meFailures = 1, selfUpdate = true) { probe, viewModel ->
        viewModel.updateNetGrip()
        probe.pumpUntilIdle()

        val post = probe.calls.first { it.path == "/api/selfupdate" && it.method == "POST" }
        assertEquals("""{"confirm":true}""", post.body)
        assertEquals("El panel se ha actualizado y ha vuelto", viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.selfUpdateRunning)
    }

    @Test
    fun laProgramacionDelPanelEnviaSoloLoQueCambia() = runSystemTest(schedule = true) { probe, viewModel ->
        viewModel.loadSettings()
        probe.pumpUntilIdle()

        viewModel.saveSelfUpdateSchedule(intervalHours = 24, windowStart = 4, windowEnd = 6)
        probe.pumpUntilIdle()

        val put = probe.calls.first { it.path == "/api/selfupdate/schedule" && it.method == "PUT" }
        assertEquals("""{"intervalHours":24,"windowStart":4,"windowEnd":6}""", put.body)
        assertEquals("Programación guardada", viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.scheduleSaving)
        assertEquals(24, viewModel.settings.value.selfUpdateSchedule?.config?.intervalHours)
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
        firmware: Boolean = false,
        selfUpdate: Boolean = false,
        schedule: Boolean = false,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe(this)
            var meCount = 0
            var started = false
            var scheduleInterval = 168
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
                        started = true
                        respond("""{"started":true}""", HttpStatusCode.OK, json)
                    }
                    path == "/api/me" -> {
                        meCount += 1
                        when {
                            !started -> respond("", HttpStatusCode.NoContent)
                            unauthorizedAfterReboot -> respond("", HttpStatusCode.Unauthorized)
                            alwaysDown -> respond("", HttpStatusCode.BadGateway)
                            meCount > meFailures -> respond("", HttpStatusCode.NoContent)
                            else -> respond("", HttpStatusCode.BadGateway)
                        }
                    }
                    path == "/api/password" ->
                        respond(passwordError, passwordStatus, json)
                    path == "/api/update" && method == "POST" -> {
                        started = true
                        respond("""{"started":true,"reboot_pending":true}""", HttpStatusCode.OK, json)
                    }
                    path == "/api/selfupdate" && method == "POST" -> {
                        started = true
                        respond("""{"phase":"restarting","progress":100}""", HttpStatusCode.OK, json)
                    }
                    path == "/api/update" -> respond("{}", HttpStatusCode.OK, json)
                    path == "/api/selfupdate/schedule" && method == "PUT" -> {
                        if (body.contains("\"intervalHours\":24")) scheduleInterval = 24
                        respond(
                            """{"config":{"enabled":false,"intervalHours":$scheduleInterval,""" +
                                """"windowStart":4,"windowEnd":6}}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    path == "/api/selfupdate/schedule" -> respond(
                        """{"config":{"enabled":false,"intervalHours":$scheduleInterval,""" +
                            """"windowStart":3,"windowEnd":5},
                            "state":{"lastResult":"up-to-date","lastCheck":1000}}""",
                        HttpStatusCode.OK,
                        json,
                    )
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
