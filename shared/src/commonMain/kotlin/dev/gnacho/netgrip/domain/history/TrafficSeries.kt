package dev.gnacho.netgrip.domain.history

import dev.gnacho.netgrip.data.dto.HistoryEntryDto

/**
 * Una muestra del historico del puente LAN. Los totales son acumulados desde
 * el arranque del router, las tasas salen de restar dos muestras consecutivas.
 */
data class TrafficSample(
    val timestamp: Long,
    val rxTotal: Long,
    val txTotal: Long,
    val rxRate: Double,
    val txRate: Double,
    val clients: Int,
    val load: Double,
)

/** Rangos del selector de la grafica, en horas. */
enum class TrafficRange(val hours: Int, val label: String) {
    HOUR(1, "1 h"),
    SIX_HOURS(6, "6 h"),
    DAY(24, "24 h"),
}

/**
 * Transforma el historico del router en muestras con tasa. El anillo del
 * servidor guarda una muestra cada 5 minutos y 288 como maximo.
 */
object TrafficSeries {

    private const val SECONDS_PER_HOUR = 3600L

    fun fromHistory(entries: List<HistoryEntryDto>): List<TrafficSample> {
        val ordered = entries.sortedBy { it.ts }
        if (ordered.isEmpty()) return emptyList()
        val out = ArrayList<TrafficSample>(ordered.size)
        var previous: HistoryEntryDto? = null
        for (entry in ordered) {
            val prev = previous
            val seconds = if (prev == null) 0L else entry.ts - prev.ts
            val rxDelta = if (prev == null) 0L else entry.rx - prev.rx
            val txDelta = if (prev == null) 0L else entry.tx - prev.tx
            // Un contador que baja es un reinicio o un salto del anillo: no es
            // trafico negativo, se dibuja como cero.
            val rxRate = if (prev != null && seconds > 0 && rxDelta >= 0) rxDelta.toDouble() / seconds else 0.0
            val txRate = if (prev != null && seconds > 0 && txDelta >= 0) txDelta.toDouble() / seconds else 0.0
            out += TrafficSample(
                timestamp = entry.ts,
                rxTotal = entry.rx,
                txTotal = entry.tx,
                rxRate = rxRate,
                txRate = txRate,
                clients = entry.clients,
                load = entry.load,
            )
            previous = entry
        }
        return out
    }

    /** Recorta a las ultimas [range] horas contando desde la ultima muestra. */
    fun window(samples: List<TrafficSample>, range: TrafficRange): List<TrafficSample> {
        if (samples.size < 2) return samples
        val last = samples.last().timestamp
        val from = last - range.hours * SECONDS_PER_HOUR
        return samples.filter { it.timestamp >= from }
    }

    fun peakBytesPerSecond(samples: List<TrafficSample>): Double =
        samples.maxOfOrNull { maxOf(it.rxRate, it.txRate) } ?: 0.0

    fun averageBytesPerSecond(samples: List<TrafficSample>, rx: Boolean): Double {
        if (samples.isEmpty()) return 0.0
        val sum = samples.sumOf { if (rx) it.rxRate else it.txRate }
        return sum / samples.size
    }

    /** Bytes transferidos dentro de la ventana, segun los acumulados. */
    fun transferred(samples: List<TrafficSample>, rx: Boolean): Long {
        if (samples.size < 2) return 0
        val first = samples.first()
        val last = samples.last()
        return if (rx) (last.rxTotal - first.rxTotal).coerceAtLeast(0)
        else (last.txTotal - first.txTotal).coerceAtLeast(0)
    }
}
