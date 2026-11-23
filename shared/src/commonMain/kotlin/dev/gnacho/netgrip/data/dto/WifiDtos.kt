package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/wifi: estado editable de cada interfaz, nunca la clave. */
@Serializable
data class WifiDto(
    val interfaces: List<WifiUiDto> = emptyList(),
)

@Serializable
data class WifiUiDto(
    val section: String = "",
    val radio: String = "",
    val ifname: String = "",
    val band: String = "",
    val ssid: String = "",
    val encryption: String = "",
    @SerialName("has_key") val hasKey: Boolean = false,
    val hidden: Boolean = false,
    val mac: String = "",
    val bssid: String = "",
    val disabled: Boolean = false,
    val clients: List<WifiClientDto> = emptyList(),
)

@Serializable
data class WifiClientDto(
    val mac: String = "",
    val signal: Int? = null,
)

/** GET /api/wireless: array desnudo, una entrada por radio fisica. */
@Serializable
data class WirelessRadioDto(
    val name: String = "",
    val up: Boolean = false,
    val band: String = "",
    val channel: String = "",
    val htmode: String = "",
    val txpower: Int = 0,
    val interfaces: List<WirelessIfaceDto> = emptyList(),
)

@Serializable
data class WirelessIfaceDto(
    val ifname: String = "",
    val ssid: String = "",
    val encryption: String = "",
    val disabled: Boolean = false,
    val hidden: Boolean = false,
    val bssid: String = "",
    val rname: String = "",
    val clients: List<WifiClientDto> = emptyList(),
)
