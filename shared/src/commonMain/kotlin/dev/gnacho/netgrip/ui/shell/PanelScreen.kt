package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.panel.HealthLevel
import dev.gnacho.netgrip.domain.panel.HealthReason
import dev.gnacho.netgrip.domain.panel.HealthScore
import dev.gnacho.netgrip.domain.panel.HealthScoreCalculator
import dev.gnacho.netgrip.domain.panel.HealthTone
import dev.gnacho.netgrip.domain.ports.PortsInsight
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.PercentBar
import dev.gnacho.netgrip.ui.common.SectionCard

@Composable
fun PanelScreen(viewModel: ShellViewModel) {
    val state by viewModel.panel.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensurePanel() }

    val health = remember(state.system, state.wan, state.mode) {
        HealthScoreCalculator.compute(state.system, state.wan, state.mode)
    }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.board == null && state.system == null && state.wan == null ->
            ErrorBox(state.error!!) { viewModel.loadPanel() }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { message -> item { InlineWarning(message) } }
            item { BoardCard(state.board) }
            item { HealthCard(health) }
            item { InternetCard(state.wan, state.mode) }
            item { ResourcesCard(state.system) }
            item { ChassisCard(state.ports) }
            item { ClientsSummaryCard(state.clients) }
        }
    }
}

@Composable
private fun BoardCard(board: BoardDto?) {
    SectionCard(title = "Equipo") {
        InfoRow("Nombre", board?.hostname?.takeIf { it.isNotBlank() } ?: "Sin dato")
        InfoRow("Modelo", board?.model?.takeIf { it.isNotBlank() } ?: "Sin dato")
        InfoRow("Firmware", firmwareLabel(board))
        InfoRow("Kernel", board?.kernel?.takeIf { it.isNotBlank() } ?: "Sin dato")
    }
}

private fun firmwareLabel(board: BoardDto?): String {
    val release = board?.release ?: return "Sin dato"
    val name = listOfNotNull(release.distribution, release.version).filter { it.isNotBlank() }.joinToString(" ")
    return name.ifBlank { "Sin dato" }
}

@Composable
private fun HealthCard(health: HealthScore) {
    val tone = when (health.tone) {
        HealthTone.OK -> BadgeTone.OK
        HealthTone.WARN -> BadgeTone.WARN
        HealthTone.DANGER -> BadgeTone.DANGER
    }
    SectionCard(
        title = "Salud",
        subtitle = "Estimación con WAN, memoria, flash y carga",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                health.score.toString(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Column {
                Badge(healthLabel(health.level), tone)
                Text(
                    "de 100",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PercentBar(health.score / 100f, tone)
        if (health.reasons.isEmpty()) {
            Text(
                "Sin avisos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            health.reasons.forEach { reason ->
                Text(
                    reasonText(reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

@Composable
private fun InternetCard(wan: WanDto?, mode: ModeDto?) {
    SectionCard(title = "Internet") {
        val isAp = mode?.effectiveRole == "ap"
        when {
            isAp -> Text(
                "Este equipo trabaja como punto de acceso. La salida a Internet la da el router principal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wan == null -> Text(
                "Sin datos de la WAN.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !wan.present -> Text(
                "No hay una WAN configurada en este equipo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(if (wan.up) "Conectada" else "Caída", if (wan.up) BadgeTone.OK else BadgeTone.DANGER)
                    if (wan.uptime > 0) Badge("Activa ${Formats.uptime(wan.uptime)}")
                }
                InfoRow("Dirección IP", wan.ipv4.joinToString(", ").ifBlank { "Sin dirección" })
                InfoRow("Puerta de enlace", wan.gateway?.takeIf { it.isNotBlank() } ?: "Sin dato")
                InfoRow("DNS", wan.dns.joinToString(", ").ifBlank { "Sin dato" })
            }
        }
    }
}

@Composable
private fun ResourcesCard(system: SystemDto?) {
    SectionCard(title = "Recursos") {
        if (system == null) {
            Text(
                "Sin datos del sistema.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        val load = Formats.load(system.load)
        InfoRow("Carga 1/5/15", load.ifBlank { "Sin dato" })
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
            PercentBar(usedPct / 100f, tone)
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
            PercentBar(1f - freePct / 100f, tone)
        }
        InfoRow("Encendido", Formats.uptime(system.uptime))
    }
}

@Composable
private fun ChassisCard(ports: List<EthPortDto>) {
    SectionCard(
        title = "Chasis",
        subtitle = if (ports.isEmpty()) null else "${PortsInsight.activeCount(ports)} de ${ports.size} puertos activos",
    ) {
        if (ports.isEmpty()) {
            Text(
                "Sin datos de puertos Ethernet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        ports.forEach { port ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    port.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (port.wan) Badge("WAN")
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
    val wifi = clients.count { it.type.startsWith("wifi") }
    val cable = clients.count { it.type == "cable" }
    SectionCard(
        title = "Clientes",
        subtitle = if (clients.isEmpty()) null else "${clients.size} conectados",
    ) {
        if (clients.isEmpty()) {
            Text(
                "No hay clientes conectados.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("WiFi $wifi", BadgeTone.NEUTRAL)
                Badge("Cable $cable", BadgeTone.NEUTRAL)
            }
            clients.filter { it.self }.firstOrNull()?.let { own ->
                Text(
                    "Este móvil: ${own.ip ?: own.mac}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
