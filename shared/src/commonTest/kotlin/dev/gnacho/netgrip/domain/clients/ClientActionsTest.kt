package dev.gnacho.netgrip.domain.clients

import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.ClientMetaEntryDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClientActionsTest {

    private fun client(
        mac: String = "aa:bb:cc:dd:ee:ff",
        type: String = "wifi5",
        ip: String? = "192.168.8.112",
        reserved: Boolean = false,
        reservable: Boolean = true,
        blocked: Boolean = false,
        blockedOn: List<String> = emptyList(),
        blockable: Boolean = true,
        self: Boolean = false,
    ) = ClientDto(
        mac = mac,
        type = type,
        ip = ip,
        reserved = reserved,
        reservable = reservable,
        blocked = blocked,
        blockedOn = blockedOn,
        blockable = blockable,
        self = self,
    )

    @Test
    fun laMacSeNormalizaYSeRechazanFormasInvalidas() {
        assertEquals("aa:bb:cc:dd:ee:ff", MacAddress.normalize(" AA:BB:CC:DD:EE:FF "))
        assertTrue(MacAddress.isValid("00:11:32:9a:bc:10"))
        assertFalse(MacAddress.isValid("aa:bb:cc:dd:ee"))
        assertFalse(MacAddress.isValid("aa-bb-cc-dd-ee-ff"))
        assertFalse(MacAddress.isValid(""))
        assertNull(MacAddress.normalize("no es una mac"))
    }

    @Test
    fun elNombreSeRecortaA40CaracteresDespuesDeQuitarEspacios() {
        assertEquals("Pixel 8", ClientMetaRules.normalizeName("  Pixel 8  "))
        assertEquals(40, ClientMetaRules.normalizeName("x".repeat(50)).length)
        assertEquals("", ClientMetaRules.normalizeName("   "))
    }

    @Test
    fun elTipoDeDispositivoSoloAceptaClavesDelCatalogo() {
        assertEquals("phone", ClientMetaRules.normalizeDeviceType("PHONE"))
        assertEquals("", ClientMetaRules.normalizeDeviceType("  "))
        assertEquals(DeviceTypes.OTHER, ClientMetaRules.normalizeDeviceType("inventado"))
        assertEquals(21, DeviceTypes.KEYS.size)
        assertEquals(
            listOf(
                "pc", "laptop", "phone", "tablet", "camera", "wearable", "printer", "audio",
                "speaker", "tv", "gaming", "iot", "gateway", "nas", "server", "appliance",
                "ac", "vacuum", "pool", "mower", "other",
            ),
            DeviceTypes.KEYS,
        )
        assertEquals("Televisión", DeviceTypes.label("tv"))
        assertEquals("Sin tipo", DeviceTypes.label(""))
    }

    @Test
    fun lasBandasSalenDelPayloadYNoDelTipoDeConexion() {
        assertEquals(listOf("2g", "5g", "6g"), ClientBlockRules.availableBands(listOf("2g", "5g", "6g", "2g", "8g")))
        assertEquals(listOf("", "2g", "5g"), ClientBlockRules.bandOptions(client(type = "wifi24"), listOf("2g", "5g")))
        assertEquals(listOf(""), ClientBlockRules.bandOptions(client(type = "cable"), listOf("2g", "5g")))
        assertEquals("wifi", ClientBlockRules.requestType(client(type = "wifi5")))
        assertEquals("wifi", ClientBlockRules.requestType(client(type = "wifi24")))
        assertEquals("cable", ClientBlockRules.requestType(client(type = "cable")))
        assertEquals("Todas las bandas", ClientBlockRules.bandLabel(""))
        assertEquals("2,4 GHz", ClientBlockRules.bandLabel("2g"))
        assertEquals("5 GHz", ClientBlockRules.bandLabel("5g"))
        assertEquals("6 GHz", ClientBlockRules.bandLabel("6g"))
    }

    @Test
    fun elPropioDispositivoNuncaSePuedeBloquear() {
        assertFalse(ClientBlockRules.canBlock(client(self = true)))
        assertFalse(ClientBlockRules.canUnblock(client(self = true, blocked = true)))
        assertFalse(ClientBlockRules.canBlock(client(blockable = false)))
        assertTrue(ClientBlockRules.canBlock(client()))
    }

    @Test
    fun elDesbloqueoOfreceLasBandasBloqueadasYTodas() {
        assertEquals(listOf("5g"), ClientBlockRules.unblockOptions(client(blockedOn = listOf("5g"))))
        assertEquals(listOf("2g", "5g", ""), ClientBlockRules.unblockOptions(client(blockedOn = listOf("2g", "5g"))))
        assertEquals(listOf(""), ClientBlockRules.unblockOptions(client(blocked = true)))
        assertTrue(ClientBlockRules.unblockOptions(client()).isEmpty())
        assertEquals("Bloqueado en 5 GHz", ClientBlockRules.stateLabel(client(blockedOn = listOf("5g"))))
        assertEquals("Bloqueado", ClientBlockRules.stateLabel(client(blocked = true)))
        assertEquals("Sin bloquear", ClientBlockRules.stateLabel(client()))
    }

    @Test
    fun laReservaSoloFijaLaIpActualYSeQuitaSiYaExiste() {
        val add = ClientReservationRules.actionFor(client(ip = "192.168.8.50"))
        assertIs<ReservationAction.SetCurrentIp>(add)
        assertEquals("192.168.8.50", add.ip)
        assertEquals(ReservationAction.Remove, ClientReservationRules.actionFor(client(reserved = true)))
        assertEquals(
            ReservationAction.Unavailable,
            ClientReservationRules.actionFor(client(reservable = false)),
        )
        assertEquals(
            ReservationAction.Unavailable,
            ClientReservationRules.actionFor(client(ip = null)),
        )
        assertEquals(
            ReservationAction.Unavailable,
            ClientReservationRules.actionFor(client(ip = "  ")),
        )
        assertEquals(
            ReservationAction.Unavailable,
            ClientReservationRules.actionFor(client(self = true)),
        )
    }

    @Test
    fun laRelecturaCompruebaBloqueoPorBanda() {
        val blocked5g = client(blockedOn = listOf("5g"))
        assertEquals(true, ClientWriteCheck.blockApplied(listOf(blocked5g), "AA:BB:CC:DD:EE:FF", true, "5g"))
        assertEquals(false, ClientWriteCheck.blockApplied(listOf(blocked5g), blocked5g.mac, true, "2g"))
        assertEquals(false, ClientWriteCheck.blockApplied(listOf(blocked5g), blocked5g.mac, true, ""))
        assertEquals(true, ClientWriteCheck.blockApplied(listOf(blocked5g), blocked5g.mac, false, "2g"))
        assertEquals(false, ClientWriteCheck.blockApplied(listOf(blocked5g), blocked5g.mac, false, ""))
        assertNull(ClientWriteCheck.blockApplied(emptyList(), blocked5g.mac, true, ""))
        assertEquals(
            true,
            ClientWriteCheck.blockApplied(listOf(client(blocked = true)), "aa:bb:cc:dd:ee:ff", true, ""),
        )
    }

    @Test
    fun laRelecturaCompruebaLaReserva() {
        assertEquals(true, ClientWriteCheck.reservationApplied(listOf(client(reserved = true)), "aa:bb:cc:dd:ee:ff", true))
        assertEquals(false, ClientWriteCheck.reservationApplied(listOf(client()), "aa:bb:cc:dd:ee:ff", true))
        assertEquals(true, ClientWriteCheck.reservationApplied(listOf(client()), "aa:bb:cc:dd:ee:ff", false))
        assertNull(ClientWriteCheck.reservationApplied(emptyList(), "aa:bb:cc:dd:ee:ff", true))
    }

    @Test
    fun laRespuestaDeMetaConfirmaElCambioYElBorrado() {
        val saved = mapOf("aa:bb:cc:dd:ee:ff" to ClientMetaEntryDto(name = "Pixel", deviceType = "phone"))
        assertTrue(ClientWriteCheck.metaApplied(saved, "AA:BB:CC:DD:EE:FF", "Pixel", "phone"))
        assertFalse(ClientWriteCheck.metaApplied(saved, "aa:bb:cc:dd:ee:ff", "Otro", "phone"))
        assertTrue(ClientWriteCheck.metaApplied(emptyMap(), "aa:bb:cc:dd:ee:ff", "", ""))
        assertFalse(ClientWriteCheck.metaApplied(saved, "aa:bb:cc:dd:ee:ff", "", ""))
        assertTrue(
            ClientWriteCheck.metaApplied(
                mapOf("aa:bb:cc:dd:ee:ff" to ClientMetaEntryDto()),
                "aa:bb:cc:dd:ee:ff",
                "",
                "",
            ),
        )
    }

    @Test
    fun losMensajesDistinguenExitoDeEstadoSinCambios() {
        assertEquals("Cliente bloqueado en todas las bandas", ClientActionMessages.blocked(""))
        assertEquals("Cliente bloqueado en 5 GHz", ClientActionMessages.blocked("5g"))
        assertEquals("Cliente desbloqueado en todas las bandas", ClientActionMessages.unblocked(""))
        assertEquals("IP 192.168.8.50 fijada para este cliente", ClientActionMessages.reserved("192.168.8.50"))
        assertTrue(ClientActionMessages.reservationUnchanged().contains("no aparece aplicada"))
        assertTrue(ClientActionMessages.stateUnchanged().contains("no ha cambiado"))
        assertTrue(ClientActionMessages.rolledBack().contains("revirtió"))
        assertTrue(ClientActionMessages.wolSent().contains("enviado"))
    }

    @Test
    fun elFormularioPrecargaElNombreDelClienteSiNoHayOverride() {
        val target = client(mac = "9c:b6:d0:12:ab:77").copy(name = "phone", deviceType = "phone")
        assertEquals("phone", ClientMetaRules.effectiveName(emptyMap(), target))
        assertEquals("phone", ClientMetaRules.effectiveDeviceType(emptyMap(), target))
    }

    @Test
    fun elOverrideGuardadoGanaSobreLoQueAnunciaElCliente() {
        val target = client(mac = "9c:b6:d0:12:ab:77").copy(name = "phone", deviceType = "phone")
        val meta = mapOf(
            "9c:b6:d0:12:ab:77" to ClientMetaEntryDto(name = "Móvil de casa", deviceType = "tablet"),
        )
        assertEquals("Móvil de casa", ClientMetaRules.effectiveName(meta, target))
        assertEquals("tablet", ClientMetaRules.effectiveDeviceType(meta, target))
    }

    @Test
    fun unOverrideParcialNoBorraElOtroCampo() {
        val target = client(mac = "9c:b6:d0:12:ab:77").copy(name = "phone", deviceType = "phone")
        val meta = mapOf("9c:b6:d0:12:ab:77" to ClientMetaEntryDto(name = "Solo nombre", deviceType = ""))
        assertEquals("Solo nombre", ClientMetaRules.effectiveName(meta, target))
        assertEquals("phone", ClientMetaRules.effectiveDeviceType(meta, target))
    }
}
