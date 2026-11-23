package dev.gnacho.netgrip.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Tokens del panel web de NetGrip (app/src/index.css, design-rev2). La app
 * reproduce la misma paleta, tipografia y radios para que el paso del panel a
 * la app no cambie de lenguaje visual.
 */

private object Light {
    val bg = Color(0xFFF6F7F9)
    val surface = Color(0xFFFFFFFF)
    val fill = Color(0xFFEFF2F6)
    val border = Color(0xFFE4E7ED)
    val borderStrong = Color(0xFFD3D8E0)
    val text = Color(0xFF1B1F27)
    val muted = Color(0xFF5D6675)
    val faint = Color(0xFF8A93A3)
    val accent = Color(0xFF2E6BE6)
    val accentHover = Color(0xFF1F56C9)
    val accentSoft = Color(0xFFE8EFFC)
    val onAccent = Color(0xFFFFFFFF)
    val teal = Color(0xFF0D9488)
    val tealSoft = Color(0xFFE0F4F1)
    val success = Color(0xFF1F9D55)
    val successSoft = Color(0xFFE3F4EA)
    val warn = Color(0xFFC77414)
    val warnSoft = Color(0xFFFBF0DD)
    val danger = Color(0xFFD64545)
    val dangerSoft = Color(0xFFFBE7E7)
    val violet = Color(0xFF7C6FCE)
    val violetSoft = Color(0xFFEFEDFB)
}

private object Dark {
    val bg = Color(0xFF0E1013)
    val surface = Color(0xFF16191E)
    val fill = Color(0xFF1E2229)
    val border = Color(0xFF262B33)
    val borderStrong = Color(0xFF333A45)
    val text = Color(0xFFE9ECF1)
    val muted = Color(0xFF98A1AF)
    val faint = Color(0xFF6B7482)
    val accent = Color(0xFF5B8DEF)
    val accentHover = Color(0xFF7AA3F2)
    val accentSoft = Color(0xFF1C2A45)
    val onAccent = Color(0xFF0F1522)
    val teal = Color(0xFF34C9B5)
    val tealSoft = Color(0xFF12332F)
    val success = Color(0xFF3FB97F)
    val successSoft = Color(0xFF14301F)
    val warn = Color(0xFFE5A44B)
    val warnSoft = Color(0xFF3A2A12)
    val danger = Color(0xFFE46868)
    val dangerSoft = Color(0xFF3A1B1B)
    val violet = Color(0xFF9A8FF0)
    val violetSoft = Color(0xFF26223D)
}

/**
 * Colores que Material 3 no tiene y el panel usa: exito, aviso, trazo tenue,
 * series de grafica y la pista de los controles segmentados.
 */
@Immutable
data class NetGripPalette(
    val success: Color,
    val successContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val faint: Color,
    val teal: Color,
    val tealContainer: Color,
    val violet: Color,
    val violetContainer: Color,
    val chartRx: Color,
    val chartTx: Color,
    val chartGrid: Color,
    val track: Color,
    val dangerContainer: Color,
    val isDark: Boolean,
)

private val LightPalette = NetGripPalette(
    success = Light.success,
    successContainer = Light.successSoft,
    warn = Light.warn,
    warnContainer = Light.warnSoft,
    faint = Light.faint,
    teal = Light.teal,
    tealContainer = Light.tealSoft,
    violet = Light.violet,
    violetContainer = Light.violetSoft,
    chartRx = Light.success,
    chartTx = Light.accent,
    chartGrid = Light.border,
    track = Light.fill,
    dangerContainer = Light.dangerSoft,
    isDark = false,
)

private val DarkPalette = NetGripPalette(
    success = Dark.success,
    successContainer = Dark.successSoft,
    warn = Dark.warn,
    warnContainer = Dark.warnSoft,
    faint = Dark.faint,
    teal = Dark.teal,
    tealContainer = Dark.tealSoft,
    violet = Dark.violet,
    violetContainer = Dark.violetSoft,
    chartRx = Dark.success,
    chartTx = Dark.accent,
    chartGrid = Dark.border,
    track = Dark.fill,
    dangerContainer = Dark.dangerSoft,
    isDark = true,
)

val LocalNetGripPalette = staticCompositionLocalOf { LightPalette }

private val LightColors = lightColorScheme(
    primary = Light.accent,
    onPrimary = Light.onAccent,
    primaryContainer = Light.accentSoft,
    onPrimaryContainer = Light.accentHover,
    secondary = Light.teal,
    onSecondary = Color.White,
    secondaryContainer = Light.tealSoft,
    onSecondaryContainer = Light.teal,
    tertiary = Light.violet,
    onTertiary = Color.White,
    tertiaryContainer = Light.violetSoft,
    onTertiaryContainer = Light.violet,
    background = Light.bg,
    onBackground = Light.text,
    surface = Light.surface,
    onSurface = Light.text,
    surfaceVariant = Light.fill,
    onSurfaceVariant = Light.muted,
    surfaceTint = Light.accent,
    inverseSurface = Light.text,
    inverseOnSurface = Light.bg,
    error = Light.danger,
    onError = Color.White,
    errorContainer = Light.dangerSoft,
    onErrorContainer = Light.danger,
    outline = Light.borderStrong,
    outlineVariant = Light.border,
    scrim = Color(0xFF0B0C0F),
    surfaceBright = Light.surface,
    surfaceDim = Light.bg,
    surfaceContainerLowest = Light.surface,
    surfaceContainerLow = Light.surface,
    surfaceContainer = Light.bg,
    surfaceContainerHigh = Light.fill,
    surfaceContainerHighest = Light.fill,
)

private val DarkColors = darkColorScheme(
    primary = Dark.accent,
    onPrimary = Dark.onAccent,
    primaryContainer = Dark.accentSoft,
    onPrimaryContainer = Dark.accentHover,
    secondary = Dark.teal,
    onSecondary = Color(0xFF06211E),
    secondaryContainer = Dark.tealSoft,
    onSecondaryContainer = Dark.teal,
    tertiary = Dark.violet,
    onTertiary = Color(0xFF120E2A),
    tertiaryContainer = Dark.violetSoft,
    onTertiaryContainer = Dark.violet,
    background = Dark.bg,
    onBackground = Dark.text,
    surface = Dark.surface,
    onSurface = Dark.text,
    surfaceVariant = Dark.fill,
    onSurfaceVariant = Dark.muted,
    surfaceTint = Dark.accent,
    inverseSurface = Dark.text,
    inverseOnSurface = Dark.bg,
    error = Dark.danger,
    onError = Color(0xFF2A0C0C),
    errorContainer = Dark.dangerSoft,
    onErrorContainer = Dark.danger,
    outline = Dark.borderStrong,
    outlineVariant = Dark.border,
    scrim = Color(0xFF000000),
    surfaceBright = Dark.fill,
    surfaceDim = Dark.bg,
    surfaceContainerLowest = Dark.bg,
    surfaceContainerLow = Dark.surface,
    surfaceContainer = Dark.surface,
    surfaceContainerHigh = Dark.fill,
    surfaceContainerHighest = Color(0xFF252A32),
)

/** Escala tipografica del panel. Roboto es la misma familia que system-ui en Android. */
private val NetGripTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight(650),
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.01).em,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight(650),
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.06.em,
    ),
)

private val NetGripShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
)

/** Tema de la app con sesion iniciada. */
@Composable
fun NetGripTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalNetGripPalette provides if (darkTheme) DarkPalette else LightPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = NetGripTypography,
            shapes = NetGripShapes,
            content = content,
        )
    }
}

/** Paleta del panel en el punto de uso. */
val MaterialTheme.netGrip: NetGripPalette
    @Composable get() = LocalNetGripPalette.current

/* --- Pantallas de acceso: mantienen su diseno propio, oscuro y calido. --- */

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
fun NetGripLoginTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LoginDarkColors, content = content)
}

/** Estilo numerico tabular para metricas y direcciones. */
val TabularNumberStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontFeatureSettings = "tnum",
)

/** Estilo monoespaciado para direcciones, MAC y nombres de puerto. */
val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
)
