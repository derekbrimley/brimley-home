package home.brimley.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import home.brimley.BuildConfig
import home.brimley.data.TodayRepository
import home.brimley.model.CatalogItem
import home.brimley.model.ShowEpisode
import home.brimley.model.ShowEpisodes
import home.brimley.model.ShowSeason
import home.brimley.tv.EpisodeRef
import home.brimley.tv.Episodes
import home.brimley.tv.LastEpisodeStore
import home.brimley.tv.StartedTitle
import home.brimley.tv.TvController
import home.brimley.tv.TvNotPaired
import home.brimley.tv.TvState
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.PaperBright
import home.brimley.ui.theme.PillShape
import home.brimley.ui.theme.Rules
import kotlinx.coroutines.launch

// A show's own page: "Next up", the seasons and a tile per episode. Each tile
// is a link straight to that episode, so the TV plays it instead of stopping on
// the show's page. A show whose service has no per-episode links gets one big
// Play for the show's link instead; debug builds say why.
@Composable
fun ShowScreen(
    item: CatalogItem,
    repository: TodayRepository,
    tv: TvController,
    lastEpisodes: LastEpisodeStore,
    onBack: () -> Unit,
    onStarted: () -> Unit,
    onSetUpTv: () -> Unit,
) {
    var episodes by remember(item.id) { mutableStateOf<ShowEpisodes?>(null) }
    var last by remember(item.id) { mutableStateOf(lastEpisodes.get(item.id)) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) {
        episodes = repository.episodes(item.id).getOrElse { ShowEpisodes(note = it.message ?: "Couldn't find the episodes") }
    }

    fun start(link: String, started: StartedTitle, ref: EpisodeRef?) {
        if (busy) return
        if (tv.state.value == TvState.NotPaired) { onSetUpTv(); return }
        busy = true
        status = "Starting on the TV…"
        scope.launch {
            tv.play(link, started)
                .onSuccess {
                    ref?.let { lastEpisodes.set(item.id, it); last = it }
                    onStarted()
                }
                .onFailure { status = if (it is TvNotPaired) "The TV isn't set up yet" else it.message ?: "Couldn't reach the TV" }
            busy = false
        }
    }
    val playShow = { start(item.link!!, StartedTitle(item.title, item.serviceLabel, item.posterUrl), ref = null) }
    val playEpisode = { s: ShowSeason, e: ShowEpisode ->
        start(e.link, StartedTitle("${item.title} · S${s.number} E${e.number}", item.serviceLabel, e.stillUrl ?: item.posterUrl), ref = EpisodeRef(s.number, e.number))
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(120.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton("← Back", onClick = onBack, filled = false, onPaper = false)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineLarge, color = Paper, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status ?: "${item.serviceLabel} · Plays on the living room TV", style = MaterialTheme.typography.bodyMedium, color = Paper)
            }
        }

        Row(Modifier.fillMaxSize().padding(30.dp)) {
            Column(Modifier.width(240.dp)) {
                val shape = RoundedCornerShape(18.dp)
                Box(
                    Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).background(PaperBright).border(BorderStroke(Rules.card, Ink), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.posterUrl != null) {
                        AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        TvIllustration(Modifier.fillMaxSize().padding(40.dp))
                    }
                }
                if (episodes?.seasons?.isEmpty() == true) {
                    Spacer(Modifier.height(20.dp))
                    InkButton("Play ▶", onClick = playShow, modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp))
                }
            }
            Spacer(Modifier.width(30.dp))
            Column(Modifier.weight(1f)) {
                val e = episodes
                when {
                    e == null -> Text("Finding the episodes…", style = MaterialTheme.typography.titleLarge, color = Ink)
                    e.seasons.isEmpty() -> {
                        Text("Tap Play to start ${item.title} on the TV.", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text("${item.serviceLabel} doesn't let us pick the episode, so it carries on where you left off. Press OK on the Playing card to start it.", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 8.dp))
                        if (BuildConfig.DEBUG) e.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, modifier = Modifier.padding(top = 18.dp)) }
                    }
                    else -> EpisodePicker(e.seasons, last, onPlay = playEpisode)
                }
            }
        }
    }
}

@Composable
private fun EpisodePicker(seasons: List<ShowSeason>, last: EpisodeRef?, onPlay: (ShowSeason, ShowEpisode) -> Unit) {
    val next = remember(seasons, last) { Episodes.next(seasons, last) }
    var shown by remember(seasons) { mutableStateOf(Episodes.openingSeason(seasons, last)?.number ?: seasons.first().number) }
    val season = seasons.firstOrNull { it.number == shown } ?: seasons.first()
    Column(Modifier.fillMaxSize()) {
        next?.let { up ->
            NextUpRow(up.season, up.episode, onClick = { onPlay(up.season, up.episode) })
            Spacer(Modifier.height(18.dp))
        }
        if (seasons.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(seasons, key = { it.number }) { s ->
                    val on = s.number == season.number
                    Box(
                        Modifier
                            .heightIn(min = 64.dp)
                            .clip(PillShape)
                            .background(if (on) Ink else Paper)
                            .border(BorderStroke(Rules.thin, Ink), PillShape)
                            .clickable { shown = s.number }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("Season ${s.number}", style = MaterialTheme.typography.labelLarge, color = if (on) Paper else Ink) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(season.episodes, key = { "${season.number}-${it.number}" }) { ep ->
                val wasLast = last != null && last.season == season.number && last.episode == ep.number
                EpisodeTile(ep, wasLast, onClick = { onPlay(season, ep) })
            }
        }
    }
}

// The big "carry on" button: the next episode's picture, number and name.
@Composable
private fun NextUpRow(season: ShowSeason, ep: ShowEpisode, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodePicture(ep, Modifier.width(200.dp), ink = Paper)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text("Next up · Season ${season.number}, episode ${ep.number}", style = MaterialTheme.typography.labelLarge, color = Paper)
            Text(ep.title, style = MaterialTheme.typography.titleLarge, color = Paper, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(14.dp))
        Text("Play ▶", style = MaterialTheme.typography.titleLarge, color = Paper)
    }
}

@Composable
private fun EpisodeTile(ep: ShowEpisode, wasLast: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            EpisodePicture(ep, Modifier.fillMaxWidth())
            if (wasLast) Pill("LAST TIME", filled = true, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text("Episode ${ep.number}", style = MaterialTheme.typography.labelLarge, color = Ink)
        Text(ep.title, style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// TMDB's still, or the episode number large when there isn't one.
@Composable
private fun EpisodePicture(ep: ShowEpisode, modifier: Modifier, ink: Color = Ink) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(PaperBright)
            .border(BorderStroke(4.dp, ink), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (ep.stillUrl != null) {
            AsyncImage(model = ep.stillUrl, contentDescription = ep.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text("${ep.number}", style = MaterialTheme.typography.displayLarge, color = Ink)
        }
    }
}
