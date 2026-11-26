package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val BATCH_BASE = "https://router.local:8080"

private const val BATCH_CHECK_ALL = """{"nodes":[
 {"id":"alfa","name":"Alfa","address":"a","reachable":true,
  "current_version":"0.82.0","latest_version":"0.83.0","update_available":true},
 {"id":"beta","name":"Beta","address":"b","reachable":true,
  "current_version":"0.82.0","latest_version":"0.82.0","update_available":false},
 {"id":"gamma","name":"Gamma","address":"c","reachable":false,"error":"timeout"}]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class FleetBatchTest {

    @Test
    fun theBatchSkipsWithReasonsAndUpdatesTheRest() = runBatchTest { engine, viewModel ->
        viewModel.startFleetUpdate()
        advanceUntilIdle()
        val state = viewModel.fleet.value
        assertEquals(false, state.batch.running)
        val tasks = state.batch.tasks.associateBy { it.id }
        assertEquals(FleetTaskStatus.UPDATED, tasks.getValue("alfa").status)
        assertEquals("ahora en la 0.83.0", tasks.getValue("alfa").detail)
        assertEquals(FleetTaskStatus.SKIPPED, tasks.getValue("beta").status)
        assertEquals("ya está en la 0.82.0", tasks.getValue("beta").detail)
        assertEquals(FleetTaskStatus.SKIPPED, tasks.getValue("gamma").status)
        assertEquals("no responde: timeout", tasks.getValue("gamma").detail)
        // Solo se manda actualizar al que lo necesita.
        assertEquals(listOf("alfa"), engine.updates)
        assertTrue(state.actionMessage!!.contains("1 actualizados"))
        assertTrue(state.actionMessage!!.contains("2 omitidos"))
    }

    @Test
    fun aTwoHundredIsNotAnUpdateUntilTheVersionChanges() = runBatchTest(confirmUpdate = false) { _, viewModel ->
        viewModel.startFleetUpdate()
        advanceUntilIdle()
        val task = viewModel.fleet.value.batch.tasks.first { it.id == "alfa" }
        assertEquals(FleetTaskStatus.UNCONFIRMED, task.status)
        assertTrue(task.detail.contains("sigue en la 0.82.0"), "detail was ${task.detail}")
        assertTrue(viewModel.fleet.value.actionMessage!!.contains("1 sin confirmar"))
    }

    @Test
    fun aRejectedUpdateIsAFailure() = runBatchTest(failUpdate = true) { engine, viewModel ->
        viewModel.startFleetUpdate()
        advanceUntilIdle()
        val task = viewModel.fleet.value.batch.tasks.first { it.id == "alfa" }
        assertEquals(FleetTaskStatus.FAILED, task.status)
        assertTrue(task.detail.isNotBlank())
        assertEquals(listOf("alfa"), engine.updates)
        // No se espera a una version que nunca se pidio.
        assertEquals(0, engine.checks)
    }

    @Test
    fun cancellingStopsAfterTheCurrentCheck() = runBatchTest { _, viewModel ->
        viewModel.startFleetUpdate()
        // Deja que salga la orden de actualizacion y pare antes de la primera comprobacion.
        advanceTimeBy(5_000)
        viewModel.cancelFleetUpdate()
        advanceUntilIdle()
        val tasks = viewModel.fleet.value.batch.tasks.associateBy { it.id }
        assertEquals(FleetTaskStatus.UNCONFIRMED, tasks.getValue("alfa").status)
        assertEquals("detenido antes de confirmar", tasks.getValue("alfa").detail)
        assertEquals(FleetTaskStatus.SKIPPED, tasks.getValue("beta").status)
        assertEquals(FleetTaskStatus.SKIPPED, tasks.getValue("gamma").status)
        assertTrue(viewModel.fleet.value.actionMessage!!.contains("1 sin confirmar"))
    }

    @Test
    fun withNothingToUpdateNoOrderIsSent() = runBatchTest(checkAll = noUpdates) { engine, viewModel ->
        viewModel.startFleetUpdate()
        advanceUntilIdle()
        assertTrue(engine.updates.isEmpty())
        assertEquals("No hay nada que actualizar", viewModel.fleet.value.actionMessage)
        assertEquals(2, viewModel.fleet.value.batch.tasks.size)
    }

    @Test
    fun aFailedFleetCheckDoesNotStartTheBatch() = runBatchTest(
        checkAllStatus = HttpStatusCode.BadGateway,
    ) { engine, viewModel ->
        viewModel.startFleetUpdate()
        advanceUntilIdle()
        assertTrue(engine.updates.isEmpty())
        assertTrue(viewModel.fleet.value.actionError != null)
        assertEquals(false, viewModel.fleet.value.batch.running)
        assertNull(viewModel.fleet.value.actionMessage)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private val noUpdates = """{"nodes":[
     {"id":"beta","name":"Beta","address":"b","reachable":true,
      "current_version":"0.82.0","latest_version":"0.82.0","update_available":false},
     {"id":"gamma","name":"Gamma","address":"c","reachable":false,"error":"timeout"}]}"""

    private class Probe {
        val updates = mutableListOf<String>()
        var checks = 0
    }

    private fun runBatchTest(
        checkAll: String = BATCH_CHECK_ALL,
        checkAllStatus: HttpStatusCode = HttpStatusCode.OK,
        confirmUpdate: Boolean = true,
        failUpdate: Boolean = false,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe()
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                val body = if (request.method == HttpMethod.Post) {
                    try {
                        request.body.toByteArray().decodeToString()
                    } catch (_: Throwable) {
                        ""
                    }
                } else {
                    ""
                }
                when (path) {
                    "/api/fleet/check-all" -> respond(checkAll, checkAllStatus, json)
                    "/api/fleet/update" -> {
                        probe.updates += body.substringAfter("\"id\":\"").substringBefore('"')
                        if (failUpdate) {
                            respond(
                                """{"error":"update: dial tcp: i/o timeout"}""",
                                HttpStatusCode.InternalServerError,
                                json,
                            )
                        } else {
                            respond("""{"status":"updating"}""", HttpStatusCode.OK, json)
                        }
                    }
                    "/api/fleet/check" -> {
                        probe.checks++
                        val id = body.substringAfter("\"id\":\"").substringBefore('"')
                        val version = if (confirmUpdate) "0.83.0" else "0.82.0"
                        respond(
                            """{"id":"$id","name":"Alfa","address":"a","reachable":true,
                               "current_version":"$version","latest_version":"0.83.0",
                               "update_available":false}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), BATCH_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
