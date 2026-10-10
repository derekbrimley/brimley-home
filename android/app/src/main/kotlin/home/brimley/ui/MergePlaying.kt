package home.brimley.ui

import home.brimley.audio.PodcastNow
import home.brimley.model.Playing
import home.brimley.tv.TvApps
import home.brimley.tv.TvState

// The Playing card's list: what the server knows (Spotify), plus what only
// the tablet knows (the TV and podcasts). The "podcast" target never comes
// from the server.
fun mergePlaying(server: List<Playing>, tv: TvState, podcast: PodcastNow?): List<Playing> {
    val pod = podcast?.let { Playing(target = "podcast", title = it.episodeTitle, subtitle = it.showTitle, imageUrl = it.imageUrl, isPlaying = it.isPlaying) }
    val tvEntry = (tv as? TvState.On)?.let { on ->
        on.started?.let { Playing(target = "tv", title = it.title, subtitle = it.serviceLabel, imageUrl = it.posterUrl, isPlaying = true) }
            ?: Playing(target = "tv", title = TvApps.label(on.appPackage) ?: "TV on", isPlaying = true)
    }
    return listOfNotNull(pod, tvEntry) + server.filter { it.target != "tv" }
}
