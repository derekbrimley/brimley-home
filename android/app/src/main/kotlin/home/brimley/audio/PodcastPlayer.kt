package home.brimley.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PodcastNow(val episodeTitle: String, val showTitle: String, val imageUrl: String?, val isPlaying: Boolean)

// One player for podcast episodes, out of whatever speaker the tablet is on.
// It takes audio focus, so Spotify on the tablet pauses while it plays.
// Call from the main thread.
class PodcastPlayer(context: Context) {
    private val player = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), /* handleAudioFocus = */ true)
        .setHandleAudioBecomingNoisy(true)
        .build()
    private var current: PodcastNow? = null
    private val _state = MutableStateFlow<PodcastNow?>(null)
    val state: StateFlow<PodcastNow?> = _state

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == Player.STATE_ENDED) stop() else publish() }
            // A dead or blocked URL: drop the entry rather than show silence as playing.
            override fun onPlayerError(error: PlaybackException) = stop()
        })
    }

    fun play(podcast: Podcast, episode: Episode, imageUrl: String?) {
        current = PodcastNow(episode.title, podcast.title, imageUrl ?: podcast.imageUrl, true)
        player.setMediaItem(MediaItem.fromUri(episode.audioUrl))
        player.prepare()
        player.play()
        publish()
    }

    fun toggle() { if (player.playWhenReady) player.pause() else player.play() }
    fun seekBy(ms: Long) { player.seekTo((player.currentPosition + ms).coerceAtLeast(0)) }
    fun stop() { player.stop(); player.clearMediaItems(); current = null; publish() }

    private fun publish() { _state.value = current?.copy(isPlaying = player.playWhenReady) }
}
