package dev.gnacho.netgrip.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.presentation.DashboardViewModel
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(sessionViewModel: SessionViewModel) {
    val api = sessionViewModel.api ?: return
    val viewModel = remember(api) { DashboardViewModel(api) }
    LaunchedEffect(api) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetGrip") },
                actions = {
                    TextButton(onClick = { sessionViewModel.logout() }) { Text("Salir") }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = viewModel.uiState) {
                UiState.Idle, UiState.Loading, is UiState.CertificateConfirmation ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                UiState.RouterUnreachable ->
                    CenteredMessage("Router inalcanzable. Revisa la conexión.") { viewModel.refresh() }
                is UiState.RateLimited ->
                    CenteredMessage(
                        "Límite de peticiones. Reintenta en ${state.retryAfterSeconds ?: "?"} s.",
                    ) { viewModel.refresh() }
                is UiState.Error ->
                    CenteredMessage(state.message) { viewModel.refresh() }
                UiState.SessionExpired ->
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Sesión caducada")
                        Button(onClick = { sessionViewModel.logout() }) {
                            Text("Volver a iniciar sesión")
                        }
                    }
                UiState.Ready -> {
                    val system = remember(viewModel.system) {
                        DashboardDataParser.parseSystem(viewModel.system)
                    }
                    val clients = remember(viewModel.clients) {
                        DashboardDataParser.parseClients(viewModel.clients)
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 8.dp,
                            end = 16.dp,
                            bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item { SystemCard(system) }
                        item { ClientsCard(clients) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemCard(fields: List<DashboardField>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                "Sistema",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (fields.isEmpty()) {
                EmptyCardMessage("No hay datos del sistema disponibles.")
            } else {
                fields.forEachIndexed { index, field ->
                    if (index > 0) CardDivider()
                    SystemFieldRow(field)
                }
            }
        }
    }
}

@Composable
private fun SystemFieldRow(field: DashboardField) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            field.label,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            field.value,
            modifier = Modifier.weight(1.1f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun ClientsCard(clients: List<DashboardClient>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Clientes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(
                        clients.size.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (clients.isEmpty()) {
                EmptyCardMessage("No hay clientes para mostrar.")
            } else {
                clients.forEachIndexed { index, client ->
                    if (index > 0) CardDivider()
                    ClientRow(client)
                }
            }
        }
    }
}

@Composable
private fun ClientRow(client: DashboardClient) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            client.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        val details = listOfNotNull(client.address, client.connection).joinToString(" · ")
        if (details.isNotEmpty()) {
            Text(
                details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun EmptyCardMessage(message: String) {
    Text(
        message,
        modifier = Modifier.padding(top = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenteredMessage(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
