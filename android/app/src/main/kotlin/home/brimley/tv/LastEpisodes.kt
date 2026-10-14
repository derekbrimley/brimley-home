package home.brimley.tv

import android.content.Context
import home.brimley.model.ShowEpisode
import home.brimley.model.ShowSeason

data class EpisodeRef(val season: Int, val episode: Int)

// The last episode the tablet started for each show, so a show's sheet can
// offer the one after it. Only the tablet's own starts count: it can't see
// what the TV played next on its own.
interface LastEpisodeStore {
    fun get(showId: String): EpisodeRef?
    fun set(showId: String, ref: EpisodeRef)
}

class PrefsLastEpisodes(context: Context) : LastEpisodeStore {
    private val prefs = context.getSharedPreferences("last_episodes", Context.MODE_PRIVATE)
    override fun get(showId: String): EpisodeRef? {
        val v = prefs.getString(showId, null) ?: return null
        val (s, e) = v.split(':').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 2 } ?: return null
        return EpisodeRef(s, e)
    }
    override fun set(showId: String, ref: EpisodeRef) { prefs.edit().putString(showId, "${ref.season}:${ref.episode}").apply() }
}

data class NextUp(val season: ShowSeason, val episode: ShowEpisode)

object Episodes {
    // The episode after the last one started, across seasons; null when nothing
    // was started, it was the last one, or it's no longer listed.
    fun next(seasons: List<ShowSeason>, last: EpisodeRef?): NextUp? {
        if (last == null) return null
        val all = seasons.flatMap { s -> s.episodes.map { NextUp(s, it) } }
        val i = all.indexOfFirst { it.season.number == last.season && it.episode.number == last.episode }
        return if (i < 0) null else all.getOrNull(i + 1)
    }

    // The season the sheet opens on: the next episode's, else the last one's, else the first.
    fun openingSeason(seasons: List<ShowSeason>, last: EpisodeRef?): ShowSeason? =
        next(seasons, last)?.season ?: seasons.firstOrNull { it.number == last?.season } ?: seasons.firstOrNull()
}
