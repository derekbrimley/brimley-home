package home.brimley.tv

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

// The one object the UI talks to about the TV. Keeps a connection open
// (reconnecting with backoff), publishes power and the app in front, and
// remembers the title the tablet started until the TV turns off.
class TvController(
    private val store: TvStore,
    private val connector: TvConnector,
    private val discovery: TvDiscovery,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TvState>(if (store.serverCertSha256 == null) TvState.NotPaired else TvState.Connecting)
    val state: StateFlow<TvState> = _state

    private val session = MutableStateFlow<TvSession?>(null)
    private val kick = Channel<Unit>(Channel.CONFLATED)   // skip the backoff wait
    private var started: StartedTitle? = null
    private var loop: Job? = null
    private var pairingHost: String? = null

    fun start() {
        if (loop?.isActive != true) loop = scope.launch { run() }
    }

    private suspend fun run() {
        var failures = 0
        while (currentCoroutineContext().isActive) {
            val pin = store.serverCertSha256
            if (pin == null) {
                _state.value = TvState.NotPaired
                kick.receive()
                continue
            }
            _state.value = TvState.Connecting
            val s = try {
                connectSomewhere(pin)
            } catch (e: TvCertificateChanged) {
                store.clear()
                continue
            }
            if (s == null) {
                _state.value = TvState.Unreachable
                withTimeoutOrNull(BACKOFF_MS[minOf(failures++, BACKOFF_MS.lastIndex)]) { kick.receive() }
                continue
            }
            failures = 0
            session.value = s
            coroutineScope {
                val watcher = launch { s.status.collect { publish(it) } }
                s.awaitClosed()
                watcher.cancel()
            }
            session.value = null
        }
    }

    // The cached address first; if that fails, look for the TV again (DHCP may
    // have moved it) and remember where it is now.
    private suspend fun connectSomewhere(pin: String): TvSession? {
        store.host?.let { host -> tryConnect(host, pin)?.let { return it } }
        val found = discovery.find(DISCOVERY_MS) ?: return null
        if (found == store.host) return null
        // A different certificate here is some other Android TV, not ours moved.
        val s = try { tryConnect(found, pin) } catch (e: TvCertificateChanged) { null }
        return s?.also { store.host = found }
    }

    private suspend fun tryConnect(host: String, pin: String): TvSession? = try {
        connector.connect(host, pin)
    } catch (e: TvCertificateChanged) {
        throw e
    } catch (e: IOException) {
        null
    }

    private fun publish(st: RemoteStatus) {
        _state.value = when (st.poweredOn) {
            null -> TvState.Connecting
            false -> { started = null; TvState.Off }
            true -> TvState.On(st.appPackage, started)
        }
    }

    // The open session, or one immediate reconnect attempt.
    private suspend fun liveSession(): TvSession? {
        session.value?.let { return it }
        kick.trySend(Unit)
        return withTimeoutOrNull(RECONNECT_MS) { session.filterNotNull().first() }
    }

    suspend fun play(link: String, title: StartedTitle): Result<Unit> {
        if (store.serverCertSha256 == null) return Result.failure(TvNotPaired())
        val s = liveSession() ?: return Result.failure(IOException("Couldn't reach the TV"))
        return runCatching {
            // A fresh session doesn't know yet; power is a toggle, so wait for
            // the TV to say before sending it, and only send it on a clear "off".
            val power = withTimeoutOrNull(POWER_STATE_MS) { s.status.first { it.poweredOn != null } }?.poweredOn
            if (power == false) {
                s.send(RemoteMessages.key(KeyCode.POWER))
                withTimeoutOrNull(POWER_ON_MS) { s.status.first { it.poweredOn == true } }
                    ?: throw IOException("The TV didn't turn on")
            }
            started = title
            s.send(RemoteMessages.appLink(link))
            publish(s.status.value)
        }
    }

    // Buttons on the Playing card: a dropped connection just means nothing happens.
    suspend fun playPause() = key(KeyCode.MEDIA_PLAY_PAUSE)
    suspend fun volumeUp() = key(KeyCode.VOLUME_UP)
    suspend fun volumeDown() = key(KeyCode.VOLUME_DOWN)

    // Power is a toggle, so only send it when the TV says it is on.
    suspend fun powerOff() {
        if (session.value?.status?.value?.poweredOn == true) key(KeyCode.POWER)
    }

    private suspend fun key(code: Int) {
        val s = session.value ?: return
        try { s.send(RemoteMessages.key(code)) } catch (_: IOException) { s.close() }
    }

    suspend fun beginPairing(): Result<PairingSession> = runCatching {
        val host = discovery.find(DISCOVERY_MS) ?: store.host ?: throw IOException("Couldn't find the TV on the Wi-Fi")
        pairingHost = host
        connector.startPairing(host)
    }

    suspend fun finishPairing(session: PairingSession, code: String): PairResult {
        val result = session.submit(code)
        if (result == PairResult.Paired) {
            store.host = pairingHost
            store.serverCertSha256 = session.serverCertSha256
            session.close()
            this.session.value?.close()
            kick.trySend(Unit)
        }
        return result
    }

    private companion object {
        val BACKOFF_MS = longArrayOf(1_000, 2_000, 5_000, 10_000, 30_000)
        const val DISCOVERY_MS = 6_000L
        const val RECONNECT_MS = 16_000L   // a dead cached address (5 s) + discovery (6 s) + connect
        const val POWER_STATE_MS = 3_000L
        const val POWER_ON_MS = 8_000L
    }
}
