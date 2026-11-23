package dev.gnacho.netgrip.domain.ports

import dev.gnacho.netgrip.data.dto.EthDeviceDto
import dev.gnacho.netgrip.data.dto.EthPortDto

/** Lecturas derivadas del chasis Ethernet. */
object PortsInsight {

    /** Un puerto activo negociando por debajo de 1 Gb/s (cable corto o de 4 hilos). */
    fun isDegradedLink(port: EthPortDto): Boolean = port.up && port.speedMbps in 1..999

    fun activeCount(ports: List<EthPortDto>): Int = ports.count { it.up }

    fun wanPort(ports: List<EthPortDto>): EthPortDto? = ports.firstOrNull { it.wan }

    fun deviceLabel(device: EthDeviceDto): String =
        device.name?.takeIf { it.isNotBlank() } ?: device.mac
}
