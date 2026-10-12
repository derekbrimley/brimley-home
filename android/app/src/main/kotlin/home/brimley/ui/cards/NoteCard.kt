package home.brimley.ui.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.brimley.model.Card
import home.brimley.ui.CardTitle
import home.brimley.ui.InkCard
import home.brimley.ui.PencilIllustration
import home.brimley.ui.theme.CardShape
import home.brimley.ui.theme.Caveat
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.Rules
import kotlinx.serialization.json.jsonPrimitive

// The note owns the card: tap anywhere (or the round pencil) to draw a new one.
@Composable
fun NoteCard(cards: List<Card>, onDrawNote: () -> Unit, modifier: Modifier = Modifier) {
    val note = cards.firstOrNull { it.kind == "drawing" || it.kind == "note" }
    InkCard(modifier.clip(CardShape).clickable(onClick = onDrawNote)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                CardTitle("Note")
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    when {
                        note == null -> Text(
                            "Draw something for the family.",
                            style = MaterialTheme.typography.bodyLarge, color = Ink,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        note.kind == "drawing" -> DrawingImage(note)
                        else -> Text(
                            note.body ?: "",
                            fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 56.sp, color = Ink,
                            modifier = Modifier.align(Alignment.CenterStart).rotate(-3f).padding(start = 6.dp, end = 80.dp), maxLines = 3,
                        )
                    }
                }
            }
            PencilButton(onDrawNote, Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
private fun PencilButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Ink)
            .border(BorderStroke(Rules.thin, Ink), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PencilIllustration(Modifier.size(40.dp), ink = Paper, paper = Ink)
    }
}

@Composable
private fun DrawingImage(note: Card) {
    val bitmap = remember(note.id) {
        runCatching {
            val b64 = note.data?.get("png")?.jsonPrimitive?.content ?: return@runCatching null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let(::inkOnly)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = "note", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    } else {
        Text("(a drawing)", style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}

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
