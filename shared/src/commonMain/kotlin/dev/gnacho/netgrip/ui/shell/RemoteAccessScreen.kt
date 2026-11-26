package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.RemoteAccessDto
import dev.gnacho.netgrip.data.dto.RemoteAccessRequest
import dev.gnacho.netgrip.data.dto.TailscaleDto
import dev.gnacho.netgrip.data.dto.WireGuardDto
import dev.gnacho.netgrip.presentation.RemoteState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.common.ToggleRow

/**
 * Acceso al router desde fuera de casa. Tailscale es el camino recomendado
 * (#27): no abre nada a Internet. WireGuard y OpenVPN se muestran como
 * alternativas y la exposicion directa queda al final, con aviso.
 */
@Composable
fun RemoteAccessScreen(viewModel: ShellViewModel) {
    val state by viewModel.remote.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.ensureRemote() }

    val feedback = state.actionError ?: state.actionMessage
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearRemoteFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && !state.loaded -> LoadingBox()
            state.error != null && state.exposure == null && state.tailscale == null ->
                ErrorBox(state.error!!) { viewModel.loadRemote() }
            !state.loaded -> LoadingBox()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                state.error?.let { message ->
                    item { Banner(message, Tone.DANGER) }
                }
                item { RecommendationCard() }
                item {
                    TailscaleCard(
                        tailscale = state.tailscale,
                        busy = state.busy,
                        onToggle = viewModel::setTailscale,
                    )
                }
                item { WireGuardCard(state.wireguard) }
                item { OpenVpnCard(state.openvpn) }
                item {
                    DdnsCard(
                        state = state,
                        busy = state.busy,
                        onForce = viewModel::forceDdns,
                    )
                }
                item {
                    ExposureCard(
                        exposure = state.exposure,
                        busy = state.busy,
                        onChange = viewModel::setRemoteAccess,
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun RecommendationCard() {
    NetGripCard(
        eyebrow = "Recomendado",
        title = "Tailscale para entrar desde fuera",
        subtitle = "Sin abrir puertos ni exponer el panel",
        icon = NetGripIcons.Shield,
        iconTone = Tone.SUCCESS,
    ) {
        Text(
            "Tailscale crea una red privada entre tus dispositivos y el router, " +
                "aunque la operadora te cambie la IP. Es la vía más simple y la única " +
                "que no publica el panel en Internet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "WireGuard es la alternativa si ya lo usas y tienes IP fija o puerto abierto. " +
                "Abrir el panel o el SSH a Internet es el último recurso.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TailscaleCard(
    tailscale: TailscaleDto?,
    busy: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    NetGripCard(
        eyebrow = "Red privada",
        title = "Tailscale",
        subtitle = when {
            tailscale == null -> "Sin datos de Tailscale"
            !tailscale.installed -> "No instalado en el router"
            tailscale.running -> "Conectado"
            tailscale.state == "NeedsLogin" -> "Falta iniciar sesión"
            else -> "Detenido"
        },
        icon = NetGripIcons.Shield,
        iconTone = if (tailscale?.running == true) Tone.SUCCESS else Tone.NEUTRAL,
    ) {
        if (tailscale == null) {
            Text(
                "El router no respondió al estado de Tailscale.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!tailscale.installed) {
            Text(
                "Instala el paquete Tailscale en el router desde LuCI o la consola. " +
                    "Después podrás encenderlo aquí.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                if (tailscale.running) "En marcha" else "Parado",
                if (tailscale.running) Tone.SUCCESS else Tone.NEUTRAL,
            )
            tailscale.ips.forEach { ip -> Pill(ip, Tone.TEAL, dot = false) }
        }
        if (tailscale.authUrl.isNotBlank()) {
            Spacer(Modifier.size(8.dp))
            Banner(
                "Falta autorizar este equipo en tu cuenta de Tailscale.",
                Tone.WARN,
            )
            Spacer(Modifier.size(8.dp))
            OutlinedButton(
                onClick = { uriHandler.openUri(tailscale.authUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Abrir la página de autorización") }
        }
        Spacer(Modifier.size(10.dp))
        if (tailscale.running) {
            OutlinedButton(
                onClick = { onToggle(false) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Detener Tailscale") }
        } else {
            OutlinedButton(
                onClick = { onToggle(true) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Conectar Tailscale")
                }
            }
        }
    }
}

@Composable
private fun WireGuardCard(wireguard: WireGuardDto?) {
    NetGripCard(
        eyebrow = "VPN propia",
        title = "WireGuard",
        subtitle = when {
            wireguard == null -> "Sin datos de WireGuard"
            !wireguard.installed -> "No instalado"
            !wireguard.active -> "Servidor desactivado"
            else -> "Servidor activo en el puerto ${wireguard.port.ifBlank { "?" }}"
        },
        icon = NetGripIcons.Shield,
        iconTone = if (wireguard?.active == true) Tone.TEAL else Tone.NEUTRAL,
    ) {
        if (wireguard == null) {
            Text(
                "Este router no respondió al estado de WireGuard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!wireguard.installed) {
            Text(
                "WireGuard no está instalado. Se configura desde LuCI o la consola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        InfoRow("Peers", wireguard.peers.size.toString())
        wireguard.endpoint.takeIf { it.isNotBlank() }?.let { InfoRow("Endpoint", it, mono = true) }
        wireguard.address.takeIf { it.isNotBlank() }?.let { InfoRow("Dirección", it, mono = true) }
        if (wireguard.endpoint.isBlank()) {
            Spacer(Modifier.size(8.dp))
            Banner(
                "Sin endpoint configurado, los clientes usan la IP de la operadora y la VPN " +
                    "deja de funcionar cuando cambia. Configura un DDNS y el endpoint desde el panel web.",
                Tone.WARN,
            )
        }
        if (wireguard.peers.isNotEmpty()) {
            Spacer(Modifier.size(8.dp))
            CardDivider()
            wireguard.peers.take(3).forEach { peer ->
                InfoRow(peer.name.ifBlank { peer.section }, peer.allowedIps.joinToString(", "), mono = true)
            }
            if (wireguard.peers.size > 3) {
                Text(
                    "y ${wireguard.peers.size - 3} más",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OpenVpnCard(openvpn: dev.gnacho.netgrip.data.dto.OpenVpnDto?) {
    NetGripCard(
        eyebrow = "VPN propia",
        title = "OpenVPN",
        subtitle = when {
            openvpn == null -> "Sin datos de OpenVPN"
            !openvpn.installed -> "No instalado"
            !openvpn.active -> "Servidor desactivado"
            else -> "Servidor activo en el puerto ${openvpn.port.ifBlank { "?" }}"
        },
        icon = NetGripIcons.Shield,
        iconTone = if (openvpn?.active == true) Tone.TEAL else Tone.NEUTRAL,
    ) {
        if (openvpn == null) {
            Text(
                "Este router no respondió al estado de OpenVPN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!openvpn.installed) {
            Text(
                "OpenVPN no está instalado. Se configura desde LuCI o la consola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        openvpn.publicHost.takeIf { it.isNotBlank() }?.let { InfoRow("Host público", it, mono = true) }
        openvpn.subnet.takeIf { it.isNotBlank() }?.let { InfoRow("Subred", it, mono = true) }
        if (openvpn.publicHost.isBlank()) {
            Spacer(Modifier.size(8.dp))
            Banner(
                "Sin host público, los clientes apuntan a la IP de la operadora y dejan de " +
                    "conectar cuando cambia.",
                Tone.WARN,
            )
        }
    }
}

@Composable
private fun DdnsCard(state: RemoteState, busy: Boolean, onForce: () -> Unit) {
    val ddns = state.ddns
    NetGripCard(
        eyebrow = "Nombre fijo",
        title = "DDNS",
        subtitle = when {
            ddns == null -> "Sin datos de DDNS"
            !ddns.installed -> "No instalado"
            ddns.entries.isEmpty() -> "Sin nombres configurados"
            ddns.entries.size == 1 -> "1 nombre"
            else -> "${ddns.entries.size} nombres"
        },
        icon = NetGripIcons.Globe,
        iconTone = Tone.VIOLET,
    ) {
        if (ddns == null) {
            Text(
                "Este router no respondió al estado de DDNS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!ddns.installed) {
            Text(
                "ddns-scripts no está instalado. Sin nombre fijo, WireGuard y OpenVPN " +
                    "dependen de una IP que cambia.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        ddns.entries.forEach { entry ->
            CardDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(entry.domain.ifBlank { entry.section }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        entry.serviceName.ifBlank { "sin servicio" } +
                            entry.lastUpdate.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(
                    when {
                        !entry.enabled -> "Apagado"
                        entry.running -> "En marcha"
                        else -> "Parado"
                    },
                    if (entry.running) Tone.SUCCESS else Tone.NEUTRAL,
                )
            }
            InfoRow("IP registrada", entry.registeredIp.ifBlank { "Sin dato" }, mono = true)
            if (ddns.wanIp.isNotBlank() && entry.registeredIp.isNotBlank() && entry.registeredIp != ddns.wanIp) {
                Banner(
                    "La IP registrada no coincide con la WAN actual (${ddns.wanIp}). " +
                        "Fuerza la actualización o revisa las credenciales del proveedor.",
                    Tone.WARN,
                )
            }
        }
        if (ddns.entries.isNotEmpty()) {
            Spacer(Modifier.size(10.dp))
            OutlinedButton(
                onClick = onForce,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(NetGripIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Forzar actualización")
                }
            }
        }
    }
}

@Composable
private fun ExposureCard(
    exposure: RemoteAccessDto?,
    busy: Boolean,
    onChange: (RemoteAccessRequest) -> Unit,
) {
    var confirm by remember { mutableStateOf<ExposureKind?>(null) }

    NetGripCard(
        eyebrow = "Último recurso",
        title = "Exposición a Internet",
        subtitle = "Permite conexiones desde cualquier red",
        icon = NetGripIcons.Warning,
        iconTone = Tone.DANGER,
    ) {
        if (exposure == null) {
            Text(
                "Sin datos de exposición. Esta tarjeta solo aplica en el router principal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!exposure.applicable) {
            Text(
                "Este equipo no es la puerta de enlace, así que no tiene reglas de entrada desde la WAN.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Text(
            "Cada permiso abre un servicio del router a Internet. Tailscale no necesita ninguno.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        ToggleRow(
            title = "Responder a ping",
            subtitle = "Permite descubrir el router desde fuera.",
            checked = exposure.pingWan,
            enabled = !busy,
            onChange = { enabled -> confirm = ExposureKind.Ping(enabled) },
        )
        Spacer(Modifier.size(8.dp))
        ToggleRow(
            title = "Panel por HTTPS desde Internet",
            subtitle = "Publica el panel en la WAN. Solo con una contraseña fuerte.",
            checked = exposure.remoteHttps,
            enabled = !busy,
            onChange = { enabled -> confirm = ExposureKind.Https(enabled) },
        )
        Spacer(Modifier.size(8.dp))
        ToggleRow(
            title = "SSH desde Internet",
            subtitle = "Abre el puerto de administración. Es el más peligroso.",
            checked = exposure.remoteSsh,
            enabled = !busy,
            onChange = { enabled -> confirm = ExposureKind.Ssh(enabled) },
        )
    }

    val kind = confirm
    if (kind != null) {
        ExposureDialog(
            kind = kind,
            onDismiss = { confirm = null },
            onConfirm = {
                confirm = null
                onChange(kind.request)
            },
        )
    }
}

/** Los tres permisos, con el texto de su confirmación. */
private sealed class ExposureKind(val on: Boolean, val request: RemoteAccessRequest) {
    class Ping(on: Boolean) : ExposureKind(on, RemoteAccessRequest(pingWan = on))
    class Https(on: Boolean) : ExposureKind(on, RemoteAccessRequest(remoteHttps = on))
    class Ssh(on: Boolean) : ExposureKind(on, RemoteAccessRequest(remoteSsh = on))
}

private fun ExposureKind.title(): String = when (this) {
    is ExposureKind.Ping -> if (on) "Permitir ping desde Internet" else "Dejar de responder a ping"
    is ExposureKind.Https -> if (on) "Publicar el panel en Internet" else "Cerrar el panel a Internet"
    is ExposureKind.Ssh -> if (on) "Abrir SSH a Internet" else "Cerrar SSH a Internet"
}

private fun ExposureKind.body(): String = when (this) {
    is ExposureKind.Ping -> if (on) {
        "El router responderá a ping desde cualquier red. Ayuda a diagnosticar, pero revela que existe."
    } else {
        "El router dejará de responder a ping desde la WAN."
    }
    is ExposureKind.Https -> if (on) {
        "El panel quedará accesible desde Internet por HTTPS. Cualquiera que conozca la IP o el nombre " +
            "podrá intentar entrar: usa una contraseña fuerte y valora Tailscale en su lugar."
    } else {
        "El panel dejará de aceptar conexiones desde Internet. Las locales siguen igual."
    }
    is ExposureKind.Ssh -> if (on) {
        "El SSH del router quedará abierto a Internet. Es el servicio más atacado: si no lo necesitas " +
            "de verdad, usa Tailscale."
    } else {
        "El SSH dejará de aceptar conexiones desde Internet."
    }
}

@Composable
private fun ExposureDialog(kind: ExposureKind, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val opening = when (kind) {
        is ExposureKind.Ping -> kind.on
        is ExposureKind.Https -> kind.on
        is ExposureKind.Ssh -> kind.on
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(NetGripIcons.Warning, if (opening) Tone.DANGER else Tone.NEUTRAL) },
        title = { Text(kind.title()) },
        text = { Text(kind.body(), style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (opening) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(if (opening) "Abrir" else "Cerrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
