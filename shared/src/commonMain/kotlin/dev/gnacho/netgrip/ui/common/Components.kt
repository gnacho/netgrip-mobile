package dev.gnacho.netgrip.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.ui.theme.MonoStyle
import dev.gnacho.netgrip.ui.theme.netGrip

/** Tonos semanticos del panel. */
enum class Tone { ACCENT, TEAL, SUCCESS, WARN, DANGER, VIOLET, NEUTRAL }

@Composable
fun Tone.container(): Color = when (this) {
    Tone.ACCENT -> MaterialTheme.colorScheme.primaryContainer
    Tone.TEAL -> MaterialTheme.netGrip.tealContainer
    Tone.SUCCESS -> MaterialTheme.netGrip.successContainer
    Tone.WARN -> MaterialTheme.netGrip.warnContainer
    Tone.DANGER -> MaterialTheme.colorScheme.errorContainer
    Tone.VIOLET -> MaterialTheme.netGrip.violetContainer
    Tone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun Tone.content(): Color = when (this) {
    Tone.ACCENT -> MaterialTheme.colorScheme.primary
    Tone.TEAL -> MaterialTheme.netGrip.teal
    Tone.SUCCESS -> MaterialTheme.netGrip.success
    Tone.WARN -> MaterialTheme.netGrip.warn
    Tone.DANGER -> MaterialTheme.colorScheme.error
    Tone.VIOLET -> MaterialTheme.netGrip.violet
    Tone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * Tarjeta del panel: superficie, borde de 1 dp, sombra suave y radio de 16.
 * La cabecera admite icono en baldosa, titulo, subtitulo y acciones.
 */
@Composable
fun NetGripCard(
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: Tone = Tone.ACCENT,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp), clip = false),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            if (eyebrow != null) {
                Text(
                    eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.netGrip.faint,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (title != null || icon != null || trailing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (icon != null) IconTile(icon, iconTone)
                    Column(modifier = Modifier.weight(1f)) {
                        if (title != null) {
                            Text(
                                title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    trailing?.invoke(this)
                }
                Spacer(Modifier.height(14.dp))
            }
            content()
        }
    }
}

/** Compatibilidad con las pantallas que ya usaban SectionCard. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTone: Tone = Tone.ACCENT,
    content: @Composable ColumnScope.() -> Unit,
) {
    NetGripCard(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTone = iconTone,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )
}

/** Baldosa de icono de 36 dp con el color del acento sobre su variante suave. */
@Composable
fun IconTile(
    icon: ImageVector,
    tone: Tone = Tone.ACCENT,
    size: Int = 36,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tone.container()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size((if (size < 36) 16 else 18).dp),
            tint = tone.content(),
        )
    }
}

/** Punto de estado de 8 dp. */
@Composable
fun StatusDot(tone: Tone) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(tone.content()),
    )
}

/** Capsula de estado con punto opcional, como las pills del panel. */
@Composable
fun Pill(
    text: String,
    tone: Tone = Tone.NEUTRAL,
    dot: Boolean = true,
) {
    Surface(shape = CircleShape, color = tone.container(), contentColor = tone.content()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (dot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tone.content()),
                )
            }
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Escala antigua de estados, mapeada a la nueva. */
enum class BadgeTone { NEUTRAL, OK, WARN, DANGER }

fun BadgeTone.toTone(): Tone = when (this) {
    BadgeTone.NEUTRAL -> Tone.NEUTRAL
    BadgeTone.OK -> Tone.SUCCESS
    BadgeTone.WARN -> Tone.WARN
    BadgeTone.DANGER -> Tone.DANGER
}

@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.NEUTRAL) {
    Pill(text, tone.toTone(), dot = tone != BadgeTone.NEUTRAL)
}

/** Contador compacto. */
@Composable
fun CountBadge(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Fila etiqueta / valor. */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    mono: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1.1f),
            style = if (mono) MonoStyle else MaterialTheme.typography.bodyMedium,
            fontWeight = if (mono) FontWeight.Normal else FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
        )
    }
}

/** Barra de ocupacion 0..1. */
@Composable
fun PercentBar(
    fraction: Float,
    tone: BadgeTone = BadgeTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    MeterBar(
        fraction = fraction,
        color = when (tone) {
            BadgeTone.NEUTRAL -> MaterialTheme.colorScheme.primary
            BadgeTone.OK -> MaterialTheme.netGrip.success
            BadgeTone.WARN -> MaterialTheme.netGrip.warn
            BadgeTone.DANGER -> MaterialTheme.colorScheme.error
        },
        modifier = modifier,
    )
}

/** Barra fina de progreso con la pista del panel. */
@Composable
fun MeterBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Int = 6,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(MaterialTheme.netGrip.track, RoundedCornerShape(50)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .background(color, RoundedCornerShape(50)),
        )
    }
}

@Composable
fun CardDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** Campo de busqueda con el estilo hundido del panel web. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                NetGripIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.netGrip.faint,
            )
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.netGrip.faint,
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    NetGripIcons.Close,
                    contentDescription = "Borrar búsqueda",
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable { onValueChange("") },
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Eyebrow de grupo, como los rotulos del menu lateral del panel. */
@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.netGrip.faint,
    )
}

/** Franja de aviso en linea. */
@Composable
fun Banner(
    text: String,
    tone: Tone = Tone.WARN,
    icon: ImageVector? = NetGripIcons.Warning,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tone.container(),
        contentColor = tone.content(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
            )
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Estado vacio con icono, titulo, cuerpo y accion opcional. */
@Composable
fun EmptyStateView(
    title: String,
    body: String? = null,
    icon: ImageVector = NetGripIcons.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.netGrip.faint)
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (body != null) {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Control segmentado con la capsula del panel. */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEach { (value, label) ->
                val isSelected = value == selected
                val background by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    label = "segment",
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(background)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingBox(message: String = "Cargando datos del router") {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(NetGripIcons.CloudOff, contentDescription = null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = onRetry) {
            Icon(NetGripIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Reintentar")
        }
    }
}

@Composable
fun EmptyBox(message: String) {
    EmptyStateView(title = message, modifier = Modifier.fillMaxSize())
}

/** Aviso en linea cuando una seccion no se pudo leer pero hay mas datos. */
@Composable
fun InlineWarning(message: String) {
    Banner(message, Tone.DANGER)
}
