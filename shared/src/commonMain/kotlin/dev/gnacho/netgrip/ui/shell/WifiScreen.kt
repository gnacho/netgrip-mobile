package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.wifi.WifiGroup
import dev.gnacho.netgrip.domain.wifi.WifiGrouping
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.EmptyBox
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.SectionCard

@Composable
fun WifiScreen(viewModel: ShellViewModel) {
    val state by viewModel.wifi.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureWifi() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.interfaces.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadWifi() }
        !state.loaded -> LoadingBox()
        !state.hasWifi -> EmptyBox("Este equipo no tiene radios WiFi.")
        state.interfaces.isEmpty() -> EmptyBox("No hay redes WiFi configuradas.")
        else -> {
            val groups = remember(state.interfaces, state.radios) {
                WifiGrouping.build(state.interfaces, state.radios)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.error?.let { message -> item { InlineWarning(message) } }
                item {
                    Text(
                        "La clave de la red nunca se muestra en esta app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(groups.size) { index ->
                    WifiGroupCard(groups[index])
                }
            }
        }
    }
}

@Composable
private fun WifiGroupCard(group: WifiGroup) {
    SectionCard(
        title = WifiGrouping.ssidLabel(group.ssid),
        subtitle = if (group.clientCount == 1) "1 cliente" else "${group.clientCount} clientes",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (group.disabled) Badge("Desactivada", BadgeTone.DANGER)
            if (group.hidden) Badge("Oculta", BadgeTone.WARN)
            group.bands.forEach { band -> Badge(WifiGrouping.bandLabel(band)) }
        }
        InfoRow("Cifrado", group.encryptions.joinToString(", ").ifBlank { "Abierta" })
        group.radios.forEach { radio ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        radio.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Badge(
                        if (radio.up) "Radio activa" else "Radio apagada",
                        if (radio.up) BadgeTone.OK else BadgeTone.DANGER,
                    )
                }
                Text(
                    listOfNotNull(
                        WifiGrouping.bandLabel(radio.band),
                        radio.channel.takeIf { it.isNotBlank() }?.let { "canal $it" },
                        radio.htmode.takeIf { it.isNotBlank() },
                        if (radio.txPower > 0) "${radio.txPower} dBm" else null,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (group.radios.isEmpty()) {
            Text(
                "Sin datos de la radio física.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
