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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.wifi.WifiGroup
import dev.gnacho.netgrip.domain.wifi.WifiGrouping
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

@Composable
fun WifiScreen(viewModel: ShellViewModel) {
    val state by viewModel.wifi.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureWifi() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.interfaces.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadWifi() }
        !state.loaded -> LoadingBox()
        !state.hasWifi -> EmptyStateView(
            title = "Este equipo no tiene radios WiFi",
            body = "Trabaja solo con puertos Ethernet.",
            icon = NetGripIcons.Wifi,
            modifier = Modifier.fillMaxSize(),
        )
        state.interfaces.isEmpty() -> EmptyStateView(
            title = "No hay redes WiFi configuradas",
            body = "Configura una red desde el panel web de NetGrip.",
            icon = NetGripIcons.Wifi,
            modifier = Modifier.fillMaxSize(),
        )
        else -> {
            val groups = remember(state.interfaces, state.radios) {
                WifiGrouping.build(state.interfaces, state.radios)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.error?.let { message -> item { InlineWarning(message) } }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            NetGripIcons.EyeOff,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.netGrip.faint,
                        )
                        Text(
                            "La clave de la red nunca se muestra en esta app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(groups, key = { it.ssid }) { group ->
                    WifiGroupCard(group)
                }
            }
        }
    }
}

@Composable
private fun WifiGroupCard(group: WifiGroup) {
    NetGripCard(
        eyebrow = if (group.disabled) "Desactivada" else "Red WiFi",
        title = WifiGrouping.ssidLabel(group.ssid),
        subtitle = if (group.clientCount == 1) "1 cliente" else "${group.clientCount} clientes",
        icon = NetGripIcons.Wifi,
        iconTone = if (group.disabled) Tone.DANGER else Tone.TEAL,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (group.disabled) Pill("Desactivada", Tone.DANGER)
            if (group.hidden) Pill("Oculta", Tone.WARN)
            group.bands.forEach { band -> Pill(WifiGrouping.bandLabel(band), Tone.NEUTRAL, dot = false) }
        }
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Cifrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                group.encryptions.joinToString(", ").ifBlank { "Abierta" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (group.encryptions.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (group.radios.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
        }
        group.radios.forEach { radio ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            radio.name,
                            modifier = Modifier.weight(1f),
                            style = MonoStyle,
                            fontWeight = FontWeight.Medium,
                        )
                        Pill(
                            if (radio.up) "Activa" else "Apagada",
                            if (radio.up) Tone.SUCCESS else Tone.DANGER,
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
