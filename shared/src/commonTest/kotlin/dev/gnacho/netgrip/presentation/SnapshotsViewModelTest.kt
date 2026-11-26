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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val SNAP_BASE = "https://router.local:8080"

private const val SNAPSHOTS_JSON = """{"snapshots":[
 {"id":"20261126-120000","timestamp":1790000000,"configs":9},
 {"id":"20261125-090000","timestamp":1789900000,"configs":8}]}"""

private const val DIFF_JSON = """{"diffs":[{"config":"network","before":"config interface 'lan'\n\toption ipaddr '192.168.1.1'",
 "after":"config interface 'lan'\n\toption ipaddr '192.168.1.2'"}]}"""

/** Copias: crear, diff temporal contra lo actual, restaurar y borrar. */
@OptIn(ExperimentalCoroutinesApi::class)
class SnapshotsViewModelTest {

    @Test
    fun laListaSeLeeYElDiffCreaUnaCopiaTemporalQueLuegoBorra() = runSnapTest { probe, viewModel ->
        viewModel.loadSnapshots()
        probe.pump()
        assertEquals(2, viewModel.snapshots.value.snapshots.size)

        viewModel.showSnapshotDiff("20261126-120000")
        probe.pump()

        assertEquals(1, probe.calls.count { it.path == "/api/config/snapshot" && it.method == "POST" })
        val diff = probe.calls.first { it.path == "/api/config/diff" }
        assertTrue(diff.query.contains("from=20261126-120000"))
        assertTrue(diff.query.contains("to=20261126-120001"))
        assertEquals(1, probe.calls.count { it.path == "/api/config/snapshot" && it.method == "DELETE" })
        assertEquals(1, viewModel.snapshots.value.diff?.size)
        assertEquals("network", viewModel.snapshots.value.diff?.first()?.config)
        assertFalse(viewModel.snapshots.value.diffBusy)
    }

    @Test
    fun siElDiffFallaLaCopiaTemporalSeBorraIgual() = runSnapTest(diffStatus = HttpStatusCode.BadGateway) { probe, viewModel ->
        viewModel.loadSnapshots()
        probe.pump()

        viewModel.showSnapshotDiff("20261126-120000")
        probe.pump()

        assertEquals(1, probe.calls.count { it.path == "/api/config/snapshot" && it.method == "DELETE" })
        assertNull(viewModel.snapshots.value.diff)
        assertTrue(viewModel.snapshots.value.actionError != null)
    }

    @Test
    fun restaurarAvisaYReleeLaLista() = runSnapTest { probe, viewModel ->
        viewModel.loadSnapshots()
        probe.pump()

        viewModel.rollbackSnapshot("20261126-120000")
        probe.pump()

        val post = probe.calls.first { it.path == "/api/config/rollback" }
        assertEquals("""{"id":"20261126-120000"}""", post.body)
        assertEquals("Configuración restaurada desde 20261126-120000", viewModel.snapshots.value.actionMessage)
        assertFalse(viewModel.snapshots.value.busy)
    }

    @Test
    fun crearUnaCopiaLaAnunciaConSuId() = runSnapTest { probe, viewModel ->
        viewModel.loadSnapshots()
        probe.pump()

        viewModel.createSnapshot()
        probe.pump()

        assertEquals("Copia 20261126-120001 guardada", viewModel.snapshots.value.actionMessage)
    }

    @Test
    fun unErrorAlRestaurarSeMuestraTalCual() = runSnapTest(
        rollbackStatus = HttpStatusCode.InternalServerError,
        rollbackError = """{"error":"no configs restored from snapshot 20261126-120000"}""",
    ) { probe, viewModel ->
        viewModel.loadSnapshots()
        probe.pump()

        viewModel.rollbackSnapshot("20261126-120000")
        probe.pump()

        assertEquals(
            "no configs restored from snapshot 20261126-120000",
            viewModel.snapshots.value.actionError,
        )
        assertFalse(viewModel.snapshots.value.busy)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Call(val path: String, val query: String, val method: String, val body: String)

    private class Probe(val scope: TestScope) {
        val calls = mutableListOf<Call>()

        fun pump() {
            repeat(12) {
                scope.runCurrent()
                scope.advanceTimeBy(1)
            }
        }
    }

    private fun runSnapTest(
        diffStatus: HttpStatusCode = HttpStatusCode.OK,
        rollbackStatus: HttpStatusCode = HttpStatusCode.OK,
        rollbackError: String = "",
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe(this)
            var created = 0
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val query = request.url.encodedQuery
                val method = request.method.value
                val body = request.body.toByteArray().decodeToString()
                probe.calls += Call(path, query, method, body)
                val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                when {
                    path == "/api/config/snapshots" -> respond(SNAPSHOTS_JSON, HttpStatusCode.OK, json)
                    path == "/api/config/snapshot" && method == "POST" -> {
                        created += 1
                        respond(
                            """{"id":"20261126-12000$created","timestamp":1790000000,"configs":9}""",
                            HttpStatusCode.OK,
                            json,
                        )
                    }
                    path == "/api/config/snapshot" && method == "DELETE" ->
                        respond("", HttpStatusCode.NoContent, json)
                    path == "/api/config/diff" ->
                        respond(if (diffStatus == HttpStatusCode.OK) DIFF_JSON else """{"error":"boom"}""", diffStatus, json)
                    path == "/api/config/rollback" ->
                        respond(
                            if (rollbackStatus == HttpStatusCode.OK) """{"status":"applied"}""" else rollbackError,
                            rollbackStatus,
                            json,
                        )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val viewModel = ShellViewModel(api = NetGripApi(MockEngine(config), SNAP_BASE), now = { 1_000 })
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
