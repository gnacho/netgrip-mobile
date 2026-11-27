package dev.gnacho.netgrip.domain.fleet

import dev.gnacho.netgrip.data.dto.FleetNodeDto

/** Recuento de la flota para la cabecera de la tarjeta. */
data class FleetSummary(
    val total: Int = 0,
    val reachable: Int = 0,
    val updates: Int = 0,
    val failing: Int = 0,
    /** Routers que el panel nunca comprobó: no es lo mismo que no responder. */
    val unchecked: Int = 0,
)

/**
 * Reglas de la flota. El panel es quien entra en cada router: el telefono no
 * tiene sus credenciales, asi que todo lo que se pinta viene de lo que ya
 * respondio el panel.
 */
object FleetRules {

    /**
     * Direccion "host:puerto" para adoptar un peer. El panel guarda la
     * direccion del anuncio sin puerto y lo espera aparte, asi que aqui se
     * compone: respeta el puerto si ya viene, y pone corchetes en IPv6.
     */
    fun hostPort(address: String, port: Int): String {
        val host = address.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        if (host.isEmpty()) return ""
        val alreadyHasPort = when {
            host.startsWith("[") -> host.contains("]:")
            host.count { it == ':' } == 1 -> true
            host.count { it == ':' } > 1 -> false
            else -> false
        }
        if (alreadyHasPort) return host
        val isIpv6 = host.contains(':')
        if (port <= 0) return if (isIpv6) "[$host]" else host
        return if (isIpv6) "[$host]:$port" else "$host:$port"
    }

    fun summary(nodes: List<FleetNodeDto>): FleetSummary = FleetSummary(
        total = nodes.size,
        reachable = nodes.count { it.reachable },
        updates = nodes.count { it.updateAvailable && it.reachable },
        failing = nodes.count { !it.reachable && !it.error.isNullOrBlank() },
        unchecked = nodes.count { !it.reachable && it.error.isNullOrBlank() },
    )

    /**
     * Motivo por el que un router no entra en una actualizacion en lote, o
     * null si puede actualizarse. Se calcula sobre el ultimo check: por eso
     * el lote empieza comprobando la flota.
     */
    fun skipReason(node: FleetNodeDto): String? = when {
        node.id.isBlank() -> "sin identificador en el panel"
        !node.reachable -> {
            val detail = node.error?.takeIf { it.isNotBlank() }
            if (detail != null) "no responde: $detail" else "no responde"
        }
        !node.updateAvailable -> {
            val version = node.currentVersion.takeIf { it.isNotBlank() }
            if (version != null) "ya está en la $version" else "sin actualización pendiente"
        }
        else -> null
    }

    /** Los que si entrarian en un lote, en el orden de la lista. */
    fun updateCandidates(nodes: List<FleetNodeDto>): List<FleetNodeDto> =
        nodes.filter { skipReason(it) == null }
}
