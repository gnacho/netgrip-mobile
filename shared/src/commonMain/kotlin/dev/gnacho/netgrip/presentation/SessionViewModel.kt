package dev.gnacho.netgrip.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.platformEngine
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.session.SessionManager
import kotlinx.coroutines.launch

class SessionViewModel(
    storage: SecureStorage,
    private val sessionManager: SessionManager = SessionManager(storage),
) : ViewModel() {

    var uiState: UiState by mutableStateOf(UiState.Idle)
        private set

    var api: NetGripApi? by mutableStateOf(null)
        private set

    val loggedIn: Boolean get() = api?.sessionCookie != null

    /**
     * Al arrancar: si hay una URL y una cookie guardadas, restaura la sesion
     * y la valida con GET /api/me. Si la sesion caduco, vuelve al login.
     */
    fun autoRestore() {
        if (uiState == UiState.Loading || loggedIn) return
        viewModelScope.launch {
            val url = sessionManager.lastUrl() ?: return@launch
            uiState = UiState.Loading
            val client = NetGripApi(platformEngine(), url)
            if (sessionManager.restore(client)) {
                api = client
                uiState = UiState.Ready
            } else {
                uiState = UiState.Idle
            }
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        val cleanUrl = baseUrl.trim().trimEnd('/')
        val client = NetGripApi(platformEngine(), cleanUrl)
        uiState = UiState.Loading
        viewModelScope.launch {
            uiState = when (val result = client.login(username, password)) {
                is ApiResult.Ok -> {
                    api = client
                    sessionManager.onLoginOk(client, cleanUrl)
                    UiState.Ready
                }
                ApiResult.Unauthorized -> UiState.Error("Usuario o contraseña incorrectos")
                is ApiResult.RateLimited -> UiState.RateLimited(result.retryAfterSeconds)
                ApiResult.Unreachable -> UiState.RouterUnreachable
                is ApiResult.HttpError -> UiState.Error("Error HTTP ${result.code}")
                is ApiResult.Unknown -> UiState.Error(result.message ?: "Error desconocido")
            }
        }
    }

    fun resetLoginState() {
        if (uiState != UiState.Loading) uiState = UiState.Idle
    }

    fun logout() {
        val client = api ?: return
        viewModelScope.launch {
            sessionManager.logout(client)
            api = null
            uiState = UiState.Idle
        }
    }
}
