package dev.gnacho.netgrip.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.CertificateFingerprintStore
import dev.gnacho.netgrip.core.network.CertificatePin
import dev.gnacho.netgrip.core.network.DefaultEndpointProbe
import dev.gnacho.netgrip.core.network.EndpointProbe
import dev.gnacho.netgrip.core.network.EndpointProbeResult
import dev.gnacho.netgrip.core.network.SelfSignedCertificatesUnsupportedException
import dev.gnacho.netgrip.core.network.platformEngine
import dev.gnacho.netgrip.core.storage.SecureStorage
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun interface NetGripApiFactory {
    fun create(baseUrl: String, certificatePin: CertificatePin?): NetGripApi
}

class SessionViewModel(
    storage: SecureStorage,
    private val sessionManager: SessionManager = SessionManager(storage),
    private val endpointProbe: EndpointProbe = DefaultEndpointProbe(storage),
    private val fingerprints: CertificateFingerprintStore = CertificateFingerprintStore(storage),
    private val apiFactory: NetGripApiFactory = NetGripApiFactory { baseUrl, certificatePin ->
        NetGripApi(platformEngine(certificatePin), baseUrl)
    },
) : ViewModel() {

    var uiState: UiState by mutableStateOf(UiState.Idle)
        private set

    var api: NetGripApi? by mutableStateOf(null)
        private set

    private var pendingLogin: PendingLogin? = null

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
            val pin = fingerprints.readForUrl(url).takeIf { url.startsWith("https://", ignoreCase = true) }
            val client = try {
                apiFactory.create(url, pin)
            } catch (error: SelfSignedCertificatesUnsupportedException) {
                uiState = UiState.Error(error.message ?: "Certificado no soportado")
                return@launch
            }
            when (val result = sessionManager.restoreWithResult(client)) {
                is ApiResult.Ok -> {
                    api = client
                    uiState = UiState.Ready
                }
                is ApiResult.CertificateChanged -> uiState = UiState.Error(certificateChangedMessage(result.host))
                is ApiResult.CertificateUnsupported -> uiState = UiState.Error(result.message)
                else -> uiState = UiState.Idle
            }
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        if (uiState == UiState.Loading) return
        val cleanUrl = baseUrl.trim().trimEnd('/')
        pendingLogin = null
        uiState = UiState.Loading
        viewModelScope.launch {
            when (val result = endpointProbe.probe(cleanUrl)) {
                is EndpointProbeResult.Resolved -> performLogin(
                    request = PendingLogin(result.baseUrl, username, password),
                    certificatePin = result.certificatePin,
                )
                is EndpointProbeResult.CertificateConfirmationRequired -> {
                    pendingLogin = PendingLogin(result.baseUrl, username, password)
                    uiState = UiState.CertificateConfirmation(result.host, result.fingerprint)
                }
                is EndpointProbeResult.CertificateChanged -> {
                    uiState = UiState.Error(certificateChangedMessage(result.host))
                }
                is EndpointProbeResult.Unsupported -> uiState = UiState.Error(result.message)
                EndpointProbeResult.Unreachable -> uiState = UiState.RouterUnreachable
                EndpointProbeResult.InvalidUrl -> uiState = UiState.Error("URL del router no válida")
            }
        }
    }

    fun confirmCertificate() {
        val confirmation = uiState as? UiState.CertificateConfirmation ?: return
        val request = pendingLogin ?: return
        pendingLogin = null
        uiState = UiState.Loading
        viewModelScope.launch {
            val pin = try {
                fingerprints.save(confirmation.host, confirmation.fingerprint)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                uiState = UiState.Error("No se pudo guardar la huella del certificado. No se han enviado credenciales.")
                return@launch
            }
            performLogin(request, pin)
        }
    }

    fun cancelCertificateConfirmation() {
        if (uiState is UiState.CertificateConfirmation) {
            pendingLogin = null
            uiState = UiState.Idle
        }
    }

    private suspend fun performLogin(request: PendingLogin, certificatePin: CertificatePin?) {
        pendingLogin = null
        val client = try {
            apiFactory.create(request.baseUrl, certificatePin)
        } catch (error: SelfSignedCertificatesUnsupportedException) {
            uiState = UiState.Error(error.message ?: "Certificado no soportado")
            return
        }
        uiState = when (val result = client.login(request.username, request.password)) {
            is ApiResult.Ok -> {
                api = client
                sessionManager.onLoginOk(client, request.baseUrl)
                UiState.Ready
            }
            ApiResult.Unauthorized -> UiState.Error("Usuario o contraseña incorrectos")
            is ApiResult.RateLimited -> UiState.RateLimited(result.retryAfterSeconds)
            ApiResult.Unreachable -> UiState.RouterUnreachable
            is ApiResult.HttpError -> UiState.Error("Error HTTP ${result.code}")
            is ApiResult.CertificateChanged -> UiState.Error(certificateChangedMessage(result.host))
            is ApiResult.CertificateUnsupported -> UiState.Error(result.message)
            is ApiResult.Unknown -> UiState.Error(result.message ?: "Error desconocido")
        }
    }

    fun resetLoginState() {
        if (uiState != UiState.Loading) {
            pendingLogin = null
            uiState = UiState.Idle
        }
    }

    fun logout() {
        val client = api ?: return
        pendingLogin = null
        viewModelScope.launch {
            sessionManager.logout(client)
            api = null
            uiState = UiState.Idle
        }
    }

    private data class PendingLogin(
        val baseUrl: String,
        val username: String,
        val password: String,
    )
}

private fun certificateChangedMessage(host: String): String =
    "El certificado de $host ha cambiado. No se han enviado credenciales ni se ha iniciado sesión."
