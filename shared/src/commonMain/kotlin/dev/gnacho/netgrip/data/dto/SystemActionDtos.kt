package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.Serializable

/** POST /api/reboot: el router acepta el reinicio y corta. */
@Serializable
data class RebootResponseDto(val started: Boolean = false)

/** POST /api/password. Cambiarla invalida todas las sesiones del panel. */
@Serializable
data class PasswordChangeRequest(
    val current: String,
    val next: String,
)

/** Cuerpo vacio para los POST que no llevan datos. */
@Serializable
data class EmptyRequest(val confirm: Boolean? = null)
