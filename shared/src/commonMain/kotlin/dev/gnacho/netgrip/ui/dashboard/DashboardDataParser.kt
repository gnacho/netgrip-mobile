package dev.gnacho.netgrip.ui.dashboard

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

data class DashboardField(
    val label: String,
    val value: String,
)

data class DashboardClient(
    val name: String,
    val address: String? = null,
    val connection: String? = null,
)

object DashboardDataParser {

    private val systemLabels = linkedMapOf(
        "hostname" to "Nombre del equipo",
        "firmware" to "Firmware",
        "uptime" to "Tiempo de actividad",
        "cpu_load" to "Carga de CPU",
        "memory" to "Memoria",
    )

    private val durationUnits = mapOf(
        "day" to "día",
        "days" to "días",
        "hour" to "hora",
        "hours" to "horas",
        "minute" to "minuto",
        "minutes" to "minutos",
        "second" to "segundo",
        "seconds" to "segundos",
    )

    private val durationUnit = Regex(
        "\\b(days?|hours?|minutes?|seconds?)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val whitespace = Regex("\\s+")

    fun parseSystem(root: JsonElement?): List<DashboardField> {
        val system = root as? JsonObject ?: return emptyList()
        val orderedKeys = systemLabels.keys.filter(system::containsKey) +
            system.keys.filterNot(systemLabels::containsKey)

        return orderedKeys.mapNotNull { key ->
            val value = system[key]
                ?.let { formatSystemValue(key, it) }
                ?: return@mapNotNull null
            DashboardField(systemLabels[key] ?: humanizeLabel(key), value)
        }
    }

    fun parseClients(root: JsonElement?): List<DashboardClient> {
        val clients = when (root) {
            is JsonArray -> root
            is JsonObject -> root["clients"] as? JsonArray
            else -> null
        } ?: return emptyList()

        return clients.mapIndexed { index, element ->
            val client = element as? JsonObject
            DashboardClient(
                name = client?.text("name")
                    ?: primitiveText(element)
                    ?: "Cliente ${index + 1}",
                address = client?.text("address"),
                connection = client?.text("connection"),
            )
        }
    }

    private fun formatSystemValue(key: String, element: JsonElement): String? {
        val value = formatValue(element) ?: return null
        if (key != "uptime") return value
        return durationUnit.replace(value) { match ->
            durationUnits[match.value.lowercase()] ?: match.value
        }
    }

    private fun formatValue(element: JsonElement): String? = when (element) {
        JsonNull -> null
        is JsonPrimitive -> if (element.isString) {
            primitiveText(element)
        } else {
            element.booleanOrNull?.let { if (it) "Sí" else "No" } ?: primitiveText(element)
        }
        is JsonArray -> {
            val values = element.mapNotNull(::formatValue)
            values.takeIf { it.size == element.size }
                ?.joinToString(", ")
                ?.takeIf(String::isNotBlank)
        }
        is JsonObject -> null
    }

    private fun primitiveText(element: JsonElement?): String? =
        (element as? JsonPrimitive)
            ?.content
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private fun JsonObject.text(key: String): String? = primitiveText(this[key])

    private fun humanizeLabel(key: String): String {
        val label = key.replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replace(whitespace, " ")
            .ifBlank { "Campo" }
        return label.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase() else character.toString()
        }
    }
}
