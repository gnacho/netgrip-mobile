package dev.gnacho.netgrip.ui.login

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.gnacho.netgrip.discovery.RouterDiscovery
import dev.gnacho.netgrip.discovery.platformRouterDiscovery
import dev.gnacho.netgrip.presentation.DiscoveryViewModel
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState
import kotlinx.coroutines.launch

typealias PlatformBackHandler = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

@Composable
fun LoginScreen(
    viewModel: SessionViewModel,
    initialUrl: String? = null,
    platformBackHandler: PlatformBackHandler = { _, _ -> },
    routerDiscovery: RouterDiscovery = platformRouterDiscovery(),
    isLocalNetworkGranted: () -> Boolean = { true },
    requestLocalNetworkPermission: (callback: (Boolean) -> Unit) -> Unit = { callback ->
        callback(true)
    },
) {
    val discoveryViewModel = remember {
        DiscoveryViewModel(routerDiscovery, isLocalNetworkGranted, requestLocalNetworkPermission)
    }
    val scope = rememberCoroutineScope()
    var target by remember {
        mutableStateOf<LoginTarget?>(initialUrl?.takeIf { it.isNotBlank() }?.let { LoginTarget(baseUrl = it) })
    }
    var showManualEntry by remember { mutableStateOf(false) }

    platformBackHandler(target != null) {
        if (viewModel.uiState !is UiState.Loading) {
            viewModel.resetLoginState()
            target = null
        }
    }

    Surface(color = dev.gnacho.netgrip.ui.theme.LoginBackground) {
        target?.let { loginTarget ->
            AccessScreen(
                target = loginTarget,
                viewModel = viewModel,
                onBack = {
                    viewModel.resetLoginState()
                    target = null
                },
            )
        } ?: WelcomeScreen(
            status = discoveryViewModel.status,
            isDiscoverySupported = discoveryViewModel.isSupported,
            routers = discoveryViewModel.routers,
            selectedRouter = discoveryViewModel.selectedRouter,
            onSearch = discoveryViewModel::search,
            onSelect = discoveryViewModel::select,
            onConnect = { router ->
                viewModel.resetLoginState()
                target = LoginTarget.fromRouter(router)
            },
            onManual = { showManualEntry = true },
            savedNetworks = viewModel.sites.networks,
            onSelectSaved = { device ->
                viewModel.resetLoginState()
                scope.launch {
                    if (!viewModel.trySwitchDevice(device.id)) {
                        target = LoginTarget(baseUrl = device.url)
                    }
                }
            },
        )
    }

    if (showManualEntry) {
        ManualEntrySheet(
            onDismiss = { showManualEntry = false },
            onContinue = { url ->
                viewModel.resetLoginState()
                showManualEntry = false
                target = LoginTarget(baseUrl = url)
            },
        )
    }
}
