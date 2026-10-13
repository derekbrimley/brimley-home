package home.brimley.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import home.brimley.ui.theme.Ink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A stylus drawing as ink on the paper behind it, cropped to its strokes. Decoded
// on a background thread: the Fridge strip decodes up to a dozen at once, pixel by pixel.
@Composable
fun DrawingImage(png: String?, key: Any, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, key) {
        value = png?.let { withContext(Dispatchers.Default) { decodeDrawing(it) } }
    }
    bitmap?.let { Image(it, contentDescription = "a drawing", contentScale = ContentScale.Fit, modifier = modifier) }
}

private fun decodeDrawing(b64: String): ImageBitmap? = runCatching {
    val bytes = Base64.decode(b64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::inkOnly)
}.getOrNull()

// Drawings are saved as black on white with the whole canvas around them.
// Crop to the strokes and turn white into transparent so the ink sits on the
// card's paper and fills the space, however small it was drawn.
private fun inkOnly(src: Bitmap): ImageBitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    val ink = Ink.toArgb() and 0x00FFFFFF
    var left = w; var top = h; var right = -1; var bottom = -1
    for (y in 0 until h) for (x in 0 until w) {
        val p = px[y * w + x]
        val lum = ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        val alpha = ((255 - lum) * (p ushr 24)) / 255
        px[y * w + x] = (alpha shl 24) or ink
        if (alpha > 40) {
            if (x < left) left = x; if (x > right) right = x
            if (y < top) top = y; if (y > bottom) bottom = y
        }
    }
    if (right < 0) return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
    val margin = 8
    left = (left - margin).coerceAtLeast(0); top = (top - margin).coerceAtLeast(0)
    right = (right + margin).coerceAtMost(w - 1); bottom = (bottom + margin).coerceAtMost(h - 1)
    val out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    return Bitmap.createBitmap(out, left, top, right - left + 1, bottom - top + 1).asImageBitmap()
}
