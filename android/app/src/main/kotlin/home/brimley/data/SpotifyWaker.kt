package home.brimley.data

import android.app.Activity
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

// Android's Spotify app drops off Spotify's device list when it has been idle.
// Connecting to it with the App Remote SDK starts it in the background, so the
// next Crate play call finds "DC-1" again. The connection is kept open.
class SpotifyWaker(private val clientId: String) {
    var activity: WeakReference<Activity>? = null
    private var remote: SpotifyAppRemote? = null

    suspend fun wake() {
        if (clientId.isBlank() || remote?.isConnected == true) return
        val ctx = activity?.get() ?: return
        withContext(Dispatchers.Main) {
            withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    val params = ConnectionParams.Builder(clientId).setRedirectUri(REDIRECT_URI).showAuthView(true).build()
                    SpotifyAppRemote.connect(ctx, params, object : Connector.ConnectionListener {
                        override fun onConnected(r: SpotifyAppRemote) { remote = r; if (cont.isActive) cont.resume(Unit) }
                        override fun onFailure(t: Throwable) { if (cont.isActive) cont.resume(Unit) }
                    })
                }
            }
        }
    }

    private companion object { const val REDIRECT_URI = "brimley-home://spotify-callback" }
}

// Spotify takes a moment to reappear on the device list after waking.
suspend fun <T> retryWhileSpeakerOff(attempts: Int = 4, delayMs: Long = 2_000, block: suspend () -> T): T {
    repeat(attempts - 1) {
        try {
            return block()
        } catch (e: ApiException) {
            if (e.message?.contains("isn't on") != true) throw e
        }
        delay(delayMs)
    }
    return block()
}
