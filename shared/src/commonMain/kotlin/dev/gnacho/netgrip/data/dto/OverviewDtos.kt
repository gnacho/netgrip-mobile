package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Board del router (GET /api/board). El handler reenvia tal cual la salida de
 * `ubus call system board`, asi que llegan claves de mas (board_name, system,
 * target): se ignoran.
 */
@Serializable
data class BoardDto(
    val hostname: String? = null,
    val model: String? = null,
    val kernel: String? = null,
    val release: ReleaseDto? = null,
)

@Serializable
data class ReleaseDto(
    val distribution: String? = null,
    val version: String? = null,
    val revision: String? = null,
)

/** GET /api/system: memoria en bytes, root en KiB (ubus system info). */
@Serializable
data class SystemDto(
    val uptime: Long = 0,
    val load: List<Double> = emptyList(),
    val memory: MemoryDto = MemoryDto(),
    val root: RootDto = RootDto(),
)

@Serializable
data class MemoryDto(
    val total: Long = 0,
    val free: Long = 0,
    val available: Long = 0,
    val cached: Long = 0,
    val buffered: Long = 0,
)

@Serializable
data class RootDto(
    val total: Long = 0,
    val free: Long = 0,
)

/** GET /api/wan. */
@Serializable
data class WanDto(
    val present: Boolean = false,
    val up: Boolean = false,
    val uptime: Long = 0,
    val ipv4: List<String> = emptyList(),
    val gateway: String? = null,
    val dns: List<String> = emptyList(),
)

/**
 * GET /api/mode. `role` puede faltar en firmwares antiguos: el rol efectivo
 * se deriva de `mode` con la misma regla simple que usa el panel web.
 */
@Serializable
data class ModeDto(
    val role: String? = null,
    val mode: String = "",
    @SerialName("has_wifi") val hasWifi: Boolean = false,
    @SerialName("port_count") val portCount: Int = 0,
    @SerialName("dnsmasq_on") val dnsmasqOn: Boolean? = null,
    @SerialName("firewall_on") val firewallOn: Boolean? = null,
) {
    val effectiveRole: String
        get() = role?.takeIf { it.isNotBlank() } ?: if (mode == "ap") "ap" else "router"
}
