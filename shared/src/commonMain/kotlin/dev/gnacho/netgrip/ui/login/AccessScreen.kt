package dev.gnacho.netgrip.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.core.network.formatSha256Fingerprint
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState

@Composable
fun AccessScreen(target: LoginTarget, viewModel: SessionViewModel, onBack: () -> Unit) {
    var url by remember(target) { mutableStateOf(target.baseUrl) }
    var username by remember(target) { mutableStateOf("root") }
    var password by remember(target) { mutableStateOf("") }
    val loading = viewModel.uiState is UiState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        TextButton(
            onClick = onBack,
            enabled = !loading,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text("Volver a resultados")
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Identifícate en tu router.",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(18.dp))
            target.routerName?.let { name ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border = CardBorder(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "  ${target.baseUrl.removePrefix("http://").removePrefix("https://")}",
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            RouterUrlField(url = url, onValueChange = { url = it }, enabled = !loading)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                enabled = !loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                enabled = !loading,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "La contraseña no se guarda. NetGrip conserva únicamente la cookie de sesión, cifrada en el dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (target.routerName != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Antes de entrar, NetGrip comprueba HTTPS sin enviar tus credenciales.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            LoginErrorState(viewModel.uiState)
            Spacer(Modifier.height(16.dp))
        }
        Button(
            onClick = { viewModel.login(url, username, password) },
            enabled = url.isNotBlank() && password.isNotBlank() && !loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text(if (loading) "Entrando" else "Entrar")
        }
        Spacer(Modifier.height(12.dp))
    }
    CertificateConfirmationDialog(
        state = viewModel.uiState,
        onConfirm = viewModel::confirmCertificate,
        onCancel = viewModel::cancelCertificateConfirmation,
    )
}

@Composable
private fun CertificateConfirmationDialog(
    state: UiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val confirmation = state as? UiState.CertificateConfirmation ?: return
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Confirmar certificado desconocido") },
        text = {
            Column {
                Text("El router ${confirmation.host} usa un certificado que este dispositivo no reconoce.")
                Spacer(Modifier.height(12.dp))
                Text("Huella SHA-256:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatSha256Fingerprint(confirmation.fingerprint),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Confirma solo si has verificado esta huella por otra vía. Un certificado distinto podría pertenecer a un atacante. NetGrip guardará la huella para este host y rechazará cualquier cambio.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Confirmar y entrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun RouterUrlField(url: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value = url,
        onValueChange = onValueChange,
        label = { Text("URL del router") },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

@Composable
private fun LoginErrorState(state: UiState) {
    when (state) {
        UiState.RouterUnreachable -> ErrorText("Router inalcanzable. Revisa la URL y la red.")
        is UiState.RateLimited -> ErrorText("Demasiados intentos. Reintenta en ${state.retryAfterSeconds ?: "?"} s.")
        is UiState.Error -> ErrorText(state.message)
        else -> Unit
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CardBorder() = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
