package dev.gnacho.netgrip.domain.format

import kotlin.math.roundToLong

/** Formato de magnitudes para la UI, en castellano y sin dependencias de JVM. */
object Formats {

    /** Bytes (RAM) a "512,4 MiB". */
    fun bytes(value: Long): String = magnitude(value.toDouble(), 1024.0, listOf("B", "KiB", "MiB", "GiB", "TiB"))

    /** KiB (root) a "12,3 MiB". */
    fun kib(value: Long): String = magnitude(value * 1024.0, 1024.0, listOf("B", "KiB", "MiB", "GiB", "TiB"))

    /** Segundos a "3 d 4 h 12 min", "2 h 7 min" o "42 s". */
    fun uptime(seconds: Long): String {
        if (seconds <= 0) return "0 s"
        val days = seconds / 86_400
        val hours = (seconds % 86_400) / 3_600
        val minutes = (seconds % 3_600) / 60
        return when {
            days > 0 -> "$days d $hours h $minutes min"
            hours > 0 -> "$hours h $minutes min"
            minutes > 0 -> "$minutes min"
            else -> "$seconds s"
        }
    }

    /** Load average a "0,42 / 0,38 / 0,31". */
    fun load(load: List<Double>): String =
        if (load.isEmpty()) "" else load.take(3).joinToString(" / ") { decimal(it, 2) }

    /** Velocidad negociada en Mb/s a "1 Gb/s", "100 Mb/s" o "Sin enlace". */
    fun speed(mbps: Int): String = when {
        mbps <= 0 -> "Sin enlace"
        mbps >= 1000 -> {
            val units = mbps / 1000.0
            if (units == units.roundToLong().toDouble()) "${units.roundToLong()} Gb/s" else "${decimal(units, 1)} Gb/s"
        }
        else -> "$mbps Mb/s"
    }

    /** Senal wifi a "-62 dBm", o null si no hay dato util. */
    fun signal(dbm: Int?): String? = if (dbm == null || dbm == 0) null else "$dbm dBm"

    /** Porcentaje redondeado, 0 si el total no es valido. */
    fun percent(part: Long, total: Long): Int =
        if (total <= 0) 0 else ((part * 100.0) / total).roundToLong().toInt()

    fun decimal(value: Double, decimals: Int): String {
        var factor = 1.0
        repeat(decimals) { factor *= 10 }
        val scaled = (value * factor).roundToLong()
        val whole = scaled / factor.toLong()
        val fraction = (scaled % factor.toLong()).let { if (it < 0) -it else it }
        if (decimals <= 0) return whole.toString()
        return "$whole,${fraction.toString().padStart(decimals, '0')}"
    }

    private fun magnitude(value: Double, base: Double, units: List<String>): String {
        if (value <= 0) return "0 ${units.first()}"
        var size = value
        var index = 0
        while (size >= base && index < units.lastIndex) {
            size /= base
            index++
        }
        val text = if (index == 0) size.roundToLong().toString() else decimal(size, 1)
        return "$text ${units[index]}"
    }
}
