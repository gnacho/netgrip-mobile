package dev.gnacho.netgrip.domain.wifi

/**
 * Reglas de las redes de invitados e IoT: mismas reglas de nombre y clave que
 * la red principal, mas la banda y la subred opcional de invitados.
 */
object NetworkEditRules {

    /** Bandas que acepta el router para estas redes. */
    val bands = listOf("both", "2g", "5g")

    fun bandLabel(band: String): String = when (band.lowercase()) {
        "both" -> "2,4 y 5 GHz"
        "2g" -> "2,4 GHz"
        "5g" -> "5 GHz"
        else -> band
    }

    /**
     * Una red apagada conserva nombre y clave para la proxima vez, asi que
     * solo se valida lo que se va a emitir. [keyRequired] distingue crear la
     * red (el router exige clave) de editarla (vacia conserva la actual).
     */
    fun configError(
        enabled: Boolean,
        ssid: String,
        key: String,
        keyRequired: Boolean = true,
    ): String? {
        if (!enabled) return null
        WifiEditRules.ssidError(ssid)?.let { return it }
        return when {
            key.isEmpty() -> if (keyRequired) "La clave no puede quedar vacía" else null
            key.length < 8 -> "La clave necesita al menos 8 caracteres"
            key.length > 63 -> "La clave no puede pasar de 63 caracteres"
            else -> null
        }
    }

    /** El router crea la red de invitados la primera vez que se activa. */
    fun guestNeedsKey(existingSsid: String): Boolean = existingSsid.isBlank()

    /**
     * Subred opcional de invitados: vacia deja la que ya hay. Si se escribe,
     * tiene que ser una IPv4 valida y no la direccion de red ni de broadcast.
     */
    fun subnetError(subnet: String): String? {
        val value = subnet.trim()
        if (value.isEmpty()) return null
        val parts = value.split(".")
        if (parts.size != 4) return "Escribe una IPv4, por ejemplo 192.168.9.1"
        val octets = parts.map { it.toIntOrNull() ?: return "Escribe una IPv4, por ejemplo 192.168.9.1" }
        if (octets.any { it < 0 || it > 255 }) {
            return "Cada número de la IPv4 va de 0 a 255"
        }
        val last = octets.last()
        if (last == 0 || last == 255) {
            return "Esa dirección es de red o de broadcast, usa otra"
        }
        return null
    }

    /** Resumen de la red tal como se muestra en la tarjeta. */
    fun summary(enabled: Boolean, ssid: String, clients: Int): String = when {
        !enabled -> "Desactivada"
        ssid.isBlank() -> "Sin nombre configurado"
        clients == 1 -> "$ssid · 1 cliente"
        else -> "$ssid · $clients clientes"
    }
}
