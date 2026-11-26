package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.GuestConfigRequest
import dev.gnacho.netgrip.data.dto.GuestProbeDto
import dev.gnacho.netgrip.data.dto.IoTConfigRequest
import dev.gnacho.netgrip.data.dto.IoTProbeDto
import dev.gnacho.netgrip.data.dto.RadioEditRequest
import dev.gnacho.netgrip.data.dto.WifiEditRequest
import dev.gnacho.netgrip.data.dto.WifiUiDto
import dev.gnacho.netgrip.data.dto.WirelessRadioDto
import dev.gnacho.netgrip.domain.wifi.NetworkEditRules
import dev.gnacho.netgrip.domain.wifi.WifiEditRules
import dev.gnacho.netgrip.domain.wifi.WifiGroup
import dev.gnacho.netgrip.domain.wifi.WifiGrouping
import dev.gnacho.netgrip.presentation.PendingConfirm
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.presentation.WifiTabState
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.InlineWarning
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiScreen(viewModel: ShellViewModel) {
    val state by viewModel.wifi.collectAsState()
    var editing by remember { mutableStateOf<WifiGroup?>(null) }
    var tuning by remember { mutableStateOf<WirelessRadioDto?>(null) }
    var guestOpen by remember { mutableStateOf(false) }
    var iotOpen by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.ensureWifi() }

    val feedback = state.actionError ?: state.actionMessage
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearWifiFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && !state.loaded -> LoadingBox()
            state.error != null && state.interfaces.isEmpty() -> ErrorBox(state.error!!) { viewModel.loadWifi() }
            !state.loaded -> LoadingBox()
            !state.hasWifi -> EmptyStateView(
                title = "Este equipo no tiene radios WiFi",
                body = "Trabaja solo con puertos Ethernet.",
                icon = NetGripIcons.Wifi,
                modifier = Modifier.fillMaxSize(),
            )
            state.interfaces.isEmpty() -> EmptyStateView(
                title = "No hay redes WiFi configuradas",
                body = "Configura una red desde el panel web de NetGrip.",
                icon = NetGripIcons.Wifi,
                modifier = Modifier.fillMaxSize(),
            )
            else -> {
                val groups = remember(state.interfaces, state.radios) {
                    WifiGrouping.build(state.interfaces, state.radios)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    state.error?.let { message -> item { InlineWarning(message) } }
                    state.pendingConfirm?.let { pending ->
                        item {
                            PendingConfirmCard(
                                pending = pending,
                                onKeep = viewModel::keepWifiChanges,
                                onRevert = viewModel::revertWifiChanges,
                            )
                        }
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                NetGripIcons.EyeOff,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 3.dp)
                                    .size(14.dp),
                                tint = MaterialTheme.netGrip.faint,
                            )
                            Text(
                                "La clave de la red nunca se muestra en esta app.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(groups, key = { it.ssid }) { group ->
                        WifiGroupCard(
                            group = group,
                            radios = state.radios,
                            busy = state.busy,
                            onEdit = { editing = group },
                            onRadio = { radio -> tuning = radio },
                        )
                    }
                    state.guest?.let { guest ->
                        item { GuestCard(guest = guest, busy = state.busy) { guestOpen = true } }
                    }
                    state.iot?.let { iot ->
                        item { IoTCard(iot = iot, busy = state.busy) { iotOpen = true } }
                    }
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

    editing?.let { group ->
        val interfaces = state.interfaces.filter { it.ssid == group.ssid }
        WifiEditSheet(
            group = group,
            interfaces = interfaces,
            busy = state.busy,
            onDismiss = { editing = null },
            onApply = { edit ->
                editing = null
                viewModel.applyWifi(edit)
            },
        )
    }

    if (guestOpen) {
        state.guest?.let { guest ->
            GuestSheet(
                guest = guest,
                busy = state.busy,
                onDismiss = { guestOpen = false },
                onApply = { cfg ->
                    guestOpen = false
                    viewModel.applyGuest(cfg)
                },
            )
        }
    }

    if (iotOpen) {
        state.iot?.let { iot ->
            IoTSheet(
                iot = iot,
                busy = state.busy,
                onDismiss = { iotOpen = false },
                onApply = { cfg ->
                    iotOpen = false
                    viewModel.applyIoT(cfg)
                },
            )
        }
    }

    tuning?.let { radio ->
        WifiRadioSheet(
            radio = radio,
            busy = state.busy,
            onDismiss = { tuning = null },
            onApply = { edit ->
                tuning = null
                viewModel.applyWifiRadio(edit)
            },
        )
    }
}

/** Los cambios estan puestos pero el router los deshara si no se confirman. */
@Composable
private fun PendingConfirmCard(
    pending: PendingConfirm,
    onKeep: () -> Unit,
    onRevert: () -> Unit,
) {
    NetGripCard(
        eyebrow = "Sin confirmar",
        title = "Los cambios se deshacen en ${pending.secondsLeft} s",
        subtitle = "Si el móvil sigue conectado por esta WiFi, confirma para conservarlos.",
        icon = NetGripIcons.Clock,
        iconTone = Tone.WARN,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onKeep,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Mantener") }
            OutlinedButton(
                onClick = onRevert,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Deshacer") }
        }
    }
}

@Composable
private fun WifiGroupCard(
    group: WifiGroup,
    radios: List<WirelessRadioDto>,
    busy: Boolean,
    onEdit: () -> Unit,
    onRadio: (WirelessRadioDto) -> Unit,
) {
    NetGripCard(
        eyebrow = if (group.disabled) "Desactivada" else "Red WiFi",
        title = WifiGrouping.ssidLabel(group.ssid),
        subtitle = if (group.clientCount == 1) "1 cliente" else "${group.clientCount} clientes",
        icon = NetGripIcons.Wifi,
        iconTone = if (group.disabled) Tone.DANGER else Tone.TEAL,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (group.disabled) Pill("Desactivada", Tone.DANGER)
            if (group.hidden) Pill("Oculta", Tone.WARN)
            group.bands.forEach { band -> Pill(WifiGrouping.bandLabel(band), Tone.NEUTRAL, dot = false) }
        }
        Spacer(Modifier.size(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Cifrado",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                group.encryptions.joinToString(", ") { WifiEditRules.encryptionLabel(it) }
                    .ifBlank { "Abierta" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (group.encryptions.isEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (group.radios.isNotEmpty()) {
            Spacer(Modifier.size(6.dp))
        }
        group.radios.forEach { radioInfo ->
            val radio = radios.firstOrNull { it.name == radioInfo.name }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            radioInfo.name,
                            modifier = Modifier.weight(1f),
                            style = MonoStyle,
                            fontWeight = FontWeight.Medium,
                        )
                        Pill(
                            if (radioInfo.up) "Activa" else "Apagada",
                            if (radioInfo.up) Tone.SUCCESS else Tone.DANGER,
                        )
                    }
                    Text(
                        listOfNotNull(
                            WifiGrouping.bandLabel(radioInfo.band),
                            radioInfo.channel.takeIf { it.isNotBlank() }?.let { "canal $it" },
                            radioInfo.htmode.takeIf { it.isNotBlank() },
                            if (radioInfo.txPower > 0) "${radioInfo.txPower} dBm" else null,
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (radio != null) {
                        TextButton(
                            onClick = { onRadio(radio) },
                            enabled = !busy,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        ) {
                            Icon(NetGripIcons.RadioTower, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Cambiar canal")
                        }
                    }
                }
            }
        }
        if (group.radios.isEmpty()) {
            Text(
                "Sin datos de la radio física.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(10.dp))
        CardDivider()
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Editar esta red")
        }
    }
}

/**
 * Editor de una red: nombre, seguridad, clave de escritura, oculta y emision.
 * Se aplica a todas las interfaces que comparten el SSID para no separar las
 * bandas sin querer. La clave vacia deja la actual.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiEditSheet(
    group: WifiGroup,
    interfaces: List<WifiUiDto>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (WifiEditRequest) -> Unit,
) {
    val current = interfaces.firstOrNull()
    var ssid by remember(group.ssid) { mutableStateOf(group.ssid) }
    var key by remember(group.ssid) { mutableStateOf("") }
    var encryption by remember(group.ssid) {
        mutableStateOf(WifiEditRules.selectedEncryption(current?.encryption.orEmpty()))
    }
    var hidden by remember(group.ssid) { mutableStateOf(group.hidden) }
    var emitting by remember(group.ssid) { mutableStateOf(!group.disabled) }
    var confirmOpen by remember { mutableStateOf(false) }

    val options = WifiEditRules.encryptions(current?.encryption.orEmpty())
    val ssidError = WifiEditRules.ssidError(ssid)
    val keyError = WifiEditRules.keyError(encryption.value, key)
    val changes = WifiEditRules.pendingChanges(
        currentSsid = group.ssid,
        currentEncryption = current?.encryption.orEmpty(),
        currentHidden = group.hidden,
        currentDisabled = group.disabled,
        ssid = ssid,
        encryption = encryption.value,
        hidden = hidden,
        disabled = !emitting,
        keyProvided = key.isNotEmpty(),
    )
    val canSave = !busy && ssidError == null && keyError == null && changes.isNotEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Editar red WiFi", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (interfaces.size > 1) {
                    "Se aplica a las ${interfaces.size} bandas de esta red."
                } else {
                    "Se aplica a la única banda de esta red."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it.take(32) },
                label = { Text("Nombre de la red") },
                singleLine = true,
                isError = ssidError != null,
                supportingText = ssidError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Seguridad", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedControl(
                options = options.map { it.value to it.label },
                selected = encryption.value,
                onSelect = { value ->
                    encryption = options.first { it.value == value }
                },
            )

            OutlinedTextField(
                value = key,
                onValueChange = { key = it.take(63) },
                label = { Text("Clave") },
                placeholder = {
                    Text(if (current?.hasKey == true) "Sin cambios" else "Sin clave")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = keyError != null,
                supportingText = {
                    Text(keyError ?: "Vacía deja la clave actual. Nunca se muestra ni se guarda.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(
                title = "Red oculta",
                subtitle = "No publica el nombre; hay que escribirlo para conectarse.",
                checked = hidden,
                onChange = { hidden = it },
            )
            SwitchRow(
                title = "Emitir esta red",
                subtitle = "Apagarla desconecta a sus clientes y deja de verse.",
                checked = emitting,
                onChange = { emitting = it },
            )

            Banner(
                "Al guardar, los clientes de esta red pueden desconectarse mientras la radio se recarga.",
                Tone.WARN,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmOpen = true },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        val target = interfaces.ifEmpty { listOfNotNull(current) }
        ConfirmDialog(
            title = "Aplicar cambios en ${if (changes.isEmpty()) "la red" else changes.joinToString(", ")}",
            body = buildString {
                append("Red \"${ssid.trim()}\"")
                if (target.size > 1) append(" en ${target.size} bandas")
                append(". Los clientes pueden desconectarse. ")
                append("El router mantiene el cambio solo si lo confirmas en los dos minutos siguientes.")
            },
            confirmLabel = "Aplicar",
            onDismiss = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                onApply(
                    WifiEditRequest(
                        section = target.firstOrNull()?.section.orEmpty(),
                        sections = target.map { it.section }.takeIf { it.size > 1 },
                        ssid = ssid.trim(),
                        key = key.takeIf {
                            it.isNotEmpty() && !WifiEditRules.isOpen(encryption.value)
                        },
                        encryption = encryption.value,
                        hidden = hidden,
                        disabled = !emitting,
                        confirm = true,
                    ),
                )
            },
        )
    }
}

/** Canal, ancho y potencia de una radio. Solo viaja lo que cambia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WifiRadioSheet(
    radio: WirelessRadioDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (RadioEditRequest) -> Unit,
) {
    var channel by remember(radio.name) { mutableStateOf(radio.channel.ifBlank { "auto" }) }
    var width by remember(radio.name) { mutableStateOf(radio.htmode) }
    var power by remember(radio.name) { mutableStateOf(if (radio.txpower > 0) radio.txpower.toString() else "") }
    var confirmOpen by remember { mutableStateOf(false) }

    val powerValue = power.toIntOrNull()
    val powerError = when {
        power.isBlank() -> null
        powerValue == null -> "Escribe un número en dBm"
        powerValue <= 0 || powerValue > 40 -> "La potencia va de 1 a 40 dBm"
        else -> null
    }
    val changedChannel = channel != radio.channel.ifBlank { "auto" }
    val changedWidth = width != radio.htmode
    val changedPower = powerValue != null && powerValue != radio.txpower
    val canSave = !busy && powerError == null && (changedChannel || changedWidth || changedPower)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ajustar radio", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                Pill(WifiEditRules.bandLabel(radio.band), Tone.NEUTRAL, dot = false)
            }
            Text(
                "${radio.name} · canal ${radio.channel.ifBlank { "auto" }} · ${radio.htmode.ifBlank { "sin dato" }}",
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Canal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(
                options = listOf("auto") + WifiEditRules.channelsFor(radio.band),
                selected = channel,
                onSelect = { channel = it },
            )

            Text("Ancho de canal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ChoiceRow(
                options = WifiEditRules.widthsFor(radio.band),
                selected = width,
                onSelect = { width = it },
            )

            OutlinedTextField(
                value = power,
                onValueChange = { power = it.filter { ch -> ch.isDigit() }.take(2) },
                label = { Text("Potencia (dBm)") },
                placeholder = { Text("Sin cambios") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = powerError != null,
                supportingText = { Text(powerError ?: "Vacío deja la potencia actual.") },
                modifier = Modifier.fillMaxWidth(),
            )

            Banner(
                "La radio se reinicia para aplicar el cambio y sus clientes se reconectan. Si el móvil usa esta WiFi, el router deshace el cambio salvo que lo confirmes.",
                Tone.WARN,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmOpen = true },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Aplicar")
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        ConfirmDialog(
            title = "Ajustar ${radio.name}",
            body = buildString {
                append("Radio de ${WifiEditRules.bandLabel(radio.band)}")
                if (changedChannel) append(", canal $channel")
                if (changedWidth) append(", ancho $width")
                if (changedPower) append(", potencia ${powerValue} dBm")
                append(". Los clientes de esta radio se reconectan y el router revierte el cambio si no lo confirmas en dos minutos.")
            },
            confirmLabel = "Aplicar",
            onDismiss = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                onApply(
                    RadioEditRequest(
                        radio = radio.name,
                        channel = channel.takeIf { changedChannel },
                        htmode = width.takeIf { changedWidth },
                        txpower = powerValue.takeIf { changedPower },
                        confirm = true,
                    ),
                )
            },
        )
    }
}

/** Fila de opciones desplazable, con la capsula del panel. */
@Composable
private fun ChoiceRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Surface(
                modifier = Modifier.clickable { onSelect(option) },
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Text(
                    option,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // La fila entera conmuta; el switch solo refleja el estado.
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Red de invitados: existe solo en un equipo con firewall (gateway). */
@Composable
private fun GuestCard(guest: GuestProbeDto, busy: Boolean, onEdit: () -> Unit) {
    NetGripCard(
        eyebrow = "INVITADOS",
        title = "Red de invitados",
        subtitle = NetworkEditRules.summary(guest.active, guest.ssid, guest.clients),
        icon = NetGripIcons.Users,
        iconTone = if (guest.active) Tone.VIOLET else Tone.NEUTRAL,
    ) {
        if (!guest.gateway) {
            Text(
                "Este router trabaja como punto de acceso. La red de invitados necesita un equipo con firewall y salida a Internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                if (guest.active) "Activa" else "Desactivada",
                if (guest.active) Tone.SUCCESS else Tone.NEUTRAL,
            )
            if (guest.isolate) Pill("Aislada", Tone.TEAL, dot = false)
            guest.subnet.takeIf { it.isNotBlank() }?.let { Pill(it, Tone.NEUTRAL, dot = false) }
        }
        if (guest.glConflict) {
            Spacer(Modifier.size(8.dp))
            Banner(
                "El firmware del router también define redes de invitado. NetGrip usa las suyas para no mezclarse.",
                Tone.WARN,
            )
        }
        Spacer(Modifier.size(10.dp))
        CardDivider()
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Configurar invitados")
        }
    }
}

/** SSID aparte para dispositivos que no deben ver la red de casa. */
@Composable
private fun IoTCard(iot: IoTProbeDto, busy: Boolean, onEdit: () -> Unit) {
    NetGripCard(
        eyebrow = "DISPOSITIVOS",
        title = "Red IoT",
        subtitle = NetworkEditRules.summary(iot.active, iot.ssid, iot.clients),
        icon = NetGripIcons.Cpu,
        iconTone = if (iot.active) Tone.TEAL else Tone.NEUTRAL,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(
                if (iot.active) "Activa" else "Desactivada",
                if (iot.active) Tone.SUCCESS else Tone.NEUTRAL,
            )
            iot.band.takeIf { it.isNotBlank() }?.let {
                Pill(NetworkEditRules.bandLabel(it), Tone.NEUTRAL, dot = false)
            }
            if (iot.isolated) Pill("Aislada", Tone.TEAL, dot = false)
        }
        Spacer(Modifier.size(10.dp))
        CardDivider()
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onEdit,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(NetGripIcons.Pencil, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Configurar IoT")
        }
    }
}

/** Editor de la red de invitados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuestSheet(
    guest: GuestProbeDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (GuestConfigRequest) -> Unit,
) {
    var enabled by remember { mutableStateOf(guest.active) }
    var ssid by remember { mutableStateOf(guest.ssid) }
    var key by remember { mutableStateOf("") }
    var band by remember { mutableStateOf("both") }
    var subnet by remember { mutableStateOf(guest.subnet) }
    var isolate by remember { mutableStateOf(guest.isolate) }
    var confirmOpen by remember { mutableStateOf(false) }

    val keyRequired = NetworkEditRules.guestNeedsKey(guest.ssid)
    val configError = NetworkEditRules.configError(enabled, ssid, key, keyRequired)
    val subnetError = NetworkEditRules.subnetError(subnet)
    val canSave = !busy && configError == null && subnetError == null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Red de invitados", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Una red aparte para visitas: no ve a los dispositivos de casa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = "Activar la red de invitados",
                subtitle = "Apagarla desconecta a quien esté conectado.",
                checked = enabled,
                onChange = { enabled = it },
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it.take(32) },
                label = { Text("Nombre de la red") },
                singleLine = true,
                isError = enabled && configError != null && ssid.trim().isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.take(63) },
                label = { Text("Clave") },
                placeholder = {
                    Text(if (keyRequired) "Obligatoria al activarla" else "Sin cambios")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = enabled && configError != null && configError.contains("clave", ignoreCase = true),
                supportingText = {
                    Text(
                        configError?.takeIf { enabled }
                            ?: if (keyRequired) {
                                "Nunca se muestra ni se guarda."
                            } else {
                                "Vacía deja la clave actual. Nunca se muestra ni se guarda."
                            },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Banda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedControl(
                options = NetworkEditRules.bands.map { it to NetworkEditRules.bandLabel(it) },
                selected = band,
                onSelect = { band = it },
            )

            OutlinedTextField(
                value = subnet,
                onValueChange = { subnet = it.take(15) },
                label = { Text("Subred (opcional)") },
                placeholder = { Text("Automática") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = subnetError != null,
                supportingText = { Text(subnetError ?: "Vacía deja la que ya hay.") },
                modifier = Modifier.fillMaxWidth(),
            )

            SwitchRow(
                title = "Aislar a los invitados",
                subtitle = "No pueden hablar entre ellos ni con la red de casa.",
                checked = isolate,
                onChange = { isolate = it },
            )

            Banner(
                "Al guardar, los invitados pueden desconectarse mientras las radios se recargan. El router comprueba el resultado y deshace el cambio si algo falla.",
                Tone.WARN,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmOpen = true },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        ConfirmDialog(
            title = if (enabled) "Activar la red de invitados" else "Desactivar la red de invitados",
            body = if (enabled) {
                "Se emitirá \"${ssid.trim()}\" en ${NetworkEditRules.bandLabel(band)}. " +
                    "Los invitados conectados pueden perder la conexión mientras se aplica."
            } else {
                "Se apagará la red de invitados y sus clientes se desconectarán."
            },
            confirmLabel = if (enabled) "Activar" else "Desactivar",
            onDismiss = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                onApply(
                    GuestConfigRequest(
                        enabled = enabled,
                        ssid = ssid.trim(),
                        key = key,
                        band = band,
                        subnet = subnet.trim(),
                        isolate = isolate,
                    ),
                )
            },
        )
    }
}

/** Editor del SSID de dispositivos IoT. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IoTSheet(
    iot: IoTProbeDto,
    busy: Boolean,
    onDismiss: () -> Unit,
    onApply: (IoTConfigRequest) -> Unit,
) {
    var enabled by remember { mutableStateOf(iot.active) }
    var ssid by remember { mutableStateOf(iot.ssid) }
    var key by remember { mutableStateOf("") }
    var band by remember { mutableStateOf(iot.band.ifBlank { "both" }) }
    var confirmOpen by remember { mutableStateOf(false) }

    val configError = NetworkEditRules.configError(enabled, ssid, key)
    val canSave = !busy && configError == null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Red IoT", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Un SSID para bombillas, enchufes y sensores, separado del resto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SwitchRow(
                title = "Activar la red IoT",
                subtitle = "Apagarla desconecta los dispositivos que la usan.",
                checked = enabled,
                onChange = { enabled = it },
            )

            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it.take(32) },
                label = { Text("Nombre de la red") },
                singleLine = true,
                isError = enabled && configError != null && ssid.trim().isEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = key,
                onValueChange = { key = it.take(63) },
                label = { Text("Clave") },
                placeholder = { Text("Obligatoria al activarla") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = enabled && configError != null && configError.contains("clave", ignoreCase = true),
                supportingText = {
                    Text(configError?.takeIf { enabled } ?: "Nunca se muestra ni se guarda.")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Banda", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SegmentedControl(
                options = NetworkEditRules.bands.map { it to NetworkEditRules.bandLabel(it) },
                selected = band,
                onSelect = { band = it },
            )

            Banner(
                "Al guardar, los dispositivos de esta red pueden desconectarse mientras la radio se recarga.",
                Tone.WARN,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Cancelar") }
                Button(
                    onClick = { confirmOpen = true },
                    enabled = canSave,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Guardar")
                    }
                }
            }
        }
    }

    if (confirmOpen) {
        ConfirmDialog(
            title = if (enabled) "Activar la red IoT" else "Desactivar la red IoT",
            body = if (enabled) {
                "Se emitirá \"${ssid.trim()}\" en ${NetworkEditRules.bandLabel(band)}. " +
                    "Los dispositivos conectados pueden perder la conexión mientras se aplica."
            } else {
                "Se apagará la red IoT y sus dispositivos se desconectarán."
            },
            confirmLabel = if (enabled) "Activar" else "Desactivar",
            onDismiss = { confirmOpen = false },
            onConfirm = {
                confirmOpen = false
                onApply(IoTConfigRequest(enabled = enabled, ssid = ssid.trim(), key = key, band = band))
            },
        )
    }
}
