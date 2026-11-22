package dev.gnacho.netgrip.discovery

/** A public, unverified NetGrip fleet beacon received on the local network. */
data class DiscoveredRouter(
    val id: String,
    val name: String,
    val version: String,
    val address: String,
    val port: Int,
) {
    val key: String get() = "$id|$address|$port"
    val baseUrl: String get() = "http://$address:$port"
}
