package dev.gnacho.netgrip.domain.ports

import dev.gnacho.netgrip.data.dto.EthDeviceDto
import dev.gnacho.netgrip.data.dto.EthPortDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PortsInsightTest {

    @Test
    fun unPuertoActivoPorDebajoDeUnGigaEstaDegradado() {
        assertTrue(PortsInsight.isDegradedLink(EthPortDto(name = "lan4", up = true, speedMbps = 100)))
    }

    @Test
    fun unGigaCompletoNoEstaDegradado() {
        assertFalse(PortsInsight.isDegradedLink(EthPortDto(name = "lan1", up = true, speedMbps = 1000)))
    }

    @Test
    fun unPuertoCaidoNoEstaDegradado() {
        assertFalse(PortsInsight.isDegradedLink(EthPortDto(name = "lan3", up = false, speedMbps = 0)))
        assertFalse(PortsInsight.isDegradedLink(EthPortDto(name = "lan3", up = false, speedMbps = 100)))
    }

    @Test
    fun cuentaLosPuertosActivos() {
        val ports = listOf(
            EthPortDto(name = "wan", wan = true, up = true, speedMbps = 1000),
            EthPortDto(name = "lan1", up = true, speedMbps = 1000),
            EthPortDto(name = "lan2", up = false, speedMbps = 0),
        )
        assertEquals(2, PortsInsight.activeCount(ports))
        assertEquals("wan", PortsInsight.wanPort(ports)?.name)
    }

    @Test
    fun elDispositivoMuestraNombreOMac() {
        assertEquals("NAS", PortsInsight.deviceLabel(EthDeviceDto(mac = "00:11:32:9A:BC:10", name = "NAS")))
        assertEquals(
            "00:11:32:9A:BC:10",
            PortsInsight.deviceLabel(EthDeviceDto(mac = "00:11:32:9A:BC:10", name = "  ")),
        )
    }
}
