package dev.gnacho.netgrip.domain.clients

import dev.gnacho.netgrip.data.dto.ClientDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientFilterTest {

    private val clients = listOf(
        ClientDto(name = "Pixel 8 Pro (Marta)", ip = "192.168.8.112", mac = "9C:B6:D0:12:AB:77", type = "wifi5"),
        ClientDto(name = "NAS", ip = "192.168.8.10", mac = "00:11:32:9A:BC:10", type = "cable"),
        ClientDto(name = "Echo Dot Cocina", ip = "192.168.8.172", mac = "B4:E6:2D:90:14:07", type = "wifi24"),
    )

    @Test
    fun unaBusquedaVaciaDevuelveTodos() {
        assertEquals(3, ClientFilter.filter(clients, "   ").size)
    }

    @Test
    fun buscaPorNombreSinDistinguirMayusculas() {
        val found = ClientFilter.filter(clients, "pixel")
        assertEquals(listOf("9C:B6:D0:12:AB:77"), found.map { it.mac })
    }

    @Test
    fun buscaPorIpParcial() {
        val found = ClientFilter.filter(clients, "192.168.8.17")
        assertEquals(listOf("B4:E6:2D:90:14:07"), found.map { it.mac })
    }

    @Test
    fun buscaPorMacParcial() {
        val found = ClientFilter.filter(clients, "00:11:32")
        assertEquals(listOf("NAS"), found.map { it.name })
    }

    @Test
    fun sinCoincidenciasDevuelveListaVacia() {
        assertTrue(ClientFilter.filter(clients, "impresora").isEmpty())
    }

    @Test
    fun lasEtiquetasTraducenElTipoDeConexion() {
        assertEquals("WiFi 2,4 GHz", ClientLabels.type("wifi24"))
        assertEquals("WiFi 5 GHz", ClientLabels.type("wifi5"))
        assertEquals("Cable", ClientLabels.type("cable"))
        assertEquals("Desconocido", ClientLabels.type(""))
    }

    @Test
    fun losAvisosDelClienteSalenEnOrden() {
        val client = ClientDto(
            name = "Tablet",
            mac = "AA:BB:CC:DD:EE:FF",
            self = true,
            blocked = true,
            parentalBlocked = true,
            quotaExceeded = true,
            reserved = true,
            quotaLimit = 1000,
        )
        assertEquals(
            listOf("Este dispositivo", "Bloqueado", "Bloqueo parental", "Cuota agotada", "IP reservada"),
            ClientLabels.flags(client),
        )
    }

    @Test
    fun sinAvisosNiOrigenesNoInventaEtiquetas() {
        val client = ClientDto(name = "TV", mac = "AA:BB:CC:DD:EE:FF", type = "cable")
        assertTrue(ClientLabels.flags(client).isEmpty())
        assertNull(ClientLabels.leaseSource(client))
        assertNull(ClientLabels.ipSource(client))
    }

    @Test
    fun traduceLosOrigenesConocidos() {
        val client = ClientDto(leaseSource = "gateway", ipSource = "reservation")
        assertEquals("Concesión del gateway", ClientLabels.leaseSource(client))
        assertEquals("Reserva DHCP", ClientLabels.ipSource(client))
    }
}
