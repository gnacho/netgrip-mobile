package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST /api/wifi. Los campos nulos se omiten, asi que solo viaja lo que
 * cambia. [key] es de escritura: nunca se lee del router ni se guarda. Con
 * [confirm] el router arma su ventana de auto-reversion y devuelve
 * [WifiWriteResponse.confirm].
 */
@Serializable
data class WifiEditRequest(
    val section: String,
    val sections: List<String>? = null,
    val ssid: String? = null,
    val key: String? = null,
    val encryption: String? = null,
    val hidden: Boolean? = null,
    val disabled: Boolean? = null,
    val confirm: Boolean? = null,
)

/** POST /api/wifi/radio. Con [confirm] se arma la ventana de reversion. */
@Serializable
data class RadioEditRequest(
    val radio: String,
    val channel: String? = null,
    val htmode: String? = null,
    val txpower: Int? = null,
    val confirm: Boolean? = null,
)

/** Respuesta de las dos escrituras: estado releido, rollback y ventana. */
@Serializable
data class WifiWriteResponse(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
    val error: String? = null,
    val state: WifiUiDto? = null,
    val confirm: ConfirmWindowDto? = null,
)

/** La radio devuelve otra forma de estado que la interfaz. */
@Serializable
data class RadioWriteResponse(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
    val error: String? = null,
    val state: WirelessRadioDto? = null,
    val confirm: ConfirmWindowDto? = null,
)

/** Ventana de confirmacion del router: id y caducidad en segundos epoch. */
@Serializable
data class ConfirmWindowDto(
    val id: String = "",
    val expires: Long = 0,
)

/** GET /api/config/confirm. */
@Serializable
data class ConfirmStatusDto(
    val pending: Boolean = false,
    val id: String = "",
    val expires: Long = 0,
)

@Serializable
data class ConfirmIdRequest(val id: String)
