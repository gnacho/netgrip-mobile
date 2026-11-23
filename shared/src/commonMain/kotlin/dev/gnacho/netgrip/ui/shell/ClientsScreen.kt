package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.domain.clients.ClientFilter
import dev.gnacho.netgrip.domain.clients.ClientLabels
import dev.gnacho.netgrip.domain.format.Formats
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
    var selected by remember { mutableStateOf<ClientDto?>(null) }
    LaunchedEffect(Unit) { viewModel.ensureClients() }

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
                            ClientCard(client) { selected = client }
                        }
                    }
                }
            }
        }
    }

    selected?.let { client ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            ClientDetail(client)
        }
    }
}

@Composable
private fun ClientCard(client: ClientDto, onClick: () -> Unit) {
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
            if (flags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    flags.take(3).forEach { flag ->
                        Badge(flag, if (flag.contains("Bloque") || flag.contains("agotada")) BadgeTone.DANGER else BadgeTone.NEUTRAL)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientDetail(client: ClientDto) {
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
        InfoRow("Dirección IP", client.ip?.takeIf { it.isNotBlank() } ?: "Sin dirección")
        InfoRow("MAC", client.mac)
        client.iface?.takeIf { it.isNotBlank() }?.let { InfoRow("Interfaz", it) }
        Formats.signal(client.signal)?.let { InfoRow("Señal", it) }
        InfoRow("Tráfico", "Subida ${Formats.bytes(client.rxBytes)} · Bajada ${Formats.bytes(client.txBytes)}")
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
            InfoRow("Bloqueado en", client.blockedOn.joinToString(", "))
        }
        client.parentalNext?.takeIf { it.isNotBlank() }?.let { InfoRow("Próximo bloqueo parental", it) }
    }
}
