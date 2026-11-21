package dev.gnacho.netgrip.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.ui.dashboard.DashboardScreen
import dev.gnacho.netgrip.ui.login.LoginScreen
import dev.gnacho.netgrip.ui.theme.NetGripTheme

@Composable
fun App(secureStorage: SecureStorage) {
    val sessionViewModel = remember { SessionViewModel(secureStorage) }
    LaunchedEffect(Unit) { sessionViewModel.autoRestore() }
    NetGripTheme {
        if (sessionViewModel.loggedIn) {
            DashboardScreen(sessionViewModel)
        } else {
            LoginScreen(sessionViewModel)
        }
    }
}
