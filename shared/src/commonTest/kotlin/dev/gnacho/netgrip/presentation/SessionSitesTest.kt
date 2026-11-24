package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.core.network.CertificateFingerprintStore
import dev.gnacho.netgrip.core.network.CertificatePin
import dev.gnacho.netgrip.core.network.EndpointProbe
import dev.gnacho.netgrip.core.network.EndpointProbeResult
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.domain.sites.SavedSites
import dev.gnacho.netgrip.domain.sites.SiteRules
import dev.gnacho.netgrip.session.SiteStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
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
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MapStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

/** Resuelve siempre a la URL pedida, como un router accesible. */
private class PassThroughProbe : EndpointProbe {
    override suspend fun probe(baseUrl: String): EndpointProbeResult {
        val clean = baseUrl.trim().trimEnd('/')
        val url = if (clean.startsWith("http://") || clean.startsWith("https://")) clean else "http://$clean"
        return EndpointProbeResult.Resolved(url)
    }
}

/** Pide confirmacion de huella hasta que se guarda. */
private class StoredPinProbe(
    private val storage: SecureStorage,
    private val host: String,
    private val fingerprint: String,
) : EndpointProbe {
    override suspend fun probe(baseUrl: String): EndpointProbeResult =
        if (CertificateFingerprintStore(storage).read(host) == null) {
            EndpointProbeResult.CertificateConfirmationRequired(baseUrl, host, fingerprint)
        } else {
            EndpointProbeResult.Resolved(baseUrl)
        }
}

private class Factory(private val engine: MockEngine) : NetGripApiFactory {
    val baseUrls = mutableListOf<String>()
    val pins = mutableListOf<CertificatePin?>()
    override fun create(baseUrl: String, certificatePin: CertificatePin?): NetGripApi {
        baseUrls += baseUrl
        pins += certificatePin
        return NetGripApi(engine, baseUrl)
    }
}

private class RouterServer(var hostname: String = "casa") {
    var fleetJson = """{"nodes":[{"id":"ap","name":"AP salón","address":"192.168.1.4:8080"}]}"""
    val calls = mutableListOf<String>()

    fun engine(scope: TestScope): MockEngine {
        val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        val config = MockEngineConfig()
        config.dispatcher = StandardTestDispatcher(scope.testScheduler)
        config.addHandler { request ->
            val path = request.url.encodedPath
            calls += "${request.method.value} $path"
            when (path) {
                "/api/login" -> respond(
                    content = "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.SetCookie, "__Secure-netgrip_session=session; Path=/"),
                )
                "/api/me" -> respond("", HttpStatusCode.NoContent)
                "/api/board" -> respond("""{"hostname":"$hostname","model":"AX6"}""", HttpStatusCode.OK, json)
                "/api/mode" -> respond(
                    """{"mode":"router","role":"router","has_wifi":true,"port_count":5}""",
                    HttpStatusCode.OK,
                    json,
                )
                "/api/fleet" -> respond(fleetJson, HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
        return MockEngine(config)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSitesTest {

    private fun runSitesTest(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            block()
        } finally {
            Dispatchers.resetMain()
        }
    }

    private suspend fun seed(storage: SecureStorage, sites: SavedSites) {
        SiteStore(storage).save(sites)
    }

    @Test
    fun elLoginCreaLaRedYElDispositivoConSuNombre() = runSitesTest {
        val storage = MapStorage()
        val server = RouterServer(hostname = "rt3")
        val engine = server.engine(this)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(engine),
        )

        viewModel.login("http://192.168.1.3:8080", "root", "clave")
        advanceUntilIdle()

        assertEquals(UiState.Ready, viewModel.uiState)
        val network = assertNotNull(viewModel.sites.currentNetwork)
        assertEquals(SiteRules.DEFAULT_NETWORK_NAME, network.name)
        val device = assertNotNull(viewModel.currentDevice)
        assertEquals("rt3", device.name)
        assertEquals("http://192.168.1.3:8080", device.url)
        assertNotNull(storage.data["session.device.${device.id}"])
    }

    @Test
    fun alArrancarVuelveAlDispositivoGuardado() = runSitesTest {
        val storage = MapStorage()
        val device = SavedDevice(id = SiteRules.deviceId("http://192.168.1.3:8080"), url = "http://192.168.1.3:8080")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(device))),
                currentNetworkId = "net-casa",
                currentDeviceId = device.id,
            ),
        )
        storage.save("session.device.${device.id}", "__Secure-netgrip_session=session")

        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()

        assertEquals(UiState.Ready, viewModel.uiState)
        assertEquals("http://192.168.1.3:8080", viewModel.baseUrl)
    }

    @Test
    fun cadaDispositivoTieneSuPropiaCookie() = runSitesTest {
        val storage = MapStorage()
        val a = SavedDevice(id = "dev-a", url = "http://192.168.1.3:8080")
        val b = SavedDevice(id = "dev-b", url = "http://192.168.1.4:8080")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(a, b))),
                currentNetworkId = "net-casa",
                currentDeviceId = a.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        storage.save("session.device.dev-b", "netgrip_http_session=cookie-b")
        val engine = RouterServer().engine(this)
        val viewModel = SessionViewModel(storage, endpointProbe = PassThroughProbe(), apiFactory = Factory(engine))

        viewModel.autoRestore()
        advanceUntilIdle()
        assertEquals("http://192.168.1.3:8080", viewModel.baseUrl)
        assertEquals("netgrip_http_session=cookie-a", viewModel.api?.sessionCookie)

        val changed = viewModel.trySwitchDevice("dev-b")
        advanceUntilIdle()

        assertTrue(changed)
        assertEquals("http://192.168.1.4:8080", viewModel.baseUrl)
        assertEquals("netgrip_http_session=cookie-b", viewModel.api?.sessionCookie)
        assertEquals("dev-b", viewModel.sites.currentDeviceId)
    }

    @Test
    fun conectarUnRouterNuevoLoAnadeALaRedActual() = runSitesTest {
        val storage = MapStorage()
        val a = SavedDevice(id = "dev-a", url = "http://192.168.1.3:8080", name = "rt3")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(a))),
                currentNetworkId = "net-casa",
                currentDeviceId = a.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val engine = RouterServer(hostname = "rt4").engine(this)
        val viewModel = SessionViewModel(storage, endpointProbe = PassThroughProbe(), apiFactory = Factory(engine))
        viewModel.autoRestore()
        advanceUntilIdle()

        val result = viewModel.connectDevice("192.168.1.4:8080", "root", "clave")
        advanceUntilIdle()

        assertIs<ConnectResult.Ok>(result)
        val network = assertNotNull(viewModel.sites.currentNetwork)
        assertEquals("Casa", network.name)
        assertEquals(2, network.devices.size)
        assertEquals("rt4", viewModel.currentDevice?.name)
        assertEquals("http://192.168.1.4:8080", viewModel.baseUrl)
    }

    @Test
    fun elCertificadoNuevoSePideAntesDeEnviarCredenciales() = runSitesTest {
        val storage = MapStorage()
        val server = RouterServer()
        val engine = server.engine(this)
        val viewModel = SessionViewModel(
            storage = storage,
            endpointProbe = StoredPinProbe(storage, "router.local", "42".repeat(32)),
            apiFactory = Factory(engine),
        )

        val first = viewModel.connectDevice("https://router.local:8080", "root", "clave")
        assertIs<ConnectResult.Certificate>(first)
        assertTrue(server.calls.none { it.contains("/api/login") })

        val second = viewModel.connectDevice(
            "https://router.local:8080",
            "root",
            "clave",
            trustHost = first.host,
            trustFingerprint = first.fingerprint,
        )
        advanceUntilIdle()

        assertIs<ConnectResult.Ok>(second)
        assertEquals("42".repeat(32), CertificateFingerprintStore(storage).read("router.local")?.sha256)
        assertTrue(server.calls.any { it == "POST /api/login" })
    }

    @Test
    fun lasRedesSeCreanRenombranYBorran() = runSitesTest {
        val storage = MapStorage()
        val device = SavedDevice(id = "dev-a", url = "http://a")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(device))),
                currentNetworkId = "net-casa",
                currentDeviceId = device.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()

        viewModel.addNetwork("Oficina")
        advanceUntilIdle()
        assertEquals(2, viewModel.sites.networks.size)
        assertEquals("Oficina", viewModel.sites.currentNetwork?.name)

        val officeId = viewModel.sites.currentNetworkId!!
        viewModel.renameNetwork(officeId, "Oficina centro")
        assertEquals("Oficina centro", viewModel.sites.network(officeId)?.name)

        viewModel.deleteNetwork(officeId)
        advanceUntilIdle()
        assertEquals(1, viewModel.sites.networks.size)
        assertEquals("Casa", viewModel.sites.currentNetwork?.name)
    }

    @Test
    fun unaRedSinDispositivosPideLogin() = runSitesTest {
        val storage = MapStorage()
        val device = SavedDevice(id = "dev-a", url = "http://a")
        seed(
            storage,
            SavedSites(
                networks = listOf(
                    SavedNetwork("net-casa", "Casa", listOf(device)),
                    SavedNetwork("net-ofi", "Oficina"),
                ),
                currentNetworkId = "net-casa",
                currentDeviceId = device.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()
        assertEquals(UiState.Ready, viewModel.uiState)

        viewModel.selectNetwork("net-ofi")
        advanceUntilIdle()

        assertNull(viewModel.api)
        assertNull(viewModel.pendingLoginUrl)
        assertEquals(UiState.Idle, viewModel.uiState)
    }

    @Test
    fun importarElFleetAnadeLosRoutersYNoDuplicaLosRepetidos() = runSitesTest {
        val storage = MapStorage()
        val device = SavedDevice(id = "dev-a", url = "http://192.168.1.3:8080", name = "rt3")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(device))),
                currentNetworkId = "net-casa",
                currentDeviceId = device.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()

        viewModel.importFleet()
        advanceUntilIdle()

        val devices = viewModel.sites.currentNetwork?.devices.orEmpty()
        assertEquals(2, devices.size)
        assertTrue(devices.any { it.name == "AP salón" && it.url == "http://192.168.1.4:8080" })

        viewModel.importFleet()
        advanceUntilIdle()
        assertEquals(2, viewModel.sites.currentNetwork?.devices?.size)
        assertTrue(viewModel.siteMessage?.contains("No hay routers nuevos") == true)
    }

    @Test
    fun quitarUnDispositivoQueNoEsElActualNoCambiaLaSesion() = runSitesTest {
        val storage = MapStorage()
        val a = SavedDevice(id = "dev-a", url = "http://a")
        val b = SavedDevice(id = "dev-b", url = "http://b")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(a, b))),
                currentNetworkId = "net-casa",
                currentDeviceId = a.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()

        viewModel.removeDevice("dev-b")
        advanceUntilIdle()

        assertEquals(listOf("dev-a"), viewModel.sites.currentNetwork?.devices?.map { it.id })
        assertEquals("http://a", viewModel.baseUrl)
        assertEquals(UiState.Ready, viewModel.uiState)
    }

    @Test
    fun elCierreDeSesionBorraLaCookieDelDispositivo() = runSitesTest {
        val storage = MapStorage()
        val a = SavedDevice(id = "dev-a", url = "http://a")
        seed(
            storage,
            SavedSites(
                networks = listOf(SavedNetwork("net-casa", "Casa", listOf(a))),
                currentNetworkId = "net-casa",
                currentDeviceId = a.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.autoRestore()
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertNull(viewModel.api)
        assertNull(storage.data["session.device.dev-a"])
        assertEquals("http://a", viewModel.pendingLoginUrl)
        assertFalse(viewModel.loggedIn)
        assertEquals(1, viewModel.sites.networks.single().devices.size)
    }

    @Test
    fun elJsonGuardadoSobreviveAlCambioDeRed() = runSitesTest {
        val storage = MapStorage()
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )
        viewModel.addNetwork("Casa")
        advanceUntilIdle()
        val stored = Json.decodeFromString(SavedSites.serializer(), storage.data.getValue("sites.v1"))
        assertEquals("Casa", stored.networks.single().name)
        assertEquals(stored.networks.single().id, stored.currentNetworkId)
    }

    @Test
    fun conectarDesdeElLoginCambiaTambienLaRedActual() = runSitesTest {
        val storage = MapStorage()
        val device = SavedDevice(id = "dev-a", url = "http://192.168.1.3:8080")
        seed(
            storage,
            SavedSites(
                networks = listOf(
                    SavedNetwork("net-casa", "Casa", listOf(device)),
                    SavedNetwork("net-ofi", "Oficina"),
                ),
                currentNetworkId = "net-ofi",
                currentDeviceId = null,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val viewModel = SessionViewModel(
            storage,
            endpointProbe = PassThroughProbe(),
            apiFactory = Factory(RouterServer().engine(this)),
        )

        val changed = viewModel.trySwitchDevice("dev-a")
        advanceUntilIdle()

        assertTrue(changed)
        assertEquals("net-casa", viewModel.sites.currentNetworkId)
        assertEquals("dev-a", viewModel.sites.currentDeviceId)
        assertEquals("Casa", viewModel.currentNetwork?.name)
    }

    @Test
    fun anadirDesdeUnaRedLaGuardaEnEsaRedYNoEnLaActual() = runSitesTest {
        val storage = MapStorage()
        val casa = SavedDevice(id = "dev-a", url = "http://192.168.1.3:8080", name = "casa")
        seed(
            storage,
            SavedSites(
                networks = listOf(
                    SavedNetwork("net-casa", "Casa", listOf(casa)),
                    SavedNetwork("net-ofi", "Oficina"),
                ),
                currentNetworkId = "net-casa",
                currentDeviceId = casa.id,
            ),
        )
        storage.save("session.device.dev-a", "netgrip_http_session=cookie-a")
        val engine = RouterServer(hostname = "ofi").engine(this)
        val viewModel = SessionViewModel(storage, endpointProbe = PassThroughProbe(), apiFactory = Factory(engine))
        viewModel.autoRestore()
        advanceUntilIdle()

        val result = viewModel.connectDevice(
            url = "http://192.168.1.9:8080",
            username = "root",
            password = "clave",
            networkId = "net-ofi",
        )
        advanceUntilIdle()

        assertIs<ConnectResult.Ok>(result)
        assertEquals("net-ofi", viewModel.sites.currentNetworkId)
        assertEquals(1, viewModel.sites.network("net-ofi")?.devices?.size)
        assertEquals("http://192.168.1.9:8080", viewModel.sites.network("net-ofi")?.devices?.single()?.url)
        assertEquals(1, viewModel.sites.network("net-casa")?.devices?.size)
    }
}
