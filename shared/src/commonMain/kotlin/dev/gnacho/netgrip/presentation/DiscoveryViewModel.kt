package dev.gnacho.netgrip.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.discovery.DiscoveredRouter
import dev.gnacho.netgrip.discovery.RouterDiscovery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class DiscoveryStatus { Idle, Searching, Complete, Error }

class DiscoveryViewModel(
    private val discovery: RouterDiscovery,
    private val isLocalNetworkGranted: () -> Boolean = { true },
    private val requestLocalNetworkPermission: (callback: (Boolean) -> Unit) -> Unit = { callback ->
        callback(true)
    },
) : ViewModel() {
    val isSupported: Boolean get() = discovery.isSupported

    var status: DiscoveryStatus by mutableStateOf(DiscoveryStatus.Idle)
        private set

    var routers: List<DiscoveredRouter> by mutableStateOf(emptyList())
        private set

    var selectedKey: String? by mutableStateOf(null)
        private set

    var errorMessage: String? by mutableStateOf(null)
        private set

    val selectedRouter: DiscoveredRouter?
        get() = routers.firstOrNull { it.key == selectedKey }

    fun search() {
        if (!discovery.isSupported || status == DiscoveryStatus.Searching) return
        if (!isLocalNetworkGranted()) {
            requestLocalNetworkPermission { granted ->
                if (granted) {
                    search()
                } else {
                    errorMessage = "Permiso de red local denegado. Actívalo para descubrir routers."
                    status = DiscoveryStatus.Error
                }
            }
            return
        }
        status = DiscoveryStatus.Searching
        routers = emptyList()
        selectedKey = null
        errorMessage = null
        viewModelScope.launch {
            try {
                routers = discovery.discover()
                status = DiscoveryStatus.Complete
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage = "${error::class.simpleName}: ${error.message ?: "sin detalle"}"
                routers = emptyList()
                status = DiscoveryStatus.Error
            }
        }
    }

    fun select(router: DiscoveredRouter) {
        if (routers.any { it.key == router.key }) selectedKey = router.key
    }
}
