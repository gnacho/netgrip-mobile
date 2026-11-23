package dev.gnacho.netgrip.domain.clients

import dev.gnacho.netgrip.data.dto.ClientDto

/** Busqueda de clientes por nombre, IP, MAC o tipo de dispositivo. */
object ClientFilter {

    fun filter(clients: List<ClientDto>, query: String): List<ClientDto> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return clients
        return clients.filter { matches(it, needle) }
    }

    fun matches(client: ClientDto, needle: String): Boolean {
        if (needle.isEmpty()) return true
        return listOfNotNull(
            client.name,
            client.ip,
            client.mac,
            client.deviceType,
            client.iface,
        ).any { it.lowercase().contains(needle) }
    }
}
