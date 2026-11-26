package dev.gnacho.netgrip.domain.wifi

/**
 * Reglas del horario semanal de una interfaz: dias 0..6 con 0 = domingo,
 * horas "HH:MM" y ventanas que pueden cruzar la medianoche.
 */
object ScheduleEditRules {

    val dayNames = listOf("D", "L", "M", "X", "J", "V", "S")
    val dayLongNames = listOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")

    /** Estados posibles de una interfaz, en el orden en que se explican. */
    fun stateLabel(
        hasSchedule: Boolean,
        enabled: Boolean,
        paused: Boolean,
        offBySchedule: Boolean,
        disabled: Boolean,
    ): String = when {
        !hasSchedule -> "Sin horario"
        paused -> "Apagada a mano"
        !enabled -> "Horario desactivado"
        offBySchedule -> "Apagada por el horario ahora"
        disabled -> "Apagada"
        else -> "Dentro de la ventana"
    }

    fun timeError(start: String, end: String): String? = when {
        !isTime(start) || !isTime(end) -> "Escribe las horas como 07:30"
        else -> null
    }

    fun isTime(value: String): Boolean {
        val parts = value.split(":")
        if (parts.size != 2) return false
        if (parts[0].length != 2 || parts[1].length != 2) return false
        val hour = parts[0].toIntOrNull() ?: return false
        val minute = parts[1].toIntOrNull() ?: return false
        return hour in 0..23 && minute in 0..59
    }

    /** Un horario activo necesita al menos un dia. */
    fun daysError(days: Set<Int>): String? =
        if (days.isEmpty()) "Elige al menos un día" else null

    fun configError(enabled: Boolean, days: Set<Int>, start: String, end: String): String? {
        if (!enabled) return null
        daysError(days)?.let { return it }
        return timeError(start, end)
    }

    /** "L M X J V" o "todos los días". */
    fun daysSummary(days: Set<Int>): String = when {
        days.isEmpty() -> "sin días"
        days.size == 7 -> "todos los días"
        else -> days.sorted().joinToString(" ") { dayNames[it] }
    }

    /** "07:00 a 23:00", avisando cuando cruza medianoche. */
    fun windowLabel(start: String, end: String): String {
        if (!isTime(start) || !isTime(end)) return ""
        val crosses = toMinutes(end) <= toMinutes(start)
        return if (crosses) "$start a $end (cruza medianoche)" else "$start a $end"
    }

    private fun toMinutes(value: String): Int {
        val parts = value.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }
}
