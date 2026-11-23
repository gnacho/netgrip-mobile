package dev.gnacho.netgrip.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.WifiUiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Estado de la pestana Panel. */
data class PanelState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val board: BoardDto? = null,
    val system: SystemDto? = null,
    val wan: WanDto? = null,
    val mode: ModeDto? = null,
    val clients: List<ClientDto> = emptyList(),
    val ports: List<EthPortDto> = emptyList(),
)

/** Estado de la pestana Clientes. */
data class ClientsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val clients: List<ClientDto> = emptyList(),
    val bands: List<String> = emptyList(),
)

/** Estado de la pestana WiFi. */
data class WifiTabState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val hasWifi: Boolean = false,
    val interfaces: List<WifiUiDto> = emptyList(),
    val radios: List<WirelessRadioDto> = emptyList(),
)

/** Estado de la pestana Puertos. */
data class PortsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val ports: List<EthPortDto> = emptyList(),
)

/** Estado de la pestana Ajustes. */
data class SettingsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val board: BoardDto? = null,
    val mode: ModeDto? = null,
    val access: AccessDto? = null,
    val mdns: MdnsDto? = null,
    val selfUpdate: SelfUpdateDto? = null,
)

/**
 * Estado de la app con sesion iniciada. Cada pestana carga sus lecturas la
 * primera vez que se abre y se refresca a peticion del usuario. Ninguna
 * llamada hace polling.
 *
 * Un 401 en cualquier lectura marca [sessionExpired] una sola vez y la shell
 * devuelve al login sin reintentar.
 */
class ShellViewModel(private val api: NetGripApi) : ViewModel() {

    private val _panel = MutableStateFlow(PanelState())
    val panel: StateFlow<PanelState> = _panel.asStateFlow()

    private val _clients = MutableStateFlow(ClientsState())
    val clients: StateFlow<ClientsState> = _clients.asStateFlow()

    private val _wifi = MutableStateFlow(WifiTabState())
    val wifi: StateFlow<WifiTabState> = _wifi.asStateFlow()

    private val _ports = MutableStateFlow(PortsState())
    val ports: StateFlow<PortsState> = _ports.asStateFlow()

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private var panelJob: Job? = null
    private var clientsJob: Job? = null
    private var wifiJob: Job? = null
    private var portsJob: Job? = null
    private var settingsJob: Job? = null

    fun ensurePanel() {
        if (_panel.value.loaded || panelJob?.isActive == true) return
        loadPanel()
    }

    fun loadPanel() {
        if (panelJob?.isActive == true) return
        panelJob = viewModelScope.launch {
            _panel.update { it.copy(loading = true, error = null) }
            var next = PanelState(loading = false, loaded = true)
            var failure: String? = null
            fun <T> merge(result: ApiResult<T>, apply: (T) -> Unit) {
                when (result) {
                    is ApiResult.Ok -> apply(result.value)
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        if (failure == null) failure = describe(result)
                    }
                }
            }
            coroutineScope {
                val board = async { api.board() }
                val system = async { api.systemInfo() }
                val wan = async { api.wan() }
                val mode = async { api.mode() }
                val clients = async { api.clients() }
                val ports = async { api.ethPorts() }
                merge(board.await()) { next = next.copy(board = it) }
                merge(system.await()) { next = next.copy(system = it) }
                merge(wan.await()) { next = next.copy(wan = it) }
                merge(mode.await()) { next = next.copy(mode = it) }
                merge(clients.await()) { value ->
                    next = next.copy(clients = value.clients)
                }
                merge(ports.await()) { value ->
                    next = next.copy(ports = value.ports)
                }
            }
            _panel.value = next.copy(error = failure)
        }
    }

    fun ensureClients() {
        if (_clients.value.loaded || clientsJob?.isActive == true) return
        loadClients()
    }

    fun loadClients() {
        if (clientsJob?.isActive == true) return
        clientsJob = viewModelScope.launch {
            _clients.update { it.copy(loading = true, error = null) }
            val next = when (val result = api.clients()) {
                is ApiResult.Ok -> ClientsState(
                    loaded = true,
                    clients = result.value.clients,
                    bands = result.value.bands,
                )
                else -> ClientsState(loaded = true, error = failWith(result))
            }
            _clients.value = next
        }
    }

    fun ensureWifi() {
        if (_wifi.value.loaded || wifiJob?.isActive == true) return
        loadWifi()
    }

    fun loadWifi() {
        if (wifiJob?.isActive == true) return
        wifiJob = viewModelScope.launch {
            _wifi.update { it.copy(loading = true, error = null) }
            var next = WifiTabState(loading = false, loaded = true)
            var failure: String? = null
            coroutineScope {
                val wifi = async { api.wifi() }
                val wireless = async { api.wireless() }
                val mode = async { api.mode() }
                val modeResult = mode.await()
                when (val result = wifi.await()) {
                    is ApiResult.Ok -> next = next.copy(interfaces = result.value.interfaces)
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        failure = failure ?: describe(result)
                    }
                }
                when (val result = wireless.await()) {
                    is ApiResult.Ok -> next = next.copy(radios = result.value)
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        failure = failure ?: describe(result)
                    }
                }
                next = next.copy(hasWifi = hasWifi(modeResult, next.interfaces.isNotEmpty()))
            }
            _wifi.value = next.copy(error = failure)
        }
    }

    fun ensurePorts() {
        if (_ports.value.loaded || portsJob?.isActive == true) return
        loadPorts()
    }

    fun loadPorts() {
        if (portsJob?.isActive == true) return
        portsJob = viewModelScope.launch {
            _ports.update { it.copy(loading = true, error = null) }
            _ports.value = when (val result = api.ethPorts()) {
                is ApiResult.Ok -> PortsState(loaded = true, ports = result.value.ports)
                else -> PortsState(loaded = true, error = failWith(result))
            }
        }
    }

    fun ensureSettings() {
        if (_settings.value.loaded || settingsJob?.isActive == true) return
        loadSettings()
    }

    fun loadSettings() {
        if (settingsJob?.isActive == true) return
        settingsJob = viewModelScope.launch {
            _settings.update { it.copy(loading = true, error = null) }
            var next = SettingsState(loading = false, loaded = true)
            var failure: String? = null
            fun <T> merge(result: ApiResult<T>, apply: (T) -> Unit) {
                when (result) {
                    is ApiResult.Ok -> apply(result.value)
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        if (failure == null) failure = describe(result)
                    }
                }
            }
            coroutineScope {
                val board = async { api.board() }
                val mode = async { api.mode() }
                val access = async { api.access() }
                val mdns = async { api.mdns() }
                val update = async { api.selfUpdate() }
                merge(board.await()) { next = next.copy(board = it) }
                merge(mode.await()) { next = next.copy(mode = it) }
                merge(access.await()) { next = next.copy(access = it) }
                merge(mdns.await()) { next = next.copy(mdns = it) }
                merge(update.await()) { next = next.copy(selfUpdate = it) }
            }
            _settings.value = next.copy(error = failure)
        }
    }

    /** Refresca la pestana indicada. */
    fun refresh(tab: ShellTab) {
        when (tab) {
            ShellTab.PANEL -> loadPanel()
            ShellTab.CLIENTS -> loadClients()
            ShellTab.WIFI -> loadWifi()
            ShellTab.PORTS -> loadPorts()
            ShellTab.SETTINGS -> loadSettings()
        }
    }

    private fun failWith(result: ApiResult<*>): String {
        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
        return describe(result)
    }

    private fun hasWifi(modeResult: ApiResult<ModeDto>, fallback: Boolean): Boolean =
        when (modeResult) {
            is ApiResult.Ok -> modeResult.value.hasWifi
            else -> fallback
        }

    private fun describe(result: ApiResult<*>): String = when (result) {
        is ApiResult.Ok -> ""
        ApiResult.Unauthorized -> "La sesión ha caducado"
        is ApiResult.RateLimited -> result.retryAfterSeconds
            ?.let { "El router limita las peticiones. Reintenta en $it s" }
            ?: "El router limita las peticiones"
        is ApiResult.HttpError -> "El router respondió con error HTTP ${result.code}"
        ApiResult.Unreachable -> "No se puede alcanzar el router"
        is ApiResult.CertificateChanged -> "El certificado de ${result.host} ha cambiado"
        is ApiResult.CertificateUnsupported -> result.message
        is ApiResult.Unknown -> result.message ?: "Error desconocido"
    }
}
