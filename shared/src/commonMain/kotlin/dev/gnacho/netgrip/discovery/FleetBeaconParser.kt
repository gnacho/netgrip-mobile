package dev.gnacho.netgrip.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Parses and validates the public beacon format implemented by NetGrip. */
object FleetBeaconParser {
    const val PROTOCOL_VERSION = 1
    const val BEACON_TYPE = "netgrip-fleet-beacon"
    const val MAX_AGE_SECONDS = 60L

    private val json = Json { ignoreUnknownKeys = true }
    private val idPattern = Regex("^[a-z0-9][a-z0-9-]*$")

    fun parse(payload: String, sourceAddress: String, nowEpochSeconds: Long): DiscoveredRouter? {
        val beacon = try {
            json.decodeFromString<FleetBeacon>(payload)
        } catch (_: Exception) {
            return null
        }

        if (beacon.v != PROTOCOL_VERSION || beacon.type != BEACON_TYPE) return null
        if (beacon.id.length !in 1..64 || !idPattern.matches(beacon.id)) return null
        if (!isDisplayable(beacon.name, 128) || !isDisplayable(beacon.version, 64)) return null
        if (beacon.port !in 1..65535) return null
        if (beacon.ts < nowEpochSeconds - MAX_AGE_SECONDS ||
            beacon.ts > nowEpochSeconds + MAX_AGE_SECONDS
        ) {
            return null
        }

        val address = beacon.address.trim().ifEmpty { sourceAddress.trim() }
        if (!isValidIpv4(address)) return null

        return DiscoveredRouter(
            id = beacon.id,
            name = beacon.name.trim(),
            version = beacon.version.trim(),
            address = address,
            port = beacon.port,
        )
    }

    private fun isDisplayable(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength && value.none { it.code < 32 || it.code == 127 }

    private fun isValidIpv4(address: String): Boolean {
        val octets = address.split('.')
        return octets.size == 4 && octets.all { octet ->
            octet.length in 1..3 &&
                octet.all(Char::isDigit) &&
                (octet.length == 1 || octet.first() != '0') &&
                octet.toIntOrNull() in 0..255
        }
    }

    @Serializable
    private data class FleetBeacon(
        val v: Int,
        val type: String,
        val id: String,
        val name: String,
        val version: String,
        val address: String = "",
        val port: Int,
        val ts: Long,
    )
}
