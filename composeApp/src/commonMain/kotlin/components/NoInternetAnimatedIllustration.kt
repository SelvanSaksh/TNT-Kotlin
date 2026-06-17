package components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val Navy = Color(0xFF163C66)
private val Accent = Color(0xFFDC2626)

@Composable
fun NoInternetAnimatedIllustration(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "noInternet")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
    val pulse = 0.5f + 0.5f * sin(phase * 2.4f)
    val slashOpacity = 0.65f + 0.35f * sin(phase * 3.2f)

    Canvas(modifier = modifier.size(200.dp, 180.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f + 8.dp.toPx())
        val outerRadius = 84.dp.toPx() * (0.92f + pulse * 0.08f)

        drawCircle(
            color = Navy.copy(alpha = 0.08f),
            radius = outerRadius,
            center = center,
        )
        drawCircle(
            color = Navy.copy(alpha = 0.12f),
            radius = 74.dp.toPx(),
            center = center,
            style = Stroke(width = 1.dp.toPx()),
        )

        val wifiColor = Navy.copy(alpha = 0.35f + pulse * 0.15f)
        drawWifiArcs(center = center, color = wifiColor, strokeWidth = 4.dp.toPx(), scale = 1f)

        val slashColor = Accent.copy(alpha = slashOpacity)
        rotate(degrees = -38f, pivot = center) {
            drawLine(
                color = slashColor,
                start = Offset(center.x - 42.dp.toPx(), center.y + 18.dp.toPx()),
                end = Offset(center.x + 42.dp.toPx(), center.y - 34.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        val badgeCenter = Offset(center.x + 52.dp.toPx(), center.y - 48.dp.toPx())
        val badgeScale = 0.9f + pulse * 0.1f
        drawCircle(
            color = Accent,
            radius = 11.dp.toPx() * badgeScale,
            center = badgeCenter,
        )
        drawLine(
            color = Color.White,
            start = Offset(badgeCenter.x, badgeCenter.y - 4.dp.toPx() * badgeScale),
            end = Offset(badgeCenter.x, badgeCenter.y + 1.dp.toPx() * badgeScale),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White,
            radius = 1.5.dp.toPx() * badgeScale,
            center = Offset(badgeCenter.x, badgeCenter.y + 5.dp.toPx() * badgeScale),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWifiArcs(
    center: Offset,
    color: Color,
    strokeWidth: Float,
    scale: Float,
) {
    val radii = listOf(18.dp.toPx(), 30.dp.toPx(), 42.dp.toPx()).map { it * scale }
    radii.forEach { radius ->
        val path = Path().apply {
            addArc(
                oval = Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius,
                ),
                startAngleDegrees = 215f,
                sweepAngleDegrees = 110f,
            )
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
    drawCircle(
        color = color,
        radius = 3.dp.toPx() * scale,
        center = Offset(center.x, center.y + 10.dp.toPx() * scale),
    )
}
