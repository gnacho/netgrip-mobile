package dev.gnacho.netgrip.domain.traffic

import kotlin.math.roundToLong

/** Unidad con la que se escribe un tope de datos en la app. */
enum class DataUnit(val label: String, val bytes: Long) {
    MIB("MiB", 1024L * 1024L),
    GIB("GiB", 1024L * 1024L * 1024L),
}

/**
 * Reglas de las politicas por dispositivo: cuota, velocidad y horario.
 *
 * El servidor valida lo mismo (internal/modules/quota.go, nftqos.go y
 * parental.go), pero la app comprueba antes para no mandar una escritura que
 * se va a rechazar y para explicar el motivo en el propio formulario.
 */
object PolicyRules {

    /** Dias de la semana como los numera Go: 0 es domingo. */
    val WEEK_DAYS: List<Pair<Int, String>> = listOf(
        1 to "L",
        2 to "M",
        3 to "X",
        4 to "J",
        5 to "V",
        6 to "S",
        0 to "D",
    )

    const val MAX_THROTTLE_KBPS = 1_000_000
    const val MAX_MBPS = 10_000

    /** "1,5" o "1.5" a numero. Null si no es un numero positivo. */
    fun parseAmount(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isEmpty()) return null
        val value = normalized.toDoubleOrNull() ?: return null
        return if (value > 0) value else null
    }

    /** Texto del usuario y unidad a bytes, o null si no hay numero valido. */
    fun limitBytes(raw: String, unit: DataUnit): Long? {
        val amount = parseAmount(raw) ?: return null
        return (amount * unit.bytes).roundToLong().takeIf { it > 0 }
    }

    /** Bytes a texto editable en [unit], sin decimales sobrantes. */
    fun amountText(bytes: Long, unit: DataUnit): String {
        if (bytes <= 0) return ""
        val units = bytes.toDouble() / unit.bytes
        val rounded = (units * 100).roundToLong() / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().replace('.', ',')
        }
    }

    fun periodLabel(raw: String): String = if (raw == "monthly") "Al mes" else "Al día"

    fun actionLabel(raw: String): String = if (raw == "throttle") "Limitar" else "Avisar"

    /** Motivo por el que no se puede guardar una cuota, o null si está bien. */
    fun validateQuota(limitBytes: Long?, action: String, throttleKbps: Int): String? = when {
        limitBytes == null || limitBytes <= 0 -> "Escribe un tope mayor que cero"
        action != "notify" && action != "throttle" -> "Elige avisar o limitar"
        action == "throttle" && throttleKbps <= 0 -> "Al limitar hace falta una velocidad en kb/s"
        throttleKbps > MAX_THROTTLE_KBPS -> "La velocidad de limitación es demasiado alta"
        else -> null
    }

    /** Motivo por el que no se puede guardar un límite de velocidad, o null. */
    fun validateQos(download: Int, upload: Int): String? = when {
        download < 0 || upload < 0 -> "Las velocidades no pueden ser negativas"
        download > MAX_MBPS || upload > MAX_MBPS -> "Como máximo $MAX_MBPS Mb/s"
        else -> null
    }

    /** "HH:MM" a minutos desde medianoche, o null si no tiene esa forma. */
    fun parseHHMM(raw: String): Int? {
        val text = raw.trim()
        if (text.length != 5 || text[2] != ':') return null
        val hour = text.substring(0, 2).toIntOrNull() ?: return null
        val minute = text.substring(3, 5).toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    /** Motivo por el que no se puede guardar un horario, o null. */
    fun validateParental(days: Set<Int>, start: String, end: String): String? = when {
        days.isEmpty() -> "Elige al menos un día"
        days.any { it !in 0..6 } -> "Día no válido"
        parseHHMM(start) == null || parseHHMM(end) == null -> "Las horas van en formato HH:MM"
        parseHHMM(start) == parseHHMM(end) -> "La hora de fin no puede ser la de inicio"
        else -> null
    }

    /** "L, X y V" para resumir los días de un horario. */
    fun daysLabel(days: List<Int>): String {
        val labels = WEEK_DAYS.filter { it.first in days }.map { it.second }
        return when (labels.size) {
            0 -> "Ningún día"
            7 -> "Todos los días"
            else -> labels.joinToString(", ")
        }
    }

    /** "18:30 a 20:00, L, X, V" o el estado del bloqueo si manda. */
    fun parentalSummary(enabled: Boolean, paused: Boolean, days: List<Int>, start: String, end: String): String =
        when {
            paused -> "Pausado: bloqueado ahora"
            !enabled -> "Apagado"
            else -> "$start a $end · ${daysLabel(days)}"
        }
}
