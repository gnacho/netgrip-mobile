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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val STORAGE_BASE = "https://router.local:8080"

private const val UNMOUNTED_JSON = """{"applicable":true,"supported":true,"usb_present":true,
 "usb_storage_loaded":true,"devices":[
  {"name":"sda1","path":"/dev/sda1","fs_type":"ext4","size_bytes":1000,
   "used_bytes":250,"free_bytes":750}],"services":[]}"""

private const val MOUNTED_JSON = """{"applicable":true,"supported":true,"usb_present":true,
 "usb_storage_loaded":true,"devices":[
  {"name":"sda1","path":"/dev/sda1","fs_type":"ext4","size_bytes":1000,
   "used_bytes":250,"free_bytes":750,"mount_point":"/mnt/sda1","managed":true}],
 "services":[]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class StorageViewModelTest {

    @Test
    fun loadsTheDisks() = runStorageTest { engine, viewModel ->
        viewModel.loadStorage()
        advanceUntilIdle()
        val state = viewModel.storage.value
        assertTrue(state.loaded)
        assertNull(state.error)
        assertEquals(1, state.probe?.devices?.size)
        assertNull(state.probe?.devices?.single()?.mountPoint)
        assertEquals(listOf("/api/storage"), engine.reads)
    }

    @Test
    fun mountingPostsTheBodyAndPaintsTheProbeTheRouterReturns() = runStorageTest { engine, viewModel ->
        viewModel.loadStorage()
        advanceUntilIdle()
        viewModel.mountStorage("/dev/sda1", "/mnt/sda1", readOnly = true)
        advanceUntilIdle()
        val state = viewModel.storage.value
        assertEquals(
            """{"device":"/dev/sda1","target":"/mnt/sda1","read_only":true}""",
            engine.writes.single().body,
        )
        // El estado nuevo es el probe que devolvio el router, no el esperado.
        assertEquals("/mnt/sda1", state.probe?.devices?.single()?.mountPoint)
        assertEquals(true, state.probe?.devices?.single()?.managed)
        assertEquals("sda1 montado en /mnt/sda1", state.actionMessage)
        assertNull(state.actionError)
    }

    @Test
    fun aRejectedMountKeepsTheRouterMessageAndTheOldState() = runStorageTest(failWrites = true) { _, viewModel ->
        viewModel.loadStorage()
        advanceUntilIdle()
        viewModel.mountStorage("/dev/sda1", "/mnt/sda1", readOnly = false)
        advanceUntilIdle()
        val state = viewModel.storage.value
        assertNull(state.probe?.devices?.single()?.mountPoint)
        assertNull(state.actionMessage)
        assertTrue(state.actionError != null)
    }

    @Test
    fun unmountingSendsTheTarget() = runStorageTest(initial = MOUNTED_JSON) { engine, viewModel ->
        viewModel.loadStorage()
        advanceUntilIdle()
        viewModel.unmountStorage("/mnt/sda1")
        advanceUntilIdle()
        assertEquals("""{"target":"/mnt/sda1"}""", engine.writes.single().body)
        assertEquals(HttpMethod.Post, engine.writes.single().method)
        assertNull(viewModel.storage.value.probe?.devices?.single()?.mountPoint)
    }

    @Test
    fun unauthorizedMarksTheSessionExpired() = runStorageTest(status = HttpStatusCode.Unauthorized) { _, viewModel ->
        viewModel.loadStorage()
        advanceUntilIdle()
        assertTrue(viewModel.sessionExpired.value)
        assertTrue(viewModel.storage.value.error != null)
    }

    @Test
    fun ensureLoadsOnlyOnce() = runStorageTest { engine, viewModel ->
        viewModel.ensureStorage()
        advanceUntilIdle()
        viewModel.ensureStorage()
        advanceUntilIdle()
        assertEquals(1, engine.reads.size)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Recorded(val method: HttpMethod, val path: String, val body: String)

    private class Probe {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Recorded>()
    }

    private fun runStorageTest(
        initial: String = UNMOUNTED_JSON,
        failWrites: Boolean = false,
        status: HttpStatusCode = HttpStatusCode.OK,
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
                if (request.method == HttpMethod.Post) {
                    val body = try {
                        request.body.toByteArray().decodeToString()
                    } catch (_: Throwable) {
                        ""
                    }
                    probe.writes += Recorded(HttpMethod.Post, path, body)
                    if (failWrites) {
                        respond("""{"error":"mount /mnt/sda1: invalid argument"}""", HttpStatusCode.BadRequest, json)
                    } else {
                        val answer = if (path == "/api/storage/unmount") UNMOUNTED_JSON else MOUNTED_JSON
                        respond(answer, HttpStatusCode.OK, json)
                    }
                } else {
                    probe.reads += path
                    if (path == "/api/storage") {
                        respond(initial, status, json)
                    } else {
                        respond("", HttpStatusCode.NotFound)
                    }
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), STORAGE_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
