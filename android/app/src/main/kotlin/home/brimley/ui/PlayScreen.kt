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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import home.brimley.BuildConfig
import home.brimley.data.TodayRepository
import androidx.compose.foundation.layout.heightIn
import home.brimley.audio.Episode
import home.brimley.audio.FeedReader
import home.brimley.audio.Podcast
import home.brimley.audio.PodcastFeeds
import home.brimley.audio.PodcastPlayer
import home.brimley.audio.storyQueue
import home.brimley.tv.EpisodeRef
import home.brimley.tv.Episodes
import home.brimley.tv.LastEpisodeStore
import home.brimley.tv.StartedTitle
import home.brimley.tv.TvController
import home.brimley.tv.TvNotPaired
import home.brimley.tv.TvState
import home.brimley.model.CatalogItem
import home.brimley.model.MusicItem
import home.brimley.model.MusicShelf
import home.brimley.model.ShowEpisode
import home.brimley.model.ShowSeason
import home.brimley.model.Stories
import home.brimley.model.StoryCard
import home.brimley.model.YotoPlayer
import home.brimley.model.Today
import kotlinx.coroutines.launch
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.PaperBright
import home.brimley.ui.theme.PillShape
import home.brimley.ui.theme.Rules

enum class PlayTab(val label: String, val where: String) {
    Music("Music", "Music goes to the kitchen speaker"),
    Watch("Watch", "Watch goes to the living room TV"),
    Stories("Stories", "Stories go to the Yoto, or play here"),
}

// One picker, three tabs. Music goes to Spotify on the tablet, Watch to the TV, Listen plays here, Stories to the Yoto or here.
@Composable
fun PlayScreen(
    today: Today?,
    repository: TodayRepository,
    tv: TvController,
    podcasts: PodcastPlayer,
    feeds: PodcastFeeds,
    lastEpisodes: LastEpisodeStore,
    onSetUpTv: () -> Unit,
    onTvDebug: () -> Unit,
    initialTab: PlayTab,
    onHome: () -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var pending by remember { mutableStateOf<CatalogItem?>(null) }
    var tvStatus by remember { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var shelves by remember { mutableStateOf<List<MusicShelf>?>(null) }
    var musicError by remember { mutableStateOf<String?>(null) }
    var musicNotice by remember { mutableStateOf<String?>(null) }
    var stories by remember { mutableStateOf<Stories?>(null) }
    var storiesError by remember { mutableStateOf<String?>(null) }
    var story by remember { mutableStateOf<StoryCard?>(null) }
    var storyStatus by remember { mutableStateOf<String?>(null) }
    var storyBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Start a link on the TV from the Watch sheet; it stays open with the reason if that fails.
    fun startOnTv(link: String, started: StartedTitle, episode: Boolean, onStarted: () -> Unit = {}) {
        if (tv.state.value == TvState.NotPaired) { pending = null; onSetUpTv(); return }
        starting = true
        tvStatus = "Starting on the TV…"
        scope.launch {
            tv.play(link, started, episode)
                .onSuccess { onStarted(); pending = null; onHome() }
                .onFailure { tvStatus = if (it is TvNotPaired) "The TV isn't set up yet" else it.message ?: "Couldn't reach the TV" }
            starting = false
        }
    }

    LaunchedEffect(tab) {
        if (tab == PlayTab.Music && shelves == null) {
            repository.musicShelves().onSuccess { shelves = it }.onFailure { musicError = it.message ?: "Couldn't reach Crate" }
        }
        if (tab == PlayTab.Stories && stories == null) {
            repository.stories().onSuccess { stories = it }.onFailure { storiesError = it.message ?: "Couldn't reach Yoto" }
        }
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(120.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InkButton("← Home", onClick = onHome, filled = false, onPaper = false)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text("Play something", style = MaterialTheme.typography.headlineLarge, color = Paper)
                Text(tab.where, style = MaterialTheme.typography.bodySmall, color = Paper)
            }
            if (BuildConfig.DEBUG && tab == PlayTab.Watch) {
                InkButton("TV test", onClick = onTvDebug, filled = false, onPaper = false)
                Spacer(Modifier.width(14.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PlayTab.entries.forEach { t ->
                    val on = t == tab
                    Box(
                        Modifier
                            .clip(PillShape)
                            .background(if (on) Paper else Ink)
                            .border(BorderStroke(Rules.thin, Paper), PillShape)
                            .clickable { tab = t }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                    ) { Text(t.label, style = MaterialTheme.typography.labelLarge, color = if (on) Ink else Paper) }
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                PlayTab.Watch -> WatchShelves(today?.catalog.orEmpty().filter { !it.link.isNullOrBlank() }, onPick = { pending = it; tvStatus = null })
                PlayTab.Music -> MusicShelves(
                    shelves = shelves,
                    error = musicError,
                    notice = musicNotice,
                    onPick = { item ->
                        musicNotice = "Starting ${item.title}…"
                        scope.launch {
                            repository.playMusic(item.uri)
                                .onSuccess { musicNotice = "${item.title} is playing in the kitchen"; onHome() }
                                .onFailure { musicNotice = null; musicError = it.message ?: "Couldn't start it" }
                        }
                    },
                )
                PlayTab.Stories -> StoryShelf(stories, storiesError, onPick = { story = it; storyStatus = null })
            }
            pending?.let { item ->
                if (item.shelf == "listen") {
                    EpisodeSheet(item, feeds, onCancel = { pending = null }, onPlay = { podcast, episode ->
                        podcasts.play(podcast, episode, item.posterUrl)
                        pending = null
                        onHome()
                    })
                } else {
                    val playShow = { startOnTv(item.link!!, StartedTitle(item.title, item.serviceLabel, item.posterUrl), episode = false) }
                    if (item.shelf == "shows") {
                        ShowSheet(
                            item, repository, last = lastEpisodes.get(item.id), status = tvStatus, busy = starting,
                            onCancel = { pending = null },
                            onPlayShow = playShow,
                            onPlayEpisode = { season, ep ->
                                val started = StartedTitle("${item.title} · S${season.number} E${ep.number}", item.serviceLabel, ep.stillUrl ?: item.posterUrl)
                                startOnTv(ep.link, started, episode = true) { lastEpisodes.set(item.id, EpisodeRef(season.number, ep.number)) }
                            },
                        )
                    } else {
                        ConfirmPlay(item, status = tvStatus, busy = starting, onCancel = { pending = null }, onPlay = playShow)
                    }
                }
            }
            story?.let { card ->
                StorySheet(
                    card, player = stories?.player, status = storyStatus, busy = storyBusy,
                    onCancel = { story = null },
                    onYoto = {
                        storyBusy = true
                        storyStatus = "Starting on the Yoto…"
                        scope.launch {
                            repository.playOnYoto(card.id)
                                .onSuccess { story = null; onHome() }
                                .onFailure { storyStatus = it.message ?: "Couldn't reach the Yoto" }
                            storyBusy = false
                        }
                    },
                    onHere = {
                        storyBusy = true
                        storyStatus = "Getting it ready…"
                        scope.launch {
                            repository.storyTracks(card.id)
                                .onSuccess { tracks ->
                                    val queue = storyQueue(card, tracks)
                                    if (queue == null) storyStatus = "This card can't play here" else { podcasts.playQueue(queue); story = null; onHome() }
                                }
                                .onFailure { storyStatus = it.message ?: "Couldn't reach Yoto" }
                            storyBusy = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MusicShelves(shelves: List<MusicShelf>?, error: String?, notice: String?, onPick: (MusicItem) -> Unit) {
    when {
        shelves == null && error == null -> ComingSoon("Looking in the crates…")
        shelves == null -> ComingSoon(error ?: "")
        shelves.isEmpty() -> ComingSoon("No crates yet. Add albums in Crate and they show up here.")
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp)) {
            (notice ?: error)?.let { line ->
                item(key = "notice") { Text(line, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(bottom = 10.dp)) }
            }
            shelves.forEach { shelf ->
                if (shelf.items.isEmpty()) return@forEach
                item(key = "h-${shelf.id}") {
                    Text(shelf.name, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                }
                item(key = "r-${shelf.id}") {
                    Column {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(shelf.items, key = { it.id }) { Cover(it, onClick = { onPick(it) }) }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(thickness = 5.dp, color = Ink)
                    }
                }
            }
        }
    }
}

// Albums start on a tap. No confirmation: a wrong album in the kitchen costs
// nothing, and the point is that a kid can do it in one go.
@Composable
private fun Cover(item: MusicItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(170.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(PaperBright)
                .border(BorderStroke(4.dp, Ink), shape),
        ) {
            if (item.imageUrl != null) {
                AsyncImage(model = item.imageUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                RecordIllustration(Modifier.fillMaxSize().padding(18.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(item.creator, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val shelfOrder = listOf("shows" to "Shows", "movies" to "Movies", "videos" to "Videos", "listen" to "Listen")

@Composable
private fun WatchShelves(catalog: List<CatalogItem>, onPick: (CatalogItem) -> Unit) {
    val groups = catalog.groupBy { it.shelf }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp)) {
        shelfOrder.forEach { (key, label) ->
            val items = groups[key].orEmpty()
            if (items.isEmpty()) return@forEach
            item(key = "h-$key") {
                Text(label, style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
            }
            item(key = "r-$key") {
                Column {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        items(items, key = { it.id }) { Poster(it, onClick = { onPick(it) }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(thickness = 5.dp, color = Ink)
                }
            }
        }
        if (catalog.isEmpty()) item { ComingSoon("Nothing on the shelves yet. Add titles to the Watch tab of the family sheet.") }
    }
}

@Composable
private fun Poster(item: CatalogItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val portrait = item.shelf == "shows" || item.shelf == "movies"
    Column(Modifier.width(if (portrait) 150.dp else 220.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(if (portrait) 2f / 3f else 16f / 9f)
                .clip(shape)
                .background(PaperBright)
                .border(BorderStroke(4.dp, Ink), shape),
        ) {
            if (item.posterUrl != null) {
                AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(10.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Pill(item.serviceLabel.uppercase(), filled = item.service == "netflix")
    }
}

@Composable
private fun Sheet(onDismiss: () -> Unit, width: androidx.compose.ui.unit.Dp = 520.dp, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.18f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(width)
                .clip(RoundedCornerShape(24.dp))
                .background(Paper)
                .border(BorderStroke(8.dp, Ink), RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}
                .padding(28.dp),
            content = content,
        )
    }
}

@Composable
private fun ConfirmPlay(item: CatalogItem, status: String?, busy: Boolean, onCancel: () -> Unit, onPlay: () -> Unit) {
    Sheet(onCancel) {
        Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        val facts = listOfNotNull(item.serviceLabel, item.runtimeMinutes?.let { "${it / 60}h ${it % 60}m" })
        Text(facts.joinToString(" · ") + " · Play on the living room TV?", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp))
        status?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 14.dp)) }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            InkButton("Not now", onClick = onCancel, filled = false, modifier = Modifier.weight(1f))
            InkButton(if (status != null && !busy) "Try again" else "Play", onClick = { if (!busy) onPlay() }, modifier = Modifier.weight(1f))
        }
    }
}

// A show: pick the episode on the tablet, so the TV starts it rather than
// stopping on the show's page. "Next up" follows the last one started here.
// Shows with no per-episode links (or when the lookup fails) get the old sheet.
@Composable
private fun ShowSheet(
    item: CatalogItem,
    repository: TodayRepository,
    last: EpisodeRef?,
    status: String?,
    busy: Boolean,
    onCancel: () -> Unit,
    onPlayShow: () -> Unit,
    onPlayEpisode: (ShowSeason, ShowEpisode) -> Unit,
) {
    var seasons by remember(item.id) { mutableStateOf<List<ShowSeason>?>(null) }
    LaunchedEffect(item.id) { seasons = repository.episodes(item.id).getOrDefault(emptyList()) }
    val list = seasons
    if (list == null) {
        Sheet(onCancel) {
            Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Finding the episodes…", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 14.dp))
            Spacer(Modifier.height(22.dp))
            InkButton("Not now", onClick = onCancel, filled = false, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    if (list.isEmpty()) {
        ConfirmPlay(item, status = status, busy = busy, onCancel = onCancel, onPlay = onPlayShow)
        return
    }
    val next = remember(list, last) { Episodes.next(list, last) }
    var shown by remember(list) { mutableStateOf(Episodes.openingSeason(list, last)?.number ?: list.first().number) }
    val season = list.firstOrNull { it.number == shown } ?: list.first()
    val play = { s: ShowSeason, e: ShowEpisode -> if (!busy) onPlayEpisode(s, e) }

    Sheet(onCancel, width = 1100.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status ?: "${item.serviceLabel} · Pick an episode for the living room TV", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.width(18.dp))
            InkButton("Not now", onClick = onCancel, filled = false)
        }
        next?.let { up ->
            Spacer(Modifier.height(18.dp))
            NextUpRow(up.season, up.episode, onClick = { play(up.season, up.episode) })
        }
        if (list.size > 1) {
            Spacer(Modifier.height(18.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.number }) { s ->
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
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(season.episodes, key = { "${season.number}-${it.number}" }) { ep ->
                val wasLast = last != null && last.season == season.number && last.episode == ep.number
                EpisodeTile(ep, wasLast, onClick = { play(season, ep) })
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
private fun EpisodePicture(ep: ShowEpisode, modifier: Modifier, ink: androidx.compose.ui.graphics.Color = Ink) {
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

@Composable
private fun EpisodeSheet(item: CatalogItem, feeds: PodcastFeeds, onCancel: () -> Unit, onPlay: (Podcast, Episode) -> Unit) {
    var podcast by remember(item.link) { mutableStateOf<Podcast?>(null) }
    var failed by remember(item.link) { mutableStateOf(false) }
    LaunchedEffect(item.link) { feeds.load(item.link!!).onSuccess { podcast = it }.onFailure { failed = true } }
    Sheet(onCancel, width = 680.dp) {
        Text(item.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        Text("Plays here in the kitchen", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
        val p = podcast
        when {
            failed -> Text("Couldn't load the episodes", style = MaterialTheme.typography.titleMedium, color = Ink)
            p == null -> Text("Finding the newest episodes…", style = MaterialTheme.typography.titleMedium, color = Ink)
            p.episodes.isEmpty() -> Text("No episodes to play", style = MaterialTheme.typography.titleMedium, color = Ink)
            else -> p.episodes.forEach { ep ->
                Column(Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable { onPlay(p, ep) }.padding(vertical = 10.dp)) {
                    Text(ep.title, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val facts = listOfNotNull(FeedReader.formatDate(ep.published), FeedReader.formatDuration(ep.durationSeconds))
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Ink)
                }
                HorizontalDivider(thickness = 3.dp, color = Ink)
            }
        }
        Spacer(Modifier.height(18.dp))
        InkButton("Not now", onClick = onCancel, filled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StoryShelf(stories: Stories?, error: String?, onPick: (StoryCard) -> Unit) {
    when {
        stories == null && error == null -> ComingSoon("Looking for the Yoto cards…")
        stories == null -> ComingSoon(error ?: "")
        stories.cards.isEmpty() -> ComingSoon("No Yoto cards yet. Cards added in the Yoto app show up here.")
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(170.dp),
            modifier = Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            gridItems(stories.cards, key = { it.id }) { StoryCover(it, onClick = { onPick(it) }) }
        }
    }
}

@Composable
private fun StoryCover(card: StoryCard, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(PaperBright)
                .border(BorderStroke(4.dp, Ink), shape),
        ) {
            if (card.imageUrl != null) {
                AsyncImage(model = card.imageUrl, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                BookIllustration(Modifier.fillMaxSize().padding(22.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(card.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        card.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

// Where a story plays. The Yoto tile stays tappable when the player looks
// asleep: the kid may have just pressed its button, and the backend checks.
@Composable
private fun StorySheet(card: StoryCard, player: YotoPlayer?, status: String?, busy: Boolean, onCancel: () -> Unit, onYoto: () -> Unit, onHere: () -> Unit) {
    Sheet(onCancel, width = 640.dp) {
        Text(card.title, style = MaterialTheme.typography.headlineMedium, color = Ink)
        card.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp)) }
        Text(status ?: "Where should it play?", style = MaterialTheme.typography.titleMedium, color = Ink, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val note = when { player == null -> "No Yoto found"; !player.online -> "Asleep: press its button"; else -> player.name }
            WhereTile("On the Yoto", note, enabled = player != null && !busy, onClick = onYoto, modifier = Modifier.weight(1f)) { YotoIllustration(it) }
            WhereTile("Here in the kitchen", "Plays on this speaker", enabled = !busy, onClick = onHere, modifier = Modifier.weight(1f)) { RecordIllustration(it) }
        }
        Spacer(Modifier.height(14.dp))
        InkButton("Not now", onClick = onCancel, filled = false, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WhereTile(label: String, note: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, picture: @Composable (Modifier) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .heightIn(min = 180.dp)
            .clip(shape)
            .border(BorderStroke(Rules.thin, Ink), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        picture(Modifier.size(84.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(note, style = MaterialTheme.typography.bodySmall, color = Ink)
    }
}

@Composable
private fun ComingSoon(text: String) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
    }
}
