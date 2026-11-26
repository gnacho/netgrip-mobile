package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.data.dto.UpdateCheckDto
import dev.gnacho.netgrip.presentation.SettingsState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: ShellViewModel,
    baseUrl: String?,
    onLogout: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val state by viewModel.settings.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.ensureSettings() }

    // Los errores se pintan en su tarjeta o en su dialogo, donde el usuario
    // puede corregirlos: el snackbar solo confirma acciones.
    val feedback = state.actionMessage
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearSettingsFeedback()
        }
    }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.board == null && state.access == null ->
            ErrorBox(state.error!!) { viewModel.loadSettings() }
        else -> Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Banner(
                    text = "Las acciones de clientes se hacen desde la app. El resto de ajustes, desde el panel web de NetGrip.",
                    tone = Tone.ACCENT,
                    icon = NetGripIcons.Info,
                )
            }
            state.error?.let { message -> item { InlineWarning(message) } }
            item { AppearanceCard(themeMode, onThemeModeChange) }
            item { DeviceCard(state.board) }
            item { ModeCard(state.mode) }
            item { AccessCard(state.access) }
            item { MdnsCard(state.mdns) }
            state.rebooting.takeIf { it }?.let { _ ->
                item {
                    Banner(
                        "El router se está reiniciando. La app espera a que vuelva; no hace falta reintentar.",
                        Tone.WARN,
                        icon = NetGripIcons.Refresh,
                    )
                }
            }
            item { FirmwareCard(state.firmware, state.actionBusy) { viewModel.installFirmware() } }
            item { UpdateCard(state.selfUpdate, state.actionBusy) { viewModel.updateNetGrip() } }
            item {
                SystemActionsCard(
                    state = state,
                    onReboot = viewModel::rebootRouter,
                    onChangePassword = viewModel::changePassword,
                    onClearFeedback = viewModel::clearSettingsFeedback,
                )
            }
            item { ConnectionCard(baseUrl, onLogout) }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
        }
    }
}

@Composable
private fun AppearanceCard(themeMode: ThemeMode, onChange: (ThemeMode) -> Unit) {
    NetGripCard(
        eyebrow = "Esta app",
        title = "Apariencia",
        subtitle = "Tema claro, oscuro o el del sistema",
        icon = NetGripIcons.Sun,
        iconTone = Tone.VIOLET,
    ) {
        SegmentedControl(
            options = listOf(
                ThemeMode.SYSTEM to "Auto",
                ThemeMode.LIGHT to "Claro",
                ThemeMode.DARK to "Oscuro",
            ),
            selected = themeMode,
            onSelect = onChange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DeviceCard(board: BoardDto?) {
    NetGripCard(
        eyebrow = "Equipo",
        title = board?.hostname?.takeIf { it.isNotBlank() } ?: "Router",
        subtitle = board?.model?.takeIf { it.isNotBlank() },
        icon = NetGripIcons.Router,
        iconTone = Tone.TEAL,
    ) {
        val release = board?.release
        InfoRow(
            "Firmware",
            listOfNotNull(release?.distribution, release?.version)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Sin dato" },
        )
        release?.revision?.takeIf { it.isNotBlank() }?.let { InfoRow("Revisión", it, mono = true) }
        InfoRow("Kernel", board?.kernel?.takeIf { it.isNotBlank() } ?: "Sin dato", mono = true)
    }
}

@Composable
private fun ModeCard(mode: ModeDto?) {
    NetGripCard(
        eyebrow = "Función",
        title = "Modo de trabajo",
        icon = NetGripIcons.Settings,
        iconTone = Tone.ACCENT,
    ) {
        if (mode == null) {
            Text(
                "Sin datos del modo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(mode.effectiveRole.uppercase(), Tone.ACCENT, dot = false)
            Pill(if (mode.hasWifi) "Con WiFi" else "Sin WiFi", if (mode.hasWifi) Tone.SUCCESS else Tone.NEUTRAL, dot = false)
        }
        Spacer(Modifier.size(8.dp))
        InfoRow("Modo", mode.mode.ifBlank { "Sin dato" })
        InfoRow("Puertos", mode.portCount.toString())
        mode.dnsmasqOn?.let { InfoRow("DNS local (dnsmasq)", yesNo(it)) }
        mode.firewallOn?.let { InfoRow("Cortafuegos", yesNo(it)) }
    }
}

@Composable
private fun AccessCard(access: AccessDto?) {
    NetGripCard(
        eyebrow = "Administración",
        title = "Acceso",
        icon = NetGripIcons.Settings,
        iconTone = Tone.WARN,
    ) {
        if (access == null) {
            Text(
                "Sin datos de acceso.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        InfoRow("Panel, puerto HTTP", access.panel.httpPort.toString(), mono = true)
        InfoRow("Panel, HTTPS", yesNo(access.panel.httpsEnabled))
        InfoRow("Panel, forzar HTTPS", yesNo(access.panel.forceHttps))
        InfoRow("Duración de sesión", access.panel.sessionTtl.ifBlank { "Sin dato" })
        InfoRow("LuCI", if (access.luci.enabled) "Activo" else "Apagado")
        if (access.luci.enabled) {
            InfoRow("LuCI, puertos", "${access.luci.httpPort} / ${access.luci.httpsPort}", mono = true)
            InfoRow("LuCI, forzar HTTPS", yesNo(access.luci.forceHttps))
        }
        InfoRow("SSH", if (access.ssh.enabled) "Activo, puerto ${access.ssh.port}" else "Apagado")
    }
}

@Composable
private fun MdnsCard(mdns: MdnsDto?) {
    NetGripCard(
        eyebrow = "Descubrimiento",
        title = "mDNS",
        icon = NetGripIcons.Search,
        iconTone = Tone.TEAL,
    ) {
        if (mdns == null) {
            Text(
                "Sin datos de mDNS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!mdns.installed) {
            Text(
                "umdns no está instalado en este equipo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(if (mdns.enabled) "Activado" else "Desactivado", if (mdns.enabled) Tone.SUCCESS else Tone.NEUTRAL)
            Pill(if (mdns.running) "En marcha" else "Parado", if (mdns.running) Tone.SUCCESS else Tone.WARN)
        }
        Spacer(Modifier.size(8.dp))
        InfoRow("Dominio", mdns.domain.ifBlank { "Sin dato" }, mono = true)
    }
}

@Composable
private fun UpdateCard(update: SelfUpdateDto?, busy: Boolean, onUpdate: () -> Unit) {
    var confirmOpen by remember { mutableStateOf(false) }
    NetGripCard(
        eyebrow = "Sistema",
        title = "Actualización",
        icon = NetGripIcons.CloudOff,
        iconTone = Tone.VIOLET,
    ) {
        if (update == null) {
            Text(
                "Sin datos de actualización.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        InfoRow("Versión instalada", update.current.ifBlank { "Sin dato" }, mono = true)
        InfoRow("Última publicada", update.latest.ifBlank { "Sin dato" }, mono = true)
        Spacer(Modifier.size(6.dp))
        when {
            update.checkError != null && update.checkError.isNotBlank() ->
                Pill("Error al comprobar", Tone.WARN)
            update.available -> Pill("Hay una versión nueva", Tone.WARN)
            else -> Pill("Al día", Tone.SUCCESS)
        }
        if (update.checkError != null && update.checkError.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(
                update.checkError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        update.notes.takeIf { it.isNotBlank() }?.let { notes ->
            Spacer(Modifier.size(6.dp))
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (update.available) {
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = { confirmOpen = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Actualizar NetGrip")
            }
        }
    }

    if (confirmOpen) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            icon = { IconTile(NetGripIcons.CloudOff, Tone.VIOLET) },
            title = { Text("Actualizar NetGrip a ${update?.latest.orEmpty()}") },
            text = {
                Text(
                    "Se descargará la versión ${update?.latest.orEmpty()} y el panel se reiniciará. " +
                        "El router y su red siguen funcionando; solo el panel deja de responder unos segundos.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOpen = false
                        onUpdate()
                    },
                ) { Text("Actualizar") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * Firmware del router (owut). Solo se ofrece instalar cuando el router dice
 * que hay version nueva y que es seguro; el riesgo se explica antes.
 */
@Composable
private fun FirmwareCard(
    firmware: UpdateCheckDto?,
    busy: Boolean,
    onInstall: () -> Unit,
) {
    var confirmOpen by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }
    val showUpgrade = firmware != null &&
        firmware.available &&
        !firmware.sameVersion &&
        firmware.owutPresent
    val blocked = firmware != null && !firmware.safeToProceed && !firmware.safeWithReinstall

    NetGripCard(
        eyebrow = "Sistema",
        title = "Firmware del router",
        icon = NetGripIcons.Router,
        iconTone = if (showUpgrade) Tone.WARN else Tone.TEAL,
    ) {
        if (firmware == null) {
            Text(
                "No se pudo leer el estado del firmware. Puede que este router no tenga owut: en ese caso se actualiza desde LuCI.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!firmware.owutPresent) {
            Text(
                "owut no está instalado, así que el router no puede actualizarse desde aquí. Usa LuCI o la consola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        InfoRow("Instalada", firmware.versionFrom.ifBlank { "Sin dato" }, mono = true)
        if (showUpgrade) {
            InfoRow("Disponible", firmware.versionTo.ifBlank { "Sin dato" }, mono = true)
        }
        Spacer(Modifier.size(6.dp))
        when {
            firmware.upgradeStarted -> Pill("Actualización en marcha", Tone.WARN)
            showUpgrade && blocked -> Pill("Bloqueada por owut", Tone.DANGER)
            showUpgrade -> Pill("Hay firmware nuevo", Tone.WARN)
            else -> Pill("Al día", Tone.SUCCESS)
        }
        if (firmware.warnings.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            firmware.warnings.forEach { warning ->
                Text(
                    "· $warning",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (showUpgrade && blocked) {
            Spacer(Modifier.size(8.dp))
            Banner(
                if (firmware.missingPackages.isNotEmpty()) {
                    "Faltan paquetes en la versión destino: ${firmware.missingPackages.joinToString(", ")}. " +
                        "owut no lo considera seguro."
                } else {
                    "owut no considera seguro actualizar este equipo."
                },
                Tone.DANGER,
            )
        }
        if (showUpgrade) {
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = { confirmOpen = true },
                enabled = !busy && !blocked && !firmware.upgradeStarted,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Instalar firmware ${firmware.versionTo}")
            }
        }
    }

    if (confirmOpen && firmware != null) {
        AlertDialog(
            onDismissRequest = { confirmOpen = false },
            icon = { IconTile(NetGripIcons.Warning, Tone.DANGER) },
            title = { Text("Instalar firmware ${firmware.versionTo}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "El router descargará el firmware, lo instalará y se reiniciará. " +
                            "Dejará de responder varios minutos y se cortará Internet. " +
                            "Si el firmware no arranca, la recuperación puede exigir acceso físico al equipo.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                        Text(
                            "Entiendo que el router se reiniciará y puede tardar varios minutos.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmOpen = false
                        onInstall()
                    },
                    enabled = accepted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Instalar") }
            },
            dismissButton = { TextButton(onClick = { confirmOpen = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ConnectionCard(baseUrl: String?, onLogout: () -> Unit) {
    NetGripCard(
        eyebrow = "Sesión",
        title = "Conexión",
        icon = NetGripIcons.Cable,
        iconTone = Tone.NEUTRAL,
    ) {
        InfoRow("Dirección", baseUrl?.takeIf { it.isNotBlank() } ?: "Sin dato", mono = true)
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión")
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "Sí" else "No"

/**
 * Reinicio del router y cambio de la contrasena de administracion. Las dos son
 * acciones que cortan la conexion, asi que piden confirmacion explicita y
 * explican lo que va a pasar.
 */
@Composable
private fun SystemActionsCard(
    state: SettingsState,
    onReboot: () -> Unit,
    onChangePassword: (String, String) -> Unit,
    onClearFeedback: () -> Unit,
) {
    var confirmReboot by remember { mutableStateOf(false) }
    var passwordOpen by remember { mutableStateOf(false) }

    NetGripCard(
        eyebrow = "Sistema",
        title = "Reinicio y contraseña",
        subtitle = "Acciones que cortan la sesión",
        icon = NetGripIcons.Warning,
        iconTone = Tone.DANGER,
    ) {
        if (state.rebooting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Reiniciando. La app espera a que el router vuelva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@NetGripCard
        }

        OutlinedButton(
            onClick = { confirmReboot = true },
            enabled = !state.actionBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reiniciar el router")
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(
            onClick = {
                onClearFeedback()
                passwordOpen = true
            },
            enabled = !state.actionBusy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cambiar la contraseña")
        }
        Spacer(Modifier.size(10.dp))
        Text(
            "Cambiar la contraseña cierra todas las sesiones del panel, también esta.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val cardError = state.actionError
        if (cardError != null && !passwordOpen) {
            Spacer(Modifier.size(8.dp))
            InlineWarning(cardError)
        }
    }

    if (confirmReboot) {
        AlertDialog(
            onDismissRequest = { confirmReboot = false },
            icon = { IconTile(NetGripIcons.Warning, Tone.DANGER) },
            title = { Text("Reiniciar el router") },
            text = {
                Text(
                    "El router dejará de responder uno o dos minutos y se cortará Internet " +
                        "mientras arranca. La app esperará a que vuelva y, si la sesión no " +
                        "sobrevive, te pedirá entrar de nuevo.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReboot = false
                        onReboot()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Reiniciar") }
            },
            dismissButton = { TextButton(onClick = { confirmReboot = false }) { Text("Cancelar") } },
        )
    }

    if (passwordOpen) {
        PasswordDialog(
            busy = state.actionBusy,
            error = state.actionError,
            onDismiss = { passwordOpen = false },
            onApply = onChangePassword,
        )
    }
}

@Composable
private fun PasswordDialog(
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onApply: (String, String) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    val localError = when {
        next.isNotEmpty() && next.length < 8 -> "La nueva contraseña necesita al menos 8 caracteres"
        repeat.isNotEmpty() && next != repeat -> "Las dos contraseñas no coinciden"
        else -> null
    }
    val canSave = !busy && current.isNotEmpty() && next.length >= 8 && next == repeat
    val shownError = localError ?: error

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(NetGripIcons.Settings, Tone.WARN) },
        title = { Text("Cambiar la contraseña") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se cambiará la contraseña de administración del router y se cerrarán todas " +
                        "las sesiones del panel. Tendrás que entrar otra vez con la nueva.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Contraseña actual") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text("Nueva contraseña") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repeat,
                    onValueChange = { repeat = it },
                    label = { Text("Repite la nueva") },
                    singleLine = true,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = shownError != null,
                    supportingText = shownError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { show = !show }) {
                    Text(if (show) "Ocultar las contraseñas" else "Mostrar las contraseñas")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(current, next) }, enabled = canSave) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Cambiar")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
