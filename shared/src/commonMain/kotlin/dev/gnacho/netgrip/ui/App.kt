package dev.gnacho.netgrip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.discovery.RouterDiscovery
import dev.gnacho.netgrip.discovery.platformRouterDiscovery
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.ui.login.LoginScreen
import dev.gnacho.netgrip.ui.login.PlatformBackHandler
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.shell.ShellScreen
import dev.gnacho.netgrip.ui.sites.DevicesListScreen
import dev.gnacho.netgrip.ui.sites.NetworksListScreen
import dev.gnacho.netgrip.ui.sites.SiteLevel
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

    var level by remember { mutableStateOf<SiteLevel>(SiteLevel.Networks) }
    val sites = sessionViewModel.sites
    val loggedIn = sessionViewModel.loggedIn

    // Conectar con un dispositivo lleva a su configuracion; perder la sesion
    // devuelve a la lista de dispositivos de esa red.
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            level = SiteLevel.Device
        } else if (level is SiteLevel.Device) {
            val networkId = sessionViewModel.currentNetwork?.id
            level = if (networkId != null) SiteLevel.Devices(networkId) else SiteLevel.Networks
        }
    }

    when {
        !sessionViewModel.sitesLoaded -> NetGripTheme(darkTheme = dark) { LoadingBox() }
        sites.networks.isEmpty() && !loggedIn -> NetGripLoginTheme {
            LoginScreen(
                viewModel = sessionViewModel,
                initialUrl = sessionViewModel.pendingLoginUrl,
                platformBackHandler = platformBackHandler,
                routerDiscovery = routerDiscovery,
                isLocalNetworkGranted = isLocalNetworkGranted,
                requestLocalNetworkPermission = requestLocalNetworkPermission,
            )
        }
        loggedIn && level is SiteLevel.Device -> NetGripTheme(darkTheme = dark) {
            ShellScreen(
                sessionViewModel = sessionViewModel,
                platformBackHandler = platformBackHandler,
                themeMode = themeController.mode,
                onThemeModeChange = { themeController.set(it) },
                onBack = {
                    val networkId = sessionViewModel.currentNetwork?.id
                    level = if (networkId != null) SiteLevel.Devices(networkId) else SiteLevel.Networks
                },
            )
        }
        level is SiteLevel.Devices -> NetGripTheme(darkTheme = dark) {
            platformBackHandler(true) { level = SiteLevel.Networks }
            val networkId = (level as SiteLevel.Devices).networkId
            DevicesListScreen(
                viewModel = sessionViewModel,
                network = sites.network(networkId),
                onBack = { level = SiteLevel.Networks },
                onOpened = { level = SiteLevel.Device },
            )
        }
        else -> NetGripTheme(darkTheme = dark) {
            NetworksListScreen(
                viewModel = sessionViewModel,
                onOpen = { network -> level = SiteLevel.Devices(network.id) },
            )
        }
    }
}
