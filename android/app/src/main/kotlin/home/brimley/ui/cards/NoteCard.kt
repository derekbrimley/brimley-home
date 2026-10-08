package home.brimley.ui.cards

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.brimley.model.Card
import home.brimley.ui.CardTitle
import home.brimley.ui.InkButton
import home.brimley.ui.InkCard
import home.brimley.ui.PencilIllustration
import home.brimley.ui.theme.Caveat
import home.brimley.ui.theme.Ink
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun NoteCard(cards: List<Card>, onDrawNote: () -> Unit, modifier: Modifier = Modifier) {
    val note = cards.firstOrNull { it.kind == "drawing" || it.kind == "note" }
    InkCard(modifier) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                CardTitle("Note")
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    when {
                        note == null -> Text(
                            "Draw something for the family.",
                            style = MaterialTheme.typography.bodyLarge, color = Ink,
                        )
                        note.kind == "drawing" -> DrawingImage(note)
                        else -> Text(
                            note.body ?: "",
                            fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = 54.sp, lineHeight = 56.sp, color = Ink,
                            modifier = Modifier.rotate(-3f).padding(start = 6.dp), maxLines = 3,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    InkButton("Draw a note", onClick = onDrawNote)
                    InkButton("Our makes", onClick = { /* milestone 6 */ }, filled = false)
                }
            }
            PencilIllustration(Modifier.align(Alignment.BottomEnd).size(96.dp).padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun DrawingImage(note: Card) {
    val bitmap = remember(note.id) {
        runCatching {
            val b64 = note.data?.get("png")?.jsonPrimitive?.content ?: return@runCatching null
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = "note", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(end = 90.dp))
    } else {
        Text("(a drawing)", style = MaterialTheme.typography.bodyLarge, color = Ink)
    }
}
