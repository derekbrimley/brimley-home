package home.brimley.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
fun PlayingCard(playing: List<Playing>, onPlaySomething: () -> Unit, onControl: (String) -> Unit, modifier: Modifier = Modifier) {
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
                1 -> NowPlaying(playing[0], big = true, onControl = onControl)
                else -> playing.take(2).forEach { NowPlaying(it, big = false, onControl = onControl) }
            }
        }
        Spacer(Modifier.height(12.dp))
        InkButton("Play something", onClick = onPlaySomething, modifier = Modifier.fillMaxWidth(), shape = TileShape)
    }
}

@Composable
private fun Transport(isPlaying: Boolean, onControl: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TransportButton(filled = false, onClick = { onControl("previous") }) { PrevIcon(it) }
        TransportButton(filled = true, onClick = { onControl(if (isPlaying) "pause" else "resume") }) { if (isPlaying) PauseIcon(it) else PlayIcon(it) }
        TransportButton(filled = false, onClick = { onControl("next") }) { NextIcon(it) }
    }
}

@Composable
private fun TransportButton(filled: Boolean, onClick: () -> Unit, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    Box(
        Modifier
            .size(56.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (filled) Ink else home.brimley.ui.theme.Paper)
            .border(BorderStroke(Rules.thin, Ink), androidx.compose.foundation.shape.CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon(if (filled) home.brimley.ui.theme.Paper else Ink) }
}

@Composable
private fun PlayIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width * 0.2f, size.height); close() }
    drawPath(p, c)
}

@Composable
private fun PauseIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
    drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.1f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
    drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.6f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
}

@Composable
private fun NextIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width * 0.7f, size.height / 2f); lineTo(0f, size.height); close() }
    drawPath(p, c)
    drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.78f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height))
}

@Composable
private fun PrevIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width, 0f); lineTo(size.width * 0.3f, size.height / 2f); lineTo(size.width, size.height); close() }
    drawPath(p, c)
    drawRect(c, topLeft = androidx.compose.ui.geometry.Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width * 0.22f, size.height))
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
private fun NowPlaying(p: Playing, big: Boolean, onControl: (String) -> Unit) {
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
            if (p.target == "kitchen") {
                Spacer(Modifier.height(10.dp))
                Transport(isPlaying = p.isPlaying, onControl = onControl)
            }
        }
    }
}
