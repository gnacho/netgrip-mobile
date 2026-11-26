package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/wifischedule: estado del horario de cada interfaz. */
@Serializable
data class WifiSchedulesDto(
    val schedules: List<WifiScheduleStateDto> = emptyList(),
)

@Serializable
data class WifiScheduleStateDto(
    val section: String = "",
    val ifname: String = "",
    val ssid: String = "",
    val disabled: Boolean = false,
    @SerialName("off_by_schedule") val offBySchedule: Boolean = false,
    val schedule: WifiScheduleDto? = null,
)

/**
 * Horario semanal de una interfaz. [days] usa la numeracion de Go
 * (0 = domingo). Con [end] anterior a [start] la ventana cruza medianoche y
 * [paused] es el "apagar ahora" manual, que gana sobre la ventana.
 */
@Serializable
data class WifiScheduleDto(
    val section: String = "",
    val enabled: Boolean = false,
    val days: List<Int> = emptyList(),
    val start: String = "",
    val end: String = "",
    val paused: Boolean = false,
)

/** Respuesta de POST /api/wifischedule: estado de todas las interfaces. */
@Serializable
data class WifiScheduleWriteResponse(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
    val error: String? = null,
    val state: WifiSchedulesDto? = null,
)
