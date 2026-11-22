package dev.gnacho.netgrip.discovery

/** Bounded, deterministic results for one on-demand discovery pass. */
class FleetDiscoveryBuffer(private val capacity: Int = 32) {
    private val entries = linkedMapOf<String, DiscoveredRouter>()

    init {
        require(capacity > 0) { "Discovery capacity must be positive" }
    }

    fun offer(payload: String, sourceAddress: String, nowEpochSeconds: Long): Boolean {
        val router = FleetBeaconParser.parse(payload, sourceAddress, nowEpochSeconds) ?: return false
        return offer(router)
    }

    fun offer(router: DiscoveredRouter): Boolean {
        if (router.key !in entries && entries.size >= capacity) return false
        entries[router.key] = router
        return true
    }

    fun snapshot(): List<DiscoveredRouter> = entries.values.sortedWith(
        compareBy({ it.name.lowercase() }, { it.address }, { it.port }),
    )
}
