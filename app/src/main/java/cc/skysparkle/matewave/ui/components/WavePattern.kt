package cc.skysparkle.matewave.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Matewave signature: thin parallel waves drawn behind the content,
 * fading towards the bottom of the surface.
 */
fun Modifier.wavePattern(color: Color, lines: Int = 7): Modifier = drawWithCache {
    val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
    val amplitude = 9.dp.toPx()
    val wavelength = size.width * 0.9f
    val gap = size.height * 0.55f / lines
    val step = 4.dp.toPx()
    val paths = List(lines) { i ->
        val baseY = gap * (i + 1)
        val phase = i * 0.55f
        Path().apply {
            var x = 0f
            moveTo(x, baseY + amplitude * sin(phase))
            while (x <= size.width) {
                x += step
                lineTo(x, baseY + amplitude * sin(2f * PI.toFloat() * x / wavelength + phase))
            }
        }
    }
    onDrawBehind {
        paths.forEachIndexed { i, path ->
            drawPath(path, color = color.copy(alpha = color.alpha * (1f - i.toFloat() / lines)), style = stroke)
        }
    }
}
