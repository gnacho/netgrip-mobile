package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.Serializable

/**
 * GET /api/logs: el ring buffer del sistema ya troceado por el panel.
 * `total` es el tamano del buffer completo antes de filtrar.
 */
@Serializable
data class LogsDto(
    val entries: List<LogEntryDto> = emptyList(),
    val total: Int = 0,
    val error: String? = null,
)

@Serializable
data class LogEntryDto(
    val ts: String = "",
    val facility: String = "",
    val level: String = "",
    val tag: String = "",
    val message: String = "",
)

/** GET /api/leds: inventario de solo lectura. */
@Serializable
data class LedsDto(
    val applicable: Boolean = false,
    val leds: List<LedDto> = emptyList(),
)

@Serializable
data class LedDto(
    val section: String = "",
    val name: String = "",
    val sysfs: String = "",
    /** Trigger de OpenWrt: netdev en los LEDs de puerto, heartbeat, ninguno... */
    val trigger: String? = null,
    val dev: String? = null,
    /** -1 cuando el panel no pudo leer el brillo. */
    val brightness: Int = -1,
    /** null cuando el estado no se pudo determinar. */
    val on: Boolean? = null,
)
