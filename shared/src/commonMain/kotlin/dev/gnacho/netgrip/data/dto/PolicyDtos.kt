package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/quotas: topes de datos por dispositivo.
 *
 * `quotas` es la intencion del usuario y `usage` el consumo que cuenta el
 * router. [applicable] es false cuando el equipo no es el gateway.
 */
@Serializable
data class QuotaProbeDto(
    val applicable: Boolean = false,
    val quotas: Map<String, QuotaDto> = emptyMap(),
    val usage: Map<String, QuotaUsageDto> = emptyMap(),
    val ts: Long = 0,
)

/**
 * Una cuota tal como la guarda el router. Los seis campos son obligatorios en
 * la escritura: el servidor rechaza un `period` o un `action` vacíos, así que
 * no llevan valor por defecto (kotlinx omitiría los que coinciden con él).
 */
@Serializable
data class QuotaDto(
    val mac: String,
    val ip: String,
    /** daily | monthly */
    val period: String,
    /** Bytes. */
    val limit: Long,
    /** notify | throttle */
    val action: String,
    @SerialName("throttle_kbps") val throttleKbps: Int,
)

@Serializable
data class QuotaUsageDto(
    val used: Long = 0,
    val limit: Long = 0,
    val remaining: Long = 0,
    val period: String = "",
    val throttled: Boolean = false,
    val exceeded: Boolean = false,
)

/** GET /api/nftqos: limite de bajada y subida por dispositivo, en Mb/s. */
@Serializable
data class NftQoSProbeDto(
    val applicable: Boolean = false,
    val limits: Map<String, NftQoSLimitDto> = emptyMap(),
)

@Serializable
data class NftQoSLimitDto(
    val mac: String = "",
    val ip: String = "",
    val download: Int = 0,
    val upload: Int = 0,
)

/** POST /api/nftqos. Con los dos a cero el router borra el limite. */
@Serializable
data class NftQoSSetRequest(
    val mac: String,
    val ip: String,
    val download: Int,
    val upload: Int,
)

/**
 * GET /api/parental: horarios de bloqueo. No trae `applicable`: el panel los
 * sirve en cualquier equipo.
 */
@Serializable
data class ParentalProbeDto(
    val rules: Map<String, ParentalRuleDto> = emptyMap(),
    val ts: Long = 0,
)

/**
 * Un horario de bloqueo. Como en la cuota, todos los campos van en cada
 * escritura: el servidor valida días y horas aunque el horario esté apagado.
 */
@Serializable
data class ParentalRuleDto(
    val mac: String,
    val enabled: Boolean,
    /** 0 es domingo, como time.Weekday de Go. */
    val days: List<Int>,
    /** "HH:MM" en hora local del router. */
    val start: String,
    val end: String,
    /** Bloqueo inmediato, por encima del horario. */
    val paused: Boolean,
)
