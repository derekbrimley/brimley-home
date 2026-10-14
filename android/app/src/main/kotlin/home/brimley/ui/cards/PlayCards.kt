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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import home.brimley.model.MusicItem
import home.brimley.model.MusicShelf
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

// The home screen's three play cards, one per place things play: Music (the
// kitchen speaker, middle column), Watch (the TV) and Yoto (right column).

// One crate, laid out as covers you can start with a tap. Whatever is playing
// in the kitchen (an album, a podcast, a story played here) sits on top with
// its controls.
@Composable
fun MusicCard(
    crate: MusicShelf?,
    crateError: String?,
    status: String?,
    playing: List<Playing>,
    onPlay: (MusicItem) -> Unit,
    onControl: (target: String, action: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = playing.firstOrNull { it.target == "kitchen" || it.target == "podcast" }
    InkCard(modifier) {
        CardTitle(crate?.name?.takeIf { it.isNotBlank() } ?: "Music", trailing = if (now == null) "kitchen quiet" else "kitchen speaker")
        Spacer(Modifier.height(12.dp))
        if (now != null) {
            Row(verticalAlignment = Alignment.Top) {
                Cover(now, 104.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(now.title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    now.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Controls(now, onControl)
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(thickness = Rules.thin, color = Ink)
            Spacer(Modifier.height(10.dp))
        }
        (status ?: crateError)?.let {
            Text(it, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 10.dp))
        }
        val items = crate?.items.orEmpty()
        when {
            crate == null && crateError == null -> Quiet("Looking in the crate…")
            crate != null && items.isEmpty() -> Quiet("This crate is empty. Add albums in Crate and they show up here.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    val isOn = now?.target == "kitchen" && now.title.equals(item.title, ignoreCase = true)
                    AlbumTile(item, isOn, onClick = { onPlay(item) })
                }
            }
        }
    }
}

// Albums start on a tap. No confirmation: a wrong album in the kitchen costs
// nothing, and the point is that a kid can do it in one go.
@Composable
private fun AlbumTile(item: MusicItem, isOn: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(PaperBright)
                .border(BorderStroke(if (isOn) 8.dp else 4.dp, Ink), shape),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(model = item.imageUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                RecordIllustration(Modifier.fillMaxSize().padding(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (isOn) Pill("Playing", filled = true)
        else Text(item.creator, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun WatchCard(
    playing: List<Playing>,
    tvSetUp: Boolean,
    onPick: () -> Unit,
    onControl: (target: String, action: String) -> Unit,
    onSetUpTv: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = playing.firstOrNull { it.target == "tv" }
    InkCard(modifier) {
        val where = when { !tvSetUp -> "TV not set up"; now != null -> "TV on"; else -> "TV off" }
        Box(Modifier.clickable(enabled = !tvSetUp, onClick = onSetUpTv)) { CardTitle("Watch", trailing = where) }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f)) {
            if (now != null) {
                NowRow(now)
                Spacer(Modifier.height(10.dp))
                Controls(now, onControl)
            } else {
                Idle("Nothing on", "Pick a show or a movie.") { TvIllustration(it) }
            }
        }
        if (tvSetUp) InkButton("Pick a show", onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = TileShape)
        else InkButton("Set up the TV", onClick = onSetUpTv, modifier = Modifier.fillMaxWidth(), shape = TileShape)
    }
}

@Composable
fun YotoCard(
    playing: List<Playing>,
    onPick: () -> Unit,
    onControl: (target: String, action: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = playing.firstOrNull { it.target == "yoto" }
    InkCard(modifier) {
        CardTitle("Yoto", trailing = now?.let { if (it.isPlaying) "playing" else "paused" } ?: "quiet")
        Spacer(Modifier.height(12.dp))
        Column(Modifier.weight(1f)) {
            if (now != null) {
                NowRow(now)
                Spacer(Modifier.height(10.dp))
                Controls(now, onControl)
            } else {
                Idle("Yoto's quiet", "Pick a story or some songs.") { YotoIllustration(it) }
            }
        }
        InkButton("Pick a story", onClick = onPick, modifier = Modifier.fillMaxWidth(), shape = TileShape)
    }
}

@Composable
private fun NowRow(p: Playing) {
    Row(verticalAlignment = Alignment.Top) {
        Cover(p, 96.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(p.title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            p.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Idle(title: String, line: String, picture: @Composable (Modifier) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        picture(Modifier.size(88.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink)
            Text(line, style = MaterialTheme.typography.bodyMedium, color = Ink)
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}
