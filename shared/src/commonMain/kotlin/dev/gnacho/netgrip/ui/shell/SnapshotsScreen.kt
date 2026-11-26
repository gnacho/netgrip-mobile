package dev.gnacho.netgrip.ui.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.data.dto.ConfigDiffDto
import dev.gnacho.netgrip.data.dto.ConfigSnapshotDto
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.presentation.SnapshotsState
import dev.gnacho.netgrip.presentation.ShellViewModel
import dev.gnacho.netgrip.ui.common.Banner
import dev.gnacho.netgrip.ui.common.CardDivider
import dev.gnacho.netgrip.ui.common.EmptyStateView
import dev.gnacho.netgrip.ui.common.ErrorBox
import dev.gnacho.netgrip.ui.common.IconTile
import dev.gnacho.netgrip.ui.common.InfoRow
import dev.gnacho.netgrip.ui.common.LoadingBox
import dev.gnacho.netgrip.ui.common.NetGripCard
import dev.gnacho.netgrip.ui.common.NetGripIcons
import dev.gnacho.netgrip.ui.common.Pill
import dev.gnacho.netgrip.ui.common.Tone
import dev.gnacho.netgrip.ui.theme.MonoStyle

/**
 * Copias de la configuracion UCI del router. Restaurar una copia recarga la
 * red, asi que antes se muestran los ficheros que cambiarian y se confirma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotsScreen(viewModel: ShellViewModel) {
    val state by viewModel.snapshots.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var restore by remember { mutableStateOf<ConfigSnapshotDto?>(null) }
    var delete by remember { mutableStateOf<ConfigSnapshotDto?>(null) }
    LaunchedEffect(Unit) { viewModel.ensureSnapshots() }

    val feedback = state.actionError ?: state.actionMessage
    LaunchedEffect(feedback) {
        if (feedback != null) {
            snackbarHostState.showSnackbar(feedback)
            viewModel.clearSnapshotFeedback()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && !state.loaded -> LoadingBox()
            state.error != null && state.snapshots.isEmpty() ->
                ErrorBox(state.error!!) { viewModel.loadSnapshots() }
            !state.loaded -> LoadingBox()
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    NetGripCard(
                        eyebrow = "Copias",
                        title = "Configuración guardada",
                        subtitle = if (state.snapshots.isEmpty()) {
                            "Aún no hay copias"
                        } else {
                            "${state.snapshots.size} copias en el router"
                        },
                        icon = NetGripIcons.Archive,
                        iconTone = Tone.VIOLET,
                    ) {
                        Text(
                            "Una copia guarda los ficheros UCI del router. Restaurarla los devuelve " +
                                "tal como estaban y recarga la red, así que la conexión puede cortarse un momento.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(10.dp))
                        Button(
                            onClick = viewModel::createSnapshot,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (state.busy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(NetGripIcons.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Guardar una copia ahora")
                            }
                        }
                    }
                }
                if (state.snapshots.isEmpty()) {
                    item {
                        EmptyStateView(
                            title = "Sin copias todavía",
                            body = "Guarda una antes de tocar algo delicado: es la vuelta atrás más rápida.",
                            icon = NetGripIcons.Archive,
                        )
                    }
                }
                items(state.snapshots, key = { it.id }) { snapshot ->
                    SnapshotCard(
                        snapshot = snapshot,
                        busy = state.busy,
                        onDiff = { viewModel.showSnapshotDiff(snapshot.id) },
                        onRestore = { restore = snapshot },
                        onDelete = { delete = snapshot },
                    )
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }

    if (state.diffFor != null || state.diffBusy) {
        ModalBottomSheet(
            onDismissRequest = viewModel::closeSnapshotDiff,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = null,
        ) {
            DiffSheet(
                id = state.diffFor.orEmpty(),
                diff = state.diff,
                busy = state.diffBusy,
                onClose = viewModel::closeSnapshotDiff,
            )
        }
    }

    restore?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { restore = null },
            icon = { IconTile(NetGripIcons.Warning, Tone.DANGER) },
            title = { Text("Restaurar ${snapshot.id}") },
            text = {
                Text(
                    "Se devolverán los ${snapshot.configs} ficheros de configuración de esa copia " +
                        "y se recargará la red. Los cambios hechos después se pierden y la conexión " +
                        "puede cortarse mientras se aplica.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = snapshot.id
                        restore = null
                        viewModel.rollbackSnapshot(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { restore = null }) { Text("Cancelar") } },
        )
    }

    delete?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { delete = null },
            icon = { IconTile(NetGripIcons.Trash, Tone.DANGER) },
            title = { Text("Borrar ${snapshot.id}") },
            text = {
                Text(
                    "La copia se borra del router y no se puede recuperar. La configuración actual no cambia.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = snapshot.id
                        delete = null
                        viewModel.deleteSnapshot(id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SnapshotCard(
    snapshot: ConfigSnapshotDto,
    busy: Boolean,
    onDiff: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    NetGripCard(
        eyebrow = "Copia",
        title = snapshot.id,
        subtitle = Formats.clock(snapshot.timestamp).ifBlank { "Sin fecha" },
        icon = NetGripIcons.Archive,
        iconTone = Tone.NEUTRAL,
    ) {
        Pill("${snapshot.configs} ficheros", Tone.NEUTRAL, dot = false)
        Spacer(Modifier.size(10.dp))
        OutlinedButton(
            onClick = onDiff,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Ver qué cambiaría") }
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onRestore,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Restaurar") }
            OutlinedButton(
                onClick = onDelete,
                enabled = !busy,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Borrar") }
        }
    }
}

/** Diferencias entre la copia elegida y lo que hay ahora, fichero a fichero. */
@Composable
private fun DiffSheet(
    id: String,
    diff: List<ConfigDiffDto>?,
    busy: Boolean,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 2000.dp)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Cambios al restaurar $id", style = MaterialTheme.typography.headlineSmall)
        when {
            busy -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Comparando con la configuración actual",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            diff == null -> Text(
                "No se pudo comparar.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            diff.isEmpty() -> Banner(
                "La copia es igual a la configuración actual: restaurarla no cambiaría nada.",
                Tone.SUCCESS,
            )
            else -> {
                Text(
                    if (diff.size == 1) {
                        "1 fichero cambia. Arriba lo que hay ahora, abajo lo que quedaría."
                    } else {
                        "${diff.size} ficheros cambian. Arriba lo que hay ahora, abajo lo que quedaría."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                diff.forEach { entry -> DiffBlock(entry) }
            }
        }
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Cerrar") }
    }
}

@Composable
private fun DiffBlock(entry: ConfigDiffDto) {
    NetGripCard(modifier = Modifier.fillMaxWidth(), title = entry.config, icon = NetGripIcons.Archive) {
        Text(
            "Ahora",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
        SelectionContainer {
            Text(
                entry.after.ifBlank { "(vacío)" },
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.size(8.dp))
        CardDivider()
        Spacer(Modifier.size(8.dp))
        Text(
            "Con la copia",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(2.dp))
        SelectionContainer {
            Text(
                entry.before.ifBlank { "(vacío)" },
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
