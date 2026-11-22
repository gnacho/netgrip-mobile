package dev.gnacho.netgrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LoginBackground = Color(0xFF0A0B0D)

private val LoginDarkColors = darkColorScheme(
    primary = Color(0xFFFF9E40),
    onPrimary = Color(0xFF0A0B0D),
    primaryContainer = Color(0xFF2A2118),
    onPrimaryContainer = Color(0xFFFF9E40),
    secondary = Color(0xFF5FA8C7),
    onSecondary = Color(0xFF0A0B0D),
    background = LoginBackground,
    onBackground = Color(0xFFFBFBFB),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFFBFBFB),
    surfaceVariant = Color(0xFF17191D),
    onSurfaceVariant = Color(0xFFB8BCC4),
    outline = Color(0xFF69707A),
    outlineVariant = Color(0xFF343A44),
    error = Color(0xFFE98270),
    onError = Color(0xFF0A0B0D),
)

@Composable
fun NetGripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

@Composable
fun NetGripLoginTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LoginDarkColors, content = content)
}
