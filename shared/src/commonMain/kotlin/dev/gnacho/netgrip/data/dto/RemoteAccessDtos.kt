package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/tailscale. */
@Serializable
data class TailscaleDto(
    val installed: Boolean = false,
    val running: Boolean = false,
    /** Running | NeedsLogin | NoState | Stopped */
    val state: String = "",
    @SerialName("auth_url") val authUrl: String = "",
    val ips: List<String> = emptyList(),
)

/** GET /api/wireguard. */
@Serializable
data class WireGuardDto(
    val installed: Boolean = false,
    val active: Boolean = false,
    val running: Boolean = false,
    @SerialName("zone_member") val zoneMember: Boolean = false,
    val port: String = "",
    val address: String = "",
    @SerialName("public_key") val publicKey: String = "",
    val peers: List<WireGuardPeerDto> = emptyList(),
    val endpoint: String = "",
    @SerialName("suggested_endpoint") val suggestedEndpoint: String = "",
    @SerialName("managed_by") val managedBy: String = "",
)

@Serializable
data class WireGuardPeerDto(
    val section: String = "",
    val name: String = "",
    @SerialName("allowed_ips") val allowedIps: List<String> = emptyList(),
    val admin: Boolean = false,
)

/** GET /api/openvpn. */
@Serializable
data class OpenVpnDto(
    val installed: Boolean = false,
    @SerialName("has_pki") val hasPki: Boolean = false,
    val active: Boolean = false,
    val running: Boolean = false,
    val port: String = "",
    val subnet: String = "",
    @SerialName("public_host") val publicHost: String = "",
    @SerialName("wan_ip") val wanIp: String = "",
    @SerialName("ddns_domains") val ddnsDomains: List<String> = emptyList(),
)

/** GET /api/ddns. */
@Serializable
data class DdnsDto(
    val installed: Boolean = false,
    val entries: List<DdnsEntryDto> = emptyList(),
    @SerialName("wan_ip") val wanIp: String = "",
)

@Serializable
data class DdnsEntryDto(
    val section: String = "",
    val enabled: Boolean = false,
    val running: Boolean = false,
    @SerialName("service_name") val serviceName: String = "",
    val domain: String = "",
    @SerialName("registered_ip") val registeredIp: String = "",
    @SerialName("last_update") val lastUpdate: String = "",
)

/** GET /api/remoteaccess: los tres permisos de entrada desde la WAN. */
@Serializable
data class RemoteAccessDto(
    val applicable: Boolean = false,
    @SerialName("ping_wan") val pingWan: Boolean = false,
    @SerialName("remote_https") val remoteHttps: Boolean = false,
    @SerialName("remote_ssh") val remoteSsh: Boolean = false,
)

/** POST /api/remoteaccess: solo viaja el permiso que cambia. */
@Serializable
data class RemoteAccessRequest(
    @SerialName("ping_wan") val pingWan: Boolean? = null,
    @SerialName("remote_https") val remoteHttps: Boolean? = null,
    @SerialName("remote_ssh") val remoteSsh: Boolean? = null,
)

/** Envoltura de las escrituras con estado releido y rollback. */
@Serializable
data class RemoteWriteResponse<T>(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
    val error: String? = null,
    val state: T? = null,
)

/** POST /api/tailscale. */
@Serializable
data class TailscaleSetRequest(val enabled: Boolean)

/** POST /api/ddns/force: seccion vacia = todas las entradas. */
@Serializable
data class DdnsForceRequest(val section: String = "")
