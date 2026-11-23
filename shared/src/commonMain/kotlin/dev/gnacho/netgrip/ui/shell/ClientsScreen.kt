package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.domain.clients.ClientBlockRules
import dev.gnacho.netgrip.domain.clients.ClientFilter
import dev.gnacho.netgrip.domain.clients.ClientLabels
import dev.gnacho.netgrip.domain.clients.ClientMetaRules
import dev.gnacho.netgrip.domain.clients.ClientReservationRules
import dev.gnacho.netgrip.domain.clients.DeviceTypes
import dev.gnacho.netgrip.domain.clients.ReservationAction
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.presentation.ClientsState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.GroupLabel
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.MeterBar
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SearchField
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.common.deviceIcon
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(viewModel: ShellViewModel) {
    val state by viewModel.clients.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedMac by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.ensureClients() }

    val feedback = state.actionError ?: state.actionMessage
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearClientActionFeedback()
        }
    }

    val selected = selectedMac?.let { mac ->
        state.clients.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
    }
    // Tras bloquear un cliente WiFi el router lo desconecta y desaparece de la
    // lista: la hoja se cierra y el aviso explica el resultado.
    LaunchedEffect(state.clients, selectedMac) {
        if (selectedMac != null && selected == null) selectedMac = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && !state.loaded -> LoadingBox()
            state.error != null && state.clients.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadClients() }
            !state.loaded -> LoadingBox()
            else -> {
                val filtered = remember(state.clients, query) { ClientFilter.filter(state.clients, query) }
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Buscar por nombre, IP o MAC",
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${filtered.size} de ${state.clients.size} clientes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.netGrip.faint,
                        )
                    }
                    when {
                        state.clients.isEmpty() -> EmptyStateView(
                            title = "No hay clientes conectados",
                            body = "Cuando un dispositivo se conecte a la red aparecerá aquí.",
                            icon = NetGripIcons.Clients,
                            modifier = Modifier.fillMaxSize(),
                        )
                        filtered.isEmpty() -> EmptyStateView(
                            title = "Ningún cliente coincide",
                            body = "Prueba con otro nombre, IP o MAC.",
                            icon = NetGripIcons.Search,
                            modifier = Modifier.fillMaxSize(),
                        )
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            state.error?.let { message -> item { InlineWarning(message) } }
                            items(filtered, key = { "${it.mac}-${it.ip}" }) { client ->
                                ClientRow(
                                    client = client,
                                    busy = state.busyMac == client.mac.lowercase(),
                                ) { selectedMac = client.mac }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }

    selected?.let { client ->
        ModalBottomSheet(
            onDismissRequest = { selectedMac = null },
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            ClientDetail(client = client, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ClientRow(client: ClientDto, busy: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(deviceIcon(client.deviceType), connectionTone(client), size = 38)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    client.name.ifBlank { client.mac },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                )
                Text(
                    listOfNotNull(
                        ClientLabels.type(client.type),
                        client.ip,
                        Formats.signal(client.signal),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    busy -> Pill("Aplicando cambio", Tone.WARN, dot = false)
                    client.blocked -> Pill("Bloqueado", Tone.DANGER, dot = false)
                    else -> ClientLabels.flags(client).firstOrNull()?.let { flag ->
                        Pill(flag, Tone.NEUTRAL, dot = false)
                    }
                }
            }
            Icon(
                NetGripIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.netGrip.faint,
            )
        }
    }
}

@Composable
private fun ClientDetail(client: ClientDto, state: ClientsState, viewModel: ShellViewModel) {
    var showEdit by remember(client.mac) { mutableStateOf(false) }
    var showBlock by remember(client.mac) { mutableStateOf(false) }
    var showReserve by remember(client.mac) { mutableStateOf(false) }
    val busy = state.busyMac == client.mac.lowercase()
    val blocks = ClientBlockRules.blockedBands(client)
    val reservation = ClientReservationRules.actionFor(client)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconTile(deviceIcon(deviceTypeOf(client, state)), connectionTone(client), size = 48)
            Column(Modifier.weight(1f)) {
                Text(
                    client.name.ifBlank { client.mac },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(client.mac, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val flags = ClientLabels.flags(client)
        if (flags.isNotEmpty() || busy) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (busy) Pill("Aplicando cambio", Tone.WARN)
                flags.forEach { flag ->
                    Pill(
                        flag,
                        if (flag.contains("Bloque") || flag.contains("agotada")) Tone.DANGER else Tone.NEUTRAL,
                        dot = false,
                    )
                }
            }
        }
        CardDivider()
        InfoRow("Tipo", ClientLabels.type(client.type))
        InfoRow("Tipo de dispositivo", DeviceTypes.label(deviceTypeOf(client, state)))
        InfoRow("Dirección IP", client.ip?.takeIf { it.isNotBlank() } ?: "Sin dirección", mono = true)
        InfoRow("MAC", client.mac, mono = true)
        client.iface?.takeIf { it.isNotBlank() }?.let { InfoRow("Interfaz", it, mono = true) }
        Formats.signal(client.signal)?.let { InfoRow("Señal", it) }
        TrafficDetail(client)
        InfoRow("Bloqueo", ClientBlockRules.stateLabel(client))
        InfoRow("Reserva DHCP", if (client.reserved) "IP fija reservada" else "Sin reserva")
        ClientLabels.leaseSource(client)?.let { InfoRow("Concesión", it) }
        ClientLabels.ipSource(client)?.let { InfoRow("Origen de la IP", it) }
        if ((client.leaseExpiry ?: 0) > 0) {
            InfoRow("Caducidad de la concesión", "Sí, con fecha límite")
        }
        if (client.quotaLimit > 0) {
            InfoRow(
                "Cuota",
                "${Formats.bytes(client.quotaUsed)} de ${Formats.bytes(client.quotaLimit)}",
            )
            MeterBar(
                fraction = (client.quotaUsed.toFloat() / client.quotaLimit.toFloat()).coerceIn(0f, 1f),
                color = if (client.quotaExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
        if (client.blockedOn.isNotEmpty()) {
            InfoRow("Bloqueado en", blocks.joinToString(", ") { ClientBlockRules.bandLabel(it) })
        }
        client.parentalNext?.takeIf { it.isNotBlank() }?.let { InfoRow("Próximo bloqueo parental", it) }

        GroupLabel("Acciones")
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    "Aplicando cambio en el router",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedButton(
            onClick = { showEdit = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Editar nombre y tipo")
        }

        if (!client.self) {
            if (blocks.isNotEmpty() || client.blocked) {
                ClientBlockRules.unblockOptions(client).forEach { band ->
                    OutlinedButton(
                        onClick = { viewModel.unblockClient(client, band) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(NetGripIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Desbloquear en ${ClientBlockRules.bandLabel(band).lowercase()}")
                    }
                }
            }
            if (!client.blocked && ClientBlockRules.canBlock(client)) {
                Button(
                    onClick = { showBlock = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(NetGripIcons.Ban, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Bloquear cliente")
                }
            }
            when (reservation) {
                is ReservationAction.SetCurrentIp -> OutlinedButton(
                    onClick = { showReserve = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(NetGripIcons.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Fijar la IP actual (${reservation.ip})")
                }
                ReservationAction.Remove -> OutlinedButton(
                    onClick = { showReserve = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(NetGripIcons.Pin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Quitar la reserva de IP")
                }
                ReservationAction.Unavailable -> Unit
            }
            OutlinedButton(
                onClick = { viewModel.wakeOnLan(client) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.Zap, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Despertar (Wake on LAN)")
            }
        } else {
            Text(
                "Este dispositivo administra el router: no se ofrece bloquearlo ni despertarlo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!client.self && !client.blockable) {
            Text(
                "El router no permite bloquear este cliente. En un AP, los clientes por cable solo se bloquean desde el gateway.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!client.self && reservation == ReservationAction.Unavailable && !client.reserved) {
            Text(
                if (client.ip.isNullOrBlank()) {
                    "Sin IP actual no se puede fijar una reserva."
                } else {
                    "El router no marca este cliente como reservable."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showEdit) {
        EditClientDialog(
            client = client,
            state = state,
            onDismiss = { showEdit = false },
            onSave = { name, deviceType ->
                showEdit = false
                viewModel.updateClientMeta(client.mac, name, deviceType)
            },
        )
    }
    if (showBlock) {
        BlockClientDialog(
            client = client,
            bands = state.bands,
            onDismiss = { showBlock = false },
            onConfirm = { band ->
                showBlock = false
                viewModel.blockClient(client, band)
            },
        )
    }
    if (showReserve) {
        ReserveClientDialog(
            client = client,
            action = reservation,
            onDismiss = { showReserve = false },
            onConfirm = {
                showReserve = false
                viewModel.toggleReservation(client)
            },
        )
    }
}

/**
 * Reparto del trafico acumulado del cliente. La API no guarda serie temporal
 * por cliente, solo los contadores de la conexion actual.
 */
@Composable
private fun TrafficDetail(client: ClientDto) {
    val rx = client.rxBytes.coerceAtLeast(0)
    val tx = client.txBytes.coerceAtLeast(0)
    val total = (rx + tx).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Tráfico de esta conexión", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Total ${Formats.bytes(rx + tx)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(NetGripIcons.ArrowDown, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.netGrip.chartRx)
            Text("Subida", Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                MeterBar(rx.toFloat() / total, MaterialTheme.netGrip.chartRx)
            }
            Text(Formats.bytes(rx), style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(NetGripIcons.ArrowUp, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.netGrip.chartTx)
            Text("Bajada", Modifier.width(52.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f)) {
                MeterBar(tx.toFloat() / total, MaterialTheme.netGrip.chartTx)
            }
            Text(Formats.bytes(tx), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.size(2.dp))
    }
}

private fun deviceTypeOf(client: ClientDto, state: ClientsState): String {
    val stored = ClientMetaRules.currentDeviceType(state.meta, client.mac)
    return stored.ifBlank { client.deviceType.orEmpty() }
}

private fun connectionTone(client: ClientDto): Tone = when (client.type) {
    "wifi5" -> Tone.ACCENT
    "wifi24" -> Tone.SUCCESS
    "cable" -> Tone.TEAL
    else -> Tone.NEUTRAL
}

@Composable
private fun EditClientDialog(
    client: ClientDto,
    state: ClientsState,
    onDismiss: () -> Unit,
    onSave: (name: String, deviceType: String) -> Unit,
) {
    // El valor efectivo es el override guardado y, si no hay, el que anuncia
    // el cliente. Sin este respaldo, guardar solo el nombre borraria el tipo.
    var name by remember(client.mac, client.name) {
        mutableStateOf(ClientMetaRules.effectiveName(state.meta, client))
    }
    var deviceType by remember(client.mac, client.deviceType) {
        mutableStateOf(ClientMetaRules.effectiveDeviceType(state.meta, client))
    }
    val busy = state.busyMac == client.mac.lowercase()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre y tipo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "El nombre sustituye al que anuncia el cliente. Déjalo vacío para volver al original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ClientMetaRules.MAX_NAME) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nombre") },
                    supportingText = { Text("${name.length}/${ClientMetaRules.MAX_NAME} caracteres") },
                )
                Text("Tipo de dispositivo", style = MaterialTheme.typography.titleSmall)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    (listOf("") + DeviceTypes.KEYS).forEach { key ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { deviceType = key }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = deviceType == key,
                                onClick = { deviceType = key },
                            )
                            Icon(
                                deviceIcon(key),
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp).size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                DeviceTypes.label(key),
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, deviceType) },
                enabled = !busy,
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun BlockClientDialog(
    client: ClientDto,
    bands: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (band: String) -> Unit,
) {
    var band by remember(client.mac) { mutableStateOf("") }
    val options = ClientBlockRules.bandOptions(client, bands)
    val cable = ClientBlockRules.isCable(client)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(NetGripIcons.Ban, Tone.DANGER) },
        title = { Text("Bloquear ${client.name.ifBlank { client.mac }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (cable) {
                        "El router añadirá una regla de firewall que rechaza el tráfico de este cliente. El cambio recarga el firewall y se puede deshacer."
                    } else {
                        "El router meterá la MAC en la lista de bloqueo del WiFi. El cliente se desconectará: si lo bloqueas en todas las bandas, dejará de aparecer en esta lista y solo podrás desbloquearlo desde el panel web."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Alcance", style = MaterialTheme.typography.titleSmall)
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { band = option }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = band == option,
                            onClick = { band = option },
                        )
                        Text(
                            ClientBlockRules.bandLabel(option),
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(band) },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Bloquear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun ReserveClientDialog(
    client: ClientDto,
    action: ReservationAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fix = action is ReservationAction.SetCurrentIp
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(NetGripIcons.Pin, Tone.ACCENT) },
        title = { Text(if (fix) "Fijar la IP actual" else "Quitar la reserva de IP") },
        text = {
            Text(
                if (fix) {
                    "El router reservará ${(action as ReservationAction.SetCurrentIp).ip} para ${client.name.ifBlank { client.mac }} en el DHCP. El cliente mantiene la conexión y podrás quitar la reserva después."
                } else {
                    "El cliente volverá a recibir una IP por DHCP y puede cambiar de dirección al renovar la concesión. La conectividad no se corta."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text(if (fix) "Fijar IP" else "Quitar reserva") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
