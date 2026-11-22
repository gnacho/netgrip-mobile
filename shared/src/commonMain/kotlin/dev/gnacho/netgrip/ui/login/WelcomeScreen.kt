package dev.gnacho.netgrip.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.discovery.DiscoveredRouter
import dev.gnacho.netgrip.presentation.DiscoveryStatus

@Composable
fun WelcomeScreen(
    status: DiscoveryStatus,
    isDiscoverySupported: Boolean,
    routers: List<DiscoveredRouter>,
    selectedRouter: DiscoveredRouter?,
    onSearch: () -> Unit,
    onSelect: (DiscoveredRouter) -> Unit,
    onConnect: (DiscoveredRouter) -> Unit,
    onManual: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                NetGripMark()
                Column {
                    Text("NetGrip", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "PANEL PARA OPENWRT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Text(
                "Tus routers, al alcance de la mano.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))
            DiscoveryRadar(
                searching = status == DiscoveryStatus.Searching,
                foundCount = routers.size,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                discoveryStatusText(status, routers.size, isDiscoverySupported),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelLarge,
                color = if (status == DiscoveryStatus.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            if (routers.isNotEmpty()) {
                Text(
                    "ENCONTRADOS EN TU RED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                routers.forEach { router ->
                    DiscoveredRouterCard(
                        router = router,
                        selected = router.key == selectedRouter?.key,
                        onClick = { onSelect(router) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = onSearch,
                    enabled = status != DiscoveryStatus.Searching,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Buscar de nuevo")
                }
            } else if (!isDiscoverySupported) {
                Text(
                    "El descubrimiento automático aún no está disponible en iOS. Añade el router a mano.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (status != DiscoveryStatus.Searching) {
                if (status == DiscoveryStatus.Complete) {
                    Text(
                        "No hemos encontrado routers. Comprueba que el móvil y NetGrip están en la misma red.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    Text(if (status == DiscoveryStatus.Idle) "Buscar routers en esta red" else "Volver a buscar")
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onManual,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
            ) {
                Text("Añadir a mano", maxLines = 1)
            }
            Button(
                onClick = { selectedRouter?.let(onConnect) },
                enabled = selectedRouter != null && status != DiscoveryStatus.Searching,
                modifier = Modifier.weight(1.15f).heightIn(min = 52.dp),
            ) {
                Text(if (selectedRouter == null) "Elige un router" else "Conectar", maxLines = 1)
            }
        }
    }
}

@Composable
private fun DiscoveredRouterCard(router: DiscoveredRouter, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = selected
                contentDescription = "${router.name}, ${router.address}, NetGrip ${router.version}, Sin verificar"
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    router.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Sin verificar",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(router.address, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text(
                    "NetGrip ${router.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

private fun discoveryStatusText(status: DiscoveryStatus, count: Int, supported: Boolean): String = when {
    !supported -> "DESCUBRIMIENTO NO DISPONIBLE"
    status == DiscoveryStatus.Idle -> "BÚSQUEDA BAJO DEMANDA"
    status == DiscoveryStatus.Searching -> "BUSCANDO ROUTERS EN TU RED"
    status == DiscoveryStatus.Error -> "NO SE PUDO BUSCAR EN ESTA RED"
    count == 0 -> "BÚSQUEDA TERMINADA · 0 ROUTERS"
    else -> "BÚSQUEDA TERMINADA · $count ROUTERS"
}
