package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/access: puertos y exposicion de panel, panel web del router y SSH. */
@Serializable
data class AccessDto(
    val panel: PanelAccessDto = PanelAccessDto(),
    val web: WebAccessDto = WebAccessDto(),
    val ssh: SshAccessDto = SshAccessDto(),
)

@Serializable
data class PanelAccessDto(
    @SerialName("http_port") val httpPort: Int = 0,
    @SerialName("https_enabled") val httpsEnabled: Boolean = false,
    @SerialName("force_https") val forceHttps: Boolean = false,
    @SerialName("session_ttl") val sessionTtl: String = "",
)

@Serializable
data class WebAccessDto(
    @SerialName("http_port") val httpPort: Int = 0,
    @SerialName("https_port") val httpsPort: Int = 0,
    @SerialName("force_https") val forceHttps: Boolean = false,
    val enabled: Boolean = false,
)

@Serializable
data class SshAccessDto(
    val enabled: Boolean = false,
    val port: String = "",
)

/** GET /api/mdns. */
@Serializable
data class MdnsDto(
    val installed: Boolean = false,
    val enabled: Boolean = false,
    val running: Boolean = false,
    val domain: String = "",
)

/** GET /api/selfupdate. */
@Serializable
data class SelfUpdateDto(
    val current: String = "",
    val latest: String = "",
    val available: Boolean = false,
    val notes: String = "",
    @SerialName("check_error") val checkError: String? = null,
)
