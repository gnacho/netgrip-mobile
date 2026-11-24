package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.sites.SavedDevice
import dev.gnacho.netgrip.domain.sites.SavedNetwork
import dev.gnacho.netgrip.presentation.ConnectResult
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip
import kotlinx.coroutines.launch

/** Dos pastillas en la cabecera: red actual y dispositivo actual. */
@Composable
fun SiteChips(
    network: SavedNetwork?,
    device: SavedDevice?,
    onNetworkClick: () -> Unit,
    onDeviceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteChip(
            icon = NetGripIcons.Network,
            label = network?.name ?: "Sin red",
            onClick = onNetworkClick,
            description = "Cambiar de red",
            modifier = Modifier.weight(1f),
        )
        SiteChip(
            icon = NetGripIcons.Router,
            label = device?.label ?: "Sin dispositivo",
            onClick = onDeviceClick,
            description = "Cambiar de dispositivo",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SiteChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                NetGripIcons.ChevronDown,
                contentDescription = description,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.netGrip.faint,
            )
        }
    }
}

/** Hoja con todas las redes guardadas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkSheet(viewModel: SessionViewModel, onDismiss: () -> Unit) {
    val sites = viewModel.sites
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<SavedNetwork?>(null) }
    var deleting by remember { mutableStateOf<SavedNetwork?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Redes", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Cada red agrupa sus routers. Puedes tener tantas como quieras.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sites.networks.isEmpty()) {
                EmptyStateView(
                    title = "Todavía no hay redes",
                    body = "Crea una red y añade tus routers.",
                    icon = NetGripIcons.Network,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    sites.networks.forEach { network ->
                        NetworkRow(
                            network = network,
                            selected = network.id == sites.currentNetworkId,
                            onSelect = {
                                viewModel.selectNetwork(network.id)
                                onDismiss()
                            },
                            onRename = { renaming = network },
                            onDelete = { deleting = network },
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Nueva red")
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
                onDismiss()
            },
        )
    }
}

@Composable
private fun NetworkRow(
    network: SavedNetwork,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                NetGripIcons.Network,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    network.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (network.devices.size == 1) "1 dispositivo" else "${network.devices.size} dispositivos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (selected) {
                Icon(NetGripIcons.Check, contentDescription = "Red actual", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRename) {
                Icon(NetGripIcons.Pencil, contentDescription = "Renombrar red", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) {
                Icon(NetGripIcons.Trash, contentDescription = "Borrar red", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Hoja con los dispositivos de la red actual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(viewModel: SessionViewModel, onDismiss: () -> Unit) {
    val network = viewModel.currentNetwork
    val current = viewModel.currentDevice
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf<SavedDevice?>(null) }
    var removing by remember { mutableStateOf<SavedDevice?>(null) }
    val message = viewModel.siteMessage

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Dispositivos", style = MaterialTheme.typography.headlineSmall)
            Text(
                network?.name ?: "Sin red",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (network == null || network.devices.isEmpty()) {
                EmptyStateView(
                    title = "Esta red no tiene dispositivos",
                    body = "Añade la dirección del router y sus credenciales.",
                    icon = NetGripIcons.Router,
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    network.devices.forEach { device ->
                        DeviceRow(
                            device = device,
                            selected = device.id == current?.id,
                            onSelect = {
                                scope.launch {
                                    if (viewModel.trySwitchDevice(device.id)) {
                                        onDismiss()
                                    } else {
                                        connecting = device
                                    }
                                }
                            },
                            onRemove = { removing = device },
                        )
                    }
                }
            }
            if (message != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = { viewModel.clearSiteMessage(); adding = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(NetGripIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Añadir dispositivo")
            }
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

    if (adding) {
        DeviceLoginDialog(
            title = "Añadir dispositivo",
            initialUrl = "",
            onDismiss = { adding = false },
            onConnected = {
                adding = false
                onDismiss()
            },
            connect = { url, user, pass, host, fingerprint ->
                viewModel.connectDevice(url, user, pass, host, fingerprint)
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
                onDismiss()
            },
            connect = { url, user, pass, host, fingerprint ->
                viewModel.connectDevice(url, user, pass, host, fingerprint)
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
                onDismiss()
            },
        )
    }
}

@Composable
private fun DeviceRow(
    device: SavedDevice,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(NetGripIcons.Router, if (selected) Tone.ACCENT else Tone.NEUTRAL, size = 34)
            Column(Modifier.weight(1f)) {
                Text(
                    device.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    device.url,
                    style = MonoStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            device.role.takeIf { it.isNotBlank() }?.let { role ->
                Pill(role.uppercase(), if (selected) Tone.ACCENT else Tone.NEUTRAL, dot = false)
            }
            if (selected) {
                Pill("Conectado", Tone.SUCCESS)
            }
            IconButton(onClick = onRemove) {
                Icon(NetGripIcons.Trash, contentDescription = "Quitar dispositivo", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Alta o conexion de un router sin salir de la app: direccion, usuario y
 * contrasena, con la confirmacion de huella cuando el certificado es nuevo.
 */
@Composable
private fun DeviceLoginDialog(
    title: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConnected: () -> Unit,
    connect: suspend (url: String, user: String, pass: String, host: String?, fingerprint: String?) -> ConnectResult,
) {
    var url by remember { mutableStateOf(initialUrl) }
    var user by remember { mutableStateOf("root") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var certificate by remember { mutableStateOf<ConnectResult.Certificate?>(null) }
    val scope = rememberCoroutineScope()

    fun submit(host: String? = null, fingerprint: String? = null) {
        if (busy) return
        error = null
        busy = true
        scope.launch {
            when (val result = connect(url, user, password, host, fingerprint)) {
                ConnectResult.Ok -> {
                    busy = false
                    onConnected()
                }
                is ConnectResult.Certificate -> {
                    busy = false
                    certificate = result
                }
                is ConnectResult.Failure -> {
                    busy = false
                    error = result.message
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (certificate == null) {
                    Text(
                        "La contraseña no se guarda. NetGrip conserva solo la cookie de sesión, cifrada.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Dirección") },
                        placeholder = { Text("192.168.1.3:8080") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Usuario") },
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Contraseña") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                } else {
                    val cert = certificate
                    Text(
                        "El certificado de ${cert?.host} no está verificado. Comprueba que la huella coincide con la del router antes de confiar.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            cert?.fingerprint.orEmpty(),
                            modifier = Modifier.padding(10.dp),
                            style = MonoStyle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                error?.let { message ->
                    Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (certificate == null) {
                Button(onClick = { submit() }, enabled = !busy) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "Conectando" else "Entrar")
                }
            } else {
                Button(
                    onClick = { submit(certificate?.host, certificate?.fingerprint) },
                    enabled = !busy,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Confiar y entrar") }
            }
        },
        dismissButton = {
            TextButton(onClick = { if (certificate != null) certificate = null else onDismiss() }) {
                Text(if (certificate != null) "Volver" else "Cancelar")
            }
        },
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(40) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nombre") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { IconTile(NetGripIcons.Warning, Tone.DANGER) },
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodySmall) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
