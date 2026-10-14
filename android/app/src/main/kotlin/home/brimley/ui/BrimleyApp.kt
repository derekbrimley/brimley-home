package home.brimley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import home.brimley.BuildConfig
import home.brimley.data.TodayRepository
import home.brimley.audio.PodcastFeeds
import home.brimley.audio.PodcastPlayer
import home.brimley.tv.LastEpisodeStore
import home.brimley.tv.TvController
import home.brimley.tv.TvState
import kotlinx.coroutines.launch
import home.brimley.ui.theme.Paper
import java.time.LocalTime

sealed interface Screen {
    data object Home : Screen
    data class Play(val tab: PlayTab) : Screen
    data class Show(val item: home.brimley.model.CatalogItem) : Screen
    data class Note(val back: Screen = Home) : Screen
    data object Fridge : Screen
    data class OpenMake(val make: home.brimley.model.Make) : Screen
    data object TvPair : Screen
    data object TvDebug : Screen
}

@Composable
fun BrimleyApp(repository: TodayRepository, tv: TvController, podcasts: PodcastPlayer, feeds: PodcastFeeds, lastEpisodes: LastEpisodeStore) {
    val state by repository.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val tvState by tv.state.collectAsState()
    val podcast by podcasts.state.collectAsState()
    val scope = rememberCoroutineScope()
    val onControl: (String, String) -> Unit = { target, action ->
        when (target) {
            "kitchen" -> repository.controlMusic(action)
            "yoto" -> repository.controlYoto(action)
            "podcast" -> when (action) { "back" -> podcasts.seekBy(-30_000); "forward" -> podcasts.seekBy(30_000); else -> podcasts.toggle() }
            "tv" -> scope.launch {
                when (action) { "ok" -> tv.ok(); "playpause" -> tv.playPause(); "volup" -> tv.volumeUp(); "voldown" -> tv.volumeDown(); "off" -> tv.powerOff() }
            }
        }
    }

    DisposableEffect(repository) {
        repository.start()
        onDispose { repository.stop() }
    }

    Box(Modifier.fillMaxSize().background(Paper)) {
        when (val s = screen) {
            Screen.Home -> {
                val today = state.today
                if (today == null) {
                    EmptyState(message = state.error ?: "Waking up…")
                } else if (isNight()) {
                    NightClock(today = today)
                } else {
                    HomeScreen(
                        today = today,
                        celebrateKid = state.celebrateKid,
                        error = state.error,
                        onToggleJob = repository::toggleJob,
                        onClaimBounty = repository::claimBounty,
                        onPlaySomething = { screen = Screen.Play(PlayTab.Watch) },
                        onOpenFridge = { screen = Screen.Fridge },
                        onDrawNote = { screen = Screen.Note() },
                        playing = mergePlaying(today.playing, tvState, podcast),
                        tvSetUp = tvState != TvState.NotPaired,
                        onControl = onControl,
                        onSetUpTv = { screen = Screen.TvPair },
                    )
                }
            }
            is Screen.Play -> PlayScreen(
                today = state.today,
                repository = repository,
                tv = tv,
                podcasts = podcasts,
                feeds = feeds,
                onOpenShow = { screen = Screen.Show(it) },
                onSetUpTv = { screen = Screen.TvPair },
                onTvDebug = { screen = Screen.TvDebug },
                initialTab = s.tab,
                onHome = { screen = Screen.Home },
            )
            is Screen.Show -> ShowScreen(
                item = s.item,
                repository = repository,
                tv = tv,
                lastEpisodes = lastEpisodes,
                onBack = { screen = Screen.Play(PlayTab.Watch) },
                onStarted = { screen = Screen.Home },
                onSetUpTv = { screen = Screen.TvPair },
            )
            Screen.TvPair -> PairScreen(tv = tv, onDone = { screen = Screen.Home })
            Screen.TvDebug -> TvDebugScreen(catalog = state.today?.catalog.orEmpty(), tv = tv, repository = repository, onHome = { screen = Screen.Home }, onPair = { screen = Screen.TvPair })
            is Screen.Note -> NoteScreen(
                onCancel = { screen = s.back },
                onSave = { png -> repository.postNote(png) },
                onSaved = { screen = s.back },
            )
            Screen.Fridge -> FridgeScreen(
                today = state.today,
                loadNotes = repository::fridgeNotes,
                onHome = { screen = Screen.Home },
                onDrawNote = { screen = Screen.Note(back = Screen.Fridge) },
                onOpenMake = { screen = Screen.OpenMake(it) },
            )
            is Screen.OpenMake -> MakeScreen(s.make, onBack = { screen = Screen.Fridge })
        }
    }
}

// Bedtime to morning: just a clock. Hours become a setting later; for now 8pm to 6am.
// home.nightClock=false in local.properties turns it off for testing.
private fun isNight(): Boolean {
    if (!BuildConfig.NIGHT_CLOCK) return false
    val h = LocalTime.now().hour
    return h >= 20 || h < 6
}
