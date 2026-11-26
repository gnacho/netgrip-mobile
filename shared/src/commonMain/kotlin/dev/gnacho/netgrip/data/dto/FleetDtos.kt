package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/fleet: routers que el panel conoce para gestionarlos juntos. */
@Serializable
data class FleetDto(
    val nodes: List<FleetNodeDto> = emptyList(),
)

/**
 * Un router del fleet. `GET /api/fleet` devuelve el ultimo estado conocido
 * (el que dejo el ultimo check) y `POST /api/fleet/check` devuelve uno fresco,
 * con la misma forma. Todo viene del panel: la app no sondea al router desde
 * el telefono, porque no tiene su contrasena.
 */
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

/** POST /api/fleet/check. */
@Serializable
data class FleetIdRequest(val id: String)

/** GET /api/fleet/discovered: routers que se anuncian en la LAN y aun no estan. */
@Serializable
data class FleetPeersDto(
    val peers: List<DiscoveredPeerDto> = emptyList(),
)

@Serializable
data class DiscoveredPeerDto(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val address: String = "",
    val port: Int = 0,
    /** RFC3339 del ultimo anuncio. */
    @SerialName("seen_at") val seenAt: String = "",
)

/**
 * POST /api/fleet/adopt. El panel comprueba que puede entrar en el peer antes
 * de guardarlo, asi que la contrasena es la de administracion de ESE router.
 */
@Serializable
data class FleetAdoptRequest(
    val id: String,
    val name: String,
    val address: String,
    val password: String,
)

/**
 * GET y POST /api/fleet/discovery-config. Sin valor por defecto: al apagar el
 * descubrimiento hay que enviar `enabled: false` explícito, no omitirlo.
 */
@Serializable
data class FleetDiscoveryConfigDto(
    val enabled: Boolean,
)

/** Respuestas de estado simples: {"status":"updating"} o {"status":"adopted"}. */
@Serializable
data class FleetStatusResponse(
    val status: String = "",
)
