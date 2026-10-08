package home.brimley.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.PaperBright
import home.brimley.ui.theme.TileShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshots.SnapshotStateList
import java.io.ByteArrayOutputStream

private class PenStroke(val points: SnapshotStateList<Offset>, val width: Float)

// The stylus canvas. Black ink on white, two pen sizes, no colors. Saved as a
// small PNG (half resolution) and posted to the backend as the Note card.
@Composable
fun NoteScreen(onCancel: () -> Unit, onSave: suspend (String) -> Boolean, onSaved: () -> Unit) {
    val strokes = remember { mutableStateListOf<PenStroke>() }
    var current by remember { mutableStateOf<PenStroke?>(null) }
    var penWidth by remember { mutableStateOf(10f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(110.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            InkButton("← Never mind", onClick = onCancel, filled = false, onPaper = false)
            Text("Draw a note", style = MaterialTheme.typography.headlineLarge, color = Paper, modifier = Modifier.weight(1f).padding(start = 10.dp))
            InkButton("Thin", onClick = { penWidth = 6f }, filled = penWidth == 6f, onPaper = false)
            InkButton("Thick", onClick = { penWidth = 14f }, filled = penWidth == 14f, onPaper = false)
            InkButton("Clear", onClick = { strokes.clear() }, filled = false, onPaper = false)
            Spacer(Modifier.width(10.dp))
            InkButton(
                if (saving) "Saving…" else if (failed) "Try again" else "Put it on the fridge",
                onClick = {
                    if (saving || strokes.isEmpty() || canvasSize == IntSize.Zero) return@InkButton
                    saving = true; failed = false
                    scope.launch {
                        val png = withContext(Dispatchers.Default) { renderPng(strokes.toList(), canvasSize) }
                        val ok = onSave(png)
                        saving = false
                        if (ok) onSaved() else failed = true
                    }
                },
                onPaper = false,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .clip(TileShape)
                .background(PaperBright)
                .border(BorderStroke(6.dp, Ink), TileShape),
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(penWidth) {
                        detectDragGestures(
                            onDragStart = { p -> current = PenStroke(mutableStateListOf(p), penWidth).also { strokes.add(it) } },
                            onDrag = { change, _ -> current?.points?.add(change.position); change.consume() },
                            onDragEnd = { current = null },
                            onDragCancel = { current = null },
                        )
                    },
            ) {
                strokes.forEach { s ->
                    drawPath(s.toPath(), Ink, style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
            if (strokes.isEmpty()) {
                Text(
                    "Use the pen. It stays on the home screen until someone draws a new one.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
        }
    }
}

private fun PenStroke.toPath(): Path = Path().apply {
    points.firstOrNull()?.let { moveTo(it.x, it.y) }
    if (points.size == 1) lineTo(points[0].x + 0.1f, points[0].y)
    for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
}

private fun renderPng(strokes: List<PenStroke>, size: IntSize): String {
    val scale = 0.5f
    val w = (size.width * scale).toInt().coerceAtLeast(1)
    val h = (size.height * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { s ->
        paint.strokeWidth = s.width * scale
        val p = android.graphics.Path()
        s.points.forEachIndexed { i, pt -> if (i == 0) p.moveTo(pt.x * scale, pt.y * scale) else p.lineTo(pt.x * scale, pt.y * scale) }
        if (s.points.size == 1) p.lineTo(s.points[0].x * scale + 0.1f, s.points[0].y * scale)
        c.drawPath(p, paint)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
