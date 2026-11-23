package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.ShellTab
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.login.PlatformBackHandler

/**
 * Shell de la app con sesion iniciada: cinco pestanas de solo lectura y el
 * cierre de sesion en la barra superior. Cada pestana carga sus datos la
 * primera vez que se abre, sin polling.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(
    sessionViewModel: SessionViewModel,
    platformBackHandler: PlatformBackHandler = { _, _ -> },
) {
    val api = sessionViewModel.api ?: return
    val viewModel = remember(api) { ShellViewModel(api) }
    var tab by remember { mutableStateOf(ShellTab.PANEL) }
    val sessionExpired by viewModel.sessionExpired.collectAsState()

    LaunchedEffect(sessionExpired) {
        if (sessionExpired) sessionViewModel.onSessionExpired()
    }
    platformBackHandler(tab != ShellTab.PANEL) { tab = ShellTab.PANEL }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tab.label) },
                actions = {
                    TextButton(onClick = { viewModel.refresh(tab) }) { Text("Actualizar") }
                    TextButton(onClick = { sessionViewModel.logout() }) { Text("Salir") }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                ShellTab.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = tab == destination,
                        onClick = { tab = destination },
                        // Sin juego de iconos en el proyecto: la etiqueta ocupa
                        // el hueco del icono para no inventar glifos.
                        icon = {
                            Text(
                                destination.label,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                ShellTab.PANEL -> PanelScreen(viewModel)
                ShellTab.CLIENTS -> ClientsScreen(viewModel)
                ShellTab.WIFI -> WifiScreen(viewModel)
                ShellTab.PORTS -> PortsScreen(viewModel)
                ShellTab.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    baseUrl = sessionViewModel.baseUrl,
                    onLogout = { sessionViewModel.logout() },
                )
            }
        }
    }
}
