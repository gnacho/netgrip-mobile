package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.ports.PortsInsight
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.EmptyBox
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox

@Composable
fun PortsScreen(viewModel: ShellViewModel) {
    val state by viewModel.ports.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensurePorts() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.ports.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadPorts() }
        !state.loaded -> LoadingBox()
        state.ports.isEmpty() -> EmptyBox("El router no ha devuelto puertos Ethernet.")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.error?.let { message -> item { InlineWarning(message) } }
            item {
                Text(
                    "${PortsInsight.activeCount(state.ports)} de ${state.ports.size} puertos activos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.ports, key = { it.name }) { port ->
                PortCard(port)
            }
        }
    }
}

@Composable
private fun PortCard(port: EthPortDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    port.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (port.wan) Badge("WAN")
                Badge(
                    if (port.up) Formats.speed(port.speedMbps) else "Sin enlace",
                    if (port.up) BadgeTone.OK else BadgeTone.NEUTRAL,
                )
            }
            if (PortsInsight.isDegradedLink(port)) {
                Badge("Enlace por debajo de 1 Gb/s", BadgeTone.WARN)
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
        }
    }
}
