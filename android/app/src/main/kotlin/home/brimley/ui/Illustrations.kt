package home.brimley.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Solid silhouettes with white cut-outs, drawn in a 100x100 box and scaled to
// the canvas. These are the "woodcut" pictures from the mockups.

private fun DrawScope.inBox(block: DrawScope.() -> Unit) {
    val s = min(size.width, size.height) / 100f
    translate((size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { block() }
    }
}

@Composable
fun CatIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper, happy: Boolean = false) {
    Canvas(modifier) {
        inBox {
            val body = Path().apply {
                moveTo(22f, 92f); cubicTo(16f, 70f, 20f, 48f, 32f, 36f)
                lineTo(28f, 16f); lineTo(44f, 28f); cubicTo(48f, 27f, 52f, 27f, 56f, 28f)
                lineTo(72f, 16f); lineTo(68f, 36f); cubicTo(80f, 48f, 84f, 70f, 78f, 92f); close()
            }
            drawPath(body, ink)
            if (happy) {
                val stroke = Stroke(width = 3f)
                drawPath(Path().apply { moveTo(34f, 48f); lineTo(40f, 52f); lineTo(46f, 48f) }, color = paper, style = stroke)
                drawPath(Path().apply { moveTo(54f, 48f); lineTo(60f, 52f); lineTo(66f, 48f) }, color = paper, style = stroke)
                drawPath(Path().apply { moveTo(40f, 62f); quadraticBezierTo(50f, 72f, 60f, 62f) }, color = paper, style = stroke)
            } else {
                drawCircle(paper, radius = 4f, center = Offset(40f, 48f))
                drawCircle(paper, radius = 4f, center = Offset(60f, 48f))
                drawPath(Path().apply { moveTo(44f, 62f); lineTo(56f, 62f); lineTo(50f, 68f); close() }, paper)
            }
        }
    }
}

@Composable
fun PencilIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper) {
    Canvas(modifier) {
        inBox {
            drawPath(Path().apply { moveTo(70f, 10f); lineTo(90f, 30f); lineTo(44f, 76f); lineTo(18f, 82f); lineTo(24f, 56f); close() }, ink)
            drawPath(Path().apply { moveTo(24f, 76f); lineTo(28f, 58f); lineTo(42f, 72f); close() }, paper)
        }
    }
}

@Composable
fun RecordIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper) {
    Canvas(modifier) {
        inBox {
            drawCircle(ink, radius = 46f, center = Offset(50f, 50f))
            drawCircle(paper, radius = 30f, center = Offset(50f, 50f), style = Stroke(width = 2f))
            drawCircle(paper, radius = 14f, center = Offset(50f, 50f))
            drawCircle(ink, radius = 3f, center = Offset(50f, 50f))
        }
    }
}

@Composable
fun TvIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper) {
    Canvas(modifier) {
        inBox {
            // antenna
            drawPath(Path().apply { moveTo(30f, 8f); lineTo(50f, 26f); lineTo(70f, 8f); lineTo(73f, 11f); lineTo(54f, 28f); lineTo(46f, 28f); lineTo(27f, 11f); close() }, ink)
            drawRoundRect(ink, topLeft = Offset(10f, 28f), size = Size(80f, 56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
            drawRect(paper, topLeft = Offset(18f, 36f), size = Size(52f, 40f))
            // halftone screen
            var y = 39f
            while (y < 74f) { var x = 21f; while (x < 68f) { drawCircle(ink, 1.2f, Offset(x, y)); x += 5f }; y += 5f }
            drawCircle(paper, 4f, Offset(82f, 46f)); drawCircle(paper, 4f, Offset(82f, 60f))
        }
    }
}

@Composable
fun BookIllustration(modifier: Modifier = Modifier, ink: Color = Ink, paper: Color = Paper) {
    Canvas(modifier) {
        inBox {
            drawPath(Path().apply { moveTo(12f, 18f); lineTo(42f, 18f); quadraticBezierTo(50f, 18f, 50f, 26f); lineTo(50f, 84f); quadraticBezierTo(50f, 78f, 44f, 78f); lineTo(12f, 78f); close() }, ink)
            drawPath(Path().apply { moveTo(88f, 18f); lineTo(58f, 18f); quadraticBezierTo(50f, 18f, 50f, 26f); lineTo(50f, 84f); quadraticBezierTo(50f, 78f, 56f, 78f); lineTo(88f, 78f); close() }, ink)
            listOf(28f, 36f, 44f).forEachIndexed { i, y ->
                val w = if (i == 2) 12f else 18f
                drawRect(paper, Offset(18f, y), Size(w, 3f)); drawRect(paper, Offset(64f, y), Size(w, 3f))
            }
        }
    }
}

fun starPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val rad = if (i % 2 == 0) r else r * 0.45f
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val x = cx + (rad * cos(a)).toFloat()
        val y = cy + (rad * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

@Composable
fun Star(modifier: Modifier = Modifier, filled: Boolean, ink: Color = Ink) {
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        val p = starPath(size.width / 2f, size.height / 2f, r * 0.95f)
        if (filled) drawPath(p, ink) else drawPath(p, ink, style = Stroke(width = r * 0.18f))
    }
}

@Composable
fun WeatherIllustration(icon: String, modifier: Modifier = Modifier, color: Color = Paper) {
    Canvas(modifier) {
        inBox {
            when (icon) {
                "sun" -> {
                    drawCircle(color, 16f, Offset(50f, 50f))
                    for (i in 0 until 8) {
                        val a = Math.toRadians((i * 45).toDouble())
                        val x1 = 50f + (26f * cos(a)).toFloat(); val y1 = 50f + (26f * sin(a)).toFloat()
                        val x2 = 50f + (38f * cos(a)).toFloat(); val y2 = 50f + (38f * sin(a)).toFloat()
                        drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                "rain", "storm" -> {
                    cloud(color)
                    for (x in listOf(34f, 50f, 66f)) drawLine(color, Offset(x, 76f), Offset(x - 6f, 94f), strokeWidth = 5f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                "snow" -> {
                    cloud(color)
                    for (x in listOf(34f, 50f, 66f)) drawCircle(color, 4f, Offset(x, 86f))
                }
                else -> cloud(color)   // cloud, fog
            }
        }
    }
}

private fun DrawScope.cloud(color: Color) {
    drawCircle(color, 18f, Offset(40f, 50f))
    drawCircle(color, 24f, Offset(58f, 44f))
    drawCircle(color, 16f, Offset(74f, 56f))
    drawRect(color, Offset(22f, 50f), Size(68f, 22f))
}
