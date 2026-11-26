package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/guestwifi: estado de la red de invitados. */
@Serializable
data class GuestProbeDto(
    val gateway: Boolean = false,
    val active: Boolean = false,
    val ssid: String = "",
    val subnet: String = "",
    val isolate: Boolean = false,
    val ifaces: List<String> = emptyList(),
    val clients: Int = 0,
    @SerialName("gl_conflict") val glConflict: Boolean = false,
)

/**
 * POST /api/guestwifi. [subnet] vacio deja la que ya hay; [isolate] nulo no
 * toca el aislamiento.
 */
@Serializable
data class GuestConfigRequest(
    val enabled: Boolean,
    val ssid: String,
    val key: String,
    val band: String,
    val subnet: String = "",
    val isolate: Boolean? = null,
)

/** GET /api/iotwifi: estado del SSID de dispositivos IoT. */
@Serializable
data class IoTProbeDto(
    val active: Boolean = false,
    val ssid: String = "",
    val band: String = "",
    val isolated: Boolean = false,
    val ifaces: List<String> = emptyList(),
    val clients: Int = 0,
)

/** POST /api/iotwifi. */
@Serializable
data class IoTConfigRequest(
    val enabled: Boolean,
    val ssid: String,
    val key: String,
    val band: String,
)

/** Envoltura de las dos escrituras: estado releido, rollback y error. */
@Serializable
data class NetworkWriteResponse<T>(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
    val error: String? = null,
    val state: T? = null,
)
