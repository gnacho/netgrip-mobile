package dev.gnacho.netgrip.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gnacho.netgrip.domain.format.Formats
import dev.gnacho.netgrip.ui.theme.netGrip

/**
 * Grafica de area de dos series, con la misma geometria que el panel web:
 * rejilla al 25, 50, 75 y 100 por ciento, curva suavizada por puntos medios,
 * relleno al 12 por ciento y una guia vertical al tocar.
 */
@Composable
fun TrafficChart(
    rx: List<Double>,
    tx: List<Double>,
    times: List<Long>,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    rxLabel: String = "Entrada",
    txLabel: String = "Salida",
) {
    val count = minOf(rx.size, tx.size, times.size)
    if (count < 2) {
        EmptyStateView(
            title = "Recogiendo datos",
            body = "El router guarda una muestra cada 5 minutos. La grafica aparece con dos muestras.",
            icon = NetGripIcons.Activity,
            modifier = modifier.height(height),
        )
        return
    }
    var selected by remember(count) { mutableStateOf<Int?>(null) }
    val palette = MaterialTheme.netGrip
    val maxValue = maxOf(rx.maxOrNull() ?: 0.0, tx.maxOrNull() ?: 0.0).coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendDot(rxLabel, palette.chartRx)
            LegendDot(txLabel, palette.chartTx)
            val index = selected?.coerceIn(0, count - 1)
            if (index != null) {
                Text(
                    "↓ ${Formats.rate(rx[index])} · ↑ ${Formats.rate(tx[index])}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(count) {
                    detectTapGestures { offset ->
                        selected = indexFor(offset.x, size.width.toFloat(), count)
                    }
                }
                .pointerInput(count) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            selected = indexFor(offset.x, size.width.toFloat(), count)
                        },
                        onDrag = { change, _ ->
                            selected = indexFor(change.position.x, size.width.toFloat(), count)
                        },
                    )
                },
        ) {
            drawSeries(rx, tx, maxValue, palette.chartGrid, palette.chartRx, palette.chartTx, selected)
        }
        val labels = listOf(Formats.clock(times.first()), Formats.clock(times[count / 2]), Formats.clock(times[count - 1]))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.faint,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, CircleShape),
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun DrawScope.drawSeries(
    rx: List<Double>,
    tx: List<Double>,
    maxValue: Double,
    grid: Color,
    rxColor: Color,
    txColor: Color,
    selected: Int?,
) {
    val padX = 4.dp.toPx()
    val padTop = 8.dp.toPx()
    val padBottom = 2.dp.toPx()
    val plotLeft = padX
    val plotRight = size.width - padX
    val plotTop = padTop
    val plotBottom = size.height - padBottom
    val plotHeight = plotBottom - plotTop

    // Rejilla al 25, 50, 75 y 100 por ciento.
    for (step in 1..4) {
        val y = plotBottom - plotHeight * (step / 4f)
        drawLine(
            color = grid,
            start = Offset(plotLeft, y),
            end = Offset(plotRight, y),
            strokeWidth = 1.dp.toPx(),
        )
    }

    fun xAt(index: Int, total: Int): Float =
        if (total <= 1) plotLeft else plotLeft + (plotRight - plotLeft) * (index / (total - 1).toFloat())

    fun yAt(value: Double): Float =
        plotBottom - (value / maxValue).coerceIn(0.0, 1.0).toFloat() * plotHeight

    fun areaPath(values: List<Double>, color: Color) {
        val path = Path()
        val fill = Path()
        values.forEachIndexed { index, value ->
            val x = xAt(index, values.size)
            val y = yAt(value)
            if (index == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, plotBottom)
                fill.lineTo(x, y)
            } else {
                val prevX = xAt(index - 1, values.size)
                val prevY = yAt(values[index - 1])
                val midX = (prevX + x) / 2f
                path.cubicTo(midX, prevY, midX, y, x, y)
                fill.cubicTo(midX, prevY, midX, y, x, y)
            }
        }
        fill.lineTo(xAt(values.size - 1, values.size), plotBottom)
        fill.close()
        drawPath(fill, color = color.copy(alpha = 0.12f))
        drawPath(path, color = color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }

    areaPath(tx, txColor)
    areaPath(rx, rxColor)

    if (selected != null) {
        val total = minOf(rx.size, tx.size)
        val index = selected.coerceIn(0, total - 1)
        val x = xAt(index, total)
        drawLine(
            color = grid,
            start = Offset(x, plotTop),
            end = Offset(x, plotBottom),
            strokeWidth = 1.5.dp.toPx(),
        )
        listOf(rx to rxColor, tx to txColor).forEach { (values, color) ->
            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, yAt(values[index])))
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = Offset(x, yAt(values[index])))
        }
    }
}

private fun indexFor(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    val fraction = (x / width).coerceIn(0f, 1f)
    return (fraction * (count - 1)).toInt().coerceIn(0, count - 1)
}

/** Anillo de progreso con la pista del panel, al estilo del gauge del panel web. */
@Composable
fun GaugeRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    strokeWidth: Dp = 10.dp,
    trackColor: Color = MaterialTheme.netGrip.track,
    content: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

/** Linea de tendencia sin ejes, para filas compactas. */
@Composable
fun Sparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 28.dp,
) {
    if (values.size < 2) return
    Canvas(modifier = modifier.height(height)) {
        val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * (index / (values.size - 1).toFloat())
            val y = size.height - (value / maxValue).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else {
                val prevX = size.width * ((index - 1) / (values.size - 1).toFloat())
                val prevY = size.height - (values[index - 1] / maxValue).toFloat() * size.height
                val midX = (prevX + x) / 2f
                path.cubicTo(midX, prevY, midX, y, x, y)
            }
        }
        drawPath(path, color = color, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}
