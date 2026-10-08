package home.brimley.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import home.brimley.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// The moment the last job is ticked: about twenty woodcut stars fly in from the
// edges, spin, and settle, then fade. Two seconds, no gray, no color.
@Composable
fun AllDoneBurst(count: Int = 22) {
    val progress = remember { Animatable(0f) }
    val stars = remember {
        val rnd = Random(System.nanoTime())
        List(count) {
            StarSpec(
                angle = rnd.nextFloat() * 360f,
                fromDistance = 0.55f + rnd.nextFloat() * 0.3f,
                toDistance = 0.12f + rnd.nextFloat() * 0.45f,
                size = 24f + rnd.nextFloat() * 44f,
                spin = (rnd.nextFloat() - 0.5f) * 540f,
                delay = rnd.nextFloat() * 0.3f,
            )
        }
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 2200, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        val cx = size.width / 2f; val cy = size.height / 2f
        val reach = maxOf(size.width, size.height)
        val t = progress.value
        stars.forEach { s ->
            val local = ((t - s.delay) / (1f - s.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val eased = 1f - (1f - local) * (1f - local)
            val d = s.fromDistance + (s.toDistance - s.fromDistance) * eased
            val a = Math.toRadians(s.angle.toDouble())
            val x = cx + (reach * d * cos(a)).toFloat()
            val y = cy + (reach * d * sin(a)).toFloat()
            val alpha = if (local > 0.8f) (1f - (local - 0.8f) / 0.2f) else 1f
            rotate(s.spin * eased, pivot = Offset(x, y)) {
                drawPath(starPath(x, y, s.size * (0.6f + 0.4f * eased)), Ink.copy(alpha = alpha))
            }
        }
    }
}

private data class StarSpec(val angle: Float, val fromDistance: Float, val toDistance: Float, val size: Float, val spin: Float, val delay: Float)
