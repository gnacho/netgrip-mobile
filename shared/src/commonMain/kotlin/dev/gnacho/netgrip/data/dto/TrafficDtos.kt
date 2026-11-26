package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GET /api/nlbwmon: el contable por flujo del router, solo lectura.
 *
 * Cuando nlbwmon no esta instalado el router responde `installed: false` y
 * ceros en el resto: no hay contabilidad que enseñar y la app no la inventa.
 */
@Serializable
data class NlbwmonProbeDto(
    val installed: Boolean = false,
    val running: Boolean = false,
    val generations: Int = 0,
    @SerialName("commit_interval") val commitInterval: Int = 0,
    @SerialName("prealloc_days") val preallocDays: Int = 0,
    @SerialName("protocol_database") val protocolDatabase: Boolean = false,
)

/** GET /api/nlbwmon/top: los que mas movieron, por dispositivo y por protocolo. */
@Serializable
data class NlbwmonTopDto(
    val devices: List<NlbwUsageDto> = emptyList(),
    val apps: List<NlbwUsageDto> = emptyList(),
)

/**
 * Una fila contada por nlbwmon.
 *
 * [downBytes] y [upBytes] son desde el punto de vista del dispositivo, al
 * contrario de ClientDto.rxBytes, donde Rx es lo que recibio el punto de
 * acceso. Son bytes contados por el router, nunca tasas calculadas.
 */
@Serializable
data class NlbwUsageDto(
    /** MAC en una fila de dispositivo, nombre del protocolo en una de aplicacion. */
    val key: String = "",
    val ip: String = "",
    val conns: Long = 0,
    @SerialName("down_bytes") val downBytes: Long = 0,
    @SerialName("up_bytes") val upBytes: Long = 0,
)

/**
 * GET /api/dpi: protocolos que nDPI reconocio en el trafico reenviado.
 *
 * [protocols] puede llegar `null`: el panel lo serializaba asi cuando no podia
 * leer `/proc/net/nf_conntrack` (bug corregido en el servidor, #661). La app lo
 * acepta y lo normaliza en TrafficRules en vez de romper la pantalla.
 */
@Serializable
data class DpiProbeDto(
    val applicable: Boolean = false,
    @SerialName("total_bytes") val totalBytes: Long = 0,
    @SerialName("total_flows") val totalFlows: Int = 0,
    val protocols: List<DpiProtocolDto>? = null,
)

@Serializable
data class DpiProtocolDto(
    val name: String = "",
    val bytes: Long = 0,
    val flows: Int = 0,
    val category: String = "",
)

/** GET /api/dpi/apps: aplicaciones identificadas por netifyd. */
@Serializable
data class DpiAppsDto(
    val apps: List<DpiAppDto> = emptyList(),
)

@Serializable
data class DpiAppDto(
    val name: String = "",
    val category: String = "",
    val bytes: Long = 0,
    @SerialName("local_bytes") val localBytes: Long = 0,
    @SerialName("other_bytes") val otherBytes: Long = 0,
    val packets: Long = 0,
    val flows: Int = 0,
)

/** GET /api/dpi/timeline: cubos de 5 minutos de las ultimas 24 h. */
@Serializable
data class DpiTimelineDto(
    val buckets: List<DpiBucketDto> = emptyList(),
    val top: List<DpiAppDto> = emptyList(),
    val totals: DpiCountersDto = DpiCountersDto(),
)

@Serializable
data class DpiBucketDto(
    /** Inicio del cubo en RFC3339 UTC. */
    val time: String = "",
    /** Bytes por aplicacion dentro del cubo. */
    val apps: Map<String, DpiCountersDto> = emptyMap(),
)

@Serializable
data class DpiCountersDto(
    val local: Long = 0,
    val other: Long = 0,
    val total: Long = 0,
)
