package dev.gnacho.netgrip.ui.login

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.gnacho.netgrip.discovery.platformRouterDiscovery
import dev.gnacho.netgrip.presentation.DiscoveryViewModel
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState

typealias PlatformBackHandler = @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit

@Composable
fun LoginScreen(viewModel: SessionViewModel, platformBackHandler: PlatformBackHandler = { _, _ -> }) {
    val discoveryViewModel = remember { DiscoveryViewModel(platformRouterDiscovery()) }
    var target by remember { mutableStateOf<LoginTarget?>(null) }
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
