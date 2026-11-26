package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.DiscoveredPeerDto
import dev.gnacho.netgrip.data.dto.FleetNodeDto
import dev.gnacho.netgrip.domain.fleet.FleetRules
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.domain.sites.SiteRules
import dev.gnacho.netgrip.presentation.FleetState
import dev.gnacho.netgrip.presentation.FleetTaskStatus
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle

/**
 * Flota: los routers que el panel gestiona juntos.
 *
 * El panel es quien entra en cada router (tiene sus contrasenas); la app pide
 * comprobaciones y pinta lo que responde. `GET /api/fleet` devuelve el ultimo
 * estado conocido y `POST /api/fleet/check` uno fresco, misma forma.
 */
@Composable
fun FleetScreen(
    viewModel: ShellViewModel,
    networks: List<SavedNetwork> = emptyList(),
    currentNetworkId: String? = null,
    onAdoptDevice: (url: String, name: String, networkId: String?) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.fleet.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureFleet() }

    when {
        state.loading && !state.loaded -> LoadingBox("Leyendo la flota del panel")
        state.error != null && state.nodes.isEmpty() && state.discovered.isEmpty() ->
            ErrorBox(state.error!!) { viewModel.loadFleet() }
        else -> {
            val summary = FleetRules.summary(state.nodes)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    SummaryCard(state, summary, viewModel)
                }
                if (state.batch.tasks.isNotEmpty()) {
                    item {
                        BatchCard(state, viewModel)
                    }
                }
                if (state.nodes.isEmpty()) {
                    item {
                        NetGripCard(
                            eyebrow = "Sin routers",
                            title = "La flota está vacía",
                            icon = NetGripIcons.Router,
                        ) {
                            EmptyStateView(
                                title = "Ningún router añadido",
                                body = "La flota se llena desde el panel o adoptando un router que " +
                                    "se anuncie en la LAN (abajo). La app no inventa la lista.",
                                icon = NetGripIcons.Router,
                            )
                        }
                    }
                }
                items(state.nodes, key = { it.id }) { node ->
                    NodeCard(node, state, viewModel)
                }
                item {
                    DiscoveryCard(state, viewModel, networks, currentNetworkId, onAdoptDevice)
                }
                item {
                    Text(
                        "Las versiones y el alcance los comprueba el panel contra cada router. " +
                            "La app no puede sondearlos: no tiene sus credenciales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.actionError?.let { message -> item { Banner(message, Tone.DANGER) } }
                state.actionMessage?.let { message -> item { Banner(message, Tone.SUCCESS) } }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    state: FleetState,
    summary: dev.gnacho.netgrip.domain.fleet.FleetSummary,
    viewModel: ShellViewModel,
) {
    var confirmUpdate by remember { mutableStateOf(false) }
    val candidates = FleetRules.updateCandidates(state.nodes)
    NetGripCard(
        eyebrow = "Flota",
        title = "Routers gestionados juntos",
        subtitle = when {
            summary.total == 0 -> "Sin routers"
            summary.failing > 0 -> "${summary.failing} sin responder"
            summary.updates > 0 -> "${summary.updates} con actualización"
            else -> "${summary.reachable} de ${summary.total} responden"
        },
        icon = NetGripIcons.Router,
        iconTone = when {
            summary.total == 0 -> Tone.NEUTRAL
            summary.failing > 0 -> Tone.WARN
            summary.updates > 0 -> Tone.ACCENT
            else -> Tone.SUCCESS
        },
    ) {
        OutlinedButton(
            onClick = viewModel::checkAllFleet,
            enabled = !state.checkingAll && state.checkingId == null && !state.batch.running,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.checkingAll && !state.batch.running) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Comprobando la flota")
            } else {
                Icon(NetGripIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Comprobar toda la flota")
            }
        }
        Spacer(Modifier.height(8.dp))
        if (state.batch.running) {
            OutlinedButton(
                onClick = viewModel::cancelFleetUpdate,
                enabled = !state.batch.cancelled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.batch.cancelled) "Deteniendo" else "Detener el lote")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Se actualizan de uno en uno. Detener para antes de la siguiente comprobación; " +
                    "lo ya lanzado no se revierte.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Button(
                onClick = { confirmUpdate = true },
                enabled = candidates.isNotEmpty() && !state.checkingAll && state.checkingId == null,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    when {
                        candidates.isEmpty() -> "Todo al día"
                        candidates.size == 1 -> "Actualizar 1 router"
                        else -> "Actualizar ${candidates.size} routers"
                    },
                )
            }
        }
    }
    if (confirmUpdate) {
        AlertDialog(
            onDismissRequest = { confirmUpdate = false },
            title = { Text("Actualizar la flota") },
            text = {
                Text(
                    "Se actualizará el panel de ${candidates.size} router(s), uno detrás de otro. " +
                        "Cada uno se reinicia: el panel no responde mientras se actualiza y no se " +
                        "da por actualizado hasta que una comprobación vea la versión nueva. " +
                        "Los que no respondan o ya estén al día se omiten con su motivo.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmUpdate = false
                        viewModel.startFleetUpdate()
                    },
                ) { Text("Actualizar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUpdate = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun BatchCard(state: FleetState, viewModel: ShellViewModel) {
    val tasks = state.batch.tasks
    val done = tasks.count { it.status != FleetTaskStatus.PENDING && it.status != FleetTaskStatus.RUNNING }
    NetGripCard(
        eyebrow = "Lote",
        title = "Resultado por router",
        subtitle = if (state.batch.running) {
            "$done de ${tasks.size} procesados"
        } else {
            "${tasks.size} routers"
        },
        icon = NetGripIcons.Zap,
        iconTone = when {
            state.batch.running -> Tone.ACCENT
            tasks.any { it.status == FleetTaskStatus.FAILED } -> Tone.DANGER
            tasks.any { it.status == FleetTaskStatus.UNCONFIRMED } -> Tone.WARN
            else -> Tone.SUCCESS
        },
        trailing = if (state.batch.running) null else {
            {
                TextButton(onClick = viewModel::clearFleetBatch) { Text("Cerrar") }
            }
        },
    ) {
        tasks.forEach { task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (task.detail.isNotBlank()) {
                        Text(
                            task.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Pill(taskLabel(task.status), taskTone(task.status))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun taskLabel(status: FleetTaskStatus): String = when (status) {
    FleetTaskStatus.PENDING -> "En cola"
    FleetTaskStatus.RUNNING -> "Actualizando"
    FleetTaskStatus.UPDATED -> "Actualizado"
    FleetTaskStatus.UNCONFIRMED -> "Sin confirmar"
    FleetTaskStatus.FAILED -> "Error"
    FleetTaskStatus.SKIPPED -> "Omitido"
    FleetTaskStatus.CANCELLED -> "No ejecutado"
}

private fun taskTone(status: FleetTaskStatus): Tone = when (status) {
    FleetTaskStatus.UPDATED -> Tone.SUCCESS
    FleetTaskStatus.RUNNING, FleetTaskStatus.PENDING -> Tone.ACCENT
    FleetTaskStatus.UNCONFIRMED, FleetTaskStatus.SKIPPED -> Tone.WARN
    FleetTaskStatus.FAILED -> Tone.DANGER
    FleetTaskStatus.CANCELLED -> Tone.NEUTRAL
}

@Composable
private fun NodeCard(node: FleetNodeDto, state: FleetState, viewModel: ShellViewModel) {
    NetGripCard(
        eyebrow = if (node.name.isNotBlank()) node.name else "Router",
        title = node.address.ifBlank { node.id },
        subtitle = when {
            !node.reachable && node.error.isNullOrBlank() -> "Sin comprobar"
            !node.reachable -> "No responde"
            node.currentVersion.isNotBlank() -> "Versión ${node.currentVersion}"
            else -> "Responde"
        },
        icon = NetGripIcons.Router,
        iconTone = when {
            !node.reachable && node.error.isNullOrBlank() -> Tone.NEUTRAL
            !node.reachable -> Tone.DANGER
            node.updateAvailable -> Tone.ACCENT
            else -> Tone.SUCCESS
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                !node.reachable && node.error.isNullOrBlank() -> Pill("Sin datos", Tone.NEUTRAL)
                !node.reachable -> Pill("No responde", Tone.DANGER)
                else -> Pill("Responde", Tone.SUCCESS)
            }
            if (node.updateAvailable) {
                Pill("Actualización a ${node.latestVersion}", Tone.ACCENT)
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { viewModel.checkFleetNode(node.id) },
                enabled = !state.checkingAll && state.checkingId == null && !state.batch.running,
            ) {
                if (state.checkingId == node.id) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    Text("Comprobar")
                }
            }
        }
        node.error?.takeIf { it.isNotBlank() }?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                error,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveryCard(
    state: FleetState,
    viewModel: ShellViewModel,
    networks: List<SavedNetwork>,
    currentNetworkId: String?,
    onAdoptDevice: (url: String, name: String, networkId: String?) -> Unit,
) {
    var adopting by remember { mutableStateOf<DiscoveredPeerDto?>(null) }
    NetGripCard(
        eyebrow = "Descubrimiento",
        title = "Routers que se anuncian en la LAN",
        subtitle = when {
            !state.discoveryEnabled -> "Apagado en este equipo"
            state.discovered.isEmpty() -> "Encendido, nadie nuevo"
            else -> "${state.discovered.size} sin añadir"
        },
        icon = NetGripIcons.Network,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Escuchar anuncios de la flota",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = state.discoveryEnabled,
                onCheckedChange = viewModel::setFleetDiscovery,
            )
        }
        if (state.discovered.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.discoveryEnabled) {
                    "Ningún router nuevo se ha anunciado todavía."
                } else {
                    "Enciende el descubrimiento para ver routers con el descubrimiento activo."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.discovered.forEach { peer ->
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        peer.name.ifBlank { peer.id },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        listOfNotNull(
                            peer.address.ifBlank { null },
                            peer.port.takeIf { it > 0 }?.toString(),
                        ).joinToString(":"),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (peer.version.isNotBlank()) {
                    Pill(peer.version, Tone.NEUTRAL)
                }
                TextButton(
                    onClick = { adopting = peer },
                    enabled = state.adopting == null,
                ) {
                    if (state.adopting == peer.id) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Adoptar")
                    }
                }
            }
        }
    }
    adopting?.let { peer ->
        AdoptDialog(
            peer = peer,
            networks = networks,
            currentNetworkId = currentNetworkId,
            busy = state.adopting != null,
            onAdopt = { name, password, networkId ->
                viewModel.adoptFleetPeer(peer, name, password) { request ->
                    onAdoptDevice(SiteRules.urlFromFleetAddress(request.address), name, networkId)
                }
                adopting = null
            },
            onDismiss = { adopting = null },
        )
    }
}

@Composable
private fun AdoptDialog(
    peer: DiscoveredPeerDto,
    networks: List<SavedNetwork>,
    currentNetworkId: String?,
    busy: Boolean,
    onAdopt: (name: String, password: String, networkId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(peer.id) { mutableStateOf(peer.name.ifBlank { peer.id }) }
    var password by remember(peer.id) { mutableStateOf("") }
    var networkId by remember(peer.id, currentNetworkId) {
        mutableStateOf(currentNetworkId ?: networks.firstOrNull()?.id)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir a la flota") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El panel entrará en ${FleetRules.hostPort(peer.address, peer.port)} con esta " +
                        "contraseña antes de añadirlo. Si no entra, no se guarda nada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña del router") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (networks.isEmpty()) {
                    Text(
                        "No hay redes en la app: se creará una con este router.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Añadirlo también a esta red",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        networks.forEach { network ->
                            FilterChip(
                                selected = network.id == networkId,
                                onClick = { networkId = network.id },
                                label = { Text(network.name) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdopt(name, password, networkId) },
                enabled = !busy && name.isNotBlank() && password.isNotBlank(),
            ) {
                Text("Adoptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
