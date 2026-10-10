package home.brimley.data

import home.brimley.model.MusicShelf
import home.brimley.model.Playing
import home.brimley.model.Today
import home.brimley.model.TodayJob
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

// One source of truth for the screen. Polls /api/today every minute, keeps the
// last good payload on disk so a reboot never shows a blank dashboard, and
// applies job ticks optimistically so the checkbox never lags the finger.
class TodayRepository(private val api: HomeApi, cacheDir: File, private val waker: SpotifyWaker? = null) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cacheFile = File(cacheDir, "today.json")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private var poller: Job? = null

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    init {
        readCache()?.let { _state.update { s -> s.copy(today = it, loading = false) } }
        if (!api.isConfigured) {
            _state.update { it.copy(today = it.today ?: SampleToday.today(), loading = false, error = "Not connected: set home.apiBase and home.apiToken") }
        }
    }

    fun start(intervalMs: Long = 60_000) {
        if (poller?.isActive == true || !api.isConfigured) return
        poller = scope.launch {
            while (isActive) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    fun stop() { poller?.cancel(); poller = null }

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

    suspend fun postNote(base64Png: String): Boolean =
        runCatching { api.postNotePng(base64Png); refresh(); true }.getOrDefault(false)

    private fun readCache(): Today? = runCatching {
        if (!cacheFile.exists()) null else json.decodeFromString(Today.serializer(), cacheFile.readText())
    }.getOrNull()

    private suspend fun writeCache(t: Today) = withContext(Dispatchers.IO) {
        runCatching { cacheFile.writeText(json.encodeToString(Today.serializer(), t)) }
    }
}
