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

/** GET|PUT /api/selfupdate/schedule: cuando se actualiza solo el panel. */
@Serializable
data class SelfUpdateScheduleDto(
    val config: SelfUpdateConfigDto = SelfUpdateConfigDto(),
    val state: SelfUpdateStateDto = SelfUpdateStateDto(),
    val status: SelfUpdateStatusDto = SelfUpdateStatusDto(),
)

@Serializable
data class SelfUpdateConfigDto(
    val enabled: Boolean = false,
    @SerialName("intervalHours") val intervalHours: Int = 168,
    @SerialName("windowStart") val windowStart: Int = 3,
    @SerialName("windowEnd") val windowEnd: Int = 5,
)

@Serializable
data class SelfUpdateStateDto(
    @SerialName("lastCheck") val lastCheck: Long = 0,
    @SerialName("lastResult") val lastResult: String = "",
    @SerialName("lastApply") val lastApply: Long = 0,
    @SerialName("lastVersion") val lastVersion: String = "",
)

/** PUT parcial: solo viaja lo que cambia. */
@Serializable
data class SelfUpdateScheduleRequest(
    val enabled: Boolean? = null,
    @SerialName("intervalHours") val intervalHours: Int? = null,
    @SerialName("windowStart") val windowStart: Int? = null,
    @SerialName("windowEnd") val windowEnd: Int? = null,
)
