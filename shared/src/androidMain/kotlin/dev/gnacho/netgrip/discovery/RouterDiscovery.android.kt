package dev.gnacho.netgrip.discovery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

private const val DISCOVERY_PORT = 5141
private const val DISCOVERY_TIMEOUT_MILLIS = 3_000L
private const val SOCKET_READ_TIMEOUT_MILLIS = 250L
private const val PACKET_BUFFER_BYTES = 2_048
private val PROBE_PAYLOAD = """{"v":1,"type":"netgrip-fleet-probe"}""".toByteArray(Charsets.UTF_8)

actual fun platformRouterDiscovery(): RouterDiscovery = AndroidRouterDiscovery()

class AndroidRouterDiscovery : RouterDiscovery {
    override val isSupported: Boolean = true

    override suspend fun discover(): List<DiscoveredRouter> = withContext(Dispatchers.IO) {
        val results = FleetDiscoveryBuffer()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            val broadcast = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(PROBE_PAYLOAD, PROBE_PAYLOAD.size, broadcast, DISCOVERY_PORT))

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
