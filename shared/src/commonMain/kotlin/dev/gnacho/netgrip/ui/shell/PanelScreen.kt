package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.history.TrafficRange
import dev.gnacho.netgrip.domain.history.TrafficSeries
import dev.gnacho.netgrip.domain.panel.HealthLevel
import dev.gnacho.netgrip.domain.panel.HealthReason
import dev.gnacho.netgrip.domain.panel.HealthScore
import dev.gnacho.netgrip.domain.panel.HealthScoreCalculator
import dev.gnacho.netgrip.domain.panel.HealthTone
import dev.gnacho.netgrip.domain.ports.PortsInsight
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.GaugeRing
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.MeterBar
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.common.content
import dev.gnacho.netgrip.ui.common.TrafficChart
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

@Composable
fun PanelScreen(viewModel: ShellViewModel) {
    val state by viewModel.panel.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensurePanel() }

    val health = remember(state.system, state.wan, state.mode) {
        HealthScoreCalculator.compute(state.system, state.wan, state.mode)
    }
    val samples = remember(state.history) { TrafficSeries.fromHistory(state.history) }
    var range by remember { mutableStateOf(TrafficRange.SIX_HOURS) }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.board == null && state.system == null && state.wan == null ->
            ErrorBox(state.error!!) { viewModel.loadPanel() }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { message -> item { InlineWarning(message) } }
            item { HealthCard(health) }
            item { TrafficCard(samples, range) { range = it } }
            item { EquipmentCard(state.board) }
            item { InternetCard(state.wan, state.mode) }
            item { ResourcesCard(state.system) }
            item { ChassisCard(state.ports) }
            item { ClientsSummaryCard(state.clients) }
        }
    }
}

@Composable
private fun HealthCard(health: HealthScore) {
    val tone = when (health.tone) {
        HealthTone.OK -> Tone.SUCCESS
        HealthTone.WARN -> Tone.WARN
        HealthTone.DANGER -> Tone.DANGER
    }
    NetGripCard(
        eyebrow = "Estado general",
        title = healthLabel(health.level),
        subtitle = "Estimación con WAN, memoria, flash y carga",
        icon = NetGripIcons.Activity,
        iconTone = tone,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GaugeRing(
                fraction = health.score / 100f,
                color = tone.content(),
                size = 104.dp,
                strokeWidth = 9.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        health.score.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "de 100",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.netGrip.faint,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                if (health.reasons.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("Sin avisos", Tone.SUCCESS)
                    }
                    Text(
                        "Todo funciona como debe.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    health.reasons.take(3).forEach { reason ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                            Pill("Aviso", Tone.WARN, dot = false)
                            Text(
                                reasonText(reason),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (health.reasons.size > 3) {
                        Text(
                            "y ${health.reasons.size - 3} más",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.netGrip.faint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficCard(
    samples: List<dev.gnacho.netgrip.domain.history.TrafficSample>,
    range: TrafficRange,
    onRangeChange: (TrafficRange) -> Unit,
) {
    val window = remember(samples, range) { TrafficSeries.window(samples, range) }
    NetGripCard(
        eyebrow = "Tráfico del puente LAN",
        title = "Tráfico de la red",
        icon = NetGripIcons.Activity,
        iconTone = Tone.ACCENT,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SegmentedControl(
                options = TrafficRange.entries.map { it to it.label },
                selected = range,
                onSelect = onRangeChange,
            )
        }
        Spacer(Modifier.size(10.dp))
        if (window.size < 2) {
            EmptyStateView(
                title = "Sin histórico todavía",
                body = "El router guarda una muestra cada 5 minutos. La gráfica aparece en cuanto haya dos.",
                icon = NetGripIcons.Activity,
            )
        } else {
            TrafficChart(
                rx = window.map { it.rxRate },
                tx = window.map { it.txRate },
                times = window.map { it.timestamp },
                rxLabel = "Entrada",
                txLabel = "Salida",
            )
            Spacer(Modifier.size(10.dp))
            CardDivider()
            Spacer(Modifier.size(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TrafficMetric("Entrada", Formats.bytes(TrafficSeries.transferred(window, rx = true)), MaterialTheme.netGrip.chartRx)
                TrafficMetric("Salida", Formats.bytes(TrafficSeries.transferred(window, rx = false)), MaterialTheme.netGrip.chartTx)
                TrafficMetric("Pico", Formats.rate(TrafficSeries.peakBytesPerSecond(window)), MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun TrafficMetric(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.netGrip.faint)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun EquipmentCard(board: BoardDto?) {
    NetGripCard(
        eyebrow = "Equipo",
        title = board?.hostname?.takeIf { it.isNotBlank() } ?: "Router",
        subtitle = board?.model?.takeIf { it.isNotBlank() },
        icon = NetGripIcons.Router,
        iconTone = Tone.TEAL,
    ) {
        InfoRow("Firmware", firmwareLabel(board))
        InfoRow("Kernel", board?.kernel?.takeIf { it.isNotBlank() } ?: "Sin dato", mono = true)
    }
}

private fun firmwareLabel(board: BoardDto?): String {
    val release = board?.release ?: return "Sin dato"
    val name = listOfNotNull(release.distribution, release.version).filter { it.isNotBlank() }.joinToString(" ")
    return name.ifBlank { "Sin dato" }
}

@Composable
private fun InternetCard(wan: WanDto?, mode: ModeDto?) {
    NetGripCard(
        eyebrow = "Conexión",
        title = "Internet",
        icon = NetGripIcons.Cable,
        iconTone = Tone.ACCENT,
    ) {
        val isAp = mode?.effectiveRole == "ap"
        when {
            isAp -> Text(
                "Este equipo trabaja como punto de acceso. La salida a Internet la da el router principal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wan == null -> Text(
                "Sin datos de la WAN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !wan.present -> Text(
                "No hay una WAN configurada en este equipo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(if (wan.up) "Conectada" else "Caída", if (wan.up) Tone.SUCCESS else Tone.DANGER)
                    if (wan.uptime > 0) Pill("Activa ${Formats.uptime(wan.uptime)}", Tone.NEUTRAL, dot = false)
                }
                Spacer(Modifier.size(8.dp))
                InfoRow("Dirección IP", wan.ipv4.joinToString(", ").ifBlank { "Sin dirección" }, mono = true)
                InfoRow("Puerta de enlace", wan.gateway?.takeIf { it.isNotBlank() } ?: "Sin dato", mono = true)
                InfoRow("DNS", wan.dns.joinToString(", ").ifBlank { "Sin dato" }, mono = true)
            }
        }
    }
}

@Composable
private fun ResourcesCard(system: SystemDto?) {
    NetGripCard(
        eyebrow = "Sistema",
        title = "Recursos",
        icon = NetGripIcons.DeviceOther,
        iconTone = Tone.VIOLET,
    ) {
        if (system == null) {
            Text(
                "Sin datos del sistema.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        val load = Formats.load(system.load)
        InfoRow("Carga 1/5/15", load.ifBlank { "Sin dato" }, mono = true)
        if (system.memory.total > 0) {
            val usedPct = Formats.percent(system.memory.total - system.memory.available, system.memory.total)
            val tone = when {
                usedPct >= 90 -> BadgeTone.DANGER
                usedPct >= 75 -> BadgeTone.WARN
                else -> BadgeTone.OK
            }
            InfoRow(
                "Memoria",
                "${Formats.bytes(system.memory.total - system.memory.available)} de ${Formats.bytes(system.memory.total)} ($usedPct %)",
            )
            Spacer(Modifier.size(4.dp))
            MeterBar(usedPct / 100f, when (tone) {
                BadgeTone.DANGER -> MaterialTheme.colorScheme.error
                BadgeTone.WARN -> MaterialTheme.netGrip.warn
                else -> MaterialTheme.netGrip.success
            })
            Spacer(Modifier.size(8.dp))
        }
        if (system.root.total > 0) {
            val freePct = Formats.percent(system.root.free, system.root.total)
            val tone = when {
                freePct <= 10 -> BadgeTone.DANGER
                freePct <= 20 -> BadgeTone.WARN
                else -> BadgeTone.OK
            }
            InfoRow(
                "Flash",
                "Libre ${Formats.kib(system.root.free)} de ${Formats.kib(system.root.total)} ($freePct %)",
            )
            Spacer(Modifier.size(4.dp))
            MeterBar(1f - freePct / 100f, when (tone) {
                BadgeTone.DANGER -> MaterialTheme.colorScheme.error
                BadgeTone.WARN -> MaterialTheme.netGrip.warn
                else -> MaterialTheme.netGrip.success
            })
            Spacer(Modifier.size(8.dp))
        }
        InfoRow("Encendido", Formats.uptime(system.uptime))
    }
}

@Composable
private fun ChassisCard(ports: List<EthPortDto>) {
    NetGripCard(
        eyebrow = "Ethernet",
        title = "Chasis",
        subtitle = if (ports.isEmpty()) null else "${PortsInsight.activeCount(ports)} de ${ports.size} puertos activos",
        icon = NetGripIcons.Ports,
        iconTone = Tone.TEAL,
    ) {
        if (ports.isEmpty()) {
            Text(
                "Sin datos de puertos Ethernet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        ports.forEach { port ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    port.name,
                    modifier = Modifier.width(46.dp),
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        !port.up -> "Sin enlace"
                        PortsInsight.isDegradedLink(port) -> "Enlace por debajo de 1 Gb/s"
                        else -> "Conectado"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (port.wan) Badge("WAN", BadgeTone.NEUTRAL)
                Badge(
                    if (port.up) Formats.speed(port.speedMbps) else "Sin enlace",
                    if (port.up) (if (PortsInsight.isDegradedLink(port)) BadgeTone.WARN else BadgeTone.OK) else BadgeTone.NEUTRAL,
                )
            }
        }
    }
}

@Composable
private fun ClientsSummaryCard(clients: List<ClientDto>) {
    val band24 = clients.count { it.type == "wifi24" }
    val band5 = clients.count { it.type == "wifi5" }
    val cable = clients.count { it.type == "cable" }
    NetGripCard(
        eyebrow = "Dispositivos",
        title = "Clientes conectados",
        subtitle = if (clients.isEmpty()) null else "${clients.size} en total",
        icon = NetGripIcons.Clients,
        iconTone = Tone.SUCCESS,
    ) {
        if (clients.isEmpty()) {
            Text(
                "No hay clientes conectados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("$band24 en 2,4 GHz", Tone.SUCCESS)
            Pill("$band5 en 5 GHz", Tone.ACCENT)
            Pill("$cable por cable", Tone.NEUTRAL)
        }
        clients.firstOrNull { it.self }?.let { own ->
            Spacer(Modifier.size(8.dp))
            Text(
                "Este móvil: ${own.ip ?: own.mac}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun healthLabel(level: HealthLevel): String = when (level) {
    HealthLevel.EXCELLENT -> "Excelente"
    HealthLevel.GOOD -> "Buena"
    HealthLevel.ATTENTION -> "Atención"
    HealthLevel.CRITICAL -> "Crítica"
}

private fun reasonText(reason: HealthReason): String = when (reason) {
    HealthReason.NoInternet -> "Sin Internet en la WAN."
    is HealthReason.Memory -> "Memoria usada al ${reason.usedPct} %."
    is HealthReason.Flash -> "Queda un ${reason.freePct} % de flash libre."
    HealthReason.Load -> "Carga del sistema alta."
    HealthReason.WifiOff -> "Todas las radios WiFi están apagadas."
}
