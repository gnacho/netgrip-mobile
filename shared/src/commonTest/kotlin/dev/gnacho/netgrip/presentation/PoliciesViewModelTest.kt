package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.ParentalRuleDto
import dev.gnacho.netgrip.data.dto.QuotaDto
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

private const val POLICY_BASE = "https://router.local:8080"
private const val DEVICE_MAC = "aa:bb:cc:dd:ee:01"

private val QUOTAS_JSON = """{"applicable":true,"ts":1795714500,
 "quotas":{"$DEVICE_MAC":{"mac":"$DEVICE_MAC","ip":"192.168.1.50","period":"daily",
  "limit":10737418240,"action":"throttle","throttle_kbps":512}},
 "usage":{"$DEVICE_MAC":{"used":1073741824,"limit":10737418240,
  "remaining":9663676416,"period":"2026-11-26","throttled":false,"exceeded":false}}}"""

private val QOS_JSON = """{"applicable":true,
 "limits":{"$DEVICE_MAC":{"mac":"$DEVICE_MAC","ip":"192.168.1.50","download":50,"upload":10}}}"""

private val PARENTAL_JSON = """{"ts":1795714500,
 "rules":{"$DEVICE_MAC":{"mac":"$DEVICE_MAC","enabled":true,"days":[1,3,5],
  "start":"18:00","end":"20:00","paused":false}}}"""

private val PARENTAL_EMPTY_JSON = """{"ts":1795714501,"rules":{}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class PoliciesViewModelTest {

    @Test
    fun loadsTheThreePoliciesInOnePass() = runPolicyTest { engine, viewModel ->
        viewModel.loadPolicies()
        advanceUntilIdle()
        val state = viewModel.policies.value
        assertTrue(state.loaded)
        assertNull(state.error)
        assertEquals(true, state.quotas?.applicable)
        assertEquals(10L * 1024 * 1024 * 1024, state.quotas?.quotas?.get(DEVICE_MAC)?.limit)
        assertEquals(50, state.qos?.limits?.get(DEVICE_MAC)?.download)
        assertEquals(listOf(1, 3, 5), state.parental?.rules?.get(DEVICE_MAC)?.days)
        assertEquals(listOf("/api/quotas", "/api/nftqos", "/api/parental"), engine.reads)
    }

    @Test
    fun savingAQuotaPostsItAndReReadsEverything() = runPolicyTest { engine, viewModel ->
        val device = ClientDto(mac = "AA:BB:CC:DD:EE:01", ip = "192.168.1.50", name = "portatil")
        viewModel.saveQuota(
            device,
            QuotaDto(
                mac = device.mac,
                ip = "",
                period = "daily",
                limit = 10L * 1024 * 1024 * 1024,
                action = "notify",
                throttleKbps = 0,
            ),
        )
        advanceUntilIdle()
        val state = viewModel.policies.value
        assertEquals("Cuota guardada", state.actionMessage)
        assertNull(state.actionError)
        assertNull(state.busyMac)
        // La MAC se normaliza y la IP sale del dispositivo, no del formulario.
        assertEquals(
            """{"mac":"$DEVICE_MAC","ip":"192.168.1.50","period":"daily","limit":10737418240,"action":"notify","throttle_kbps":0}""",
            engine.writes.single().body,
        )
        assertEquals(HttpMethod.Post, engine.writes.single().method)
        // Relectura de las tres politicas tras escribir.
        assertEquals(3, engine.reads.size)
    }

    @Test
    fun aRejectedWriteKeepsTheErrorAndDoesNotReRead() = runPolicyTest(failPost = true) { engine, viewModel ->
        val device = ClientDto(mac = DEVICE_MAC, ip = "192.168.1.50")
        viewModel.saveQos(device, 50, 10)
        advanceUntilIdle()
        val state = viewModel.policies.value
        assertTrue(state.actionError != null)
        assertNull(state.actionMessage)
        assertNull(state.busyMac)
        assertEquals(1, engine.writes.size)
        assertTrue(engine.reads.isEmpty())
    }

    @Test
    fun removingARuleUsesTheMacInTheQuery() = runPolicyTest { engine, viewModel ->
        viewModel.removeParental(DEVICE_MAC)
        advanceUntilIdle()
        val write = engine.writes.single()
        assertEquals(HttpMethod.Delete, write.method)
        assertTrue(write.path.startsWith("/api/parental?mac="), "path was ${write.path}")
        assertEquals("Horario quitado", viewModel.policies.value.actionMessage)
    }

    @Test
    fun unauthorizedMarksTheSessionExpired() = runPolicyTest(quotasStatus = HttpStatusCode.Unauthorized) { _, viewModel ->
        viewModel.loadPolicies()
        advanceUntilIdle()
        assertTrue(viewModel.sessionExpired.value)
        // El resto de politicas se pinta igual.
        assertEquals(50, viewModel.policies.value.qos?.limits?.get(DEVICE_MAC)?.download)
    }

    @Test
    fun ensureLoadsOnlyOnce() = runPolicyTest { engine, viewModel ->
        viewModel.ensurePolicies()
        advanceUntilIdle()
        viewModel.ensurePolicies()
        advanceUntilIdle()
        assertEquals(3, engine.reads.size)
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private class Recorded(val method: HttpMethod, val path: String, val body: String)

    private class Probe {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Recorded>()
    }

    private fun runPolicyTest(
        failPost: Boolean = false,
        quotasStatus: HttpStatusCode = HttpStatusCode.OK,
        block: suspend TestScope.(Probe, ShellViewModel) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val probe = Probe()
            val config = MockEngineConfig()
            config.dispatcher = StandardTestDispatcher(testScheduler)
            config.addHandler { request: HttpRequestData ->
                val path = request.url.encodedPath
                val isWrite = request.method == HttpMethod.Post || request.method == HttpMethod.Delete
                if (isWrite) {
                    val body = try {
                        request.body.toByteArray().decodeToString()
                    } catch (_: Throwable) {
                        ""
                    }
                    probe.writes += Recorded(request.method, request.url.encodedPath + "?" + request.url.encodedQuery, body)
                    if (failPost) {
                        return@addHandler respond(
                            content = """{"error":"limit must be positive"}""",
                            status = HttpStatusCode.BadRequest,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                    val patch = when (path) {
                        "/api/quotas" -> QUOTAS_JSON
                        "/api/nftqos" -> QOS_JSON
                        else -> PARENTAL_EMPTY_JSON
                    }
                    respond(
                        content = patch,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                } else {
                    probe.reads += path
                    val (body, status) = when (path) {
                        "/api/quotas" -> QUOTAS_JSON to quotasStatus
                        "/api/nftqos" -> QOS_JSON to HttpStatusCode.OK
                        "/api/parental" -> PARENTAL_JSON to HttpStatusCode.OK
                        else -> null to HttpStatusCode.NotFound
                    }
                    if (body == null) {
                        respond("", status)
                    } else {
                        respond(
                            content = body,
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                        )
                    }
                }
            }
            val viewModel = ShellViewModel(NetGripApi(MockEngine(config), POLICY_BASE))
            block(probe, viewModel)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
