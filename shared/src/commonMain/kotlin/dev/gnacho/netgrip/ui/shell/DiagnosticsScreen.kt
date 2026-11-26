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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.LedDto
import dev.gnacho.netgrip.data.dto.LogEntryDto
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

/**
 * Diagnostico: el registro del sistema y los LEDs del router, las dos lecturas
 * que sirve el panel (`/api/logs` y `/api/leds`). Nada de esta pantalla escribe
 * en el equipo.
 */
@Composable
fun DiagnosticsScreen(viewModel: ShellViewModel) {
    val state by viewModel.diagnostics.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureDiagnostics() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.logs.isEmpty() && state.leds.isEmpty() ->
            ErrorBox(state.error!!) { viewModel.loadDiagnostics() }
        else -> {
            var filter by remember(state.filter) { mutableStateOf(state.filter) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NetGripCard(
                        eyebrow = "Registro",
                        title = "Lo último del sistema",
                        subtitle = if (state.logTotal > 0) {
                            "${state.logs.size} de ${state.logTotal} líneas"
                        } else {
                            "Sin líneas"
                        },
                        icon = NetGripIcons.Diagnostics,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = filter,
                                onValueChange = { filter = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Filtrar por texto") },
                            )
                            IconButton(onClick = { viewModel.loadDiagnostics(filter) }) {
                                Icon(NetGripIcons.Refresh, contentDescription = "Buscar")
                            }
                        }
                        if (state.logs.isEmpty()) {
                            Text(
                                "No hay líneas que coincidan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                    }
                }
                items(state.logs) { entry -> LogRow(entry) }
                item {
                    NetGripCard(
                        eyebrow = "Luces",
                        title = "LEDs del equipo",
                        subtitle = if (state.ledsApplicable) {
                            "${state.leds.size} configurados"
                        } else {
                            "Este equipo no expone LEDs"
                        },
                        icon = NetGripIcons.Wifi,
                    ) {
                        if (state.leds.isEmpty()) {
                            Text(
                                if (state.ledsApplicable) "Sin LEDs configurados." else "No aplica en este equipo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                items(state.leds) { led -> LedRow(led) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntryDto) {
    NetGripCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Pill(
                text = entry.level.ifBlank { "log" },
                tone = when (entry.level.lowercase()) {
                    "err", "crit", "alert", "emerg" -> Tone.DANGER
                    "warn", "warning" -> Tone.WARN
                    else -> Tone.NEUTRAL
                },
            )
            Text(
                text = entry.tag.ifBlank { "—" },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.ts,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun LedRow(led: LedDto) {
    NetGripCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = led.name.ifBlank { led.section },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            led.on?.let { on ->
                Pill(text = if (on) "Encendido" else "Apagado", tone = if (on) Tone.SUCCESS else Tone.NEUTRAL)
            }
        }
        Text(
            text = listOfNotNull(
                led.trigger?.takeIf { it.isNotBlank() }?.let { "trigger $it" },
                led.dev?.takeIf { it.isNotBlank() }?.let { "dev $it" },
                led.sysfs.takeIf { it.isNotBlank() },
            ).joinToString(" · "),
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
