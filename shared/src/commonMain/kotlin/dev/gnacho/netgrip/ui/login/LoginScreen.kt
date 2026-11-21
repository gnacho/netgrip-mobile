package dev.gnacho.netgrip.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.presentation.SessionViewModel
import dev.gnacho.netgrip.presentation.UiState

@Composable
fun LoginScreen(viewModel: SessionViewModel) {
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("NetGrip", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del router (http:// o https://)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Usuario (vacío = root)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        when (val state = viewModel.uiState) {
            UiState.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            UiState.RouterUnreachable ->
                Text("Router inalcanzable. Revisa la URL y la red.", color = MaterialTheme.colorScheme.error)
            is UiState.RateLimited ->
                Text(
                    "Demasiados intentos. Reintenta en ${state.retryAfterSeconds ?: "?"} s.",
                    color = MaterialTheme.colorScheme.error,
                )
            is UiState.Error ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }

        Button(
            onClick = { viewModel.login(url, username, password) },
            enabled = url.isNotBlank() && password.isNotBlank() && viewModel.uiState !is UiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Conectar")
        }
    }
}
