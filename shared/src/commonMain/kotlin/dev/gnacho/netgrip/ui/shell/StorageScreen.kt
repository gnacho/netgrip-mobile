package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.StorageDeviceDto
import dev.gnacho.netgrip.data.dto.StorageProbeDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.storage.StorageRules
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.presentation.StorageState
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.PercentBar
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle

/**
 * Almacenamiento: los discos que ve el router, no los de la app.
 *
 * Se puede montar un disco detectado (punto de montaje y solo lectura) y
 * desmontar los que el propio panel montó. Un montaje hecho a mano se ve pero
 * no se toca: el router lo rechazaría y la app no lo intenta.
 */
@Composable
fun StorageScreen(viewModel: ShellViewModel) {
    val state by viewModel.storage.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureStorage() }

    when {
        state.loading && !state.loaded -> LoadingBox("Leyendo los discos del router")
        state.error != null && state.probe == null -> ErrorBox(state.error!!) { viewModel.loadStorage() }
        else -> {
            val probe = state.probe
            val devices = probe?.devices.orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StorageSummaryCard(probe, devices.size) }
                if (devices.isEmpty()) {
                    item {
                        NetGripCard(
                            eyebrow = "Discos",
                            title = "Ninguno detectado",
                            icon = NetGripIcons.Layers,
                        ) {
                            EmptyStateView(
                                title = "Sin discos",
                                body = "El router no ve ningún disco ahora mismo. Si conectas uno, aparece aquí.",
                                icon = NetGripIcons.Layers,
                            )
                        }
                    }
                }
                items(devices, key = { it.path.ifBlank { it.name } }) { device ->
                    StorageDeviceCard(device, state, viewModel)
                }
                item {
                    Text(
                        "Los servicios de compartición (Samba, minidlna) y sus carpetas siguen en el " +
                            "panel: esta pantalla solo monta y desmonta discos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                state.actionError?.let { message -> item { Banner(message, Tone.DANGER) } }
                state.actionMessage?.let { message -> item { Banner(message, Tone.SUCCESS) } }
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(probe: StorageProbeDto?, devices: Int) {
    val subtitle = when {
        probe == null -> "Sin leer"
        !probe.supported -> "Este equipo no tiene puertos USB"
        !probe.usbPresent -> "Sin nada enchufado"
        !probe.usbStorageLoaded -> "Hay USB, pero el kernel no cargó el almacenamiento"
        devices == 1 -> "1 disco"
        else -> "$devices discos"
    }
    NetGripCard(
        eyebrow = "Almacenamiento",
        title = "Discos del router",
        subtitle = subtitle,
        icon = NetGripIcons.Layers,
        iconTone = when {
            devices > 0 -> Tone.SUCCESS
            probe?.usbPresent == true -> Tone.WARN
            else -> Tone.NEUTRAL
        },
    ) {
        when {
            probe == null -> Text(
                "Sin datos todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !probe.supported -> Text(
                "Este router no expone puertos USB, así que no hay nada que montar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !probe.usbStorageLoaded -> Text(
                "El equipo tiene USB pero el kernel no cargó el controlador de almacenamiento: " +
                    "un disco no aparecería como /dev/sd*. Se arregla en el router, no desde la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            !probe.usbPresent -> Text(
                "No hay nada enchufado. Conecta el disco en el router y actualiza.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "Los tamaños y el uso los mide el router. Montar deja una entrada en fstab; " +
                    "desmontar la quita, pero solo para montajes hechos desde el panel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageDeviceCard(device: StorageDeviceDto, state: StorageState, viewModel: ShellViewModel) {
    var showMount by remember(device.path) { mutableStateOf(false) }
    var showUnmount by remember(device.path) { mutableStateOf(false) }
    val mounted = !device.mountPoint.isNullOrBlank()
    val busy = state.busy == device.path || (device.mountPoint != null && state.busy == device.mountPoint)

    NetGripCard(
        eyebrow = device.name.ifBlank { "Disco" },
        title = device.path.ifBlank { "Sin ruta" },
        subtitle = listOfNotNull(
            device.fsType.takeIf { it.isNotBlank() },
            Formats.bytes(device.sizeBytes).takeIf { device.sizeBytes > 0 },
        ).joinToString(" · ").ifBlank { "Sin datos del disco" },
        icon = NetGripIcons.Layers,
        iconTone = when {
            StorageRules.canUnmount(device) -> Tone.SUCCESS
            mounted -> Tone.WARN
            else -> Tone.NEUTRAL
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                StorageRules.canUnmount(device) ->
                    Pill("Montado en ${device.mountPoint}", Tone.SUCCESS)
                StorageRules.mountedOutsidePanel(device) ->
                    Pill("Montado en ${device.mountPoint} · a mano", Tone.WARN)
                else -> Pill("Sin montar", Tone.NEUTRAL)
            }
            if (device.managed) Pill("Del panel", Tone.NEUTRAL)
        }
        if (device.sizeBytes > 0) {
            Spacer(Modifier.height(8.dp))
            PercentBar(fraction = StorageRules.usedPercent(device) / 100f)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Formats.bytes(device.usedBytes)} de ${Formats.bytes(device.sizeBytes)} · " +
                    "quedan ${Formats.bytes(device.freeBytes)}",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        when {
            StorageRules.canUnmount(device) -> OutlinedButton(
                onClick = { showUnmount = true },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(NetGripIcons.Unplug, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Desmontar")
                }
            }
            StorageRules.mountedOutsidePanel(device) -> Text(
                "Montado fuera del panel: se ve aquí, pero no se desmonta desde la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Button(
                onClick = { showMount = true },
                enabled = !busy && StorageRules.isDevicePath(device.path),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Montar")
                }
            }
        }
    }

    if (showMount) {
        MountDialog(
            device = device,
            busy = busy,
            onMount = { target, readOnly ->
                showMount = false
                viewModel.mountStorage(device.path, target, readOnly)
            },
            onDismiss = { showMount = false },
        )
    }
    if (showUnmount) {
        AlertDialog(
            onDismissRequest = { showUnmount = false },
            title = { Text("Desmontar el disco") },
            text = {
                Text(
                    "Se desmonta ${device.mountPoint} y se quita su entrada de fstab. Si algo está " +
                        "escribiendo en el disco, el router puede rechazarlo.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnmount = false
                        viewModel.unmountStorage(device.mountPoint.orEmpty())
                    },
                ) { Text("Desmontar") }
            },
            dismissButton = {
                TextButton(onClick = { showUnmount = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun MountDialog(
    device: StorageDeviceDto,
    busy: Boolean,
    onMount: (target: String, readOnly: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var target by remember(device.path) { mutableStateOf(StorageRules.suggestTarget(device.path)) }
    var readOnly by remember(device.path) { mutableStateOf(false) }
    val error = StorageRules.validateTarget(target)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Montar ${device.name.ifBlank { device.path }}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Se monta ${device.path} y se guarda la entrada para que vuelva tras reiniciar. " +
                        "Si el montaje falla, el router quita la entrada.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("Punto de montaje") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Solo lectura", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "El disco se monta sin poder escribir en él.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = readOnly, onCheckedChange = { readOnly = it })
                }
                if (error != null) {
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onMount(StorageRules.normalizeTarget(target), readOnly) },
                enabled = !busy && error == null,
            ) {
                Text("Montar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
