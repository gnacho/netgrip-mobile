package dev.gnacho.netgrip.domain.wifi

import dev.gnacho.netgrip.data.dto.WifiUiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto

/** Datos fisicos de la radio que sirve un SSID. */
data class WifiRadioInfo(
    val name: String,
    val up: Boolean,
    val band: String,
    val channel: String,
    val htmode: String,
    val txPower: Int,
)

/** Un SSID con todas sus interfaces y las radios que lo emiten. */
data class WifiGroup(
    val ssid: String,
    val bands: List<String>,
    val encryptions: List<String>,
    val hidden: Boolean,
    val disabled: Boolean,
    val clientCount: Int,
    val radios: List<WifiRadioInfo>,
)

/** Agrupa /api/wifi por SSID y cruza /api/wireless por nombre de radio. */
object WifiGrouping {

    fun build(
        interfaces: List<WifiUiDto>,
        radios: List<WirelessRadioDto> = emptyList(),
    ): List<WifiGroup> {
        val radiosByName = radios.associateBy { it.name }
        return interfaces.groupBy { it.ssid }.map { (ssid, group) ->
            WifiGroup(
                ssid = ssid,
                bands = group.map { it.band }.filter { it.isNotBlank() }.distinct(),
                encryptions = group.map { it.encryption }.filter { it.isNotBlank() }.distinct(),
                hidden = group.any { it.hidden },
                disabled = group.all { it.disabled },
                clientCount = group.flatMap { iface -> iface.clients.map { it.mac } }.distinct().size,
                radios = group.mapNotNull { radiosByName[it.radio] }
                    .distinctBy { it.name }
                    .map { radio ->
                        WifiRadioInfo(
                            name = radio.name,
                            up = radio.up,
                            band = radio.band,
                            channel = radio.channel,
                            htmode = radio.htmode,
                            txPower = radio.txpower,
                        )
                    },
            )
        }
    }

    /** Etiqueta legible de la banda tal como la publica ubus ("2g", "5g"...). */
    fun bandLabel(band: String): String = when (band.lowercase()) {
        "2g", "2.4g", "2.4ghz" -> "2,4 GHz"
        "5g", "5ghz" -> "5 GHz"
        "6g", "6ghz" -> "6 GHz"
        "60g", "60ghz" -> "60 GHz"
        else -> band
    }

    /** Nombre visible de un SSID: los ocultos no tienen uno real. */
    fun ssidLabel(ssid: String): String = ssid.ifBlank { "SSID oculto" }
}
