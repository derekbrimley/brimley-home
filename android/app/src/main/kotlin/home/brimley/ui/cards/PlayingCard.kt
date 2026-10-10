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
fun PlayingCard(
    playing: List<Playing>,
    tvSetUp: Boolean,
    onPlaySomething: () -> Unit,
    onControl: (target: String, action: String) -> Unit,
    onSetUpTv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    InkCard(modifier) {
        val where = listOf(
            if (playing.any { it.target == "kitchen" || it.target == "podcast" }) "kitchen speaker" else "kitchen quiet",
            when { !tvSetUp -> "TV not set up"; playing.any { it.target == "tv" } -> "TV on"; else -> "TV off" },
            playing.firstOrNull { it.target == "yoto" }?.let { "Yoto playing" } ?: "Yoto quiet",
        ).joinToString(" · ")
        Box(Modifier.clickable(enabled = !tvSetUp, onClick = onSetUpTv)) { CardTitle("Playing", trailing = where) }
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
private fun Controls(p: Playing, onControl: (String, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        when (p.target) {
            "kitchen" -> {
                RoundButton(filled = false, onClick = { onControl("kitchen", "previous") }) { PrevIcon(it) }
                RoundButton(filled = true, onClick = { onControl("kitchen", if (p.isPlaying) "pause" else "resume") }) { if (p.isPlaying) PauseIcon(it) else PlayIcon(it) }
                RoundButton(filled = false, onClick = { onControl("kitchen", "next") }) { NextIcon(it) }
            }
            "podcast" -> {
                RoundButton(filled = false, onClick = { onControl("podcast", "back") }) { Label("−30", it) }
                RoundButton(filled = true, onClick = { onControl("podcast", if (p.isPlaying) "pause" else "resume") }) { if (p.isPlaying) PauseIcon(it) else PlayIcon(it) }
                RoundButton(filled = false, onClick = { onControl("podcast", "forward") }) { Label("+30", it) }
            }
            "tv" -> {
                RoundButton(filled = true, onClick = { onControl("tv", "playpause") }) { PlayPauseIcon(it) }
                RoundButton(filled = false, onClick = { onControl("tv", "voldown") }) { Label("−", it) }
                RoundButton(filled = false, onClick = { onControl("tv", "volup") }) { Label("+", it) }
                InkButton("TV off", onClick = { onControl("tv", "off") }, filled = false, modifier = Modifier.height(64.dp))
            }
        }
    }
}

@Composable
private fun Label(text: String, c: androidx.compose.ui.graphics.Color) = Text(text, style = MaterialTheme.typography.labelLarge, color = c)

@Composable
private fun PlayPauseIcon(c: androidx.compose.ui.graphics.Color) = Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
    androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }, c)
    }
    PauseIcon(c)
}

@Composable
private fun RoundButton(filled: Boolean, onClick: () -> Unit, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    Box(
        Modifier
            .size(64.dp)
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
private fun NowPlaying(p: Playing, big: Boolean, onControl: (String, String) -> Unit) {
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
                when (p.target) { "kitchen", "podcast" -> "Kitchen speaker"; "tv" -> "Living room TV"; else -> "Yoto" },
                filled = false,
            )
            if (p.target != "yoto") {
                Spacer(Modifier.height(10.dp))
                Controls(p, onControl)
            }
        }
    }
}
