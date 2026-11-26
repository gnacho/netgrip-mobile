package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.DpiAppDto
import dev.gnacho.netgrip.data.dto.DpiProtocolDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.domain.traffic.TrafficPoint
import dev.gnacho.netgrip.domain.traffic.TrafficRow
import dev.gnacho.netgrip.domain.traffic.TrafficRules
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.presentation.TrafficState
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.CardDivider
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
 * Trafico: quien y que consume la conexion, segun la contabilidad del router.
 *
 * Los bytes de esta pantalla los conto nlbwmon (por dispositivo y protocolo) o
 * netifyd (por aplicacion). La app no calcula tasas a partir de ellos ni
 * rellena huecos: si el router no mide algo, se dice.
 */
@Composable
fun TrafficScreen(viewModel: ShellViewModel) {
    val state by viewModel.traffic.collectAsState()
    LaunchedEffect(Unit) { viewModel.ensureTraffic() }

    when {
        state.loading && !state.loaded -> LoadingBox("Leyendo la contabilidad del router")
        state.error != null && state.devices.isEmpty() && state.apps.isEmpty() && state.dpi == null ->
            ErrorBox(state.error!!) { viewModel.loadTraffic() }
        else -> {
            val devices = TrafficRules.deviceRows(state.devices, state.names)
            val apps = TrafficRules.appRows(state.apps)
            val protocols = TrafficRules.protocolRows(state.dpi?.protocols)
            val dpiApps = TrafficRules.dpiAppRows(state.dpiApps)
            val points = TrafficRules.points(state.timeline?.buckets.orEmpty())
            val deviceTotal = devices.sumOf { it.total }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { AccountingCard(state, devices.size, apps.size) }
                item { DevicesCard(devices, deviceTotal, state.names.isNotEmpty()) }
                itemsIndexed(devices) { index, row ->
                    DeviceRowCard(
                        row = row,
                        share = Formats.percent(row.total, deviceTotal),
                        last = index == devices.lastIndex,
                    )
                }
                item { ProtocolsCard(apps) }
                item { DpiCard(state, protocols, dpiApps) }
                item { TimelineCard(points, state.timeline?.totals?.total ?: 0) }
                state.error?.let { message -> item { Banner(message, Tone.WARN) } }
            }
        }
    }
}

@Composable
private fun AccountingCard(state: TrafficState, devices: Int, apps: Int) {
    val probe = state.nlbwmon
    val (subtitle, tone) = when {
        probe == null -> "Sin leer" to Tone.NEUTRAL
        !probe.installed -> "No está instalado en este equipo" to Tone.NEUTRAL
        !probe.running -> "Instalado pero parado" to Tone.WARN
        else -> "$devices dispositivos y $apps protocolos contados" to Tone.SUCCESS
    }
    NetGripCard(
        eyebrow = "Contabilidad",
        title = "Lo cuenta el router",
        subtitle = subtitle,
        icon = NetGripIcons.ChartColumn,
        iconTone = tone,
    ) {
        if (probe == null) {
            Text(
                "Sin datos todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!probe.installed) {
            Text(
                "El desglose por dispositivo y por protocolo sale de nlbwmon, que este " +
                    "router no tiene instalado, así que no hay nada que enseñar. Las cifras " +
                    "no se estiman en el teléfono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!probe.running) {
            Banner(
                "nlbwmon está instalado pero parado: no está contando tráfico nuevo.",
                Tone.WARN,
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            listOfNotNull(
                "histórico ${probe.preallocDays} d".takeIf { probe.preallocDays > 0 },
                "guarda cada ${probe.commitInterval} s".takeIf { probe.commitInterval > 0 },
                "${probe.generations} ficheros".takeIf { probe.generations > 0 },
            ).ifEmpty { listOf("sin ajustes que mostrar") }.joinToString(" · "),
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DevicesCard(devices: List<TrafficRow>, total: Long, namesResolved: Boolean) {
    NetGripCard(
        eyebrow = "Por dispositivo",
        title = "Quién ha movido más",
        subtitle = when {
            devices.isEmpty() -> "Sin tráfico contado"
            else -> "${Formats.bytes(total)} en total"
        },
        icon = NetGripIcons.Users,
    ) {
        if (devices.isEmpty()) {
            EmptyStateView(
                title = "Sin datos por dispositivo",
                body = "nlbwmon solo cuenta mientras está funcionando, y los contadores WiFi " +
                    "del punto de acceso solo ven las estaciones asociadas a él. En cuanto " +
                    "pase tráfico aparecerá aquí.",
                icon = NetGripIcons.ChartColumn,
            )
        } else {
            Text(
                "Bytes contados por nlbwmon. La descarga y la subida son desde el punto " +
                    "de vista del dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!namesResolved) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "No se pudieron leer los nombres de los equipos: se muestran por IP o MAC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceRowCard(row: TrafficRow, share: Int, last: Boolean) {
    NetGripCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (row.detail.isNotBlank()) {
                    Text(
                        row.detail,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Pill(text = "$share %", tone = Tone.NEUTRAL)
        }
        Spacer(Modifier.height(8.dp))
        PercentBar(fraction = share / 100f)
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(
                "↓ ${Formats.bytes(row.down)}",
                "↑ ${Formats.bytes(row.up)}",
                "${row.connections} conexiones".takeIf { row.connections > 0 },
            ).joinToString("  "),
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!last) Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ProtocolsCard(apps: List<TrafficRow>) {
    NetGripCard(
        eyebrow = "Por protocolo",
        title = "Qué tipo de tráfico",
        subtitle = if (apps.isEmpty()) "Sin protocolos contados" else "${apps.size} protocolos",
        icon = NetGripIcons.Layers,
    ) {
        if (apps.isEmpty()) {
            Text(
                "nlbwmon nombra el protocolo con su base de datos (HTTPS, QUIC, DNS...). " +
                    "Todavía no hay filas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val total = apps.sumOf { it.total }
            apps.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formats.bytes(row.total),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${Formats.percent(row.total, total)} %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                PercentBar(fraction = Formats.percent(row.total, total) / 100f)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun DpiCard(state: TrafficState, protocols: List<DpiProtocolDto>, apps: List<DpiAppDto>) {
    val dpi = state.dpi
    NetGripCard(
        eyebrow = "Aplicaciones",
        title = "Qué apps usan la conexión",
        subtitle = when {
            dpi == null -> "Sin leer"
            !dpi.applicable -> "netifyd no está activo"
            apps.isEmpty() -> "Sin aplicaciones identificadas"
            else -> "${apps.size} aplicaciones · ${Formats.bytes(dpi.totalBytes)}"
        },
        icon = NetGripIcons.DeviceAppliance,
    ) {
        if (dpi == null) {
            Text(
                "Sin datos todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (!dpi.applicable) {
            Text(
                "Este equipo no tiene netifyd en marcha, así que no hay DPI que leer. " +
                    "nlbwmon sí puede seguir contando por protocolo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        if (protocols.isEmpty() && apps.isEmpty()) {
            Text(
                "netifyd está activo pero no ha identificado flujos: puede que no haya " +
                    "tráfico reenviado o que el panel no pueda leer las conexiones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        apps.forEach { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        app.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        TrafficRules.category(app.category),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        Formats.bytes(app.bytes),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${app.flows} flujos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (apps.isNotEmpty() && protocols.isNotEmpty()) {
            CardDivider()
            Spacer(Modifier.height(8.dp))
        }
        if (protocols.isNotEmpty()) {
            Text(
                "Protocolos vistos por nDPI",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            protocols.take(8).forEach { protocol ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        protocol.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        Formats.bytes(protocol.bytes),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun TimelineCard(points: List<TrafficPoint>, total: Long) {
    NetGripCard(
        eyebrow = "Últimas 24 horas",
        title = "Cómo se reparte",
        subtitle = if (points.isEmpty()) "Sin cubos todavía" else "${Formats.bytes(total)} contados",
        icon = NetGripIcons.Clock,
    ) {
        if (points.isEmpty()) {
            Text(
                "netifyd agrega el tráfico en cubos de 5 minutos y los guarda en memoria: " +
                    "tras reiniciar el panel empieza de cero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@NetGripCard
        }
        val visible = points.takeLast(24)
        val max = visible.maxOf { it.bytes }.coerceAtLeast(1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            visible.forEach { point ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction = (point.bytes.toFloat() / max).coerceIn(0.02f, 1f))
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                visible.first().label,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "cada 5 minutos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                visible.last().label,
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Bytes por cubo, tal como los mide el router. No se convierten en tasas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
