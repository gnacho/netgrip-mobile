package dev.gnacho.netgrip.domain.sites

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SiteRulesTest {

    @Test
    fun elIdDelDispositivoSaleDeLaUrlYNoCambia() {
        assertEquals("dev-http-192-168-1-3-8080", SiteRules.deviceId("http://192.168.1.3:8080"))
        assertEquals("dev-http-192-168-1-3-8080", SiteRules.deviceId("  http://192.168.1.3:8080/ "))
        assertEquals(SiteRules.deviceId("https://casa.local"), SiteRules.deviceId("https://casa.local/"))
    }

    @Test
    fun dosRoutersDistintosTienenIdsDistintos() {
        assertFalse(SiteRules.deviceId("http://192.168.1.3:8080") == SiteRules.deviceId("http://192.168.1.4:8080"))
    }

    @Test
    fun laUrlSeRecortaSinTocarEsquemaNiPuerto() {
        assertEquals("https://casa.local:8443", SiteRules.normalizeUrl("  https://casa.local:8443/  "))
        assertEquals("http://192.168.1.3", SiteRules.normalizeUrl("http://192.168.1.3"))
    }

    @Test
    fun laDireccionDelFleetGanaEsquemaSiFalta() {
        assertEquals("http://192.168.1.4:8080", SiteRules.urlFromFleetAddress("192.168.1.4:8080"))
        assertEquals("https://casa.local", SiteRules.urlFromFleetAddress("https://casa.local"))
        assertEquals("http://rt3.lan", SiteRules.urlFromFleetAddress(" http://rt3.lan/ "))
        assertEquals("", SiteRules.urlFromFleetAddress("   "))
    }

    @Test
    fun elIdDeRedSeDesambiguaConSufijo() {
        val existing = listOf(SavedNetwork(id = "net-casa", name = "Casa"))
        assertEquals("net-oficina", SiteRules.networkId("Oficina", existing))
        assertEquals("net-casa-2", SiteRules.networkId("Casa", existing))
        assertEquals("net-casa-3", SiteRules.networkId("Casa", existing + SavedNetwork("net-casa-2", "Casa 2")))
    }

    @Test
    fun anadirUnDispositivoNoDuplicaYReemplazaElExistente() {
        val device = SavedDevice(id = "dev-a", url = "http://a")
        val sites = SavedSites(networks = listOf(SavedNetwork("net-1", "Casa", listOf(device))))
        val updated = sites.withDevice("net-1", device.copy(name = "Router A"))
        assertEquals(1, updated.networks.single().devices.size)
        assertEquals("Router A", updated.networks.single().devices.single().name)

        val second = updated.withDevice("net-1", SavedDevice(id = "dev-b", url = "http://b"))
        assertEquals(2, second.networks.single().devices.size)
    }

    @Test
    fun quitarElDispositivoActualLimpiaLaSeleccion() {
        val sites = SavedSites(
            networks = listOf(
                SavedNetwork(
                    "net-1",
                    "Casa",
                    listOf(SavedDevice("dev-a", "http://a"), SavedDevice("dev-b", "http://b")),
                ),
            ),
            currentNetworkId = "net-1",
            currentDeviceId = "dev-a",
        )
        val updated = sites.withoutDevice("net-1", "dev-a")
        assertEquals(listOf("dev-b"), updated.networks.single().devices.map { it.id })
        assertEquals(null, updated.currentDeviceId)
    }

    @Test
    fun laRedYElDispositivoActualesSeResuelvenConRespaldo() {
        val sites = SavedSites(
            networks = listOf(SavedNetwork("net-1", "Casa", listOf(SavedDevice("dev-a", "http://a")))),
        )
        assertEquals("net-1", sites.currentNetwork?.id)
        assertEquals("dev-a", sites.currentDevice?.id)
        assertTrue(sites.currentDevice?.label == "a")
    }
}
