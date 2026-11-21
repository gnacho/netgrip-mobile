package dev.gnacho.netgrip.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.data.api.NetGripApi
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

/**
 * Carga las lecturas basicas del spike: /api/system y /api/clients.
 * Los cuerpos siguen como JsonElement y la UI los presenta con parsing tolerante.
 */
class DashboardViewModel(private val api: NetGripApi) : ViewModel() {

    var uiState: UiState by mutableStateOf(UiState.Idle)
        private set
    var system: JsonElement? by mutableStateOf(null)
        private set
    var clients: JsonElement? by mutableStateOf(null)
        private set

    fun refresh() {
        uiState = UiState.Loading
        viewModelScope.launch {
            uiState = when (val sys = api.system()) {
                is ApiResult.Ok -> {
                    system = sys.value
                    when (val cli = api.clients()) {
                        is ApiResult.Ok -> {
                            clients = cli.value
                            UiState.Ready
                        }
                        else -> mapError(cli)
                    }
                }
                else -> mapError(sys)
            }
        }
    }

    private fun mapError(result: ApiResult<*>): UiState = when (result) {
        ApiResult.Unauthorized -> UiState.SessionExpired
        ApiResult.Unreachable -> UiState.RouterUnreachable
        is ApiResult.RateLimited -> UiState.RateLimited(result.retryAfterSeconds)
        is ApiResult.HttpError -> UiState.Error("Error HTTP ${result.code}")
        is ApiResult.Unknown -> UiState.Error(result.message ?: "Error desconocido")
        else -> UiState.Error("Error desconocido")
    }
}
