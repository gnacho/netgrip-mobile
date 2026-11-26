package dev.gnacho.netgrip.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /api/diagnostics/selftest. */
@Serializable
data class SelfTestDto(
    val gateway: Boolean = false,
    val wan: Boolean = false,
    val dns: Boolean = false,
    val ntp: Boolean = false,
    @SerialName("all_ok") val allOk: Boolean = false,
    val checks: List<SelfTestCheckDto> = emptyList(),
    val tools: SelfTestToolsDto = SelfTestToolsDto(),
)

@Serializable
data class SelfTestCheckDto(
    val key: String = "",
    val ok: Boolean = false,
    /** Algo que no se pudo demostrar pero no es una averia. */
    val info: Boolean = false,
    val detail: String = "",
)

@Serializable
data class SelfTestToolsDto(
    val ping: Boolean = false,
    val traceroute: Boolean = false,
    val nslookup: Boolean = false,
    val dig: Boolean = false,
)

/** POST /api/diagnostics: una prueba puntual. */
@Serializable
data class DiagnosticsRunRequest(
    val test: String,
    val host: String = "",
    val count: Int = 0,
    val query: String = "",
    val resolver: String = "",
    val port: Int = 0,
)

@Serializable
data class PingSampleDto(
    val seq: Int = 0,
    @SerialName("time_ms") val timeMs: Double = 0.0,
    val error: Boolean = false,
)

@Serializable
data class PingResultDto(
    val host: String = "",
    val count: Int = 0,
    val sent: Int = 0,
    val received: Int = 0,
    @SerialName("loss_pct") val lossPct: Double = 0.0,
    @SerialName("min_ms") val minMs: Double = 0.0,
    @SerialName("avg_ms") val avgMs: Double = 0.0,
    @SerialName("max_ms") val maxMs: Double = 0.0,
    val samples: List<PingSampleDto> = emptyList(),
    @SerialName("missing_tool") val missingTool: String = "",
)

@Serializable
data class TraceHopDto(
    val hop: Int = 0,
    val host: String = "",
    val rtts: List<Double> = emptyList(),
    val raw: String = "",
    val parsed: Boolean = false,
)

@Serializable
data class TracerouteResultDto(
    val host: String = "",
    val hops: List<TraceHopDto> = emptyList(),
    @SerialName("missing_tool") val missingTool: String = "",
)

@Serializable
data class DnsAnswerDto(
    val name: String = "",
    val type: String = "",
    val value: String = "",
    val ttl: Int = 0,
)

@Serializable
data class DnsResultDto(
    val query: String = "",
    val resolver: String = "",
    val answers: List<DnsAnswerDto> = emptyList(),
    val raw: String = "",
    val parsed: Boolean = false,
    @SerialName("missing_tool") val missingTool: String = "",
)

@Serializable
data class TcpResultDto(
    val host: String = "",
    val port: Int = 0,
    val open: Boolean = false,
    val error: String = "",
)

/** Lo que devuelve una prueba, ya decodificado segun su tipo. */
sealed interface DiagnosticsOutcome {
    data class Ping(val value: PingResultDto) : DiagnosticsOutcome
    data class Trace(val value: TracerouteResultDto) : DiagnosticsOutcome
    data class Dns(val value: DnsResultDto) : DiagnosticsOutcome
    data class Tcp(val value: TcpResultDto) : DiagnosticsOutcome

    /** Texto plano para pintar y copiar. */
    fun lines(): List<String> = when (this) {
        is Ping -> buildList {
            add("ping ${value.host}: ${value.received}/${value.sent} respuestas, ${value.lossPct}% perdidas")
            if (value.missingTool.isNotBlank()) add("herramienta ausente: ${value.missingTool}")
            value.samples.forEach { sample ->
                add(
                    if (sample.error) {
                        "#${sample.seq} error"
                    } else {
                        "#${sample.seq} ${sample.timeMs} ms"
                    },
                )
            }
            if (value.received > 0) {
                add("min ${value.minMs} · media ${value.avgMs} · max ${value.maxMs} ms")
            }
        }
        is Trace -> buildList {
            add("traceroute ${value.host}")
            if (value.missingTool.isNotBlank()) add("herramienta ausente: ${value.missingTool}")
            value.hops.forEach { hop ->
                add(
                    "${hop.hop} ${hop.host.ifBlank { hop.raw }} " +
                        hop.rtts.joinToString(" ") { "$it ms" },
                )
            }
        }
        is Dns -> buildList {
            add("dns ${value.query}" + value.resolver.takeIf { it.isNotBlank() }?.let { " via $it" }.orEmpty())
            if (value.missingTool.isNotBlank()) add("herramienta ausente: ${value.missingTool}")
            value.answers.forEach { answer ->
                add("${answer.name} ${answer.type} ${answer.value}")
            }
            if (value.answers.isEmpty() && value.raw.isNotBlank()) add(value.raw)
        }
        is Tcp -> listOf(
            "tcp ${value.host}:${value.port}: " + (if (value.open) "abierto" else "cerrado o inalcanzable") +
                value.error.takeIf { it.isNotBlank() }?.let { " (${it})" }.orEmpty(),
        )
    }
}
