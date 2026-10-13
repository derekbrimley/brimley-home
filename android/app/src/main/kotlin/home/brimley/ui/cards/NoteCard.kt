package home.brimley.ui.cards

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.brimley.model.Card
import home.brimley.ui.CardTitle
import home.brimley.ui.DrawingImage
import home.brimley.ui.InkCard
import home.brimley.ui.PencilIllustration
import home.brimley.ui.theme.CardShape
import home.brimley.ui.theme.Caveat
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.Rules
import kotlinx.serialization.json.jsonPrimitive

// The note owns the card: tap it to open the Fridge, or the round pencil to draw a new one.
@Composable
fun NoteCard(cards: List<Card>, onOpenFridge: () -> Unit, onDrawNote: () -> Unit, modifier: Modifier = Modifier) {
    val note = cards.firstOrNull { it.kind == "drawing" || it.kind == "note" }
    InkCard(modifier.clip(CardShape).clickable(onClick = onOpenFridge)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                CardTitle("Note", trailing = "Fridge →")
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
                    when {
                        note == null -> Text(
                            "Draw something for the family.",
                            style = MaterialTheme.typography.bodyLarge, color = Ink,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        note.kind == "drawing" -> DrawingImage(note.data?.get("png")?.jsonPrimitive?.content, note.id, Modifier.fillMaxSize())
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
