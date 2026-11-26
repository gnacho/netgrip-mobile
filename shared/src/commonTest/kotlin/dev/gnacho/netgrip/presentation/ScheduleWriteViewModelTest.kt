package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.WifiScheduleDto
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
import kotlin.test.assertNull

private const val SCHED_BASE = "https://router.local:8080"

private const val SCHEDULES_EMPTY = """{"schedules":[
 {"section":"default_radio0","ifname":"phy0-ap0","ssid":"Casa","disabled":false,"off_by_schedule":false}
]}"""

private const val SCHEDULES_SAVED = """{"schedules":[
 {"section":"default_radio0","ifname":"phy0-ap0","ssid":"Casa","disabled":true,"off_by_schedule":true,
  "schedule":{"section":"default_radio0","enabled":true,"days":[1,2,3,4,5],
  "start":"07:00","end":"23:00","paused":false}}
]}"""

/** Horario WiFi: cuerpo exacto, relectura y rollback. */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleWriteViewModelTest {

    @Test
    fun elHorarioViajaConDiasYHorasYSeRelee() = runScheduleTest { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applySchedule(
            WifiScheduleDto(
                section = "default_radio0",
                enabled = true,
                days = listOf(1, 2, 3, 4, 5),
                start = "07:00",
                end = "23:00",
                paused = false,
            ),
        )
        probe.pump()

        val post = probe.calls.last { it.path == "/api/wifischedule" && it.method == "POST" }
        assertEquals(
            "{\"section\":\"default_radio0\",\"enabled\":true,\"days\":[1,2,3,4,5]," +
                "\"start\":\"07:00\",\"end\":\"23:00\"}",
            post.body,
        )
        assertEquals("Horario guardado y verificado", viewModel.wifi.value.actionMessage)
        assertFalse(viewModel.wifi.value.busy)
        val saved = viewModel.wifi.value.schedules.single()
        assertEquals(true, saved.offBySchedule)
        assertEquals(listOf(1, 2, 3, 4, 5), saved.schedule?.days)
    }

    @Test
    fun unRollbackDelHorarioSeMuestraComoError() = runScheduleTest(rolledBack = true) { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applySchedule(
            WifiScheduleDto("default_radio0", true, listOf(1), "07:00", "23:00"),
        )
        probe.pump()

        assertEquals(
            "El router deshizo el horario y dejó el anterior",
            viewModel.wifi.value.actionError,
        )
        assertNull(viewModel.wifi.value.actionMessage)
    }

    @Test
    fun elErrorDelServidorLlegaTalCual() = runScheduleTest(httpError = """{"status":"failed","error":"at least one day required"}""") { probe, viewModel ->
        viewModel.loadWifi()
        probe.pump()

        viewModel.applySchedule(
            WifiScheduleDto("default_radio0", true, emptyList(), "07:00", "23:00"),
        )
        probe.pump()

        assertEquals("at least one day required", viewModel.wifi.value.actionError)
        assertFalse(viewModel.wifi.value.busy)
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

    private fun runScheduleTest(
        rolledBack: Boolean = false,
        httpError: String? = null,
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
                val writes = probe.calls.count { it.path == "/api/wifischedule" }
                when {
                    path == "/api/wifi" -> respond("""{"interfaces":[]}""", HttpStatusCode.OK, json)
                    path == "/api/wireless" -> respond("[]", HttpStatusCode.OK, json)
                    path == "/api/mode" -> respond("""{"has_wifi":true}""", HttpStatusCode.OK, json)
                    path == "/api/config/confirm" -> respond("""{"pending":false}""", HttpStatusCode.OK, json)
                    path == "/api/guestwifi" -> respond("", HttpStatusCode.NotFound)
                    path == "/api/iotwifi" -> respond("", HttpStatusCode.NotFound)
                    path == "/api/wifischedule" && method == "GET" ->
                        respond(if (writes > 0) SCHEDULES_SAVED else SCHEDULES_EMPTY, HttpStatusCode.OK, json)
                    httpError != null && path == "/api/wifischedule" ->
                        respond(httpError, HttpStatusCode.BadRequest, json)
                    path == "/api/wifischedule" -> respond(
                        """{"status":"applied","rolled_back":$rolledBack,"state":$SCHEDULES_SAVED}""",
                        HttpStatusCode.OK,
                        json,
                    )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), SCHED_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
