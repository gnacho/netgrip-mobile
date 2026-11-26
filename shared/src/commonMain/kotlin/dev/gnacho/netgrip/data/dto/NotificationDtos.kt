package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/telegram. [botToken] llega enmascarado (primeros y ultimos
 * caracteres): el token real no se puede leer desde la app.
 */
@Serializable
data class TelegramDto(
    @SerialName("chatId") val chatId: String = "",
    val enabled: Boolean = false,
    @SerialName("botToken") val botToken: String = "",
)

/**
 * POST /api/telegram. Un token vacio conserva el guardado; el router valida
 * el par contra la API de Telegram antes de aceptarlo.
 */
@Serializable
data class TelegramSetRequest(
    @SerialName("botToken") val botToken: String = "",
    @SerialName("chatId") val chatId: String,
    val enabled: Boolean,
)

@Serializable
data class TelegramSetResponse(
    val ok: Boolean = false,
    @SerialName("botName") val botName: String = "",
    @SerialName("chatName") val chatName: String = "",
)

/** GET /api/ntfy. El token solo se sabe si existe, nunca su valor. */
@Serializable
data class NtfyDto(
    val server: String = "",
    val topic: String = "",
    @SerialName("tokenSet") val tokenSet: Boolean = false,
    val enabled: Boolean = false,
)

/** POST /api/ntfy. Campos vacios conservan lo guardado salvo enabled. */
@Serializable
data class NtfySetRequest(
    val server: String = "",
    val topic: String = "",
    val token: String = "",
    val enabled: Boolean,
)

@Serializable
data class OkResponse(val ok: Boolean = false)
