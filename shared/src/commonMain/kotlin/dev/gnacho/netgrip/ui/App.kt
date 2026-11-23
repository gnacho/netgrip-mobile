package dev.gnacho.netgrip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.discovery.RouterDiscovery
import dev.gnacho.netgrip.discovery.platformRouterDiscovery
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.ui.login.LoginScreen
import dev.gnacho.netgrip.ui.login.PlatformBackHandler
import dev.gnacho.netgrip.ui.shell.ShellScreen
import dev.gnacho.netgrip.ui.theme.NetGripLoginTheme
import dev.gnacho.netgrip.ui.theme.NetGripTheme
import dev.gnacho.netgrip.ui.theme.SystemBars
import dev.gnacho.netgrip.ui.theme.ThemeController
import dev.gnacho.netgrip.ui.theme.ThemeMode

@Composable
fun App(
    secureStorage: SecureStorage,
    platformBackHandler: PlatformBackHandler = { _, _ -> },
    routerDiscovery: RouterDiscovery = platformRouterDiscovery(),
    isLocalNetworkGranted: () -> Boolean = { true },
    requestLocalNetworkPermission: (callback: (Boolean) -> Unit) -> Unit = { callback ->
        callback(true)
    },
) {
    val sessionViewModel = remember { SessionViewModel(secureStorage) }
    val scope = rememberCoroutineScope()
    val themeController = remember { ThemeController(secureStorage, scope) }
    LaunchedEffect(Unit) { sessionViewModel.autoRestore() }
    LaunchedEffect(Unit) { themeController.load() }

    val systemDark = isSystemInDarkTheme()
    val dark = when (themeController.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    SystemBars(darkTheme = dark)

    if (sessionViewModel.loggedIn) {
        NetGripTheme(darkTheme = dark) {
            ShellScreen(
                sessionViewModel = sessionViewModel,
                platformBackHandler = platformBackHandler,
                themeMode = themeController.mode,
                onThemeModeChange = { themeController.set(it) },
            )
        }
    } else {
        NetGripLoginTheme {
            LoginScreen(
                viewModel = sessionViewModel,
                platformBackHandler = platformBackHandler,
                routerDiscovery = routerDiscovery,
                isLocalNetworkGranted = isLocalNetworkGranted,
                requestLocalNetworkPermission = requestLocalNetworkPermission,
            )
        }
    }
}
