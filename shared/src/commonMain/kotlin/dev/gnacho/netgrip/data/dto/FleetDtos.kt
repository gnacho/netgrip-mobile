package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/fleet: routers que el panel conoce para gestionarlos juntos. */
@Serializable
data class FleetDto(
    val nodes: List<FleetNodeDto> = emptyList(),
)

@Serializable
data class FleetNodeDto(
    val id: String = "",
    val name: String = "",
    val address: String = "",
    val reachable: Boolean = false,
    @SerialName("current_version") val currentVersion: String = "",
    @SerialName("latest_version") val latestVersion: String = "",
    @SerialName("update_available") val updateAvailable: Boolean = false,
    val error: String? = null,
)
