package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
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
import dev.gnacho.netgrip.ui.common.Badge
import dev.gnacho.netgrip.ui.common.BadgeTone
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.SectionCard

@Composable
fun SettingsScreen(
    viewModel: ShellViewModel,
    baseUrl: String?,
    onLogout: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Esta app solo lee datos. Los cambios se hacen desde el panel web de NetGrip.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.error?.let { message -> item { InlineWarning(message) } }
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
private fun DeviceCard(board: BoardDto?) {
    SectionCard(title = "Equipo") {
        InfoRow("Nombre", board?.hostname?.takeIf { it.isNotBlank() } ?: "Sin dato")
        InfoRow("Modelo", board?.model?.takeIf { it.isNotBlank() } ?: "Sin dato")
        val release = board?.release
        InfoRow(
            "Firmware",
            listOfNotNull(release?.distribution, release?.version)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Sin dato" },
        )
        release?.revision?.takeIf { it.isNotBlank() }?.let { InfoRow("Revisión", it) }
        InfoRow("Kernel", board?.kernel?.takeIf { it.isNotBlank() } ?: "Sin dato")
    }
}

@Composable
private fun ModeCard(mode: ModeDto?) {
    SectionCard(title = "Modo de trabajo") {
        if (mode == null) {
            Text(
                "Sin datos del modo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(mode.effectiveRole.uppercase(), BadgeTone.NEUTRAL)
            if (mode.hasWifi) Badge("Con WiFi") else Badge("Sin WiFi")
        }
        InfoRow("Modo", mode.mode.ifBlank { "Sin dato" })
        InfoRow("Puertos", mode.portCount.toString())
        mode.dnsmasqOn?.let { InfoRow("DNS local (dnsmasq)", yesNo(it)) }
        mode.firewallOn?.let { InfoRow("Cortafuegos", yesNo(it)) }
    }
}

@Composable
private fun AccessCard(access: AccessDto?) {
    SectionCard(title = "Acceso") {
        if (access == null) {
            Text(
                "Sin datos de acceso.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        InfoRow("Panel, puerto HTTP", access.panel.httpPort.toString())
        InfoRow("Panel, HTTPS", yesNo(access.panel.httpsEnabled))
        InfoRow("Panel, forzar HTTPS", yesNo(access.panel.forceHttps))
        InfoRow("Duración de sesión", access.panel.sessionTtl.ifBlank { "Sin dato" })
        InfoRow("LuCI", if (access.luci.enabled) "Activo" else "Apagado")
        if (access.luci.enabled) {
            InfoRow("LuCI, puertos", "${access.luci.httpPort} / ${access.luci.httpsPort}")
            InfoRow("LuCI, forzar HTTPS", yesNo(access.luci.forceHttps))
        }
        InfoRow("SSH", if (access.ssh.enabled) "Activo, puerto ${access.ssh.port}" else "Apagado")
    }
}

@Composable
private fun MdnsCard(mdns: MdnsDto?) {
    SectionCard(title = "mDNS") {
        if (mdns == null) {
            Text(
                "Sin datos de mDNS.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        if (!mdns.installed) {
            Text(
                "umdns no está instalado en este equipo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(if (mdns.enabled) "Activado" else "Desactivado", if (mdns.enabled) BadgeTone.OK else BadgeTone.NEUTRAL)
            Badge(if (mdns.running) "En marcha" else "Parado", if (mdns.running) BadgeTone.OK else BadgeTone.WARN)
        }
        InfoRow("Dominio", mdns.domain.ifBlank { "Sin dato" })
    }
}

@Composable
private fun UpdateCard(update: SelfUpdateDto?) {
    SectionCard(title = "Actualización") {
        if (update == null) {
            Text(
                "Sin datos de actualización.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        InfoRow("Versión instalada", update.current.ifBlank { "Sin dato" })
        InfoRow("Última publicada", update.latest.ifBlank { "Sin dato" })
        when {
            update.checkError != null && update.checkError.isNotBlank() -> Badge("Error al comprobar", BadgeTone.WARN)
            update.available -> Badge("Hay una versión nueva", BadgeTone.WARN)
            else -> Badge("Al día", BadgeTone.OK)
        }
        if (update.checkError != null && update.checkError.isNotBlank()) {
            Text(
                update.checkError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        update.notes.takeIf { it.isNotBlank() }?.let { notes ->
            Text(
                notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Las actualizaciones se lanzan desde el panel web.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ConnectionCard(baseUrl: String?, onLogout: () -> Unit) {
    SectionCard(title = "Conexión") {
        InfoRow("Dirección", baseUrl?.takeIf { it.isNotBlank() } ?: "Sin dato")
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
                Text("Cerrar sesión")
            }
        }
    }
}

private fun yesNo(value: Boolean): String = if (value) "Sí" else "No"
