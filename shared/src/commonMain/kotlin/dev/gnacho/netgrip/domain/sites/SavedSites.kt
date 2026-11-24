package dev.gnacho.netgrip.domain.sites

import kotlinx.serialization.Serializable

/**
 * Un router guardado en la app. La cookie de sesion no vive aqui: se guarda
 * cifrada aparte, indexada por [id].
 */
@Serializable
data class SavedDevice(
    val id: String,
    val url: String,
    val name: String = "",
    val model: String = "",
    val role: String = "",
    val lastSeenAt: Long = 0,
) {
    /** Nombre legible: el del router o, si aun no se conoce, la URL. */
    val label: String get() = name.ifBlank { url.removePrefix("https://").removePrefix("http://") }
}

/** Una red con todos sus routers. */
@Serializable
data class SavedNetwork(
    val id: String,
    val name: String,
    val devices: List<SavedDevice> = emptyList(),
)

/** Estado persistido del selector de red y dispositivo. */
@Serializable
data class SavedSites(
    val networks: List<SavedNetwork> = emptyList(),
    val currentNetworkId: String? = null,
    val currentDeviceId: String? = null,
) {
    val currentNetwork: SavedNetwork?
        get() = networks.firstOrNull { it.id == currentNetworkId } ?: networks.firstOrNull()

    val currentDevice: SavedDevice?
        get() = currentNetwork?.devices?.firstOrNull { it.id == currentDeviceId }
            ?: currentNetwork?.devices?.firstOrNull()

    fun network(id: String?): SavedNetwork? = networks.firstOrNull { it.id == id }

    fun device(id: String?): SavedDevice? =
        networks.asSequence().flatMap { it.devices.asSequence() }.firstOrNull { it.id == id }

    /** Reemplaza una red conservando el orden. */
    fun withNetwork(network: SavedNetwork): SavedSites =
        copy(networks = networks.map { if (it.id == network.id) network else it })

    fun withDevice(networkId: String, device: SavedDevice): SavedSites {
        val target = network(networkId) ?: return this
        val exists = target.devices.any { it.id == device.id }
        val devices = if (exists) {
            target.devices.map { if (it.id == device.id) device else it }
        } else {
            target.devices + device
        }
        return withNetwork(target.copy(devices = devices))
    }

    fun withoutDevice(networkId: String, deviceId: String): SavedSites {
        val target = network(networkId) ?: return this
        return withNetwork(target.copy(devices = target.devices.filterNot { it.id == deviceId }))
            .let { if (it.currentDeviceId == deviceId) it.copy(currentDeviceId = null) else it }
    }
}

/** Identificadores estables y reglas de alta de redes y dispositivos. */
object SiteRules {

    const val DEFAULT_NETWORK_NAME = "Mi red"

    /** Id estable de un dispositivo a partir de su URL, para no duplicarlo. */
    fun deviceId(url: String): String = "dev-" + slug(normalizeUrl(url)).ifBlank { "router" }

    /** Id de red a partir del nombre, con sufijo si ya existe. */
    fun networkId(name: String, existing: List<SavedNetwork>): String {
        val base = "net-" + slug(name).ifBlank { "red" }
        if (existing.none { it.id == base }) return base
        var index = 2
        while (existing.any { it.id == "$base-$index" }) index++
        return "$base-$index"
    }

    /** URL sin espacios ni barra final, conservando esquema y puerto. */
    fun normalizeUrl(raw: String): String = raw.trim().trimEnd('/')

    /**
     * URL de un nodo del fleet. El panel web antepone http:// cuando falta el
     * esquema y deja el puerto tal cual, asi que se copia esa regla.
     */
    fun urlFromFleetAddress(address: String): String {
        val clean = address.trim()
        if (clean.isEmpty()) return ""
        return if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            normalizeUrl(clean)
        } else {
            normalizeUrl("http://$clean")
        }
    }

    private fun slug(value: String): String = value
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)
}
