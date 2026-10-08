package home.brimley.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import home.brimley.model.Playing
import home.brimley.ui.CardTitle
import home.brimley.ui.InkButton
import home.brimley.ui.InkCard
import home.brimley.ui.Pill
import home.brimley.ui.RecordIllustration
import home.brimley.ui.TvIllustration
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.PaperBright
import home.brimley.ui.theme.Rules
import home.brimley.ui.theme.TileShape

@Composable
fun PlayingCard(playing: List<Playing>, onPlaySomething: () -> Unit, modifier: Modifier = Modifier) {
    InkCard(modifier) {
        val where = listOf(
            playing.firstOrNull { it.target == "kitchen" }?.let { "kitchen speaker" } ?: "kitchen quiet",
            playing.firstOrNull { it.target == "tv" }?.let { "TV on" } ?: "TV off",
            playing.firstOrNull { it.target == "yoto" }?.let { "Yoto playing" } ?: "Yoto quiet",
        ).joinToString(" · ")
        CardTitle("Playing", trailing = where)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (playing.size) {
                0 -> NothingPlaying()
                1 -> NowPlaying(playing[0], big = true)
                else -> playing.take(2).forEach { NowPlaying(it, big = false) }
            }
        }
        Spacer(Modifier.height(12.dp))
        InkButton("Play something", onClick = onPlaySomething, modifier = Modifier.fillMaxWidth(), shape = TileShape)
    }
}

@Composable
private fun NothingPlaying() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RecordIllustration(Modifier.size(96.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text("Nothing playing", style = MaterialTheme.typography.titleLarge, color = Ink)
            Text("Pick an album, a show or a story.", style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

@Composable
private fun NowPlaying(p: Playing, big: Boolean) {
    val coverSize = if (big) 150.dp else 84.dp
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(coverSize)
                .clip(RoundedCornerShape(14.dp))
                .background(PaperBright)
                .border(BorderStroke(Rules.thin, Ink), RoundedCornerShape(14.dp)),
        ) {
            if (p.imageUrl != null) {
                AsyncImage(model = p.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(coverSize))
            } else if (p.target == "tv") {
                TvIllustration(Modifier.size(coverSize))
            } else {
                RecordIllustration(Modifier.size(coverSize))
            }
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(p.title, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            Pill(
                when (p.target) { "kitchen" -> "Kitchen speaker"; "tv" -> "Living room TV"; else -> "Yoto" },
                filled = false,
            )
        }
    }
}
