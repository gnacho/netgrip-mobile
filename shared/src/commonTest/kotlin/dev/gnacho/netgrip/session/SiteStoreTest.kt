package dev.gnacho.netgrip.session

import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.domain.sites.SavedSites
import dev.gnacho.netgrip.domain.sites.SiteRules
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private class MemoryStorage : SecureStorage {
    val data = mutableMapOf<String, String>()
    override suspend fun save(key: String, value: String) { data[key] = value }
    override suspend fun read(key: String): String? = data[key]
    override suspend fun delete(key: String) { data.remove(key) }
}

class SiteStoreTest {

    @Test
    fun sinNadaGuardadoDevuelveListaVacia() = runTest {
        val store = SiteStore(MemoryStorage())
        val sites = store.load(null)
        assertTrue(sites.networks.isEmpty())
        assertEquals(null, sites.currentNetworkId)
    }

    @Test
    fun migraLaSesionUnicaAUnaRedConUnDispositivo() = runTest {
        val storage = MemoryStorage()
        val store = SiteStore(storage)
        val sites = store.load("http://192.168.1.3:8080/")

        val network = assertNotNull(sites.currentNetwork)
        assertEquals(SiteRules.DEFAULT_NETWORK_NAME, network.name)
        val device = assertNotNull(sites.currentDevice)
        assertEquals("http://192.168.1.3:8080", device.url)
        assertEquals(SiteRules.deviceId("http://192.168.1.3:8080"), device.id)
        assertTrue(storage.data.containsKey("sites.v1"))
    }

    @Test
    fun loGuardadoSeVuelveALeerIgual() = runTest {
        val storage = MemoryStorage()
        val store = SiteStore(storage)
        val original = SavedSites(
            networks = listOf(
                SavedNetwork("net-1", "Casa", listOf(SavedDevice("dev-a", "http://a", name = "rt3"))),
                SavedNetwork("net-2", "Oficina"),
            ),
            currentNetworkId = "net-2",
            currentDeviceId = null,
        )
        store.save(original)
        val loaded = store.load(null)
        assertEquals(original, loaded)
    }

    @Test
    fun unJsonRotoNoRompeLaAppYMigraLaUrl() = runTest {
        val storage = MemoryStorage()
        storage.save("sites.v1", "{ esto no es json")
        val sites = SiteStore(storage).load("http://casa.local")
        assertEquals(1, sites.networks.size)
        assertEquals("http://casa.local", sites.currentDevice?.url)
    }
}
