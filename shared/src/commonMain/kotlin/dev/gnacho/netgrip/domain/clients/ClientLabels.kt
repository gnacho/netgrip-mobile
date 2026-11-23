package dev.gnacho.netgrip.domain.clients

import dev.gnacho.netgrip.data.dto.ClientDto

/** Etiquetas legibles de un cliente para la lista y el detalle. */
object ClientLabels {

    fun type(type: String): String = when (type) {
        "wifi24" -> "WiFi 2,4 GHz"
        "wifi5" -> "WiFi 5 GHz"
        "cable" -> "Cable"
        else -> if (type.isBlank()) "Desconocido" else type
    }

    /** Avisos activos del cliente, en orden de importancia. */
    fun flags(client: ClientDto): List<String> = buildList {
        if (client.self) add("Este dispositivo")
        if (client.blocked) add("Bloqueado")
        if (client.parentalBlocked) add("Bloqueo parental")
        if (client.quotaExceeded) add("Cuota agotada")
        if (client.quotaThrottled) add("Cuota limitada")
        if (client.reserved) add("IP reservada")
        if (client.quotaLimit > 0 && !client.quotaExceeded) add("Con cuota")
    }

    /** Texto del origen de la concesión DHCP, o null si no hay dato. */
    fun leaseSource(client: ClientDto): String? = when (client.leaseSource) {
        "local" -> "Concesión local"
        "gateway" -> "Concesión del gateway"
        else -> client.leaseSource?.takeIf { it.isNotBlank() }
    }

    /** Texto del origen de la IP, o null si no hay dato. */
    fun ipSource(client: ClientDto): String? = when (client.ipSource) {
        "arp" -> "Vista en ARP"
        "reservation" -> "Reserva DHCP"
        else -> client.ipSource?.takeIf { it.isNotBlank() }
    }
}
