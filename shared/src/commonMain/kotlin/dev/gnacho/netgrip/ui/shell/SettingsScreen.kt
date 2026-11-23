package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.AccessDto
import dev.gnacho.netgrip.data.dto.BoardDto
import dev.gnacho.netgrip.data.dto.MdnsDto
import dev.gnacho.netgrip.data.dto.ModeDto
import dev.gnacho.netgrip.data.dto.SelfUpdateDto
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.ErrorBox
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
    LaunchedEffect(Unit) { viewModel.ensureSettings() }

    when {
        state.loading && !state.loaded -> LoadingBox()
        state.error != null && state.board == null && state.access == null ->
            ErrorBox(state.error!!) { viewModel.loadSettings() }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Banner(
                    text = "Los cambios se hacen desde el panel web de NetGrip. Esta app solo lee.",
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
            item { UpdateCard(state.selfUpdate) }
            item { ConnectionCard(baseUrl, onLogout) }
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
private fun UpdateCard(update: SelfUpdateDto?) {
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
        Spacer(Modifier.size(6.dp))
        Text(
            "Las actualizaciones se lanzan desde el panel web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
