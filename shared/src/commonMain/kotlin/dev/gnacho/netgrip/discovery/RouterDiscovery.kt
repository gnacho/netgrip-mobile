package dev.gnacho.netgrip.discovery

interface RouterDiscovery {
    val isSupported: Boolean
    suspend fun discover(): List<DiscoveredRouter>
}

expect fun platformRouterDiscovery(): RouterDiscovery
