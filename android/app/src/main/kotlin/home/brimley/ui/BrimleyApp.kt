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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import home.brimley.BuildConfig
import home.brimley.data.TodayRepository
import home.brimley.ui.theme.Paper
import java.time.LocalTime

sealed interface Screen {
    data object Home : Screen
    data class Play(val tab: PlayTab) : Screen
    data object Note : Screen
}

@Composable
fun BrimleyApp(repository: TodayRepository) {
    val state by repository.state.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

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
                        onDrawNote = { screen = Screen.Note },
                        onControl = repository::controlMusic,
                    )
                }
            }
            is Screen.Play -> PlayScreen(
                today = state.today,
                repository = repository,
                initialTab = s.tab,
                onHome = { screen = Screen.Home },
            )
            Screen.Note -> NoteScreen(
                onCancel = { screen = Screen.Home },
                onSave = { png -> repository.postNote(png) },
                onSaved = { screen = Screen.Home },
            )
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
