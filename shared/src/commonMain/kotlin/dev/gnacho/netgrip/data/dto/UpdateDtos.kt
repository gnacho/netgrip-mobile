package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/update: resultado de `owut check` en el router. */
@Serializable
data class UpdateCheckDto(
    val available: Boolean = false,
    @SerialName("same_version") val sameVersion: Boolean = false,
    @SerialName("owut_present") val owutPresent: Boolean = false,
    @SerialName("version_from") val versionFrom: String = "",
    @SerialName("version_to") val versionTo: String = "",
    @SerialName("out_of_date_packages") val outOfDatePackages: Int = 0,
    val warnings: List<String> = emptyList(),
    @SerialName("safe_to_proceed") val safeToProceed: Boolean = false,
    @SerialName("missing_packages") val missingPackages: List<String> = emptyList(),
    @SerialName("safe_with_reinstall") val safeWithReinstall: Boolean = false,
    @SerialName("upgrade_started") val upgradeStarted: Boolean = false,
)

/** POST /api/update: el router acepta el sysupgrade y se reiniciara. */
@Serializable
data class UpdateStartDto(
    val started: Boolean = false,
    @SerialName("reboot_pending") val rebootPending: Boolean = false,
)

/** GET /api/selfupdate/status: progreso de la actualizacion del panel. */
@Serializable
data class SelfUpdateStatusDto(
    val phase: String = "idle",
    val progress: Int = 0,
    val message: String = "",
)
