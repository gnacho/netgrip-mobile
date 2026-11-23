package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyBox
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox

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
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Buscar por nombre, IP o MAC") },
                        )
                        Text(
                            "${filtered.size} de ${state.clients.size} clientes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    when {
                        state.clients.isEmpty() -> EmptyBox("No hay clientes conectados.")
                        filtered.isEmpty() -> EmptyBox("Ningún cliente coincide con la búsqueda.")
                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            state.error?.let { message -> item { InlineWarning(message) } }
                            items(filtered, key = { "${it.mac}-${it.ip}" }) { client ->
                                ClientCard(
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
        ModalBottomSheet(onDismissRequest = { selectedMac = null }) {
            ClientDetail(client = client, state = state, viewModel = viewModel)
        }
    }
}

@Composable
private fun ClientCard(client: ClientDto, busy: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                client.name.ifBlank { client.mac },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                listOfNotNull(
                    ClientLabels.type(client.type),
                    client.ip,
                    Formats.signal(client.signal),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val flags = ClientLabels.flags(client)
            if (busy || flags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (busy) Badge("Aplicando cambio", BadgeTone.WARN)
                    flags.take(3).forEach { flag ->
                        Badge(flag, if (flag.contains("Bloque") || flag.contains("agotada")) BadgeTone.DANGER else BadgeTone.NEUTRAL)
                    }
                }
            }
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
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            client.name.ifBlank { client.mac },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        ClientLabels.flags(client).takeIf { it.isNotEmpty() }?.let { flags ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                flags.forEach { flag ->
                    Badge(flag, if (flag.contains("Bloque") || flag.contains("agotada")) BadgeTone.DANGER else BadgeTone.NEUTRAL)
                }
            }
        }
        CardDivider()
        InfoRow("Tipo", ClientLabels.type(client.type))
        InfoRow("Tipo de dispositivo", DeviceTypes.label(deviceTypeOf(client, state)))
        InfoRow("Dirección IP", client.ip?.takeIf { it.isNotBlank() } ?: "Sin dirección")
        InfoRow("MAC", client.mac)
        client.iface?.takeIf { it.isNotBlank() }?.let { InfoRow("Interfaz", it) }
        Formats.signal(client.signal)?.let { InfoRow("Señal", it) }
        InfoRow("Tráfico", "Subida ${Formats.bytes(client.rxBytes)} · Bajada ${Formats.bytes(client.txBytes)}")
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
        }
        if (client.blockedOn.isNotEmpty()) {
            InfoRow("Bloqueado en", blocks.joinToString(", ") { ClientBlockRules.bandLabel(it) })
        }
        client.parentalNext?.takeIf { it.isNotBlank() }?.let { InfoRow("Próximo bloqueo parental", it) }

        CardDivider()
        Text(
            "Acciones",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (busy) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                Text("Aplicando cambio en el router", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedButton(
            onClick = { showEdit = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Editar nombre y tipo") }

        if (!client.self) {
            if (blocks.isNotEmpty() || client.blocked) {
                ClientBlockRules.unblockOptions(client).forEach { band ->
                    OutlinedButton(
                        onClick = { viewModel.unblockClient(client, band) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Desbloquear en ${ClientBlockRules.bandLabel(band).lowercase()}")
                    }
                }
            }
            if (!client.blocked && ClientBlockRules.canBlock(client)) {
                Button(
                    onClick = { showBlock = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Bloquear cliente") }
            }
            when (reservation) {
                is ReservationAction.SetCurrentIp -> OutlinedButton(
                    onClick = { showReserve = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Fijar la IP actual (${reservation.ip})") }
                ReservationAction.Remove -> OutlinedButton(
                    onClick = { showReserve = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Quitar la reserva de IP") }
                ReservationAction.Unavailable -> Unit
            }
            OutlinedButton(
                onClick = { viewModel.wakeOnLan(client) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Despertar (Wake on LAN)") }
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

private fun deviceTypeOf(client: ClientDto, state: ClientsState): String {
    val stored = ClientMetaRules.currentDeviceType(state.meta, client.mac)
    return stored.ifBlank { client.deviceType.orEmpty() }
}

@Composable
private fun EditClientDialog(
    client: ClientDto,
    state: ClientsState,
    onDismiss: () -> Unit,
    onSave: (name: String, deviceType: String) -> Unit,
) {
    var name by remember(client.mac) {
        mutableStateOf(ClientMetaRules.currentName(state.meta, client.mac))
    }
    var deviceType by remember(client.mac) {
        mutableStateOf(ClientMetaRules.currentDeviceType(state.meta, client.mac))
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
            Button(onClick = { onConfirm(band) }) { Text("Bloquear") }
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
