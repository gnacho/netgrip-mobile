package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.core.network.CertificateFingerprintStore
import dev.gnacho.netgrip.core.network.CertificatePin
import dev.gnacho.netgrip.core.network.EndpointProbe
import dev.gnacho.netgrip.core.network.EndpointProbeResult
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.session.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class SessionStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

private class FakeEndpointProbe(private val result: EndpointProbeResult) : EndpointProbe {
    override suspend fun probe(baseUrl: String): EndpointProbeResult = result
}

private class RecordingApiFactory(private val engine: MockEngine) : NetGripApiFactory {
    val pins = mutableListOf<CertificatePin?>()

    override fun create(baseUrl: String, certificatePin: CertificatePin?): NetGripApi {
        pins += certificatePin
        return NetGripApi(engine, baseUrl)
    }
}

private val testFingerprint = "42".repeat(32)

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {

    @Test
    fun credentialsAreNotSentBeforeFingerprintConfirmation() = runSessionTest {
        val storage = SessionStorage()
        val store = CertificateFingerprintStore(storage)
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = FakeEndpointProbe(
                EndpointProbeResult.CertificateConfirmationRequired(
                    baseUrl = "https://router.local:8080",
                    host = "router.local",
                    fingerprint = testFingerprint,
                ),
            ),
            fingerprints = store,
            apiFactory = factory,
        )

        viewModel.login("http://router.local:8080", "root", "test-password")
        testScheduler.advanceUntilIdle()

        assertIs<UiState.CertificateConfirmation>(viewModel.uiState)
        assertEquals(0, factory.pins.size)
        assertEquals(0, engine.requestHistory.size)
        assertTrue(storage.data.values.none { "test-password" in it })

        viewModel.confirmCertificate()
        testScheduler.advanceUntilIdle()

        assertEquals(UiState.Ready, viewModel.uiState)
        // login + board + mode
        assertEquals(3, engine.requestHistory.size)
        assertEquals(testFingerprint, store.read("router.local")?.sha256)
        assertEquals("https://router.local:8080", SessionManager(storage).lastUrl())
    }

    @Test
    fun cancellingConfirmationDoesNotPersistOrLogin() = runSessionTest {
        val storage = SessionStorage()
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = FakeEndpointProbe(
                EndpointProbeResult.CertificateConfirmationRequired(
                    "https://router.local:8080",
                    "router.local",
                    testFingerprint,
                ),
            ),
            apiFactory = factory,
        )

        viewModel.login("http://router.local:8080", "root", "test-password")
        testScheduler.advanceUntilIdle()
        viewModel.cancelCertificateConfirmation()

        assertEquals(UiState.Idle, viewModel.uiState)
        assertNull(CertificateFingerprintStore(storage).read("router.local"))
        assertEquals(0, factory.pins.size)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun changedCertificateBlocksCredentialsAndLogin() = runSessionTest {
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = SessionStorage(),
            endpointProbe = FakeEndpointProbe(
                EndpointProbeResult.CertificateChanged(
                    host = "router.local",
                    expectedFingerprint = testFingerprint,
                    actualFingerprint = "24".repeat(32),
                ),
            ),
            apiFactory = factory,
        )

        viewModel.login("http://router.local:8080", "root", "test-password")
        testScheduler.advanceUntilIdle()

        val error = assertIs<UiState.Error>(viewModel.uiState)
        assertTrue("ha cambiado" in error.message)
        assertEquals(0, factory.pins.size)
        assertEquals(0, engine.requestHistory.size)
    }

    @Test
    fun systemTrustedCertificateLogsInWithoutPrompt() = runSessionTest {
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = SessionStorage(),
            endpointProbe = FakeEndpointProbe(
                EndpointProbeResult.Resolved("https://router.local:8080"),
            ),
            apiFactory = factory,
        )

        viewModel.login("http://router.local:8080", "root", "test-password")
        testScheduler.advanceUntilIdle()

        assertEquals(UiState.Ready, viewModel.uiState)
        assertEquals<List<CertificatePin?>>(listOf(null), factory.pins)
        // login + board + mode: el alta del dispositivo guarda nombre y rol
        assertEquals(3, engine.requestHistory.size)
    }

    @Test
    fun sessionRestoreUsesThePersistedFingerprint() = runSessionTest {
        val storage = SessionStorage()
        storage.save("last.url", "https://router.local:8080")
        storage.save("session.default", "__Secure-netgrip_session=session")
        val pin = CertificateFingerprintStore(storage).save("router.local", testFingerprint)
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = FakeEndpointProbe(EndpointProbeResult.Unreachable),
            apiFactory = factory,
        )

        viewModel.autoRestore()
        testScheduler.advanceUntilIdle()

        assertEquals(UiState.Ready, viewModel.uiState)
        assertEquals<List<CertificatePin?>>(listOf(pin), factory.pins)
        // me + board + mode
        assertEquals(3, engine.requestHistory.size)
    }

    private fun runSessionTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun sessionExpiredClearsTheStoredCookieWithoutCallingTheRouter() = runSessionTest {
        val storage = SessionStorage()
        storage.save("last.url", "https://router.local:8080")
        storage.save("session.default", "__Secure-netgrip_session=session")
        val engine = successfulLoginEngine()
        val factory = RecordingApiFactory(engine)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = FakeEndpointProbe(EndpointProbeResult.Unreachable),
            apiFactory = factory,
        )

        viewModel.autoRestore()
        testScheduler.advanceUntilIdle()
        assertEquals(UiState.Ready, viewModel.uiState)
        assertEquals("https://router.local:8080", viewModel.baseUrl)
        val requestsBefore = engine.requestHistory.size

        viewModel.onSessionExpired()
        testScheduler.advanceUntilIdle()

        assertNull(viewModel.api)
        assertNull(viewModel.baseUrl)
        assertEquals(UiState.Idle, viewModel.uiState)
        assertNull(storage.data["session.default"])
        assertTrue(storage.data.keys.none { it.startsWith("session.") })
        assertEquals("https://router.local:8080", storage.data["last.url"])
        assertEquals(requestsBefore, engine.requestHistory.size)
    }

    private fun TestScope.successfulLoginEngine(): MockEngine {
        val config = MockEngineConfig()
        config.dispatcher = StandardTestDispatcher(testScheduler)
        config.addHandler {
            respond(
                content = "",
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.SetCookie, "__Secure-netgrip_session=session; Path=/"),
            )
        }
        return MockEngine(config)
    }
}
