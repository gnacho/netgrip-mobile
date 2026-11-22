package dev.gnacho.netgrip.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntrySheet(onDismiss: () -> Unit, onContinue: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Añadir router a mano", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Escribe la dirección HTTP o HTTPS del router con NetGrip instalado.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = it
                    showError = false
                },
                label = { Text("Dirección") },
                placeholder = { Text("http://192.168.1.1:8080") },
                isError = showError,
                supportingText = {
                    if (showError) Text("Escribe una URL válida, con host y puerto válido si lo incluye.")
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
            Spacer(Modifier.height(16.dp))
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        val normalized = normalizeManualRouterUrl(url)
                        if (normalized == null) showError = true else onContinue(normalized)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("Continuar")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
