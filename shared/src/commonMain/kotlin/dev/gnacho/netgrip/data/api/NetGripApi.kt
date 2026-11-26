package dev.gnacho.netgrip.data.api

import io.ktor.http.encodeURLParameter
import dev.gnacho.netgrip.core.network.ApiResult
import dev.gnacho.netgrip.core.network.CertificateChangedException
import dev.gnacho.netgrip.core.network.SelfSignedCertificatesUnsupportedException
import dev.gnacho.netgrip.core.network.UnknownCertificateException
import dev.gnacho.netgrip.core.network.certificateValidationException
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.ClientBlockRequest
import dev.gnacho.netgrip.data.dto.ClientMetaDto
import dev.gnacho.netgrip.data.dto.ClientMetaRequest
import dev.gnacho.netgrip.data.dto.ClientReserveRequest
import dev.gnacho.netgrip.data.dto.ClientWriteResponseDto
import dev.gnacho.netgrip.data.dto.ClientsDto
import dev.gnacho.netgrip.data.dto.EthPortsDto
import dev.gnacho.netgrip.data.dto.FleetDto
import dev.gnacho.netgrip.data.dto.FleetAdoptRequest
import dev.gnacho.netgrip.data.dto.FleetDiscoveryConfigDto
import dev.gnacho.netgrip.data.dto.FleetIdRequest
import dev.gnacho.netgrip.data.dto.FleetNodeDto
import dev.gnacho.netgrip.data.dto.FleetPeersDto
import dev.gnacho.netgrip.data.dto.FleetStatusResponse
import dev.gnacho.netgrip.data.dto.EmptyRequest
import dev.gnacho.netgrip.data.dto.GuestConfigRequest
import dev.gnacho.netgrip.data.dto.GuestProbeDto
import dev.gnacho.netgrip.data.dto.HistoryDto
import dev.gnacho.netgrip.data.dto.IoTConfigRequest
import dev.gnacho.netgrip.data.dto.IoTProbeDto
import dev.gnacho.netgrip.data.dto.NetworkWriteResponse
import dev.gnacho.netgrip.data.dto.LedDto
import dev.gnacho.netgrip.data.dto.LedsDto
import dev.gnacho.netgrip.data.dto.LogEntryDto
import dev.gnacho.netgrip.data.dto.LoginRequest
import dev.gnacho.netgrip.data.dto.LogsDto
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.NtfyDto
import dev.gnacho.netgrip.data.dto.NtfySetRequest
import dev.gnacho.netgrip.data.dto.OkResponse
import dev.gnacho.netgrip.data.dto.TelegramDto
import dev.gnacho.netgrip.data.dto.TelegramSetRequest
import dev.gnacho.netgrip.data.dto.TelegramSetResponse
import dev.gnacho.netgrip.data.dto.PasswordChangeRequest
import dev.gnacho.netgrip.data.dto.DdnsDto
import dev.gnacho.netgrip.data.dto.ConfigSnapshotDto
import dev.gnacho.netgrip.data.dto.DdnsForceRequest
import dev.gnacho.netgrip.data.dto.DiffsDto
import dev.gnacho.netgrip.data.dto.RollbackRequest
import dev.gnacho.netgrip.data.dto.RollbackResponse
import dev.gnacho.netgrip.data.dto.SnapshotsDto
import dev.gnacho.netgrip.data.dto.DiagnosticsOutcome
import dev.gnacho.netgrip.data.dto.DiagnosticsRunRequest
import dev.gnacho.netgrip.data.dto.DpiAppsDto
import dev.gnacho.netgrip.data.dto.DpiProbeDto
import dev.gnacho.netgrip.data.dto.DpiTimelineDto
import dev.gnacho.netgrip.data.dto.NlbwmonProbeDto
import dev.gnacho.netgrip.data.dto.NlbwmonTopDto
import dev.gnacho.netgrip.data.dto.NftQoSProbeDto
import dev.gnacho.netgrip.data.dto.NftQoSSetRequest
import dev.gnacho.netgrip.data.dto.ParentalProbeDto
import dev.gnacho.netgrip.data.dto.ParentalRuleDto
import dev.gnacho.netgrip.data.dto.QuotaDto
import dev.gnacho.netgrip.data.dto.QuotaProbeDto
import dev.gnacho.netgrip.data.dto.SelfTestDto
import dev.gnacho.netgrip.data.dto.OpenVpnDto
import dev.gnacho.netgrip.data.dto.RebootResponseDto
import dev.gnacho.netgrip.data.dto.RemoteAccessDto
import dev.gnacho.netgrip.data.dto.RemoteAccessRequest
import dev.gnacho.netgrip.data.dto.RemoteWriteResponse
import dev.gnacho.netgrip.data.dto.TailscaleDto
import dev.gnacho.netgrip.data.dto.TailscaleSetRequest
import dev.gnacho.netgrip.data.dto.WireGuardDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.data.dto.SelfUpdateScheduleDto
import dev.gnacho.netgrip.data.dto.SelfUpdateScheduleRequest
import dev.gnacho.netgrip.data.dto.SelfUpdateStatusDto
import dev.gnacho.netgrip.data.dto.SystemDto
import dev.gnacho.netgrip.data.dto.UpdateCheckDto
import dev.gnacho.netgrip.data.dto.UpdateStartDto
import dev.gnacho.netgrip.data.dto.WanDto
import dev.gnacho.netgrip.data.dto.ConfirmIdRequest
import dev.gnacho.netgrip.data.dto.ConfirmStatusDto
import dev.gnacho.netgrip.data.dto.RadioEditRequest
import dev.gnacho.netgrip.data.dto.RadioWriteResponse
import dev.gnacho.netgrip.data.dto.WifiDto
import dev.gnacho.netgrip.data.dto.WifiEditRequest
import dev.gnacho.netgrip.data.dto.WifiScheduleDto
import dev.gnacho.netgrip.data.dto.WifiScheduleWriteResponse
import dev.gnacho.netgrip.data.dto.WifiSchedulesDto
import dev.gnacho.netgrip.data.dto.WifiWriteResponse
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import dev.gnacho.netgrip.data.dto.WolRequest
import dev.gnacho.netgrip.data.dto.WolResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * Cliente de la API REST de NetGrip para la app movil.
 *
 * Verificado en internal/server/server.go:
 *  - POST /api/login devuelve 204 y la sesion via Set-Cookie; 401 con
 *    credenciales incorrectas; 429 con Retry-After bajo rate limit; 502 si
 *    rpcd no responde.
 *  - GET /api/me devuelve 204 con sesion valida y 401 en caso contrario.
 *  - POST /api/logout revoca la sesion.
 *  - El resto de lecturas exigen sesion y responden 502 cuando ubus falla.
 *
 * Las escrituras se limitan a /api/clients/meta, /api/clients/block,
 * /api/clients/reserve y /api/wol (verificadas en internal/server/server.go y
 * internal/modules). Un non-2xx puede traer {"error":"..."}, que se conserva
 * en [ApiResult.HttpError.message] para mostrarlo.
 *
 * Los DTO viven en data/dto y son tolerantes: claves de mas se ignoran y los
 * campos opcionales del router tienen valor por defecto.
 */
class NetGripApi(
    engine: HttpClientEngine,
    private val baseUrl: String,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(json)
        }
        // Las escrituras de invitados e IoT esperan a un healthcheck de hasta
        // 75 s en el router; el resto no necesita tanto.
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
            requestTimeoutMillis = 20_000
        }
    }

    /** Cookie de sesion en formato "nombre=valor". Se persiste cifrada. */
    var sessionCookie: String? = null
        private set

    fun restoreSessionCookie(cookie: String) {
        sessionCookie = cookie
    }

    suspend fun login(username: String, password: String): ApiResult<Unit> = safeCall {
        val response = client.post("$baseUrl/api/login") {
            contentType(ContentType.Application.Json)
            // NetGrip usa root si el usuario va vacio
            setBody(LoginRequest(username.ifBlank { "root" }, password))
        }
        when (response.status) {
            HttpStatusCode.NoContent -> {
                val cookie = extractSessionCookie(response)
                if (cookie == null) {
                    ApiResult.Unknown("login correcto pero sin cookie de sesion")
                } else {
                    sessionCookie = cookie
                    ApiResult.Ok(Unit)
                }
            }
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value)
        }
    }

    /** Probe de sesion: 204 si la cookie sigue siendo valida. */
    suspend fun me(): ApiResult<Unit> = safeCall {
        val response = client.get("$baseUrl/api/me") { sessionHeader() }
        when (response.status) {
            HttpStatusCode.NoContent -> ApiResult.Ok(Unit)
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            else -> ApiResult.HttpError(response.status.value)
        }
    }

    suspend fun board(): ApiResult<BoardDto> =
        getData("/api/board") { json.decodeFromJsonElement(it) }

    suspend fun systemInfo(): ApiResult<SystemDto> =
        getData("/api/system") { json.decodeFromJsonElement(it) }

    suspend fun wan(): ApiResult<WanDto> =
        getData("/api/wan") { json.decodeFromJsonElement(it) }

    suspend fun mode(): ApiResult<ModeDto> =
        getData("/api/mode") { json.decodeFromJsonElement(it) }

    suspend fun clients(): ApiResult<ClientsDto> =
        getData("/api/clients") { json.decodeFromJsonElement(it) }

    suspend fun ethPorts(): ApiResult<EthPortsDto> =
        getData("/api/ethports") { json.decodeFromJsonElement(it) }

    suspend fun wifi(): ApiResult<WifiDto> =
        getData("/api/wifi") { json.decodeFromJsonElement(it) }

    /** /api/wireless responde un array desnudo, sin envoltorio. */
    suspend fun wireless(): ApiResult<List<WirelessRadioDto>> =
        getData("/api/wireless") { json.decodeFromJsonElement(it) }

    suspend fun access(): ApiResult<AccessDto> =
        getData("/api/access") { json.decodeFromJsonElement(it) }

    suspend fun mdns(): ApiResult<MdnsDto> =
        getData("/api/mdns") { json.decodeFromJsonElement(it) }

    suspend fun selfUpdate(): ApiResult<SelfUpdateDto> =
        getData("/api/selfupdate") { json.decodeFromJsonElement(it) }

    /**
     * GET /api/logs. [lines] lo acota el servidor (200 por defecto, maximo
     * 1000) y [filter] es un texto, no una expresion regular: se aplica en Go.
     */
    suspend fun logs(lines: Int = 200, filter: String = ""): ApiResult<LogsDto> {
        val query = StringBuilder("/api/logs?lines=").append(lines)
        if (filter.isNotBlank()) query.append("&filter=").append(filter.encodeURLParameter())
        return getData(query.toString()) { json.decodeFromJsonElement(it) }
    }

    /** GET /api/leds: inventario de los LEDs configurados, solo lectura. */
    suspend fun leds(): ApiResult<LedsDto> =
        getData("/api/leds") { json.decodeFromJsonElement(it) }

    /** GET /api/fleet: routers del fleet del panel, para importarlos como dispositivos. */
    suspend fun fleet(): ApiResult<FleetDto> =
        getData("/api/fleet") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/fleet/check: el panel entra en [id] y devuelve version y
     * alcanzabilidad frescas. El telefono no puede sondearlo por su cuenta.
     */
    suspend fun fleetCheck(id: String): ApiResult<FleetNodeDto> =
        postJson("/api/fleet/check", FleetIdRequest(id), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** POST /api/fleet/check-all: el panel comprueba todos en paralelo. */
    suspend fun fleetCheckAll(): ApiResult<FleetDto> =
        postJson("/api/fleet/check-all", EmptyRequest(), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** GET /api/fleet/discovered: peers que se anuncian y no estan adoptados. */
    suspend fun fleetDiscovered(): ApiResult<FleetPeersDto> =
        getData("/api/fleet/discovered") { json.decodeFromJsonElement(it) }

    /** POST /api/fleet/adopt: verifica credenciales en el peer y lo añade. */
    suspend fun fleetAdopt(request: FleetAdoptRequest): ApiResult<FleetStatusResponse> =
        postJson("/api/fleet/adopt", request, longRunning = true) { json.decodeFromJsonElement(it) }

    /** GET /api/fleet/discovery-config. */
    suspend fun fleetDiscoveryConfig(): ApiResult<FleetDiscoveryConfigDto> =
        getData("/api/fleet/discovery-config") { json.decodeFromJsonElement(it) }

    /** POST /api/fleet/discovery-config. */
    suspend fun setFleetDiscoveryConfig(enabled: Boolean): ApiResult<FleetDiscoveryConfigDto> =
        postJson("/api/fleet/discovery-config", FleetDiscoveryConfigDto(enabled)) {
            json.decodeFromJsonElement(it)
        }

    /** GET /api/clients/meta: nombres y tipos asignados a mano. */
    suspend fun clientMeta(): ApiResult<ClientMetaDto> =
        getData("/api/clients/meta") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/clients/meta. Reemplaza la ficha completa del cliente: con
     * nombre y tipo vacios se borra el override (el servidor elimina la MAC
     * del mapa). Devuelve el mapa actualizado.
     */
    suspend fun setClientMeta(mac: String, name: String, deviceType: String): ApiResult<ClientMetaDto> =
        postJson("/api/clients/meta", ClientMetaRequest(mac, name, deviceType)) {
            json.decodeFromJsonElement(it)
        }

    /**
     * POST /api/clients/block. [type] es "wifi" o "cable" y [band] "" o una
     * banda del payload /api/clients. Solo se llama desde acciones
     * confirmadas y nunca con un cliente self o no blockable.
     */
    suspend fun blockClient(
        mac: String,
        type: String,
        band: String,
        blocked: Boolean,
    ): ApiResult<ClientWriteResponseDto> =
        postJson("/api/clients/block", ClientBlockRequest(mac, type, band, blocked)) {
            json.decodeFromJsonElement(it)
        }

    /**
     * POST /api/clients/reserve. Solo para fijar la IP actual del cliente o
     * para quitar una reserva existente.
     */
    suspend fun reserveClient(mac: String, ip: String, reserved: Boolean): ApiResult<ClientWriteResponseDto> =
        postJson("/api/clients/reserve", ClientReserveRequest(mac, ip, reserved)) {
            json.decodeFromJsonElement(it)
        }

    /**
     * POST /api/reboot. Responde antes de reiniciar; a partir de ahi la sesion
     * puede morir y la app tiene que sondear /api/me hasta que vuelva.
     */
    suspend fun reboot(): ApiResult<RebootResponseDto> =
        postJson("/api/reboot", EmptyRequest()) { json.decodeFromJsonElement(it) }

    /**
     * POST /api/password. Responde 204 y mata TODAS las sesiones del panel,
     * incluida la de esta app: despues hay que volver a entrar.
     */
    suspend fun changePassword(current: String, next: String): ApiResult<Unit> = safeCall {
        val response = client.post("$baseUrl/api/password") {
            contentType(ContentType.Application.Json)
            sessionHeader()
            setBody(PasswordChangeRequest(current, next))
        }
        when (response.status) {
            HttpStatusCode.NoContent, HttpStatusCode.OK -> ApiResult.Ok(Unit)
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value, errorMessage(response))
        }
    }

    /** POST /api/wol: paquete magico, no persiste nada en el router. */
    suspend fun wakeOnLan(mac: String): ApiResult<WolResponseDto> =
        postJson("/api/wol", WolRequest(mac)) { json.decodeFromJsonElement(it) }

    /**
     * POST /api/wifi. Solo se llama desde el editor de una red, con
     * confirmacion previa y con [WifiEditRequest.confirm] para que el router
     * arme su ventana de auto-reversion.
     */
    suspend fun setWifi(edit: WifiEditRequest): ApiResult<WifiWriteResponse> =
        postJson("/api/wifi", edit) { json.decodeFromJsonElement(it) }

    /** POST /api/wifi/radio: canal de una radio, con healthcheck y rollback. */
    suspend fun setWifiRadio(edit: RadioEditRequest): ApiResult<RadioWriteResponse> =
        postJson("/api/wifi/radio", edit) { json.decodeFromJsonElement(it) }

    /** GET /api/config/confirm: ventana de reversion pendiente, si la hay. */
    suspend fun confirmStatus(): ApiResult<ConfirmStatusDto> =
        getData("/api/config/confirm") { json.decodeFromJsonElement(it) }

    /** GET /api/guestwifi: red de invitados, solo lectura. */
    suspend fun guestWifi(): ApiResult<GuestProbeDto> =
        getData("/api/guestwifi") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/guestwifi. El router hace snapshot y healthcheck, que puede
     * tardar hasta 75 s: se pide con timeout largo en vez de cortar antes.
     */
    suspend fun setGuestWifi(cfg: GuestConfigRequest): ApiResult<NetworkWriteResponse<GuestProbeDto>> =
        postJson("/api/guestwifi", cfg, longRunning = true) { json.decodeFromJsonElement(it) }

    /** GET /api/iotwifi: SSID de dispositivos IoT. */
    suspend fun iotWifi(): ApiResult<IoTProbeDto> =
        getData("/api/iotwifi") { json.decodeFromJsonElement(it) }

    /** POST /api/iotwifi, con el mismo healthcheck que invitados. */
    suspend fun setIoTWifi(cfg: IoTConfigRequest): ApiResult<NetworkWriteResponse<IoTProbeDto>> =
        postJson("/api/iotwifi", cfg, longRunning = true) { json.decodeFromJsonElement(it) }

    /** GET /api/config/snapshots: copias guardadas en el router. */
    suspend fun snapshots(): ApiResult<SnapshotsDto> =
        getData("/api/config/snapshots") { json.decodeFromJsonElement(it) }

    /** POST /api/config/snapshot: guarda una copia de la configuracion UCI. */
    suspend fun createSnapshot(): ApiResult<ConfigSnapshotDto> =
        postJson("/api/config/snapshot", EmptyRequest()) { json.decodeFromJsonElement(it) }

    /** GET /api/config/diff: que cambia entre dos copias. */
    suspend fun diffSnapshots(from: String, to: String): ApiResult<DiffsDto> =
        getData("/api/config/diff?from=${from.encodeURLParameter()}&to=${to.encodeURLParameter()}") {
            json.decodeFromJsonElement(it)
        }

    /** POST /api/config/rollback: restaura una copia y recarga la red. */
    suspend fun rollbackSnapshot(id: String): ApiResult<RollbackResponse> =
        postJson("/api/config/rollback", RollbackRequest(id), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** DELETE /api/config/snapshot? Solo se llama con confirmacion. */
    suspend fun deleteSnapshot(id: String): ApiResult<Unit> = safeCall {
        val response = client.delete("$baseUrl/api/config/snapshot?id=${id.encodeURLParameter()}") {
            sessionHeader()
        }
        when (response.status) {
            HttpStatusCode.NoContent, HttpStatusCode.OK -> ApiResult.Ok(Unit)
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value, errorMessage(response))
        }
    }

    /** GET /api/diagnostics/selftest: comprobacion de gateway, WAN, DNS y NTP. */
    suspend fun selfTest(): ApiResult<SelfTestDto> =
        getData("/api/diagnostics/selftest") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/diagnostics. Cada prueba tarda lo suyo (ping hasta 15 s,
     * traceroute hasta 30 s): se pide con timeout largo.
     */
    suspend fun runDiagnostics(
        test: String,
        host: String = "",
        count: Int = 0,
        query: String = "",
        resolver: String = "",
        port: Int = 0,
    ): ApiResult<DiagnosticsOutcome> = postJson(
        "/api/diagnostics",
        DiagnosticsRunRequest(test, host, count, query, resolver, port),
        longRunning = true,
    ) { element ->
        when (test) {
            "ping" -> DiagnosticsOutcome.Ping(json.decodeFromJsonElement(element))
            "traceroute" -> DiagnosticsOutcome.Trace(json.decodeFromJsonElement(element))
            "dns" -> DiagnosticsOutcome.Dns(json.decodeFromJsonElement(element))
            else -> DiagnosticsOutcome.Tcp(json.decodeFromJsonElement(element))
        }
    }

    /** GET /api/telegram: configuracion con el token enmascarado. */
    suspend fun telegram(): ApiResult<TelegramDto> =
        getData("/api/telegram") { json.decodeFromJsonElement(it) }

    /** POST /api/telegram: el router valida el bot contra Telegram. */
    suspend fun setTelegram(req: TelegramSetRequest): ApiResult<TelegramSetResponse> =
        postJson("/api/telegram", req, longRunning = true) { json.decodeFromJsonElement(it) }

    /** POST /api/telegram/test: envia un mensaje de prueba. */
    suspend fun testTelegram(): ApiResult<OkResponse> =
        postJson("/api/telegram/test", EmptyRequest(), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** GET /api/ntfy. */
    suspend fun ntfy(): ApiResult<NtfyDto> =
        getData("/api/ntfy") { json.decodeFromJsonElement(it) }

    /** POST /api/ntfy. */
    suspend fun setNtfy(req: NtfySetRequest): ApiResult<OkResponse> =
        postJson("/api/ntfy", req) { json.decodeFromJsonElement(it) }

    /** POST /api/ntfy/test: publica un mensaje de prueba en el topic. */
    suspend fun testNtfy(): ApiResult<OkResponse> =
        postJson("/api/ntfy/test", EmptyRequest(), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** GET /api/tailscale: estado del nodo Tailscale del router. */
    suspend fun tailscale(): ApiResult<TailscaleDto> =
        getData("/api/tailscale") { json.decodeFromJsonElement(it) }

    /** POST /api/tailscale: arranca o para el nodo Tailscale. */
    suspend fun setTailscale(enabled: Boolean): ApiResult<RemoteWriteResponse<TailscaleDto>> =
        postJson("/api/tailscale", TailscaleSetRequest(enabled)) { json.decodeFromJsonElement(it) }

    /** GET /api/wireguard: servidor WireGuard y sus peers, solo lectura. */
    suspend fun wireGuard(): ApiResult<WireGuardDto> =
        getData("/api/wireguard") { json.decodeFromJsonElement(it) }

    /** GET /api/openvpn: servidor OpenVPN, solo lectura. */
    suspend fun openVpn(): ApiResult<OpenVpnDto> =
        getData("/api/openvpn") { json.decodeFromJsonElement(it) }

    /** GET /api/ddns: entradas de nombre dinamico. */
    suspend fun ddns(): ApiResult<DdnsDto> =
        getData("/api/ddns") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/ddns/force. Con [section] vacia actualiza todas las entradas,
     * que es lo que ofrece la pantalla. Tarda: el router llama al proveedor.
     */
    suspend fun forceDdns(section: String = ""): ApiResult<RemoteWriteResponse<DdnsDto>> =
        postJson("/api/ddns/force", DdnsForceRequest(section), longRunning = true) {
            json.decodeFromJsonElement(it)
        }

    /** GET /api/remoteaccess: que puertos de la WAN aceptan conexiones. */
    suspend fun remoteAccess(): ApiResult<RemoteAccessDto> =
        getData("/api/remoteaccess") { json.decodeFromJsonElement(it) }

    /** POST /api/remoteaccess: abre o cierra un permiso de la WAN. */
    suspend fun setRemoteAccess(req: RemoteAccessRequest): ApiResult<RemoteWriteResponse<RemoteAccessDto>> =
        postJson("/api/remoteaccess", req) { json.decodeFromJsonElement(it) }

    /** GET /api/update: estado del firmware segun `owut check`. */
    suspend fun updateCheck(): ApiResult<UpdateCheckDto> =
        getData("/api/update") { json.decodeFromJsonElement(it) }

    /**
     * POST /api/update. El router descarga el firmware, lo instala y se
     * reinicia: la respuesta solo dice que empezo.
     */
    suspend fun startFirmwareUpdate(): ApiResult<UpdateStartDto> =
        postJson("/api/update", EmptyRequest(confirm = true)) { json.decodeFromJsonElement(it) }

    /** GET /api/selfupdate/status: fase y progreso del panel. */
    suspend fun selfUpdateStatus(): ApiResult<SelfUpdateStatusDto> =
        getData("/api/selfupdate/status") { json.decodeFromJsonElement(it) }

    /** POST /api/selfupdate: descarga y reinicia el panel. */
    suspend fun startSelfUpdate(): ApiResult<SelfUpdateStatusDto> =
        postJson("/api/selfupdate", EmptyRequest(confirm = true)) { json.decodeFromJsonElement(it) }

    /** GET /api/selfupdate/schedule: programacion del panel. */
    suspend fun selfUpdateSchedule(): ApiResult<SelfUpdateScheduleDto> =
        getData("/api/selfupdate/schedule") { json.decodeFromJsonElement(it) }

    /** PUT /api/selfupdate/schedule: solo los campos que cambian. */
    suspend fun setSelfUpdateSchedule(req: SelfUpdateScheduleRequest): ApiResult<SelfUpdateScheduleDto> =
        putJson("/api/selfupdate/schedule", req) { json.decodeFromJsonElement(it) }

    /** GET /api/wifischedule: horario de cada interfaz WiFi. */
    suspend fun wifiSchedules(): ApiResult<WifiSchedulesDto> =
        getData("/api/wifischedule") { json.decodeFromJsonElement(it) }

    /** POST /api/wifischedule: guarda el horario de una interfaz. */
    suspend fun setWifiSchedule(schedule: WifiScheduleDto): ApiResult<WifiScheduleWriteResponse> =
        postJson("/api/wifischedule", schedule) { json.decodeFromJsonElement(it) }

    /** POST /api/config/confirm: conserva los cambios aplicados. */
    suspend fun keepConfirm(id: String): ApiResult<ConfirmStatusDto> =
        postJson("/api/config/confirm", ConfirmIdRequest(id)) { json.decodeFromJsonElement(it) }

    /** POST /api/config/confirm/rollback: deshace los cambios aplicados. */
    suspend fun revertConfirm(id: String): ApiResult<ConfirmStatusDto> =
        postJson("/api/config/confirm/rollback", ConfirmIdRequest(id)) { json.decodeFromJsonElement(it) }
    /** Historico de 24 h del puente LAN. Contadores acumulados, no tasas. */
    suspend fun history(): ApiResult<HistoryDto> =
        getData("/api/history") { json.decodeFromJsonElement(it) }

    /** GET /api/nlbwmon: estado del contable por flujo del router. */
    suspend fun nlbwmon(): ApiResult<NlbwmonProbeDto> =
        getData("/api/nlbwmon") { json.decodeFromJsonElement(it) }

    /**
     * GET /api/nlbwmon/top: dispositivos y protocolos que mas movieron.
     * Listas vacias cuando nlbwmon no esta instalado.
     */
    suspend fun nlbwmonTop(): ApiResult<NlbwmonTopDto> =
        getData("/api/nlbwmon/top") { json.decodeFromJsonElement(it) }

    /** GET /api/dpi: protocolos vistos por nDPI, solo lectura. */
    suspend fun dpi(): ApiResult<DpiProbeDto> =
        getData("/api/dpi") { json.decodeFromJsonElement(it) }

    /** GET /api/dpi/apps: aplicaciones identificadas por netifyd. */
    suspend fun dpiApps(): ApiResult<DpiAppsDto> =
        getData("/api/dpi/apps") { json.decodeFromJsonElement(it) }

    /** GET /api/dpi/timeline: cubos de 5 minutos de las ultimas 24 h. */
    suspend fun dpiTimeline(): ApiResult<DpiTimelineDto> =
        getData("/api/dpi/timeline") { json.decodeFromJsonElement(it) }

    /** GET /api/quotas: topes de datos por dispositivo y su consumo. */
    suspend fun quotas(): ApiResult<QuotaProbeDto> =
        getData("/api/quotas") { json.decodeFromJsonElement(it) }

    /** POST /api/quotas: crea o reemplaza el tope de un dispositivo. */
    suspend fun setQuota(quota: QuotaDto): ApiResult<QuotaProbeDto> =
        postJson("/api/quotas", quota) { json.decodeFromJsonElement(it) }

    /** DELETE /api/quotas: quita el tope de [mac]. */
    suspend fun deleteQuota(mac: String): ApiResult<QuotaProbeDto> =
        deleteData("/api/quotas?mac=${mac.encodeURLParameter()}") { json.decodeFromJsonElement(it) }

    /** GET /api/nftqos: limite de bajada y subida por dispositivo. */
    suspend fun nftQos(): ApiResult<NftQoSProbeDto> =
        getData("/api/nftqos") { json.decodeFromJsonElement(it) }

    /** POST /api/nftqos: fija el limite en Mb/s (0 y 0 lo borra). */
    suspend fun setNftQosLimit(limit: NftQoSSetRequest): ApiResult<NftQoSProbeDto> =
        postJson("/api/nftqos", limit) { json.decodeFromJsonElement(it) }

    /** DELETE /api/nftqos: quita el limite de [mac]. */
    suspend fun deleteNftQosLimit(mac: String): ApiResult<NftQoSProbeDto> =
        deleteData("/api/nftqos?mac=${mac.encodeURLParameter()}") { json.decodeFromJsonElement(it) }

    /** GET /api/parental: horarios de bloqueo por dispositivo. */
    suspend fun parental(): ApiResult<ParentalProbeDto> =
        getData("/api/parental") { json.decodeFromJsonElement(it) }

    /** POST /api/parental: guarda un horario y aplica el bloqueo al momento. */
    suspend fun setParentalRule(rule: ParentalRuleDto): ApiResult<ParentalProbeDto> =
        postJson("/api/parental", rule) { json.decodeFromJsonElement(it) }

    /** DELETE /api/parental: quita el horario y su bloqueo. */
    suspend fun deleteParentalRule(mac: String): ApiResult<ParentalProbeDto> =
        deleteData("/api/parental?mac=${mac.encodeURLParameter()}") { json.decodeFromJsonElement(it) }


    suspend fun logout(): ApiResult<Unit> = safeCall {
        try {
            client.post("$baseUrl/api/logout") { sessionHeader() }
        } finally {
            sessionCookie = null
        }
        ApiResult.Ok(Unit)
    }

    private suspend fun <T> getData(path: String, decode: (JsonElement) -> T): ApiResult<T> =
        safeCall {
            val response = client.get("$baseUrl$path") { sessionHeader() }
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
                HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
                else -> ApiResult.HttpError(response.status.value)
            }
        }

    /**
     * DELETE con respuesta JSON: el panel devuelve el probe completo despues
     * de borrar, asi que la app no tiene que adivinar el estado nuevo.
     */
    private suspend fun <T> deleteData(path: String, decode: (JsonElement) -> T): ApiResult<T> =
        safeCall {
            val response = client.delete("$baseUrl$path") { sessionHeader() }
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
                HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
                else -> ApiResult.HttpError(response.status.value, errorMessage(response))
            }
        }

    private fun HttpRequestBuilder.sessionHeader() {
        sessionCookie?.let { header(HttpHeaders.Cookie, it) }
    }

    /**
     * POST con cuerpo JSON y envelope de error. En un non-2xx se intenta
     * leer {"error":"..."} para poder mostrarlo tal cual.
     */
    private suspend inline fun <reified B, reified T> postJson(
        path: String,
        body: B,
        longRunning: Boolean = false,
        crossinline decode: (JsonElement) -> T,
    ): ApiResult<T> = safeCall {
        val response = client.post("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            sessionHeader()
            if (longRunning) {
                timeout {
                    requestTimeoutMillis = LONG_WRITE_TIMEOUT_MILLIS
                    socketTimeoutMillis = LONG_WRITE_TIMEOUT_MILLIS
                }
            }
            setBody(body)
        }
        when (response.status) {
            HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value, errorMessage(response))
        }
    }

    /** PUT con cuerpo JSON y el mismo envelope de error que [postJson]. */
    private suspend inline fun <reified B, reified T> putJson(
        path: String,
        body: B,
        crossinline decode: (JsonElement) -> T,
    ): ApiResult<T> = safeCall {
        val response = client.put("$baseUrl$path") {
            contentType(ContentType.Application.Json)
            sessionHeader()
            setBody(body)
        }
        when (response.status) {
            HttpStatusCode.OK -> ApiResult.Ok(decode(response.body<JsonElement>()))
            HttpStatusCode.Unauthorized -> ApiResult.Unauthorized
            HttpStatusCode.TooManyRequests -> ApiResult.RateLimited(retryAfter(response))
            else -> ApiResult.HttpError(response.status.value, errorMessage(response))
        }
    }

    /** Texto de {"error":"..."} del router, o null si el cuerpo no lo trae. */
    private suspend fun errorMessage(response: HttpResponse): String? =
        try {
            val element = response.body<JsonElement>()
            (element as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }

    private fun extractSessionCookie(response: HttpResponse): String? {
        val setCookies = response.headers.getAll(HttpHeaders.SetCookie).orEmpty()
        for (name in SESSION_COOKIE_NAMES) {
            val prefix = "$name="
            setCookies.firstOrNull { it.startsWith(prefix) }
                ?.substringBefore(';')
                ?.let { return it }
        }
        return null
    }

    private fun retryAfter(response: HttpResponse): Long? =
        response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()

    private suspend fun <T> safeCall(block: suspend () -> ApiResult<T>): ApiResult<T> =
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (t: Throwable) {
            when (val certificateError = t.certificateValidationException()) {
                is CertificateChangedException -> ApiResult.CertificateChanged(
                    host = certificateError.host,
                    expectedFingerprint = certificateError.expectedFingerprint,
                    actualFingerprint = certificateError.actualFingerprint,
                )
                is SelfSignedCertificatesUnsupportedException ->
                    ApiResult.CertificateUnsupported(certificateError.message ?: "Certificado no soportado")
                is UnknownCertificateException ->
                    ApiResult.Unknown(certificateError.message)
                null -> if (t is IOException) ApiResult.Unreachable else ApiResult.Unknown(t.message)
            }
        }

    companion object {
        /** El healthcheck de invitados e IoT sondea 75 veces, una por segundo. */
        const val LONG_WRITE_TIMEOUT_MILLIS = 100_000L

        // NetGrip emite nombres distintos segun sirva HTTP o HTTPS, y acepta la legacy
        private val SESSION_COOKIE_NAMES = listOf(
            "__Secure-netgrip_session",
            "netgrip_http_session",
            "netgrip_session",
        )
    }
}
