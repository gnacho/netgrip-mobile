package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.Serializable

/**
 * GET /api/history: anillo de hasta 288 muestras cada 5 minutos (24 h).
 * `rx` y `tx` son bytes acumulados del puente LAN, no tasas: la tasa se
 * obtiene restando muestras consecutivas.
 */
@Serializable
data class HistoryDto(
    val entries: List<HistoryEntryDto> = emptyList(),
)

@Serializable
data class HistoryEntryDto(
    val ts: Long = 0,
    val rx: Long = 0,
    val tx: Long = 0,
    val load: Double = 0.0,
    val clients: Int = 0,
)
