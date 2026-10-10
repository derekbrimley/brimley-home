package home.brimley.tv

import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

// What the Playing card knows about the TV.
sealed interface TvState {
    data object NotPaired : TvState
    data object Connecting : TvState
    data object Unreachable : TvState
    data object Off : TvState
    data class On(val appPackage: String?, val started: StartedTitle?) : TvState
}

// The catalog title the tablet launched, shown until the TV turns off.
data class StartedTitle(val title: String, val serviceLabel: String, val posterUrl: String?)

enum class PairResult { Paired, WrongCode, Failed }

// The TV presented a different certificate than the one pinned at pairing.
class TvCertificateChanged : IOException("The TV's certificate changed; pair again")

class TvNotPaired : IOException("The TV isn't set up yet")

// One live connection to the TV's remote port.
interface TvSession {
    val status: StateFlow<RemoteStatus>
    suspend fun send(frame: ByteArray)
    suspend fun awaitClosed()
    fun close()
}

// An open pairing handshake; the TV is showing its code.
interface PairingSession {
    val serverCertSha256: String
    suspend fun submit(code: String): PairResult
    fun close()
}

// Opens sockets to the TV. Real one in AtvConnector; fakes in tests.
interface TvConnector {
    suspend fun connect(host: String, pinnedCertSha256: String): TvSession   // IOException, or TvCertificateChanged
    suspend fun startPairing(host: String): PairingSession                     // the TV shows a code once this returns
}

// Finds the TV's address on the local network, or null.
fun interface TvDiscovery { suspend fun find(timeoutMs: Long): String? }

// Where the paired TV is and the fingerprint of its certificate.
interface TvStore {
    var host: String?
    var serverCertSha256: String?
    fun clear()
}

// What the Playing card calls the app in front. Unknown apps (including the
// Google TV home screen) get no label.
object TvApps {
    private val labels = mapOf(
        "com.disney.disneyplus" to "Disney+",
        "com.netflix.ninja" to "Netflix",
        "com.wbd.stream" to "Max",
        "com.google.android.youtube.tv" to "YouTube",
        "com.google.android.youtube.tvkids" to "YouTube Kids",
        "com.amazon.amazonvideo.livingroom" to "Prime Video",
    )
    fun label(appPackage: String?): String? = appPackage?.let { labels[it] }
}
