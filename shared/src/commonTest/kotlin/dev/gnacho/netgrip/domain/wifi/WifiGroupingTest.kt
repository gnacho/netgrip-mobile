package dev.gnacho.netgrip.domain.wifi

import dev.gnacho.netgrip.data.dto.WifiClientDto
import dev.gnacho.netgrip.data.dto.WifiUiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WifiGroupingTest {

    private fun iface(
        ssid: String,
        radio: String,
        band: String,
        clients: List<WifiClientDto> = emptyList(),
        hidden: Boolean = false,
        disabled: Boolean = false,
        encryption: String = "sae-mixed",
    ) = WifiUiDto(
        section = "wifinet-$radio-$band",
        radio = radio,
        ifname = "phy-$radio",
        band = band,
        ssid = ssid,
        encryption = encryption,
        hasKey = true,
        hidden = hidden,
        disabled = disabled,
        clients = clients,
    )

    @Test
    fun agrupaElMismoSsidConSusBandas() {
        val groups = WifiGrouping.build(
            listOf(
                iface("CasaGarcia", "radio0", "2g"),
                iface("CasaGarcia", "radio1", "5g"),
                iface("CasaGarcia-Invitados", "radio0", "2g"),
            ),
        )
        assertEquals(2, groups.size)
        val main = groups.first { it.ssid == "CasaGarcia" }
        assertEquals(listOf("2g", "5g"), main.bands)
        assertEquals(listOf("sae-mixed"), main.encryptions)
    }

    @Test
    fun cuentaClientesDistintosEntreBandas() {
        val repeated = WifiClientDto(mac = "9C:B6:D0:12:AB:77", signal = -52)
        val groups = WifiGrouping.build(
            listOf(
                iface("CasaGarcia", "radio0", "2g", clients = listOf(repeated)),
                iface("CasaGarcia", "radio1", "5g", clients = listOf(repeated)),
            ),
        )
        assertEquals(1, groups.single().clientCount)
    }

    @Test
    fun marcaOcultaYDesactivada() {
        val hidden = WifiGrouping.build(listOf(iface("Oculta", "radio0", "2g", hidden = true))).single()
        assertTrue(hidden.hidden)
        assertFalse(hidden.disabled)

        val disabled = WifiGrouping.build(
            listOf(
                iface("Apagada", "radio0", "2g", disabled = true),
                iface("Apagada", "radio1", "5g", disabled = true),
            ),
        ).single()
        assertTrue(disabled.disabled)
    }

    @Test
    fun cruzaLaRadioPorNombreConCanalYHtmode() {
        val radios = listOf(
            WirelessRadioDto(name = "radio0", up = true, band = "2g", channel = "6", htmode = "HE40", txpower = 20),
            WirelessRadioDto(name = "radio1", up = false, band = "5g", channel = "44", htmode = "HE80", txpower = 23),
        )
        val groups = WifiGrouping.build(
            listOf(iface("CasaGarcia", "radio0", "2g"), iface("CasaGarcia", "radio1", "5g")),
            radios,
        )
        val group = groups.single()
        assertEquals(listOf("radio0", "radio1"), group.radios.map { it.name })
        assertEquals("6", group.radios[0].channel)
        assertEquals("HE40", group.radios[0].htmode)
        assertEquals(20, group.radios[0].txPower)
        assertFalse(group.radios[1].up)
    }

    @Test
    fun sinRadioConocidaNoInventaDatos() {
        val group = WifiGrouping.build(listOf(iface("CasaGarcia", "radio9", "2g"))).single()
        assertTrue(group.radios.isEmpty())
    }

    @Test
    fun unSsidVacioSeEtiquetaComoOculto() {
        assertEquals("SSID oculto", WifiGrouping.ssidLabel(""))
        assertEquals("CasaGarcia", WifiGrouping.ssidLabel("CasaGarcia"))
        assertTrue(WifiGrouping.build(listOf(iface("", "radio0", "2g", hidden = true))).single().ssid.isEmpty())
    }

    @Test
    fun traduceLasBandasDeUbus() {
        assertEquals("2,4 GHz", WifiGrouping.bandLabel("2g"))
        assertEquals("5 GHz", WifiGrouping.bandLabel("5g"))
        assertEquals("6 GHz", WifiGrouping.bandLabel("6g"))
        assertEquals("raro", WifiGrouping.bandLabel("raro"))
    }
}
