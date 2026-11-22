package dev.gnacho.netgrip.discovery

actual fun platformRouterDiscovery(): RouterDiscovery = UnsupportedRouterDiscovery

private object UnsupportedRouterDiscovery : RouterDiscovery {
    override val isSupported: Boolean = false

    override suspend fun discover(): List<DiscoveredRouter> = emptyList()
}
