package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/clients/meta y respuesta de POST /api/clients/meta.
 *
 * Verificado en internal/modules/clients_meta.go: el mapa va de MAC en
 * minusculas a {name, device_type}. Un POST con name y device_type vacios
 * borra la entrada, de modo que la MAC desaparece del mapa.
 */
@Serializable
data class ClientMetaDto(
    val meta: Map<String, ClientMetaEntryDto> = emptyMap(),
)

@Serializable
data class ClientMetaEntryDto(
    val name: String = "",
    @SerialName("device_type") val deviceType: String = "",
)

/**
 * POST /api/clients/meta. Los tres campos van siempre en el cuerpo: el
 * servidor reemplaza la ficha completa (no hace merge de campos).
 */
@Serializable
data class ClientMetaRequest(
    val mac: String,
    val name: String,
    @SerialName("device_type") val deviceType: String,
)

/**
 * POST /api/clients/block. `type` es "wifi" o "cable" (el servidor trata
 * cualquier valor distinto de "cable" como WiFi). `band` es "" para todas
 * las bandas o una clave de la lista `bands` de /api/clients.
 */
@Serializable
data class ClientBlockRequest(
    val mac: String,
    val type: String,
    val band: String,
    val blocked: Boolean,
)

/** POST /api/clients/reserve. */
@Serializable
data class ClientReserveRequest(
    val mac: String,
    val ip: String,
    val reserved: Boolean,
)

/**
 * Respuesta comun de /api/clients/block y /api/clients/reserve:
 * {"rolled_back":bool,"status":"applied"}. Un 400 devuelve {"error":"..."}.
 */
@Serializable
data class ClientWriteResponseDto(
    val status: String = "",
    @SerialName("rolled_back") val rolledBack: Boolean = false,
)

/** POST /api/wol. */
@Serializable
data class WolRequest(
    val mac: String,
)

/** Respuesta de POST /api/wol: {"status":"sent"}. */
@Serializable
data class WolResponseDto(
    val status: String = "",
)

/** Cuerpo de error del router: {"error":"..."} en cualquier non-2xx. */
@Serializable
data class ApiErrorDto(
    val error: String = "",
)
