package dev.gnacho.netgrip.ui.login

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val Accent = Color(0xFFFF9E40)
private val NetworkBlue = Color(0xFF5FA8C7)
private val Border = Color(0xFF343A44)
private val Surface = Color(0xFF17191D)

@Composable
fun NetGripMark(modifier: Modifier = Modifier) {
    Canvas(modifier.size(52.dp)) {
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = Surface,
            size = size,
            cornerRadius = CornerRadius(14.dp.toPx()),
        )
        drawRoundRect(
            color = Border,
            size = size,
            cornerRadius = CornerRadius(14.dp.toPx()),
            style = Stroke(1.dp.toPx()),
        )
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(NetworkBlue, 2.3.dp.toPx(), Offset(size.width * 0.27f, size.height * 0.33f))
        drawCircle(NetworkBlue, 2.3.dp.toPx(), Offset(size.width * 0.73f, size.height * 0.33f))
        drawLine(NetworkBlue, Offset(size.width * 0.29f, size.height * 0.35f), Offset(size.width * 0.42f, size.height * 0.44f), stroke)
        drawLine(NetworkBlue, Offset(size.width * 0.71f, size.height * 0.35f), Offset(size.width * 0.58f, size.height * 0.44f), stroke)
        drawLine(NetworkBlue, Offset(size.width * 0.31f, size.height * 0.33f), Offset(size.width * 0.69f, size.height * 0.33f), stroke)
        drawArc(
            color = Accent.copy(alpha = 0.45f),
            startAngle = 205f,
            sweepAngle = 130f,
            useCenter = false,
            topLeft = Offset(center.x - 11.dp.toPx(), center.y - 4.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(22.dp.toPx(), 22.dp.toPx()),
            style = Stroke(stroke),
        )
        drawArc(
            color = Accent,
            startAngle = 215f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(center.x - 6.dp.toPx(), center.y + 1.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(Accent, 2.4.dp.toPx(), Offset(center.x, size.height * 0.7f))
    }
}

@Composable
fun DiscoveryRadar(searching: Boolean, foundCount: Int, modifier: Modifier = Modifier) {
    val sweepAngle = if (searching) {
        val transition = rememberInfiniteTransition(label = "discoveryRadar")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(animation = tween(durationMillis = 2200, easing = LinearEasing)),
            label = "radarSweep",
        )
        angle
    } else {
        25f
    }
    val description = when {
        searching -> "Buscando routers NetGrip en la red local"
        else -> "Radar con $foundCount routers encontrados"
    }
    Canvas(modifier.size(152.dp).semantics { contentDescription = description }) {
        val lineWidth = 1.dp.toPx()
        listOf(0.5f, 0.35f, 0.2f, 0.075f).forEach { fraction ->
            drawCircle(Border, radius = size.minDimension * fraction, style = Stroke(lineWidth))
        }
        drawLine(Border.copy(alpha = 0.55f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), lineWidth)
        drawLine(Border.copy(alpha = 0.55f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), lineWidth)
        withTransform({
            rotate(sweepAngle, pivot = center)
        }) {
            drawArc(
                color = Accent.copy(alpha = if (searching) 0.24f else 0.08f),
                startAngle = -75f,
                sweepAngle = 85f,
                useCenter = true,
            )
        }
        drawRoundRect(
            color = Surface,
            topLeft = Offset(center.x - 12.dp.toPx(), center.y - 12.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
        )
        drawRoundRect(
            color = Border,
            topLeft = Offset(center.x - 12.dp.toPx(), center.y - 12.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(24.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(lineWidth),
        )
        drawCircle(Accent, 2.5.dp.toPx(), center)
        val dots = listOf(
            Offset(size.width * 0.25f, size.height * 0.31f),
            Offset(size.width * 0.73f, size.height * 0.25f),
            Offset(size.width * 0.61f, size.height * 0.74f),
        )
        dots.take(foundCount.coerceAtMost(dots.size)).forEach { dot ->
            drawCircle(Accent.copy(alpha = 0.18f), 7.dp.toPx(), dot)
            drawCircle(Accent, 3.5.dp.toPx(), dot)
        }
    }
}
