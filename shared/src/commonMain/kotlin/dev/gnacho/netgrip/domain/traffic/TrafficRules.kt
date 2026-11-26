package dev.gnacho.netgrip.domain.traffic

import dev.gnacho.netgrip.data.dto.DpiAppDto
import dev.gnacho.netgrip.data.dto.DpiBucketDto
import dev.gnacho.netgrip.data.dto.DpiProtocolDto
import dev.gnacho.netgrip.data.dto.NlbwUsageDto
import dev.gnacho.netgrip.domain.format.formatClock

/**
 * Una fila de la tabla de trafico ya resuelta para pintar.
 *
 * [down] y [up] son bytes contados por el router. Aqui no se calcula ninguna
 * tasa: lo que la app no puede medir, no lo enseña.
 */
data class TrafficRow(
    val key: String,
    val label: String,
    /** IP, MAC o categoria, segun la fila. Vacio cuando no aporta nada. */
    val detail: String,
    val connections: Long,
    val down: Long,
    val up: Long,
) {
    val total: Long get() = down + up
}

/** Un cubo de la linea de tiempo, con su hora local ya resuelta. */
data class TrafficPoint(val label: String, val bytes: Long)

/**
 * Reglas puras de la pestana Trafico: resuelven nombres, ordenan y normalizan
 * lo que responde el router. Todo lo que entra viene de su contabilidad
 * (nlbwmon) o de su DPI (netifyd), nada se estima en el telefono.
 */
object TrafficRules {

    /** Etiqueta con la que nlbwmon marca el trafico que no supo nombrar. */
    const val UNKNOWN_APP = "other"

    /** Dispositivos ordenados de mas a menos trafico contado. */
    fun deviceRows(
        usage: List<NlbwUsageDto>,
        names: Map<String, String> = emptyMap(),
    ): List<TrafficRow> =
        usage
            .filter { it.key.isNotBlank() }
            .map { row ->
                val name = names[row.key.lowercase()].orEmpty().trim()
                val label = when {
                    name.isNotBlank() -> name
                    row.ip.isNotBlank() -> row.ip
                    else -> row.key
                }
                TrafficRow(
                    key = row.key,
                    label = label,
                    detail = if (row.ip.isNotBlank() && row.ip != label) row.ip else "",
                    connections = row.conns,
                    down = row.downBytes,
                    up = row.upBytes,
                )
            }
            .sortedByDescending { it.total }

    /** Protocolos de nlbwmon ordenados igual, con "other" legible. */
    fun appRows(usage: List<NlbwUsageDto>): List<TrafficRow> =
        usage
            .filter { it.key.isNotBlank() }
            .map { row ->
                TrafficRow(
                    key = row.key,
                    label = appLabel(row.key),
                    detail = "",
                    connections = row.conns,
                    down = row.downBytes,
                    up = row.upBytes,
                )
            }
            .sortedByDescending { it.total }

    fun appLabel(raw: String): String =
        if (raw.isBlank() || raw.equals(UNKNOWN_APP, ignoreCase = true)) "Sin identificar" else raw

    /**
     * Protocolos del DPI, nunca null: el servidor puede devolver `protocols:
     * null` cuando no puede leer conntrack y la pantalla tiene que seguir.
     */
    fun protocolRows(protocols: List<DpiProtocolDto>?): List<DpiProtocolDto> =
        protocols.orEmpty()
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.bytes }

    /** Aplicaciones de netifyd ordenadas por bytes, sin entradas vacias. */
    fun dpiAppRows(apps: List<DpiAppDto>): List<DpiAppDto> =
        apps.filter { it.name.isNotBlank() && it.bytes > 0 }
            .sortedByDescending { it.bytes }

    /**
     * Cubos de 5 minutos con su hora local. Un cubo sin fecha valida se
     * descarta: mejor un hueco que una hora inventada.
     */
    fun points(
        buckets: List<DpiBucketDto>,
        clock: (Long) -> String = { formatClock(it) },
    ): List<TrafficPoint> =
        buckets.mapNotNull { bucket ->
            val epoch = rfc3339Seconds(bucket.time) ?: return@mapNotNull null
            TrafficPoint(clock(epoch), bucket.apps.values.sumOf { it.total })
        }

    /** Nombre en castellano de una categoria de netifyd, o la cruda si no se conoce. */
    fun category(raw: String): String = when (raw.trim().lowercase()) {
        "" -> "Sin categoria"
        "streaming" -> "Streaming"
        "social" -> "Redes sociales"
        "gaming" -> "Juegos"
        "voip" -> "Voz"
        "chat" -> "Mensajeria"
        "mail" -> "Correo"
        "file" -> "Ficheros"
        "web" -> "Web"
        "media" -> "Multimedia"
        "software" -> "Software"
        "shopping" -> "Compras"
        "business" -> "Empresa"
        "vpn" -> "VPN"
        "remote" -> "Acceso remoto"
        "dns" -> "DNS"
        "database" -> "Base de datos"
        "iot" -> "IoT"
        "p2p" -> "P2P"
        "update" -> "Actualizaciones"
        else -> raw.trim()
    }

    /**
     * Instantes RFC3339 ("2026-11-26T17:35:00Z", con fraccion o desfase) a
     * segundos de la epoca. Null si el texto no tiene esa forma.
     */
    fun rfc3339Seconds(text: String): Long? {
        if (text.length < 19) return null
        if (text[4] != '-' || text[7] != '-' || (text[10] != 'T' && text[10] != 't')) return null
        if (text[13] != ':' || text[16] != ':') return null
        val year = text.substring(0, 4).toIntOrNull() ?: return null
        val month = text.substring(5, 7).toIntOrNull() ?: return null
        val day = text.substring(8, 10).toIntOrNull() ?: return null
        val hour = text.substring(11, 13).toIntOrNull() ?: return null
        val minute = text.substring(14, 16).toIntOrNull() ?: return null
        val second = text.substring(17, 19).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31) return null
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..60) return null
        val rest = text.substring(19)
        val offsetSeconds = when {
            rest.isEmpty() -> 0
            rest[0] == 'Z' || rest[0] == 'z' -> 0
            rest[0] == '+' || rest[0] == '-' -> {
                val sign = if (rest[0] == '-') -1 else 1
                val body = rest.substring(1).substringBefore('.')
                val offsetHour = body.substringBefore(':').toIntOrNull() ?: return null
                val offsetMinute = body.substringAfter(':', "0").toIntOrNull() ?: 0
                sign * (offsetHour * 3600 + offsetMinute * 60)
            }
            // Fraccion de segundo sin desfase: "….123".
            else -> 0
        }
        return daysFromCivil(year, month, day) * 86_400 +
            hour * 3_600L + minute * 60L + second - offsetSeconds
    }

    /** Dias desde 1970 de una fecha del calendario gregoriano (proleptico). */
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = if (month <= 2) year - 1 else year
        val era = (if (y >= 0) y else y - 399) / 400
        val yearOfEra = y - era * 400
        val dayOfYear = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
        val dayOfEra = yearOfEra * 365 + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
        return era.toLong() * 146_097 + dayOfEra - 719_468
    }
}
