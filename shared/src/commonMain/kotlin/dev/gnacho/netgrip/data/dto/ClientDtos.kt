package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/clients. */
@Serializable
data class ClientsDto(
    val clients: List<ClientDto> = emptyList(),
    val bands: List<String> = emptyList(),
    val ts: Long = 0,
)

/**
 * Un cliente de la red. `type` es wifi24 | wifi5 | cable. Los contadores van
 * en bytes. Los campos opcionales llegan con omitempty en el router: ausentes
 * y 0 significan lo mismo aqui.
 */
@Serializable
data class ClientDto(
    val name: String = "",
    val ip: String? = null,
    val mac: String = "",
    val type: String = "",
    @SerialName("device_type") val deviceType: String? = null,
    val iface: String? = null,
    val signal: Int? = null,
    @SerialName("rx_bytes") val rxBytes: Long = 0,
    @SerialName("tx_bytes") val txBytes: Long = 0,
    val self: Boolean = false,
    val reserved: Boolean = false,
    val reservable: Boolean = false,
    val blocked: Boolean = false,
    val blockable: Boolean = false,
    @SerialName("blocked_on") val blockedOn: List<String> = emptyList(),
    @SerialName("parental_blocked") val parentalBlocked: Boolean = false,
    @SerialName("parental_next") val parentalNext: String? = null,
    @SerialName("lease_expiry") val leaseExpiry: Long? = null,
    @SerialName("lease_source") val leaseSource: String? = null,
    @SerialName("ip_source") val ipSource: String? = null,
    @SerialName("quota_used") val quotaUsed: Long = 0,
    @SerialName("quota_limit") val quotaLimit: Long = 0,
    @SerialName("quota_remaining") val quotaRemaining: Long = 0,
    @SerialName("quota_period") val quotaPeriod: String? = null,
    @SerialName("quota_throttled") val quotaThrottled: Boolean = false,
    @SerialName("quota_exceeded") val quotaExceeded: Boolean = false,
)

/** GET /api/ethports. */
@Serializable
data class EthPortsDto(
    val ports: List<EthPortDto> = emptyList(),
)

@Serializable
data class EthPortDto(
    val name: String = "",
    val wan: Boolean = false,
    val up: Boolean = false,
    @SerialName("speed_mbps") val speedMbps: Int = 0,
    val devices: List<EthDeviceDto> = emptyList(),
)

@Serializable
data class EthDeviceDto(
    val mac: String = "",
    val name: String? = null,
)
