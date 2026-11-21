package dev.gnacho.netgrip.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.presentation.DashboardViewModel
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private val prettyJson = Json { prettyPrint = true }

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
                UiState.Idle, UiState.Loading ->
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
                UiState.Ready ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { JsonCard("Sistema (/api/system)", viewModel.system) }
                        item { JsonCard("Clientes (/api/clients)", viewModel.clients) }
                    }
            }
        }
    }
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

@Composable
private fun JsonCard(title: String, json: JsonElement?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                json?.let { prettyJson.encodeToString(JsonElement.serializer(), it) } ?: "Sin datos",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
