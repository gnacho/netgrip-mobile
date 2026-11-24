package dev.gnacho.netgrip.ui.sites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.StatusDot
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.shell.ConfirmDialog
import dev.gnacho.netgrip.ui.shell.DeviceLoginDialog
import dev.gnacho.netgrip.ui.shell.NameDialog
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip
import kotlinx.coroutines.launch

/** Niveles de navegacion: red, sus dispositivos y la configuracion del dispositivo. */
sealed interface SiteLevel {
    data object Networks : SiteLevel
    data class Devices(val networkId: String) : SiteLevel
    data object Device : SiteLevel
}

/** Primer nivel: todas las redes guardadas. */
@Composable
fun NetworksListScreen(
    viewModel: SessionViewModel,
    onOpen: (SavedNetwork) -> Unit,
) {
    val sites = viewModel.sites
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedNetwork?>(null) }
    var deleting by remember { mutableStateOf<SavedNetwork?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SiteHeader(
            title = "Redes",
            subtitle = if (sites.networks.isEmpty()) {
                "Aún no hay redes guardadas"
            } else {
                val networks = sites.networks.size
                val devices = sites.networks.sumOf { it.devices.size }
                "${networks} ${if (networks == 1) "red" else "redes"} · " +
                    "$devices ${if (devices == 1) "dispositivo" else "dispositivos"}"
            },
            onBack = null,
        ) {
            IconButton(onClick = { creating = true }) {
                Icon(
                    NetGripIcons.Plus,
                    contentDescription = "Nueva red",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (sites.networks.isEmpty()) {
            EmptyStateView(
                title = "Sin redes",
                body = "Crea una red y añade sus routers.",
                icon = NetGripIcons.Network,
                actionLabel = "Nueva red",
                onAction = { creating = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(sites.networks, key = { it.id }) { network ->
                    NetworkCard(
                        network = network,
                        selected = network.id == sites.currentNetworkId,
                        onOpen = { onOpen(network) },
                        onRename = { renaming = network },
                        onDelete = { deleting = network },
                    )
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "Nueva red",
            initial = "",
            confirmLabel = "Crear",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.addNetwork(name)
                creating = false
            },
        )
    }
    renaming?.let { network ->
        NameDialog(
            title = "Renombrar red",
            initial = network.name,
            confirmLabel = "Guardar",
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameNetwork(network.id, name)
                renaming = null
            },
        )
    }
    deleting?.let { network ->
        ConfirmDialog(
            title = "Borrar ${network.name}",
            body = "Se quitarán la red y sus ${network.devices.size} dispositivos de la app. Los routers no cambian.",
            confirmLabel = "Borrar red",
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteNetwork(network.id)
                deleting = null
            },
        )
    }
}

/** Segundo nivel: los dispositivos de la red elegida. */
@Composable
fun DevicesListScreen(
    viewModel: SessionViewModel,
    network: SavedNetwork?,
    onBack: () -> Unit,
    onOpened: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val current = viewModel.currentDevice
    var adding by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf<SavedDevice?>(null) }
    var removing by remember { mutableStateOf<SavedDevice?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    val message = viewModel.siteMessage
    val canImport = viewModel.loggedIn && network != null && viewModel.currentNetwork?.id == network.id

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        SiteHeader(
            title = network?.name ?: "Dispositivos",
            subtitle = when {
                network == null -> "La red ya no existe"
                network.devices.isEmpty() -> "Sin dispositivos"
                network.devices.size == 1 -> "1 dispositivo"
                else -> "${network.devices.size} dispositivos"
            },
            onBack = onBack,
        ) {
            if (network != null) {
                IconButton(onClick = { viewModel.clearSiteMessage(); adding = true }) {
                    Icon(
                        NetGripIcons.Plus,
                        contentDescription = "Añadir dispositivo",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (network == null || network.devices.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                EmptyStateView(
                    title = "Esta red no tiene dispositivos",
                    body = "Añade la dirección del router y sus credenciales.",
                    icon = NetGripIcons.Router,
                    actionLabel = if (network != null) "Añadir dispositivo" else null,
                    onAction = { if (network != null) adding = true },
                    modifier = Modifier.weight(1f),
                )
                if (canImport) {
                    OutlinedButton(
                        onClick = { viewModel.importFleet() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(NetGripIcons.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Importar del fleet")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(network.devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        selected = device.id == current?.id,
                        busy = busyId == device.id,
                        onOpen = {
                            if (device.id == current?.id && viewModel.loggedIn) {
                                onOpened()
                            } else {
                                busyId = device.id
                                scope.launch {
                                    val ok = viewModel.trySwitchDevice(device.id)
                                    busyId = null
                                    if (ok) onOpened() else connecting = device
                                }
                            }
                        },
                        onRemove = { removing = device },
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    if (message != null) {
                        Text(
                            message,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.clearSiteMessage(); adding = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(NetGripIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Añadir dispositivo")
                    }
                    if (canImport) {
                        OutlinedButton(
                            onClick = { viewModel.importFleet() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(NetGripIcons.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Importar del fleet")
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        DeviceLoginDialog(
            title = "Añadir dispositivo",
            initialUrl = "",
            onDismiss = { adding = false },
            onConnected = {
                adding = false
                onOpened()
            },
            connect = { url, user, pass, host, fingerprint ->
                viewModel.connectDevice(url, user, pass, host, fingerprint, networkId = network?.id)
            },
        )
    }
    connecting?.let { device ->
        DeviceLoginDialog(
            title = "Conectar con ${device.label}",
            initialUrl = device.url,
            onDismiss = { connecting = null },
            onConnected = {
                connecting = null
                onOpened()
            },
            connect = { url, user, pass, host, fingerprint ->
                viewModel.connectDevice(url, user, pass, host, fingerprint, networkId = network?.id)
            },
        )
    }
    removing?.let { device ->
        ConfirmDialog(
            title = "Quitar ${device.label}",
            body = "Se quitará de la app. El router no cambia.",
            confirmLabel = "Quitar",
            onDismiss = { removing = null },
            onConfirm = {
                viewModel.removeDevice(device.id)
                removing = null
            },
        )
    }
}

@Composable
private fun SiteHeader(
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    NetGripIcons.ChevronLeft,
                    contentDescription = "Volver",
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions()
    }
}

@Composable
private fun NetworkCard(
    network: SavedNetwork,
    selected: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(
                NetGripIcons.Network,
                if (selected) Tone.ACCENT else Tone.NEUTRAL,
                size = 40,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    network.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (network.devices.size == 1) "1 dispositivo" else "${network.devices.size} dispositivos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) Pill("Actual", Tone.SUCCESS)
            IconButton(onClick = onRename) {
                Icon(
                    NetGripIcons.Pencil,
                    contentDescription = "Renombrar red",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    NetGripIcons.Trash,
                    contentDescription = "Borrar red",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: SavedDevice,
    selected: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconTile(NetGripIcons.Router, if (selected) Tone.ACCENT else Tone.TEAL, size = 40)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        device.label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    device.role.takeIf { it.isNotBlank() }?.let { role ->
                        Pill(role.uppercase(), Tone.NEUTRAL, dot = false)
                    }
                }
                Text(
                    device.url,
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                busy -> CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                selected -> StatusDot(Tone.SUCCESS)
            }
            IconButton(onClick = onRemove) {
                Icon(
                    NetGripIcons.Trash,
                    contentDescription = "Quitar dispositivo",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
