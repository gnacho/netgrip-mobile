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
import dev.gnacho.netgrip.domain.format.nowEpochSeconds
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.domain.sites.SavedSites
import dev.gnacho.netgrip.domain.sites.SiteRules
import dev.gnacho.netgrip.session.SessionManager
import dev.gnacho.netgrip.session.SiteStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

fun interface NetGripApiFactory {
    fun create(baseUrl: String, certificatePin: CertificatePin?): NetGripApi
}

/** Resultado de conectar con un dispositivo desde el dialogo de acceso. */
sealed interface ConnectResult {
    data object Ok : ConnectResult
    data class Certificate(val host: String, val fingerprint: String) : ConnectResult
    data class Failure(val message: String) : ConnectResult
}

/**
 * Estado de la sesion y de la lista de redes y dispositivos. La app puede
 * guardar redes ilimitadas, cada una con sus routers, y cambiar entre ellos sin
 * perder las cookies: cada dispositivo tiene su propia sesion cifrada.
 */
class SessionViewModel(
    storage: SecureStorage,
    private val sessionManager: SessionManager = SessionManager(storage),
    private val endpointProbe: EndpointProbe = DefaultEndpointProbe(storage),
    private val fingerprints: CertificateFingerprintStore = CertificateFingerprintStore(storage),
    private val siteStore: SiteStore = SiteStore(storage),
    private val apiFactory: NetGripApiFactory = NetGripApiFactory { baseUrl, certificatePin ->
        NetGripApi(platformEngine(certificatePin), baseUrl)
    },
) : ViewModel() {

    var uiState: UiState by mutableStateOf(UiState.Idle)
        private set

    var api: NetGripApi? by mutableStateOf(null)
        private set

    /** URL base de la sesion activa, para mostrarla en Ajustes. */
    var baseUrl: String? by mutableStateOf(null)
        private set

    /** Redes y dispositivos guardados. */
    var sites: SavedSites by mutableStateOf(SavedSites())
        private set

    /** True cuando la lista guardada ya se ha leido (o no habia nada). */
    var sitesLoaded by mutableStateOf(false)
        private set

    /** Aviso para las hojas de red y dispositivo. */
    var siteMessage: String? by mutableStateOf(null)
        private set

    /**
     * URL que la pantalla de acceso debe rellenar cuando no hay sesion. Se usa
     * al arrancar con un dispositivo guardado sin cookie valida.
     */
    var pendingLoginUrl: String? by mutableStateOf(null)
        private set

    private var pendingLogin: PendingLogin? = null

    val loggedIn: Boolean get() = api?.sessionCookie != null

    val currentNetwork: SavedNetwork? get() = sites.currentNetwork

    val currentDevice: SavedDevice? get() = sites.currentDevice

    /**
     * Al arrancar: carga las redes guardadas (migrando la sesion unica de
     * versiones anteriores) y restaura la del dispositivo actual.
     */
    fun autoRestore() {
        if (uiState == UiState.Loading || loggedIn) return
        viewModelScope.launch {
            sites = siteStore.load(sessionManager.lastUrl())
            sitesLoaded = true
            val device = sites.currentDevice
            if (device == null) {
                uiState = UiState.Idle
                return@launch
            }
            sessionManager.moveCookie(SessionManager.DEFAULT_PROFILE, device.id)
            restore(device)
        }
    }

    private suspend fun restore(device: SavedDevice) {
        uiState = UiState.Loading
        val client = try {
            apiFactory.create(device.url, pinFor(device.url))
        } catch (error: SelfSignedCertificatesUnsupportedException) {
            uiState = UiState.Error(error.message ?: "Certificado no soportado")
            return
        }
        when (val result = sessionManager.restoreWithResult(client, device.id)) {
            is ApiResult.Ok -> {
                api = client
                baseUrl = device.url
                uiState = UiState.Ready
                pendingLoginUrl = null
                markSeen(device)
                refreshDeviceInfo(client, device)
            }
            is ApiResult.CertificateChanged -> uiState = UiState.Error(certificateChangedMessage(result.host))
            is ApiResult.CertificateUnsupported -> uiState = UiState.Error(result.message)
            else -> {
                // Sin cookie, 401 o router inalcanzable: al login de ese router.
                api = null
                baseUrl = null
                pendingLoginUrl = device.url
                uiState = UiState.Idle
            }
        }
    }

    fun login(baseUrl: String, username: String, password: String) {
        if (uiState == UiState.Loading) return
        val cleanUrl = baseUrl.trim().trimEnd('/')
        pendingLogin = null
        loginNotice = null
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
                baseUrl = request.baseUrl
                sessionManager.onLoginOk(client, request.baseUrl, SiteRules.deviceId(request.baseUrl))
                pendingLoginUrl = null
                forceLogin = false
                val device = SavedDevice(
                    id = SiteRules.deviceId(request.baseUrl),
                    url = SiteRules.normalizeUrl(request.baseUrl),
                    lastSeenAt = nowEpochSeconds(),
                )
                upsertDevice(device)
                refreshDeviceInfo(client, device)
                UiState.Ready
            }
            ApiResult.Unauthorized -> UiState.Error("Usuario o contraseña incorrectos")
            is ApiResult.RateLimited -> UiState.RateLimited(result.retryAfterSeconds)
            ApiResult.Unreachable -> UiState.RouterUnreachable
            is ApiResult.HttpError -> UiState.Error(result.message ?: "Error HTTP ${result.code}")
            is ApiResult.CertificateChanged -> UiState.Error(certificateChangedMessage(result.host))
            is ApiResult.CertificateUnsupported -> UiState.Error(result.message)
            is ApiResult.Unknown -> UiState.Error(result.message ?: "Error desconocido")
        }
    }

    /**
     * Conecta con un dispositivo desde la hoja de dispositivos, sin salir de la
     * app. Si el certificado necesita confirmacion devuelve [ConnectResult.Certificate]
     * para que el dialogo la pida y vuelva a llamar con la huella.
     */
    suspend fun connectDevice(
        url: String,
        username: String,
        password: String,
        trustHost: String? = null,
        trustFingerprint: String? = null,
        networkId: String? = null,
    ): ConnectResult {
        ensureSites()
        val cleanUrl = SiteRules.normalizeUrl(url)
        if (cleanUrl.isBlank()) return ConnectResult.Failure("Escribe la dirección del router")
        if (trustHost != null && trustFingerprint != null) {
            runCatching { fingerprints.save(trustHost, trustFingerprint) }
                .onFailure { return ConnectResult.Failure("No se pudo guardar la huella del certificado") }
        }
        val resolved = when (val probe = endpointProbe.probe(cleanUrl)) {
            is EndpointProbeResult.Resolved -> probe
            is EndpointProbeResult.CertificateConfirmationRequired ->
                return ConnectResult.Certificate(probe.host, probe.fingerprint)
            is EndpointProbeResult.CertificateChanged ->
                return ConnectResult.Failure("El certificado de ${probe.host} ha cambiado")
            is EndpointProbeResult.Unsupported -> return ConnectResult.Failure(probe.message)
            EndpointProbeResult.Unreachable -> return ConnectResult.Failure("No se puede alcanzar el router")
            EndpointProbeResult.InvalidUrl -> return ConnectResult.Failure("URL del router no válida")
        }
        val client = try {
            apiFactory.create(resolved.baseUrl, resolved.certificatePin)
        } catch (error: SelfSignedCertificatesUnsupportedException) {
            return ConnectResult.Failure(error.message ?: "Certificado no soportado")
        }
        return when (val result = client.login(username.ifBlank { "root" }, password)) {
            is ApiResult.Ok -> {
                val device = SavedDevice(
                    id = SiteRules.deviceId(resolved.baseUrl),
                    url = SiteRules.normalizeUrl(resolved.baseUrl),
                    lastSeenAt = nowEpochSeconds(),
                )
                sessionManager.onLoginOk(client, resolved.baseUrl, device.id)
                api = client
                baseUrl = device.url
                pendingLoginUrl = null
                uiState = UiState.Ready
                upsertDevice(device, networkOverride = networkId)
                refreshDeviceInfo(client, device)
                ConnectResult.Ok
            }
            ApiResult.Unauthorized -> ConnectResult.Failure("Usuario o contraseña incorrectos")
            is ApiResult.RateLimited -> ConnectResult.Failure(
                result.retryAfterSeconds?.let { "El router limita los intentos. Prueba en $it s" }
                    ?: "El router limita los intentos",
            )
            ApiResult.Unreachable -> ConnectResult.Failure("No se puede alcanzar el router")
            is ApiResult.HttpError -> ConnectResult.Failure(result.message ?: "Error HTTP ${result.code}")
            is ApiResult.CertificateChanged -> ConnectResult.Failure("El certificado de ${result.host} ha cambiado")
            is ApiResult.CertificateUnsupported -> ConnectResult.Failure(result.message)
            is ApiResult.Unknown -> ConnectResult.Failure(result.message ?: "Error desconocido")
        }
    }

    /**
     * Cambia al dispositivo indicado restaurando su cookie. Devuelve false si
     * no hay sesion guardada, para que la UI pida credenciales.
     */
    suspend fun trySwitchDevice(deviceId: String): Boolean {
        ensureSites()
        val device = sites.device(deviceId) ?: return false
        if (loggedIn && siteMessage == null && device.id == currentDevice?.id) return true
        val client = try {
            apiFactory.create(device.url, pinFor(device.url))
        } catch (error: SelfSignedCertificatesUnsupportedException) {
            return false
        }
        val result = sessionManager.restoreWithResult(client, device.id)
        return if (result is ApiResult.Ok) {
            api = client
            baseUrl = device.url
            uiState = UiState.Ready
            pendingLoginUrl = null
            val owner = sites.networks.firstOrNull { network -> network.devices.any { it.id == device.id } }
            sites = sites.copy(
                currentNetworkId = owner?.id ?: sites.currentNetworkId,
                currentDeviceId = device.id,
            )
            persist()
            markSeen(device)
            refreshDeviceInfo(client, device)
            true
        } else {
            false
        }
    }

    fun selectNetwork(networkId: String) {
        val network = sites.network(networkId) ?: return
        sites = sites.copy(
            currentNetworkId = network.id,
            currentDeviceId = network.devices.firstOrNull()?.id,
        )
        persist()
        val device = network.devices.firstOrNull()
        if (device == null) {
            clearSession()
            pendingLoginUrl = null
            return
        }
        viewModelScope.launch {
            if (!trySwitchDevice(device.id)) {
                clearSession()
                pendingLoginUrl = device.url
            }
        }
    }

    fun addNetwork(name: String) {
        val clean = name.trim()
        if (clean.isBlank()) {
            siteMessage = "Escribe un nombre para la red"
            return
        }
        val network = SavedNetwork(
            id = SiteRules.networkId(clean, sites.networks),
            name = clean,
        )
        sites = sites.copy(networks = sites.networks + network, currentNetworkId = network.id)
        persist()
    }

    fun renameNetwork(networkId: String, name: String) {
        val clean = name.trim()
        if (clean.isBlank()) {
            siteMessage = "Escribe un nombre para la red"
            return
        }
        val network = sites.network(networkId) ?: return
        sites = sites.withNetwork(network.copy(name = clean))
        persist()
    }

    fun deleteNetwork(networkId: String) {
        val remaining = sites.networks.filterNot { it.id == networkId }
        val next = remaining.firstOrNull()
        sites = sites.copy(
            networks = remaining,
            currentNetworkId = next?.id,
            currentDeviceId = next?.devices?.firstOrNull()?.id,
        )
        persist()
        if (sites.currentNetworkId == null) {
            clearSession()
            pendingLoginUrl = null
            return
        }
        val device = sites.currentDevice
        viewModelScope.launch {
            if (device == null) {
                clearSession()
                pendingLoginUrl = null
            } else if (!trySwitchDevice(device.id)) {
                clearSession()
                pendingLoginUrl = device.url
            }
        }
    }

    fun removeDevice(deviceId: String) {
        val network = sites.networks.firstOrNull { it.devices.any { d -> d.id == deviceId } } ?: return
        val wasCurrent = sites.currentDeviceId == deviceId
        sites = sites.withoutDevice(network.id, deviceId)
        persist()
        if (!wasCurrent) return
        val next = sites.currentNetwork?.devices?.firstOrNull()
        if (next == null) {
            clearSession()
            pendingLoginUrl = null
            return
        }
        viewModelScope.launch {
            if (!trySwitchDevice(next.id)) {
                clearSession()
                pendingLoginUrl = next.url
            }
        }
    }

    /**
     * Importa como dispositivos los routers que el panel conoce en su fleet.
     * Nunca guarda contrasenas del fleet: solo nombre y direccion.
     */
    fun importFleet() {
        val client = api ?: run {
            siteMessage = "Conéctate a un router para importar su fleet"
            return
        }
        viewModelScope.launch {
            when (val result = client.fleet()) {
                is ApiResult.Ok -> {
                    val network = currentNetwork
                    if (network == null) {
                        siteMessage = "Crea una red antes de importar"
                        return@launch
                    }
                    var added = 0
                    result.value.nodes.forEach { node ->
                        val url = SiteRules.urlFromFleetAddress(node.address)
                        if (url.isBlank()) return@forEach
                        val id = SiteRules.deviceId(url)
                        if (sites.device(id) != null) return@forEach
                        sites = sites.withDevice(
                            network.id,
                            SavedDevice(
                                id = id,
                                url = url,
                                name = node.name.takeIf { it.isNotBlank() }.orEmpty(),
                                role = "router",
                            ),
                        )
                        added++
                    }
                    persist()
                    siteMessage = if (added == 0) {
                        "No hay routers nuevos en el fleet"
                    } else {
                        "Añadidos $added routers del fleet"
                    }
                }
                ApiResult.Unauthorized -> onSessionExpired()
                else -> siteMessage = "No se pudo leer el fleet del router"
            }
        }
    }

    fun clearSiteMessage() {
        siteMessage = null
    }

    fun resetLoginState() {
        if (uiState != UiState.Loading) {
            pendingLogin = null
            uiState = UiState.Idle
        }
    }

    /** El aviso de contrasena se consume al entrar o al volver atras. */
    fun consumeLoginNotice() {
        loginNotice = null
    }

    /**
     * Cierra la sesion del dispositivo actual. La cookie se revoca y se borra,
     * pero el dispositivo sigue en la lista. Si otra red o dispositivo tiene
     * sesion, se cambia a el; si no, se pide login de nuevo.
     */
    fun logout() {
        val client = api ?: return
        val device = currentDevice
        pendingLogin = null
        viewModelScope.launch {
            sessionManager.logout(client, device?.id ?: SessionManager.DEFAULT_PROFILE)
            api = null
            baseUrl = null
            uiState = UiState.Idle
            val alternative = sites.networks
                .asSequence()
                .flatMap { it.devices.asSequence() }
                .firstOrNull { it.id != device?.id }
            if (alternative != null && trySwitchDevice(alternative.id)) return@launch
            pendingLoginUrl = device?.url
        }
    }

    /**
     * Cierre por sesion caducada: un GET devolvio 401. No se llama al router
     * (ya no hay sesion) y se borra la cookie guardada para no reintentar con
     * un token muerto en el siguiente arranque.
     */
    fun onSessionExpired() {
        if (api == null) return
        val device = currentDevice
        pendingLogin = null
        viewModelScope.launch {
            sessionManager.forgetSession(device?.id ?: SessionManager.DEFAULT_PROFILE)
            api = null
            baseUrl = null
            uiState = UiState.Idle
            pendingLoginUrl = device?.url
        }
    }

    /**
     * La contrasena cambio en el router: todas las sesiones murieron. Se
     * olvida la cookie local y se pide login con un aviso, sin llamar al
     * router (la sesion ya no vale).
     */
    fun onPasswordChanged() {
        val device = currentDevice
        pendingLogin = null
        viewModelScope.launch {
            sessionManager.forgetSession(device?.id ?: SessionManager.DEFAULT_PROFILE)
            api = null
            baseUrl = null
            uiState = UiState.Idle
            pendingLoginUrl = device?.url
            forceLogin = true
            loginNotice = "Contraseña cambiada. Todas las sesiones se cerraron: entra con la nueva."
        }
    }

    /** Aviso de un solo uso en la pantalla de acceso. */
    var loginNotice by mutableStateOf<String?>(null)
        private set

    /**
     * Hay que entrar de nuevo ahora mismo (por ejemplo tras cambiar la
     * contrasena): la app muestra el login en vez de la lista de dispositivos.
     */
    var forceLogin by mutableStateOf(false)
        private set

    fun clearLoginNotice() {
        loginNotice = null
    }

    /** Carga la lista guardada la primera vez que se necesita. */
    private suspend fun ensureSites() {
        if (sites.networks.isEmpty()) {
            sites = siteStore.load(sessionManager.lastUrl())
        }
        sitesLoaded = true
    }

    private fun clearSession() {
        api = null
        baseUrl = null
        uiState = UiState.Idle
        pendingLoginUrl = null
    }

    private suspend fun pinFor(url: String): CertificatePin? =
        fingerprints.readForUrl(url).takeIf { url.startsWith("https://", ignoreCase = true) }

    private suspend fun markSeen(device: SavedDevice) {
        val network = sites.networks.firstOrNull { it.devices.any { d -> d.id == device.id } } ?: return
        sites = sites.withDevice(network.id, device.copy(lastSeenAt = nowEpochSeconds()))
        persist()
    }

    private suspend fun refreshDeviceInfo(client: NetGripApi, device: SavedDevice) {
        val board = (client.board() as? ApiResult.Ok)?.value
        val mode = (client.mode() as? ApiResult.Ok)?.value
        if (board == null && mode == null) return
        val updated = device.copy(
            name = board?.hostname?.takeIf { it.isNotBlank() } ?: device.name,
            model = board?.model?.takeIf { it.isNotBlank() } ?: device.model,
            role = mode?.effectiveRole ?: device.role,
            lastSeenAt = nowEpochSeconds(),
        )
        upsertDevice(updated)
    }

    /**
     * Guarda el dispositivo en su red: la que ya lo contiene o, si es nuevo, la
     * actual. Si no hay ninguna red, crea "Mi red".
     */
    private suspend fun upsertDevice(device: SavedDevice, networkOverride: String? = null) {
        var state = sites
        val owning = state.networks.firstOrNull { network -> network.devices.any { it.id == device.id } }
        val target = owning
            ?: networkOverride?.let { state.network(it) }
            ?: state.currentNetwork
        if (target == null) {
            val network = SavedNetwork(
                id = SiteRules.networkId(SiteRules.DEFAULT_NETWORK_NAME, state.networks),
                name = SiteRules.DEFAULT_NETWORK_NAME,
                devices = listOf(device),
            )
            state = state.copy(networks = state.networks + network, currentNetworkId = network.id)
        } else {
            state = state.withDevice(target.id, device).copy(currentNetworkId = target.id)
        }
        sites = state.copy(currentDeviceId = device.id)
        persist()
    }

    private fun persist() {
        val snapshot = sites
        viewModelScope.launch { siteStore.save(snapshot) }
    }

    private data class PendingLogin(
        val baseUrl: String,
        val username: String,
        val password: String,
    )
}

private fun certificateChangedMessage(host: String): String =
    "El certificado de $host ha cambiado. No se han enviado credenciales ni se ha iniciado sesión."
