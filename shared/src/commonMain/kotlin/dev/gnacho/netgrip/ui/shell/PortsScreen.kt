package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.ports.PortsInsight
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.MeterBar
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.common.content
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

@Composable
fun PortsScreen(viewModel: ShellViewModel) {
    val state by viewModel.ports.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensurePorts() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.ports.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadPorts() }
        !state.loaded -> LoadingBox()
        state.ports.isEmpty() -> EmptyStateView(
            title = "Sin puertos Ethernet",
            body = "El router no ha devuelto puertos.",
            icon = NetGripIcons.Ports,
            modifier = Modifier.fillMaxSize(),
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { message -> item { InlineWarning(message) } }
            item {
                NetGripCard(
                    eyebrow = "Ethernet",
                    title = "Chasis",
                    subtitle = "${PortsInsight.activeCount(state.ports)} de ${state.ports.size} puertos activos",
                    icon = NetGripIcons.Ports,
                    iconTone = Tone.TEAL,
                ) {
                    state.ports.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { port ->
                                PortTile(port, Modifier.weight(1f))
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
            item {
                NetGripCard(
                    eyebrow = "Detalle",
                    title = "Dispositivos por puerto",
                    icon = NetGripIcons.Cable,
                    iconTone = Tone.ACCENT,
                ) {
                    state.ports.forEach { port ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    port.name,
                                    modifier = Modifier.weight(1f),
                                    style = MonoStyle,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (port.wan) Pill("WAN", Tone.VIOLET, dot = false)
                                Pill(
                                    if (port.up) Formats.speed(port.speedMbps) else "Sin enlace",
                                    when {
                                        !port.up -> Tone.NEUTRAL
                                        PortsInsight.isDegradedLink(port) -> Tone.WARN
                                        else -> Tone.SUCCESS
                                    },
                                    dot = false,
                                )
                            }
                            if (port.devices.isEmpty()) {
                                Text(
                                    if (port.up) "Conectado, sin dispositivos identificados" else "Sin dispositivos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                port.devices.forEach { device ->
                                    Text(
                                        PortsInsight.deviceLabel(device),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (PortsInsight.isDegradedLink(port)) {
                                Text(
                                    "Negocia por debajo de 1 Gb/s: cable dañado o de 4 hilos.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.netGrip.warn,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Baldosa de puerto, al estilo del chasis del panel web. */
@Composable
private fun PortTile(port: EthPortDto, modifier: Modifier = Modifier) {
    val tone = when {
        !port.up -> Tone.NEUTRAL
        PortsInsight.isDegradedLink(port) -> Tone.WARN
        else -> Tone.SUCCESS
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    port.name,
                    modifier = Modifier.weight(1f),
                    style = MonoStyle,
                    fontWeight = FontWeight.SemiBold,
                )
                if (port.wan) Pill("WAN", Tone.VIOLET, dot = false)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(
                    if (port.up) "Enlace" else "Sin enlace",
                    tone,
                )
            }
            Text(
                if (port.up) Formats.speed(port.speedMbps) else "Sin cable",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            MeterBar(
                fraction = if (!port.up) 0f else (port.speedMbps / 1000f).coerceIn(0.1f, 1f),
                color = tone.content(),
                height = 4,
            )
        }
    }
}
