package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.ClientDto
import dev.gnacho.netgrip.data.dto.NftQoSLimitDto
import dev.gnacho.netgrip.data.dto.ParentalRuleDto
import dev.gnacho.netgrip.data.dto.QuotaDto
import dev.gnacho.netgrip.data.dto.QuotaUsageDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.traffic.DataUnit
import dev.gnacho.netgrip.domain.traffic.PolicyRules
import dev.gnacho.netgrip.presentation.PoliciesState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.SegmentedControl
import dev.gnacho.netgrip.ui.common.Tone

/**
 * Politicas por dispositivo: tope de datos, limite de velocidad y horario de
 * bloqueo. Las tres se escriben en el router y se releen despues de guardar;
 * ninguna se pinta por adelantado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PoliciesCard(
    clients: List<ClientDto>,
    names: Map<String, String>,
    state: PoliciesState,
    onOpen: (ClientDto) -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val rows = remember(clients, names) {
        clients.sortedBy { names[it.mac.lowercase()] ?: it.name.ifBlank { it.ip.orEmpty() } }
    }
    val withPolicy = remember(rows, state.quotas, state.qos, state.parental) {
        rows.filter { policyPills(it, state).isNotEmpty() }
    }
    val visible = if (showAll) rows else withPolicy
    NetGripCard(
        eyebrow = "Políticas",
        title = "Cuotas, velocidad y horarios",
        subtitle = when {
            state.loading && !state.loaded -> "Leyendo"
            rows.isEmpty() -> "Sin dispositivos"
            else -> "${rows.size} dispositivos · ${withPolicy.size} con límites"
        },
        icon = NetGripIcons.Shield,
    ) {
        if (rows.isEmpty()) {
            Text(
                "El router no ha listado ningún dispositivo todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (visible.isEmpty()) {
            Text(
                "Ningún dispositivo tiene cuota, límite de velocidad ni horario.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEach { client ->
            val mac = client.mac.lowercase()
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            names[mac]?.takeIf { it.isNotBlank() }
                                ?: client.name.ifBlank { client.ip.orEmpty() },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            listOfNotNull(client.ip, client.mac).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onOpen(client) }) { Text("Límites") }
                }
                val pills = policyPills(client, state)
                if (pills.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        pills.forEach { (text, tone) -> Pill(text = text, tone = tone) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(4.dp))
            }
        }
        TextButton(onClick = { showAll = !showAll }) {
            Text(
                if (showAll) {
                    "Ver solo los que tienen límites"
                } else {
                    "Ver los ${rows.size} dispositivos"
                },
            )
        }
    }
}

/** Resumen corto de lo que ya tiene aplicado un dispositivo. */
internal fun policyPills(client: ClientDto, state: PoliciesState): List<Pair<String, Tone>> {
    val mac = client.mac.lowercase()
    val pills = mutableListOf<Pair<String, Tone>>()
    state.quotas?.quotas?.get(mac)?.let { quota ->
        val usage = state.quotas.usage[mac]
        val tone = when {
            usage?.exceeded == true -> Tone.DANGER
            usage?.throttled == true -> Tone.WARN
            else -> Tone.NEUTRAL
        }
        pills += "Cuota ${Formats.bytes(quota.limit)} ${PolicyRules.periodLabel(quota.period).lowercase()}" to tone
    }
    state.qos?.limits?.get(mac)?.let { limit ->
        pills += "Velocidad ${limit.download}/${limit.upload} Mb/s" to Tone.NEUTRAL
    }
    state.parental?.rules?.get(mac)?.let { rule ->
        if (rule.enabled || rule.paused) {
            pills += PolicyRules.parentalSummary(rule.enabled, rule.paused, rule.days, rule.start, rule.end) to
                if (rule.paused) Tone.WARN else Tone.NEUTRAL
        }
    }
    return pills
}

/**
 * Hoja de edicion de un dispositivo. Tres formularios, cada uno con su
 * confirmacion y su relectura: guardar uno no toca los otros.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicySheet(
    device: ClientDto,
    viewModel: ShellViewModel,
    state: PoliciesState,
    onDismiss: () -> Unit,
) {
    var tab by remember(device.mac) { mutableStateOf("quota") }
    val mac = device.mac.lowercase()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Límites del dispositivo",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    device.name.takeIf { it.isNotBlank() },
                    device.ip,
                    device.mac,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedControl(
                options = listOf(
                    "quota" to "Cuota",
                    "qos" to "Velocidad",
                    "parental" to "Horario",
                ),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.fillMaxWidth(),
            )
            when (tab) {
                "quota" -> QuotaForm(device, mac, state, viewModel)
                "qos" -> QosForm(device, mac, state, viewModel)
                else -> ParentalForm(mac, state, viewModel)
            }
            if (state.actionMessage != null || state.actionError != null) {
                Banner(
                    state.actionError ?: state.actionMessage.orEmpty(),
                    if (state.actionError != null) Tone.DANGER else Tone.SUCCESS,
                )
            }
        }
    }
}

@Composable
private fun QuotaForm(device: ClientDto, mac: String, state: PoliciesState, viewModel: ShellViewModel) {
    val existing = state.quotas?.quotas?.get(mac)
    val usage = state.quotas?.usage?.get(mac)
    val applicable = state.quotas?.applicable ?: true
    val initialUnit = if (existing == null || existing.limit >= DataUnit.GIB.bytes) DataUnit.GIB else DataUnit.MIB
    var unit by remember(existing?.limit) { mutableStateOf(initialUnit) }
    var period by remember(existing?.period) { mutableStateOf(existing?.period ?: "daily") }
    var amount by remember(existing?.limit) {
        mutableStateOf(
            PolicyRules.amountText(existing?.limit ?: 10L * DataUnit.GIB.bytes, initialUnit),
        )
    }
    var action by remember(existing?.action) { mutableStateOf(existing?.action ?: "notify") }
    var kbps by remember(existing?.throttleKbps) {
        mutableStateOf((existing?.throttleKbps ?: 0).takeIf { it > 0 }?.toString() ?: "")
    }
    var confirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    val limitBytes = PolicyRules.limitBytes(amount, unit)
    val error = PolicyRules.validateQuota(limitBytes, action, kbps.toIntOrNull() ?: 0)
    val busy = state.busyMac != null
    val hasIp = !device.ip.isNullOrBlank()

    if (!applicable) {
        Banner("El router no aplica cuotas: solo el equipo que hace de gateway puede contarlas.", Tone.WARN)
    }
    if (!hasIp) {
        Banner("El dispositivo no tiene IP ahora mismo. La cuota se aplica por IP contada, así que hay que esperar a que el router la vea.", Tone.WARN)
    }
    usage?.let { UsageRow(it) }

    SegmentedControl(
        options = listOf("daily" to "Al día", "monthly" to "Al mes"),
        selected = period,
        onSelect = { period = it },
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(8) },
            label = { Text("Tope") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
        )
        SegmentedControl(
            options = listOf(DataUnit.MIB to "MiB", DataUnit.GIB to "GiB"),
            selected = unit,
            onSelect = { chosen ->
                val bytes = PolicyRules.limitBytes(amount, unit)
                unit = chosen
                if (bytes != null) amount = PolicyRules.amountText(bytes, chosen)
            },
            modifier = Modifier.weight(1f),
        )
    }
    SegmentedControl(
        options = listOf("notify" to "Avisar", "throttle" to "Limitar"),
        selected = action,
        onSelect = { action = it },
        modifier = Modifier.fillMaxWidth(),
    )
    if (action == "throttle") {
        OutlinedTextField(
            value = kbps,
            onValueChange = { kbps = it.filter { ch -> ch.isDigit() }.take(7) },
            label = { Text("Velocidad al limitar (kb/s)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { confirm = true },
            enabled = !busy && error == null && hasIp && applicable,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(if (existing == null) "Guardar cuota" else "Cambiar cuota")
            }
        }
        if (existing != null) {
            TextButton(onClick = { confirmRemove = true }, enabled = !busy) {
                Text("Quitar")
            }
        }
    }
    if (confirm) {
        val bytes = limitBytes ?: 0
        PolicyConfirm(
            title = "Guardar la cuota",
            body = "${Formats.bytes(bytes)} ${PolicyRules.periodLabel(period).lowercase()}, " +
                "acción ${PolicyRules.actionLabel(action).lowercase()}. Se aplica en el router.",
            onConfirm = {
                confirm = false
                viewModel.saveQuota(
                    device,
                    QuotaDto(
                        mac = mac,
                        ip = device.ip.orEmpty(),
                        period = period,
                        limit = bytes,
                        action = action,
                        throttleKbps = if (action == "throttle") kbps.toIntOrNull() ?: 0 else 0,
                    ),
                )
            },
            onDismiss = { confirm = false },
        )
    }
    if (confirmRemove) {
        PolicyConfirm(
            title = "Quitar la cuota",
            body = "El dispositivo dejará de tener tope de datos. Se aplica en el router.",
            onConfirm = {
                confirmRemove = false
                viewModel.removeQuota(mac)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun UsageRow(usage: QuotaUsageDto) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${Formats.bytes(usage.used)} de ${Formats.bytes(usage.limit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (usage.exceeded) Pill("Agotada", Tone.DANGER)
            if (usage.throttled) Pill("Limitada", Tone.WARN)
        }
        Text(
            "Quedan ${Formats.bytes(usage.remaining)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QosForm(device: ClientDto, mac: String, state: PoliciesState, viewModel: ShellViewModel) {
    val existing: NftQoSLimitDto? = state.qos?.limits?.get(mac)
    val applicable = state.qos?.applicable ?: true
    var download by remember(existing?.download) {
        mutableStateOf((existing?.download ?: 0).takeIf { it > 0 }?.toString() ?: "")
    }
    var upload by remember(existing?.upload) {
        mutableStateOf((existing?.upload ?: 0).takeIf { it > 0 }?.toString() ?: "")
    }
    val down = download.toIntOrNull() ?: 0
    val up = upload.toIntOrNull() ?: 0
    val error = PolicyRules.validateQos(down, up)
    val busy = state.busyMac != null
    val hasIp = !device.ip.isNullOrBlank()
    var confirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    if (!applicable) {
        Banner("El router no aplica límites de velocidad: solo el equipo que hace de gateway puede.", Tone.WARN)
    }
    if (!hasIp) {
        Banner("El dispositivo no tiene IP ahora mismo, y el límite se aplica por IP.", Tone.WARN)
    }
    Text(
        "Mb/s de bajada y de subida. Deja los dos a cero para quitarlo.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = download,
            onValueChange = { download = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text("Bajada Mb/s") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = upload,
            onValueChange = { upload = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text("Subida Mb/s") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { confirm = true },
            enabled = !busy && error == null && hasIp && applicable && (down > 0 || up > 0),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(if (existing == null) "Guardar límite" else "Cambiar límite")
            }
        }
        if (existing != null) {
            TextButton(onClick = { confirmRemove = true }, enabled = !busy) {
                Text("Quitar")
            }
        }
    }
    if (confirm) {
        PolicyConfirm(
            title = "Guardar el límite",
            body = "Bajada ${down} Mb/s, subida ${up} Mb/s. Se aplica en el router.",
            onConfirm = {
                confirm = false
                viewModel.saveQos(device, down, up)
            },
            onDismiss = { confirm = false },
        )
    }
    if (confirmRemove) {
        PolicyConfirm(
            title = "Quitar el límite",
            body = "El dispositivo dejará de tener tope de velocidad. Se aplica en el router.",
            onConfirm = {
                confirmRemove = false
                viewModel.removeQos(mac)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun ParentalForm(mac: String, state: PoliciesState, viewModel: ShellViewModel) {
    val existing = state.parental?.rules?.get(mac)
    var enabled by remember(existing?.enabled) { mutableStateOf(existing?.enabled ?: false) }
    var paused by remember(existing?.paused) { mutableStateOf(existing?.paused ?: false) }
    var days by remember(existing?.days) { mutableStateOf(existing?.days?.toSet() ?: emptySet()) }
    var start by remember(existing?.start) { mutableStateOf(existing?.start ?: "18:00") }
    var end by remember(existing?.end) { mutableStateOf(existing?.end ?: "20:00") }
    val busy = state.busyMac != null
    val error = PolicyRules.validateParental(days, start, end)
    var confirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }

    Text(
        "Fuera de la franja el dispositivo queda bloqueado. Pausar lo bloquea ahora mismo, " +
            "aunque el horario esté apagado.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Horario activo", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = enabled, onCheckedChange = { enabled = it })
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Pausar ahora (bloquea)",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Switch(checked = paused, onCheckedChange = { paused = it })
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PolicyRules.WEEK_DAYS.forEach { (value, label) ->
            FilterChip(
                selected = value in days,
                onClick = {
                    days = if (value in days) days - value else days + value
                },
                label = { Text(label) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = start,
            onValueChange = { start = it.take(5) },
            label = { Text("Desde") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it.take(5) },
            label = { Text("Hasta") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    if (error != null) {
        Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { confirm = true },
            enabled = !busy && error == null,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text(if (existing == null) "Guardar horario" else "Cambiar horario")
            }
        }
        if (existing != null) {
            TextButton(onClick = { confirmRemove = true }, enabled = !busy) {
                Text("Quitar")
            }
        }
    }
    if (confirm) {
        PolicyConfirm(
            title = "Guardar el horario",
            body = when {
                paused -> "El dispositivo quedará bloqueado ahora mismo hasta que se quite la pausa."
                enabled -> "Bloqueado fuera de $start a $end los días ${PolicyRules.daysLabel(days.sorted())}."
                else -> "El horario queda guardado pero apagado: no bloquea."
            },
            onConfirm = {
                confirm = false
                viewModel.saveParental(
                    ParentalRuleDto(
                        mac = mac,
                        enabled = enabled,
                        days = days.sorted(),
                        start = start,
                        end = end,
                        paused = paused,
                    ),
                )
            },
            onDismiss = { confirm = false },
        )
    }
    if (confirmRemove) {
        PolicyConfirm(
            title = "Quitar el horario",
            body = "El dispositivo dejará de tener bloqueo por horario y se le quitará el actual.",
            onConfirm = {
                confirmRemove = false
                viewModel.removeParental(mac)
            },
            onDismiss = { confirmRemove = false },
        )
    }
}

@Composable
private fun PolicyConfirm(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Aplicar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
