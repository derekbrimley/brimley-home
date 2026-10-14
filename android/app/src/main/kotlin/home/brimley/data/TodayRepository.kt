package home.brimley.data

import home.brimley.model.FridgeNote
import home.brimley.model.MusicShelf
import home.brimley.model.Playing
import home.brimley.model.ShowEpisodes
import home.brimley.model.Stories
import home.brimley.model.StoryTrack
import home.brimley.model.Today
import home.brimley.model.TodayJob
import home.brimley.model.toFridgeNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

data class HomeState(
    val today: Today? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val celebrateKid: String? = null,   // set for ~3s when a kid's last job is ticked
)

// One source of truth for the screen. Polls /api/today every minute (and
// /api/playing every 15 s while the server reports something playing), keeps the
// last good payload on disk so a reboot never shows a blank dashboard, and
// applies job ticks optimistically so the checkbox never lags the finger.
class TodayRepository(private val api: HomeApi, cacheDir: File, private val waker: SpotifyWaker? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cacheFile = File(cacheDir, "today.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var poller: Job? = null
    private var playingPoller: Job? = null

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        readCache()?.let { _state.update { s -> s.copy(today = it, loading = false) } }
        if (!api.isConfigured) {
            _state.update { it.copy(today = it.today ?: SampleToday.today(), loading = false, error = "Not connected: set home.apiBase and home.apiToken") }
        }
    }

    fun start(intervalMs: Long = 60_000, playingIntervalMs: Long = 15_000) {
        if (poller?.isActive == true || !api.isConfigured) return
        poller = scope.launch {
            while (isActive) {
                refresh()
                delay(intervalMs)
            }
        }
        // A pause at the Yoto's button or in the Spotify app shows within ~15 s
        // instead of a minute. Idle when nothing plays, so it costs nothing then.
        playingPoller = scope.launch {
            while (isActive) {
                delay(playingIntervalMs)
                if (_state.value.today?.playing?.isNotEmpty() == true) refreshPlaying()
            }
        }
    }

    fun stop() { poller?.cancel(); poller = null; playingPoller?.cancel(); playingPoller = null }

    private suspend fun refreshPlaying() {
        runCatching { api.playing() }.onSuccess { playing ->
            _state.update { s -> s.today?.let { s.copy(today = it.copy(playing = playing)) } ?: s }
        }
    }

    suspend fun refresh() {
        if (!api.isConfigured) return
        runCatching { api.today() }
            .onSuccess { t ->
                _state.update { it.copy(today = t, loading = false, error = null) }
                writeCache(t)
            }
            .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't reach home") } }
    }

    fun toggleJob(job: TodayJob) {
        val today = _state.value.today ?: return
        val nowDone = !job.done
        val updated = today.copy(jobs = today.jobs.map { block ->
            if (block.kid != job.kid) block else {
                val jobs = block.jobs.map { if (it.id == job.id) it.copy(done = nowDone) else it }
                val done = jobs.count { it.done }
                val allDone = jobs.isNotEmpty() && done == jobs.size
                val starDelta = when { allDone && !block.allDone -> 1; !allDone && block.allDone -> -1; else -> 0 }
                block.copy(jobs = jobs, doneCount = done, allDone = allDone, starsThisWeek = (block.starsThisWeek + starDelta).coerceAtLeast(0))
            }
        })
        val justFinished = updated.jobs.firstOrNull { it.kid == job.kid }?.allDone == true && nowDone
        _state.update { it.copy(today = updated, celebrateKid = if (justFinished) job.kid else it.celebrateKid) }
        scope.launch {
            runCatching { api.setJobDone(job.id, nowDone, today.date) }
                .onFailure { refresh() }   // server disagreed or was unreachable: fall back to truth
            if (justFinished) {
                delay(3_500)
                _state.update { it.copy(celebrateKid = null) }
            }
        }
    }

    fun claimBounty(row: Int) {
        val today = _state.value.today ?: return
        _state.update { s ->
            s.copy(today = today.copy(jobs = today.jobs.map { b ->
                if (b.bounty?.row == row) b.copy(bounty = b.bounty.copy(status = "waiting")) else b
            }))
        }
        scope.launch { runCatching { api.claimBounty(row) }.onFailure { refresh() } }
    }

    // A show's episodes, fetched when its page opens. No seasons means "open the show's page".
    suspend fun episodes(catalogId: String): Result<ShowEpisodes> =
        if (!api.isConfigured) Result.success(ShowEpisodes(note = "Not connected")) else runCatching { api.episodes(catalogId) }

    suspend fun musicShelves(): Result<List<MusicShelf>> =
        if (!api.isConfigured) Result.success(SampleToday.shelves()) else runCatching { api.musicShelves() }

    // Start an album on Spotify on the tablet. Wakes the Spotify app first (it
    // drops off the device list when idle) and retries while it reappears.
    suspend fun playMusic(uri: String): Result<Unit> = runCatching {
        waker?.wake()
        retryWhileSpeakerOff { api.playMusic(uri) }
        refresh()
    }

    fun controlMusic(action: String) {
        val today = _state.value.today ?: return
        // Optimistic pause/resume so the button flips under the finger.
        val flipped = today.playing.map { p ->
            if (p.target != "kitchen") p else when (action) {
                "pause" -> p.copy(isPlaying = false)
                "resume" -> p.copy(isPlaying = true)
                else -> p
            }
        }
        _state.update { it.copy(today = today.copy(playing = flipped)) }
        scope.launch {
            runCatching { api.controlMusic(action) }
            delay(1_500)
            refresh()
        }
    }

    suspend fun stories(): Result<Stories> =
        if (!api.isConfigured) Result.success(SampleToday.stories()) else runCatching { api.stories() }

    suspend fun storyTracks(cardId: String): Result<List<StoryTrack>> = runCatching { api.storyTracks(cardId) }

    // The backend records the command, so the refresh shows the Yoto at once.
    suspend fun playOnYoto(cardId: String): Result<Unit> = runCatching { api.playOnYoto(cardId); refresh() }

    fun controlYoto(action: String) {
        val today = _state.value.today ?: return
        // Optimistic, like the kitchen: the button flips (or the entry goes) under the finger.
        val updated = when (action) {
            "stop" -> today.playing.filter { it.target != "yoto" }
            else -> today.playing.map { if (it.target == "yoto") it.copy(isPlaying = action == "resume") else it }
        }
        _state.update { it.copy(today = today.copy(playing = updated)) }
        scope.launch {
            runCatching { api.controlYoto(action) }
            refresh()
        }
    }

    suspend fun postNote(base64Png: String): Boolean =
        runCatching { api.postNotePng(base64Png); refresh(); true }.getOrDefault(false)

    // The Fridge's notes. Fetched when it opens, never polled or cached: drawings are PNGs.
    suspend fun fridgeNotes(): Result<List<FridgeNote>> =
        if (!api.isConfigured) Result.success(_state.value.today?.cards.orEmpty().mapNotNull { it.toFridgeNote() })
        else runCatching { api.notes() }

    private fun readCache(): Today? = runCatching {
        if (!cacheFile.exists()) null else json.decodeFromString(Today.serializer(), cacheFile.readText())
    }.getOrNull()

    private suspend fun writeCache(t: Today) = withContext(Dispatchers.IO) {
        runCatching { cacheFile.writeText(json.encodeToString(Today.serializer(), t)) }
    }
}
