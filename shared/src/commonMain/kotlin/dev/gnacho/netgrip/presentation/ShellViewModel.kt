package dev.gnacho.netgrip.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.data.api.NetGripApi
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.ClientMetaEntryDto
import dev.gnacho.netgrip.data.dto.ClientMetaDto
import dev.gnacho.netgrip.data.dto.ClientsDto
import dev.gnacho.netgrip.data.dto.EthPortDto
import dev.gnacho.netgrip.data.dto.HistoryEntryDto
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.data.dto.LedDto
import dev.gnacho.netgrip.data.dto.LogEntryDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.ConfirmWindowDto
import dev.gnacho.netgrip.data.dto.RadioEditRequest
import dev.gnacho.netgrip.data.dto.WifiEditRequest
import dev.gnacho.netgrip.data.dto.WifiUiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import dev.gnacho.netgrip.domain.clients.ClientActionMessages
import dev.gnacho.netgrip.domain.clients.ClientBlockRules
import dev.gnacho.netgrip.domain.clients.ClientMetaRules
import dev.gnacho.netgrip.domain.clients.ClientReservationRules
import dev.gnacho.netgrip.domain.clients.ClientWriteCheck
import dev.gnacho.netgrip.domain.clients.MacAddress
import dev.gnacho.netgrip.domain.clients.ReservationAction
import dev.gnacho.netgrip.domain.format.nowEpochSeconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    val history: List<HistoryEntryDto> = emptyList(),
)

/** Estado de la pestana Clientes. */
data class ClientsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val clients: List<ClientDto> = emptyList(),
    val bands: List<String> = emptyList(),
    val meta: Map<String, ClientMetaEntryDto> = emptyMap(),
    /** MAC de la accion de escritura en curso, si hay una. */
    val busyMac: String? = null,
    /** Resultado de la ultima accion, para el aviso. */
    val actionMessage: String? = null,
    /** Error de la ultima accion, para el aviso. */
    val actionError: String? = null,
)

/** Ventana de auto-reversion que arma el router tras una escritura. */
data class PendingConfirm(
    val id: String,
    val expires: Long,
    val secondsLeft: Int,
)

/** Estado de la pestana WiFi. */
data class WifiTabState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val hasWifi: Boolean = false,
    val interfaces: List<WifiUiDto> = emptyList(),
    val radios: List<WirelessRadioDto> = emptyList(),
    /** Escritura en curso: bloquea los botones de guardar. */
    val busy: Boolean = false,
    /** Resultado de la ultima escritura, para el aviso. */
    val actionMessage: String? = null,
    val actionError: String? = null,
    /** Cambios aplicados que el router deshara si no se confirman. */
    val pendingConfirm: PendingConfirm? = null,
)

/** Estado de la pestana Puertos. */
data class PortsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val ports: List<EthPortDto> = emptyList(),
)

/** Estado de la pestana Diagnostico. */
data class DiagnosticsState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val logs: List<LogEntryDto> = emptyList(),
    /** Tamano del buffer completo, antes de filtrar. */
    val logTotal: Int = 0,
    val filter: String = "",
    val ledsApplicable: Boolean = false,
    val leds: List<LedDto> = emptyList(),
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
class ShellViewModel(
    private val api: NetGripApi,
    /** Reloj en segundos epoch; los tests lo fijan para la cuenta atras. */
    private val now: () -> Long = ::nowEpochSeconds,
) : ViewModel() {

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

    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics: StateFlow<DiagnosticsState> = _diagnostics.asStateFlow()

    private val _sessionExpired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    private var panelJob: Job? = null
    private var clientsJob: Job? = null
    private var wifiJob: Job? = null
    private var portsJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var settingsJob: Job? = null
    private var confirmJob: Job? = null

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
                val history = async { api.history() }
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
                merge(history.await()) { value ->
                    next = next.copy(history = value.entries)
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
            val (clientsResult, metaResult) = readClientsAndMeta()
            var next = _clients.value.copy(loading = false, loaded = true, error = null)
            if (clientsResult is ApiResult.Ok) {
                next = next.copy(
                    clients = clientsResult.value.clients,
                    bands = clientsResult.value.bands,
                )
            } else {
                next = next.copy(error = failWith(clientsResult))
            }
            if (metaResult is ApiResult.Ok) {
                next = next.copy(meta = metaResult.value.meta)
            } else {
                if (metaResult == ApiResult.Unauthorized) _sessionExpired.value = true
                if (next.error == null) next = next.copy(error = describe(metaResult))
            }
            _clients.value = next
        }
    }

    /**
     * POST /api/clients/meta. Con nombre y tipo vacios borra el override, que
     * es la unica forma de volver al nombre que sirve el router.
     */
    fun updateClientMeta(mac: String, rawName: String, rawDeviceType: String) {
        val state = _clients.value
        if (state.busyMac != null) return
        val normalizedMac = MacAddress.normalize(mac)
        if (normalizedMac == null) {
            failLocally("La MAC del cliente no es válida, no se envía la petición")
            return
        }
        val name = ClientMetaRules.normalizeName(rawName)
        val deviceType = ClientMetaRules.normalizeDeviceType(rawDeviceType)
        if (name.isEmpty() && deviceType.isEmpty() && !ClientMetaRules.hasOverride(state.meta, normalizedMac)) {
            setFeedback(message = "Este cliente no tenía nombre ni tipo guardados")
            return
        }
        viewModelScope.launch {
            setBusy(normalizedMac)
            when (val result = api.setClientMeta(normalizedMac, name, deviceType)) {
                is ApiResult.Ok -> {
                    val applied = ClientWriteCheck.metaApplied(result.value.meta, normalizedMac, name, deviceType)
                    _clients.update { it.copy(meta = result.value.meta) }
                    val readError = reloadAfterWrite()
                    when {
                        !applied -> setFeedback(
                            error = "El router aceptó la petición, pero el nombre o el tipo no aparecen guardados.",
                        )
                        readError != null -> setFeedback(
                            error = ClientActionMessages.readFailed(readError),
                        )
                        name.isEmpty() && deviceType.isEmpty() -> setFeedback(message = ClientActionMessages.metaCleared())
                        else -> setFeedback(message = ClientActionMessages.metaSaved())
                    }
                }
                else -> setFeedback(error = failWith(result))
            }
        }
    }

    /** Bloquea un cliente en todas las bandas ("") o en una concreta. */
    fun blockClient(client: ClientDto, band: String) = runBlockWrite(client, blocked = true, band = band)

    /** Desbloquea un cliente en todas las bandas ("") o en una concreta. */
    fun unblockClient(client: ClientDto, band: String) = runBlockWrite(client, blocked = false, band = band)

    /**
     * Fija la IP actual del cliente o quita su reserva. Nunca pide una IP
     * nueva: el servidor no valida que la IP elegida sea libre.
     */
    fun toggleReservation(client: ClientDto) {
        when (val action = ClientReservationRules.actionFor(client)) {
            is ReservationAction.SetCurrentIp -> runReserveWrite(client, action.ip, reserved = true)
            ReservationAction.Remove -> runReserveWrite(client, client.ip.orEmpty(), reserved = false)
            ReservationAction.Unavailable -> failLocally(
                if (client.self) {
                    "No se reserva la IP del propio dispositivo"
                } else {
                    "Este cliente no se puede reservar: no tiene IP o el router no la marca como reservable"
                },
            )
        }
    }

    /** POST /api/wol. No persiste nada: solo envía el paquete mágico. */
    fun wakeOnLan(client: ClientDto) {
        val state = _clients.value
        if (state.busyMac != null) return
        val normalizedMac = MacAddress.normalize(client.mac)
        if (normalizedMac == null) {
            failLocally("La MAC del cliente no es válida, no se envía el paquete")
            return
        }
        viewModelScope.launch {
            setBusy(normalizedMac)
            when (val result = api.wakeOnLan(normalizedMac)) {
                is ApiResult.Ok -> {
                    val status = result.value.status.trim()
                    if (status.isEmpty() || status == "sent") {
                        setFeedback(message = ClientActionMessages.wolSent())
                    } else {
                        setFeedback(error = "El router respondió \"$status\" al despertar el cliente")
                    }
                }
                else -> setFeedback(error = failWith(result))
            }
        }
    }

    /** Limpia el aviso de la ultima accion despues de mostrarlo. */
    fun clearClientActionFeedback() {
        _clients.update { it.copy(actionMessage = null, actionError = null) }
    }

    fun ensureWifi() {
        if (_wifi.value.loaded || wifiJob?.isActive == true) return
        loadWifi()
    }

    fun loadWifi() {
        if (wifiJob?.isActive == true) return
        wifiJob = viewModelScope.launch {
            _wifi.update { it.copy(loading = true, error = null) }
            var next = _wifi.value.copy(loading = false, loaded = true)
            var failure: String? = null
            coroutineScope {
                val wifi = async { api.wifi() }
                val wireless = async { api.wireless() }
                val mode = async { api.mode() }
                // La ventana de reversion sobrevive a cerrar la app: se
                // reanuda al cargar la pestana en vez de perderse.
                val confirm = async { api.confirmStatus() }
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
                (confirm.await() as? ApiResult.Ok)?.let { status ->
                    val id = status.value.id
                    val expires = status.value.expires
                    if (status.value.pending && id.isNotBlank() && expires > 0) {
                        next = next.copy(pendingConfirm = pendingAt(id, expires))
                        startConfirmTicker()
                    }
                }
                next = next.copy(hasWifi = hasWifi(modeResult, next.interfaces.isNotEmpty()))
            }
            _wifi.value = next.copy(error = failure)
        }
    }

    /**
     * POST /api/wifi. Nunca pinta el cambio de forma optimista: relee
     * /api/wifi y /api/wireless y solo entonces informa.
     */
    fun applyWifi(edit: WifiEditRequest) {
        if (_wifi.value.busy) return
        viewModelScope.launch {
            _wifi.update { it.copy(busy = true, actionMessage = null, actionError = null) }
            when (val result = api.setWifi(edit)) {
                is ApiResult.Ok -> finishWifiWrite(
                    status = result.value.status,
                    rolledBack = result.value.rolledBack,
                    error = result.value.error,
                    window = result.value.confirm,
                )
                else -> setWifiFeedback(error = failWith(result))
            }
        }
    }

    /** POST /api/wifi/radio: canal, ancho y potencia de una radio. */
    fun applyWifiRadio(edit: RadioEditRequest) {
        if (_wifi.value.busy) return
        viewModelScope.launch {
            _wifi.update { it.copy(busy = true, actionMessage = null, actionError = null) }
            when (val result = api.setWifiRadio(edit)) {
                is ApiResult.Ok -> finishWifiWrite(
                    status = result.value.status,
                    rolledBack = result.value.rolledBack,
                    error = result.value.error,
                    window = result.value.confirm,
                )
                else -> setWifiFeedback(error = failWith(result))
            }
        }
    }

    /** Conserva los cambios aplicados y cierra la ventana de reversion. */
    fun keepWifiChanges() {
        val pending = _wifi.value.pendingConfirm ?: return
        confirmJob?.cancel()
        _wifi.update { it.copy(pendingConfirm = null) }
        viewModelScope.launch {
            when (val result = api.keepConfirm(pending.id)) {
                is ApiResult.Ok -> setWifiFeedback(message = "Cambios confirmados")
                else -> setWifiFeedback(error = failWith(result))
            }
        }
    }

    /** Deshace los cambios aplicados sin esperar a que caduque la ventana. */
    fun revertWifiChanges() {
        val pending = _wifi.value.pendingConfirm ?: return
        confirmJob?.cancel()
        _wifi.update { it.copy(pendingConfirm = null, busy = true) }
        viewModelScope.launch {
            when (val result = api.revertConfirm(pending.id)) {
                is ApiResult.Ok -> {
                    val readError = reloadWifi()
                    setWifiFeedback(
                        message = if (readError == null) "Cambios deshechos" else null,
                        error = readError,
                    )
                }
                else -> setWifiFeedback(error = failWith(result))
            }
        }
    }

    /** Limpia el aviso de la ultima escritura despues de mostrarlo. */
    fun clearWifiFeedback() {
        _wifi.update { it.copy(actionMessage = null, actionError = null) }
    }

    private suspend fun finishWifiWrite(
        status: String,
        rolledBack: Boolean,
        error: String?,
        window: ConfirmWindowDto?,
    ) {
        val serverError = error?.takeIf { it.isNotBlank() }
        if (rolledBack || status == "rolled_back" || status == "failed") {
            val readError = reloadWifi()
            setWifiFeedback(
                error = serverError ?: if (readError == null) {
                    "El router deshizo el cambio y dejó la configuración como estaba"
                } else {
                    readError
                },
            )
            return
        }
        val readError = reloadWifi()
        val id = window?.id.orEmpty()
        val expires = window?.expires ?: 0
        if (id.isNotBlank() && expires > 0) {
            _wifi.update { it.copy(pendingConfirm = pendingAt(id, expires)) }
            startConfirmTicker()
            setWifiFeedback(
                message = if (readError == null) {
                    "Cambio aplicado. Confírmalo antes de que el router lo deshaga."
                } else {
                    null
                },
                error = readError?.let { "El cambio se aplicó, pero no se pudo releer: $it" },
            )
            return
        }
        setWifiFeedback(
            message = if (readError == null) "Cambios aplicados y verificados" else null,
            error = readError,
        )
    }

    /** Relee el estado WiFi sin tocar la deteccion de radios ni el modo. */
    private suspend fun reloadWifi(): String? {
        val wifi = api.wifi()
        val wireless = api.wireless()
        var next = _wifi.value
        var readError: String? = null
        when (wifi) {
            is ApiResult.Ok -> next = next.copy(interfaces = wifi.value.interfaces, error = null)
            else -> {
                if (wifi == ApiResult.Unauthorized) _sessionExpired.value = true
                readError = describe(wifi)
            }
        }
        when (wireless) {
            is ApiResult.Ok -> next = next.copy(radios = wireless.value)
            else -> {
                if (wireless == ApiResult.Unauthorized) _sessionExpired.value = true
                if (readError == null) readError = describe(wireless)
            }
        }
        _wifi.value = next
        return readError
    }

    private fun setWifiFeedback(message: String? = null, error: String? = null) {
        _wifi.update { it.copy(busy = false, actionMessage = message, actionError = error) }
    }

    private fun pendingAt(id: String, expires: Long): PendingConfirm =
        PendingConfirm(id = id, expires = expires, secondsLeft = secondsLeft(expires))

    private fun secondsLeft(expires: Long): Int =
        (expires - now()).coerceAtLeast(0).toInt()

    /**
     * Cuenta atras de la ventana. Al llegar a cero el router ya revirtio, asi
     * que se relee y se avisa: no se asume que el cambio sigue puesto.
     */
    private fun startConfirmTicker() {
        confirmJob?.cancel()
        confirmJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val pending = _wifi.value.pendingConfirm ?: return@launch
                val left = secondsLeft(pending.expires)
                if (left <= 0) {
                    _wifi.update { it.copy(pendingConfirm = null) }
                    val readError = reloadWifi()
                    setWifiFeedback(
                        error = readError ?: "El router deshizo el cambio al no confirmarse a tiempo",
                    )
                    return@launch
                }
                _wifi.update { it.copy(pendingConfirm = pending.copy(secondsLeft = left)) }
            }
        }
    }

    fun ensureDiagnostics() {
        if (_diagnostics.value.loaded || diagnosticsJob?.isActive == true) return
        loadDiagnostics()
    }

    /** Recarga los logs con [filter] (texto, no regex) y el inventario de LEDs. */
    fun loadDiagnostics(filter: String = _diagnostics.value.filter) {
        if (diagnosticsJob?.isActive == true) return
        diagnosticsJob = viewModelScope.launch {
            _diagnostics.update { it.copy(loading = true, error = null, filter = filter) }
            var next = _diagnostics.value.copy(loading = false, loaded = true)
            var failure: String? = null
            coroutineScope {
                val logs = async { api.logs(filter = filter) }
                val leds = async { api.leds() }
                when (val result = logs.await()) {
                    is ApiResult.Ok -> next = next.copy(logs = result.value.entries, logTotal = result.value.total)
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        failure = failure ?: describe(result)
                    }
                }
                when (val result = leds.await()) {
                    is ApiResult.Ok -> next = next.copy(
                        ledsApplicable = result.value.applicable,
                        leds = result.value.leds,
                    )
                    else -> {
                        if (result == ApiResult.Unauthorized) _sessionExpired.value = true
                        failure = failure ?: describe(result)
                    }
                }
            }
            _diagnostics.value = next.copy(error = failure)
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
            ShellTab.DIAGNOSTICS -> loadDiagnostics()
            ShellTab.SETTINGS -> loadSettings()
        }
    }

    private suspend fun readClientsAndMeta(): Pair<ApiResult<ClientsDto>, ApiResult<ClientMetaDto>> =
        coroutineScope {
            val clients = async { api.clients() }
            val meta = async { api.clientMeta() }
            clients.await() to meta.await()
        }

    /**
     * Relectura tras una escritura. Nunca pinta el cambio de forma optimista:
     * el estado local solo cambia con lo que devuelve el router. Devuelve el
     * error de lectura, si lo hubo, para no fingir que se verificó.
     */
    private suspend fun reloadAfterWrite(): String? {
        val (clientsResult, metaResult) = readClientsAndMeta()
        val readError = when {
            clientsResult !is ApiResult.Ok -> failWith(clientsResult)
            metaResult !is ApiResult.Ok -> {
                if (metaResult == ApiResult.Unauthorized) _sessionExpired.value = true
                describe(metaResult)
            }
            else -> null
        }
        _clients.update { current ->
            var next = current
            if (clientsResult is ApiResult.Ok) {
                next = next.copy(
                    clients = clientsResult.value.clients,
                    bands = clientsResult.value.bands,
                    error = null,
                )
            }
            if (metaResult is ApiResult.Ok) next = next.copy(meta = metaResult.value.meta)
            next
        }
        return readError
    }

    private fun runBlockWrite(client: ClientDto, blocked: Boolean, band: String) {
        if (_clients.value.busyMac != null) return
        if (blocked && !ClientBlockRules.canBlock(client)) {
            failLocally(
                if (client.self) {
                    "No se puede bloquear el propio dispositivo con el que administras el router"
                } else {
                    "El router no permite bloquear este cliente"
                },
            )
            return
        }
        if (!blocked && !ClientBlockRules.canUnblock(client)) {
            failLocally("Este cliente no consta como bloqueado")
            return
        }
        val normalizedMac = MacAddress.normalize(client.mac)
        if (normalizedMac == null) {
            failLocally("La MAC del cliente no es válida, no se envía la petición")
            return
        }
        val cable = ClientBlockRules.isCable(client)
        val requestedBand = if (cable) "" else band.trim().lowercase()
        if (requestedBand.isNotEmpty() &&
            requestedBand !in ClientBlockRules.availableBands(_clients.value.bands)
        ) {
            failLocally("La banda ${ClientBlockRules.bandLabel(requestedBand)} no está disponible en este router")
            return
        }
        viewModelScope.launch {
            setBusy(normalizedMac)
            val result = api.blockClient(
                mac = normalizedMac,
                type = ClientBlockRules.requestType(client),
                band = requestedBand,
                blocked = blocked,
            )
            when {
                result !is ApiResult.Ok -> setFeedback(error = failWith(result))
                result.value.rolledBack -> {
                    reloadAfterWrite()
                    setFeedback(error = ClientActionMessages.rolledBack())
                }
                else -> applyOutcome(verifyBlock(normalizedMac, blocked, requestedBand))
            }
        }
    }

    private fun runReserveWrite(client: ClientDto, ip: String, reserved: Boolean) {
        if (_clients.value.busyMac != null) return
        val normalizedMac = MacAddress.normalize(client.mac)
        if (normalizedMac == null) {
            failLocally("La MAC del cliente no es válida, no se envía la petición")
            return
        }
        if (reserved && ip.isBlank()) {
            failLocally("El cliente no tiene una IP actual que fijar")
            return
        }
        viewModelScope.launch {
            setBusy(normalizedMac)
            when (val result = api.reserveClient(normalizedMac, ip, reserved)) {
                is ApiResult.Ok -> {
                    if (result.value.rolledBack) {
                        reloadAfterWrite()
                        setFeedback(error = ClientActionMessages.rolledBack())
                    } else {
                        applyOutcome(verifyReservation(normalizedMac, reserved, ip))
                    }
                }
                else -> setFeedback(error = failWith(result))
            }
        }
    }

    /**
     * El router cachea /api/clients unos 2 s. Si la primera relectura no
     * refleja el cambio se espera y se reintenta una sola vez.
     */
    private suspend fun verifyBlock(mac: String, blocked: Boolean, band: String): ActionOutcome {
        var readError = reloadAfterWrite()
        var applied = if (readError == null) {
            ClientWriteCheck.blockApplied(_clients.value.clients, mac, blocked, band)
        } else {
            null
        }
        if (readError == null && applied == false) {
            delay(WRITE_RETRY_DELAY_MS)
            readError = reloadAfterWrite()
            applied = if (readError == null) {
                ClientWriteCheck.blockApplied(_clients.value.clients, mac, blocked, band)
            } else {
                null
            }
        }
        return when {
            readError != null -> ActionOutcome(ClientActionMessages.readFailed(readError), isError = true)
            applied == true -> ActionOutcome(
                if (blocked) ClientActionMessages.blocked(band) else ClientActionMessages.unblocked(band),
                isError = false,
            )
            applied == false -> ActionOutcome(ClientActionMessages.stateUnchanged(), isError = true)
            else -> ActionOutcome(ClientActionMessages.clientAbsent(), isError = false)
        }
    }

    private suspend fun verifyReservation(mac: String, reserved: Boolean, ip: String): ActionOutcome {
        var readError = reloadAfterWrite()
        var applied = if (readError == null) {
            ClientWriteCheck.reservationApplied(_clients.value.clients, mac, reserved)
        } else {
            null
        }
        if (readError == null && applied == false) {
            delay(WRITE_RETRY_DELAY_MS)
            readError = reloadAfterWrite()
            applied = if (readError == null) {
                ClientWriteCheck.reservationApplied(_clients.value.clients, mac, reserved)
            } else {
                null
            }
        }
        return when {
            readError != null -> ActionOutcome(ClientActionMessages.readFailed(readError), isError = true)
            applied == true -> ActionOutcome(
                if (reserved) ClientActionMessages.reserved(ip) else ClientActionMessages.reservationRemoved(),
                isError = false,
            )
            applied == false -> ActionOutcome(ClientActionMessages.reservationUnchanged(), isError = true)
            else -> ActionOutcome(ClientActionMessages.clientAbsent(), isError = false)
        }
    }

    private fun applyOutcome(outcome: ActionOutcome) {
        setFeedback(
            message = outcome.message.takeUnless { outcome.isError },
            error = outcome.message.takeIf { outcome.isError },
        )
    }

    private fun setBusy(mac: String) {
        _clients.update { it.copy(busyMac = mac, actionMessage = null, actionError = null) }
    }

    private fun setFeedback(message: String? = null, error: String? = null) {
        _clients.update { it.copy(busyMac = null, actionMessage = message, actionError = error) }
    }

    private fun failLocally(message: String) {
        _clients.update { it.copy(busyMac = null, actionMessage = null, actionError = message) }
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
        is ApiResult.HttpError -> result.message?.takeIf { it.isNotBlank() }
            ?: "El router respondió con error HTTP ${result.code}"
        ApiResult.Unreachable -> "No se puede alcanzar el router"
        is ApiResult.CertificateChanged -> "El certificado de ${result.host} ha cambiado"
        is ApiResult.CertificateUnsupported -> result.message
        is ApiResult.Unknown -> result.message ?: "Error desconocido"
    }

    private data class ActionOutcome(val message: String, val isError: Boolean)

    private companion object {
        /** /api/clients se cachea 2 s en el servidor: reintento unico. */
        const val WRITE_RETRY_DELAY_MS = 2_100L
    }
}
