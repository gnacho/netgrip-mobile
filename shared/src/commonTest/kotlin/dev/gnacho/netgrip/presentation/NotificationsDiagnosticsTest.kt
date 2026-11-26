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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val NOTIFY_BASE = "https://router.local:8080"

private const val TELEGRAM_JSON = """{"chatId":"12345","enabled":true,"botToken":"123456...WXYZ"}"""
private const val NTFY_JSON = """{"server":"https://ntfy.sh","topic":"casa","tokenSet":true,"enabled":true}"""

private const val SELFTEST_JSON = """{"gateway":true,"wan":true,"dns":true,"ntp":false,"all_ok":false,
 "checks":[{"key":"gateway","ok":true,"detail":"192.168.1.1 responde"},
           {"key":"ntp","ok":false,"detail":"sin respuesta"}],
 "tools":{"ping":true,"traceroute":false,"nslookup":true,"dig":false}}"""

private const val PING_JSON = """{"host":"1.1.1.1","count":2,"sent":2,"received":2,"loss_pct":0,
 "min_ms":10.1,"avg_ms":11.2,"max_ms":12.3,
 "samples":[{"seq":1,"time_ms":10.1},{"seq":2,"time_ms":12.3}]}"""

/** Avisos (Telegram y ntfy) y pruebas de diagnostico. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsDiagnosticsTest {

    @Test
    fun guardarTelegramEnviaSuCuerpoYRelee() = runNotifyTest { probe, viewModel ->
        viewModel.loadSettings()
        probe.pump()

        viewModel.saveTelegram("", "12345", true)
        probe.pump()

        val post = probe.calls.first { it.path == "/api/telegram" && it.method == "POST" }
        // El token vacio se omite: el router conserva el guardado.
        assertEquals("""{"chatId":"12345","enabled":true}""", post.body)
        assertEquals("Telegram guardado: bot-demo en Casa", viewModel.settings.value.actionMessage)
        assertFalse(viewModel.settings.value.notifyBusy)
    }

    @Test
    fun laPruebaDeTelegramInformaDeLoQueRespondeElRouter() = runNotifyTest { probe, viewModel ->
        viewModel.loadSettings()
        probe.pump()

        viewModel.testTelegram()
        probe.pump()

        assertEquals("Mensaje de prueba enviado", viewModel.settings.value.actionMessage)
    }

    @Test
    fun guardarNtfyYProbarloUsanSusEndpoints() = runNotifyTest { probe, viewModel ->
        viewModel.loadSettings()
        probe.pump()

        viewModel.saveNtfy("https://ntfy.sh", "casa", "", true)
        probe.pump()
        viewModel.testNtfy()
        probe.pump()

        val post = probe.calls.first { it.path == "/api/ntfy" && it.method == "POST" }
        assertEquals("""{"server":"https://ntfy.sh","topic":"casa","enabled":true}""", post.body)
        assertEquals("Mensaje de prueba enviado a ntfy", viewModel.settings.value.actionMessage)
    }

    @Test
    fun unErrorDeTelegramLlegaTalCual() = runNotifyTest(telegramError = """{"error":"chat not found"}""") { probe, viewModel ->
        viewModel.loadSettings()
        probe.pump()

        viewModel.saveTelegram("123:abc", "999", true)
        probe.pump()

        assertEquals("chat not found", viewModel.settings.value.actionError)
        assertFalse(viewModel.settings.value.notifyBusy)
    }

    @Test
    fun elSelftestSeLeeConSusMotivos() = runNotifyTest { probe, viewModel ->
        viewModel.loadSelfTest()
        probe.pump()

        val selfTest = viewModel.diagnostics.value.selfTest
        assertEquals(false, selfTest?.allOk)
        assertEquals(2, selfTest?.checks?.size)
        assertEquals("sin respuesta", selfTest?.checks?.get(1)?.detail)
        assertTrue(selfTest?.tools?.ping == true)
        assertFalse(selfTest?.tools?.traceroute == true)
    }

    @Test
    fun unPingSeMuestraComoTextoDelRouter() = runNotifyTest { probe, viewModel ->
        viewModel.runDiagnostic("ping", host = "1.1.1.1", count = 2)
        probe.pump()

        val post = probe.calls.first { it.path == "/api/diagnostics" }
        assertEquals("""{"test":"ping","host":"1.1.1.1","count":2}""", post.body)
        val lines = viewModel.diagnostics.value.toolResult
        assertTrue(lines.first().contains("2/2 respuestas"))
        assertTrue(lines.any { it.contains("min 10.1") })
        assertEquals(null, viewModel.diagnostics.value.toolRunning)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Call(val path: String, val method: String, val body: String)

    private class Probe(val scope: TestScope) {
        val calls = mutableListOf<Call>()

        fun pump() {
            repeat(12) {
                scope.runCurrent()
                scope.advanceTimeBy(1)
            }
        }
    }

    private fun runNotifyTest(
        telegramError: String? = null,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe(this)
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val method = request.method.value
                val body = request.body.toByteArray().decodeToString()
                probe.calls += Call(path, method, body)
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                when {
                    telegramError != null && path == "/api/telegram" ->
                        respond(telegramError, HttpStatusCode.BadRequest, json)
                    path == "/api/telegram" && method == "POST" ->
                        respond("""{"ok":true,"botName":"bot-demo","chatName":"Casa"}""", HttpStatusCode.OK, json)
                    path == "/api/telegram/test" -> respond("""{"ok":true}""", HttpStatusCode.OK, json)
                    path == "/api/telegram" -> respond(TELEGRAM_JSON, HttpStatusCode.OK, json)
                    path == "/api/ntfy" && method == "POST" -> respond("""{"ok":true}""", HttpStatusCode.OK, json)
                    path == "/api/ntfy/test" -> respond("""{"ok":true}""", HttpStatusCode.OK, json)
                    path == "/api/ntfy" -> respond(NTFY_JSON, HttpStatusCode.OK, json)
                    path == "/api/diagnostics/selftest" -> respond(SELFTEST_JSON, HttpStatusCode.OK, json)
                    path == "/api/diagnostics" -> respond(PING_JSON, HttpStatusCode.OK, json)
                    // Resto de lecturas de Ajustes: vacias pero validas.
                    path == "/api/board" || path == "/api/mode" || path == "/api/access" ||
                        path == "/api/mdns" || path == "/api/selfupdate" ||
                        path == "/api/selfupdate/schedule" || path == "/api/update" ->
                        respond("{}", HttpStatusCode.OK, json)
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), NOTIFY_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
