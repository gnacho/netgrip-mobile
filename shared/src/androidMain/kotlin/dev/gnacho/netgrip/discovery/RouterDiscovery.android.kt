package dev.gnacho.netgrip.discovery

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

private const val DISCOVERY_PORT = 5141
private const val DISCOVERY_TIMEOUT_MILLIS = 3_000L
private const val SOCKET_READ_TIMEOUT_MILLIS = 250L
private const val PACKET_BUFFER_BYTES = 2_048
private val PROBE_PAYLOAD = """{"v":1,"type":"netgrip-fleet-probe"}""".toByteArray(Charsets.UTF_8)

actual fun platformRouterDiscovery(): RouterDiscovery = AndroidRouterDiscovery()

class AndroidRouterDiscovery(context: Context? = null) : RouterDiscovery {
    override val isSupported: Boolean = true
    private val wifiManager = context?.applicationContext?.getSystemService(WifiManager::class.java)

    override suspend fun discover(): List<DiscoveredRouter> = withContext(Dispatchers.IO) {
        val results = FleetDiscoveryBuffer()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            sendProbeTargets(interfaceBroadcastAddresses(wifiManager)) { target ->
                socket.send(DatagramPacket(PROBE_PAYLOAD, PROBE_PAYLOAD.size, target, DISCOVERY_PORT))
            }

            val bytes = ByteArray(PACKET_BUFFER_BYTES)
            val packet = DatagramPacket(bytes, bytes.size)
            val deadline = System.nanoTime() + DISCOVERY_TIMEOUT_MILLIS * NANOS_PER_MILLI
            while (true) {
                val remainingNanos = deadline - System.nanoTime()
                if (remainingNanos <= 0) break
                val remainingMillis = (remainingNanos + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI
                socket.soTimeout = remainingMillis.coerceAtMost(SOCKET_READ_TIMEOUT_MILLIS).toInt()
                packet.length = bytes.size
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val source = packet.address?.hostAddress ?: continue
                results.offer(
                    payload = String(packet.data, packet.offset, packet.length, Charsets.UTF_8),
                    sourceAddress = source,
                    nowEpochSeconds = System.currentTimeMillis() / 1_000L,
                )
            }
            results.snapshot()
        }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}

internal fun interfaceBroadcastAddresses(wifiManager: WifiManager? = null): List<InetAddress> {
    val targets = linkedSetOf<InetAddress>()
    wifiManager?.dhcpInfo?.let { info ->
        ipv4BroadcastFromDhcp(info.ipAddress, info.netmask)?.let(targets::plusAssign)
    }
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList().forEach { iface ->
            if (runCatching { iface.isUp }.getOrDefault(false) ||
                runCatching { iface.isLoopback }.getOrDefault(true)
            ) {
                return@forEach
            }
            iface.interfaceAddresses.forEach { address ->
                val ip = address.address
                if (ip is Inet4Address) {
                    val directed = address.broadcast
                        ?: ipv4Broadcast(ip, address.networkPrefixLength.toInt())
                    if (directed != null) targets += directed
                }
            }
        }
    }
    targets += InetAddress.getByName("255.255.255.255")
    return targets.toList()
}

internal fun ipv4BroadcastFromDhcp(ipAddress: Int, netmask: Int): InetAddress? {
    if (ipAddress == 0 || netmask == 0) return null
    val broadcast = ipAddress or netmask.inv()
    return runCatching {
        InetAddress.getByAddress(ByteArray(4) { index ->
            (broadcast ushr (24 - index * 8)).toByte()
        })
    }.getOrNull()
}

internal fun ipv4Broadcast(address: Inet4Address, prefixLength: Int): InetAddress? {
    if (prefixLength !in 0..32) return null
    val mask = if (prefixLength == 0) 0 else -1 shl (32 - prefixLength)
    val numericAddress = address.address.fold(0) { result, octet ->
        (result shl 8) or (octet.toInt() and 0xFF)
    }
    val broadcast = numericAddress or mask.inv()
    return runCatching {
        InetAddress.getByAddress(ByteArray(4) { index ->
            (broadcast ushr (24 - index * 8)).toByte()
        })
    }.getOrNull()
}

internal fun sendProbeTargets(targets: List<InetAddress>, send: (InetAddress) -> Unit) {
    var lastError: IOException? = null
    var sent = false
    targets.forEach { target ->
        try {
            send(target)
            sent = true
        } catch (error: IOException) {
            lastError = error
        }
    }
    if (!sent && lastError != null) throw lastError
}
