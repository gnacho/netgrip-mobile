package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import dev.gnacho.netgrip.presentation.DiagnosticsState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
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
                    ToolsCards(viewModel, state)
                }
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

/**
 * Comprobacion del router y pruebas puntuales. Nada de esto escribe en el
 * equipo: solo lanza lo que el router ya sabe medir y muestra su respuesta.
 */
@Composable
private fun ToolsCards(viewModel: ShellViewModel, state: DiagnosticsState) {
    var test by remember { mutableStateOf("ping") }
    var host by remember { mutableStateOf("1.1.1.1") }
    var query by remember { mutableStateOf("example.org") }
    var resolver by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }

    NetGripCard(
        eyebrow = "Comprobación",
        title = "Estado del router",
        subtitle = state.selfTest?.let { if (it.allOk) "Todo responde" else "Hay algo que revisar" }
            ?: "Sin comprobar",
        icon = NetGripIcons.CircleCheck,
        iconTone = when {
            state.selfTest == null -> Tone.NEUTRAL
            state.selfTest.allOk -> Tone.SUCCESS
            else -> Tone.WARN
        },
    ) {
        state.selfTest?.let { selfTest ->
            selfTest.checks.forEach { check ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(
                        when {
                            check.ok -> "Correcto"
                            check.info -> "Sin confirmar"
                            else -> "Falla"
                        },
                        when {
                            check.ok -> Tone.SUCCESS
                            check.info -> Tone.WARN
                            else -> Tone.DANGER
                        },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(check.key, style = MaterialTheme.typography.titleSmall)
                        if (check.detail.isNotBlank()) {
                            Text(
                                check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(4.dp))
            }
            val tools = selfTest.tools
            Text(
                "Herramientas: " + listOfNotNull(
                    "ping".takeIf { tools.ping },
                    "traceroute".takeIf { tools.traceroute },
                    "nslookup".takeIf { tools.nslookup },
                    "dig".takeIf { tools.dig },
                ).ifEmpty { listOf("ninguna") }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = viewModel::loadSelfTest,
            enabled = !state.selfTestBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.selfTestBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(NetGripIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Comprobar ahora")
            }
        }
    }

    Spacer(Modifier.size(12.dp))

    NetGripCard(
        eyebrow = "Prueba puntual",
        title = "Ping, ruta, DNS o puerto",
        subtitle = "Lo ejecuta el router y devuelve su medición",
        icon = NetGripIcons.Activity,
        iconTone = Tone.ACCENT,
    ) {
        SegmentedControl(
            options = listOf(
                "ping" to "Ping",
                "traceroute" to "Ruta",
                "dns" to "DNS",
                "tcp" to "Puerto",
            ),
            selected = test,
            onSelect = { test = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.size(8.dp))
        when (test) {
            "ping", "traceroute" -> OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Host o IP") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            "dns" -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Nombre a resolver") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = resolver,
                    onValueChange = { resolver = it },
                    label = { Text("Resolver (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host o IP") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter { ch -> ch.isDigit() }.take(5) },
                    label = { Text("Puerto") },
                    singleLine = true,
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        Spacer(Modifier.size(10.dp))
        Button(
            onClick = {
                when (test) {
                    "ping" -> viewModel.runDiagnostic("ping", host = host.trim(), count = 4)
                    "traceroute" -> viewModel.runDiagnostic("traceroute", host = host.trim())
                    "dns" -> viewModel.runDiagnostic(
                        "dns",
                        query = query.trim(),
                        resolver = resolver.trim(),
                    )
                    else -> viewModel.runDiagnostic(
                        "tcp",
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 0,
                    )
                }
            },
            enabled = state.toolRunning == null && host.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.toolRunning != null) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Ejecutando")
            } else {
                Text("Ejecutar")
            }
        }
        state.toolError?.let { error ->
            Spacer(Modifier.size(8.dp))
            Banner(error, Tone.DANGER)
        }
        if (state.toolResult.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            CardDivider()
            Spacer(Modifier.size(8.dp))
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.toolResult.forEach { line ->
                        Text(
                            line,
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            TextButton(onClick = viewModel::clearToolResult) { Text("Limpiar") }
        }
    }
}
