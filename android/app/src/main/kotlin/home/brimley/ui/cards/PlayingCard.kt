package home.brimley.ui.cards

// Kept for now; the home screen uses MusicCard, WatchCard and YotoCard (PlayCards.kt).

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
import home.brimley.ui.YotoIllustration
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
            playing.firstOrNull { it.target == "yoto" }?.let { if (it.isPlaying) "Yoto playing" else "Yoto paused" } ?: "Yoto quiet",
        ).joinToString(" · ")
        Box(Modifier.clickable(enabled = !tvSetUp, onClick = onSetUpTv)) { CardTitle("Playing", trailing = where) }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (playing.size) {
                0 -> NothingPlaying()
                1 -> NowPlaying(playing[0], onControl = onControl)
                else -> playing.take(2).forEach { CompactPlaying(it, onControl = onControl) }
            }
        }
        Spacer(Modifier.height(12.dp))
        InkButton("Play something", onClick = onPlaySomething, modifier = Modifier.fillMaxWidth(), shape = TileShape)
    }
}

@Composable
internal fun Controls(p: Playing, onControl: (String, String) -> Unit) {
    if (p.target == "tv") return TvControls(onControl)
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
            "yoto" -> {
                RoundButton(filled = true, onClick = { onControl("yoto", if (p.isPlaying) "pause" else "resume") }) { if (p.isPlaying) PauseIcon(it) else PlayIcon(it) }
                InkButton("Stop", onClick = { onControl("yoto", "stop") }, filled = false, modifier = Modifier.height(64.dp))
            }
        }
    }
}

// OK is the remote's centre button: it presses Play on the title's page, or
// picks the profile, wherever the TV has landed. ⏮ and ⏭ are the media keys
// (back to the start, next episode) where the app honours them. None of these
// can move around the app's menus. Volume and power stay on the TV's own remote.
@Composable
private fun TvControls(onControl: (String, String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(filled = true, onClick = { onControl("tv", "ok") }) { Label("OK", it) }
        RoundButton(filled = false, onClick = { onControl("tv", "playpause") }) { PlayPauseIcon(it) }
        RoundButton(filled = false, onClick = { onControl("tv", "previous") }) { PrevIcon(it) }
        RoundButton(filled = false, onClick = { onControl("tv", "next") }) { NextIcon(it) }
    }
}

@Composable
internal fun Label(text: String, c: androidx.compose.ui.graphics.Color) = Text(text, style = MaterialTheme.typography.labelLarge, color = c)

@Composable
internal fun PlayPauseIcon(c: androidx.compose.ui.graphics.Color) = Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
    androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
        drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2f); lineTo(0f, size.height); close() }, c)
    }
    PauseIcon(c)
}

@Composable
internal fun RoundButton(filled: Boolean, onClick: () -> Unit, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
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
internal fun PlayIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
    val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.2f, 0f); lineTo(size.width, size.height / 2f); lineTo(size.width * 0.2f, size.height); close() }
    drawPath(p, c)
}

@Composable
internal fun PauseIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(22.dp)) {
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

internal fun whereLabel(p: Playing) = when (p.target) { "kitchen", "podcast" -> "Kitchen speaker"; "tv" -> "Living room TV"; else -> "Yoto" }

@Composable
internal fun Cover(p: Playing, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(PaperBright)
            .border(BorderStroke(Rules.thin, Ink), RoundedCornerShape(14.dp)),
    ) {
        if (p.imageUrl != null) {
            AsyncImage(model = p.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else if (p.target == "tv") {
            TvIllustration(Modifier.size(size))
        } else if (p.target == "yoto") {
            YotoIllustration(Modifier.size(size))
        } else {
            RecordIllustration(Modifier.size(size))
        }
    }
}

// One thing playing: the big cover, the title, and all its controls.
@Composable
private fun NowPlaying(p: Playing, onControl: (String, String) -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Cover(p, 150.dp)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(p.title, style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            Pill(whereLabel(p), filled = false)
            Spacer(Modifier.height(10.dp))
            Controls(p, onControl)
        }
    }
}

// Two things playing: a row each, fixed height so both always fit. The main
// button only (play/pause), plus Stop for the Yoto, which has no speaker of
// its own to turn down from here.
@Composable
private fun CompactPlaying(p: Playing, onControl: (String, String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(p, 72.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(whereLabel(p), style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.target == "tv") {
                RoundButton(filled = true, onClick = { onControl("tv", "ok") }) { Label("OK", it) }
                RoundButton(filled = false, onClick = { onControl("tv", "playpause") }) { PlayPauseIcon(it) }
            } else {
                RoundButton(filled = true, onClick = { onControl(p.target, if (p.isPlaying) "pause" else "resume") }) { if (p.isPlaying) PauseIcon(it) else PlayIcon(it) }
            }
            if (p.target == "yoto") RoundButton(filled = false, onClick = { onControl("yoto", "stop") }) { StopIcon(it) }
        }
    }
}

@Composable
internal fun StopIcon(c: androidx.compose.ui.graphics.Color) = androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
    drawRect(c)
}
