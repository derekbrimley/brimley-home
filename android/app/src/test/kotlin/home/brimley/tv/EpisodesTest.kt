package home.brimley.tv

import home.brimley.model.ShowEpisode
import home.brimley.model.ShowSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodesTest {
    private fun ep(n: Int) = ShowEpisode(n, "Episode $n", "https://d/$n")
    private val seasons = listOf(ShowSeason(1, listOf(ep(1), ep(2))), ShowSeason(2, listOf(ep(1))))

    @Test fun nextIsTheFollowingEpisodeAcrossSeasons() {
        assertEquals(EpisodeRef(1, 2), Episodes.next(seasons, EpisodeRef(1, 1))?.let { EpisodeRef(it.season.number, it.episode.number) })
        assertEquals(EpisodeRef(2, 1), Episodes.next(seasons, EpisodeRef(1, 2))?.let { EpisodeRef(it.season.number, it.episode.number) })
    }

    @Test fun noNextAtTheEndOrWithoutHistory() {
        assertNull(Episodes.next(seasons, EpisodeRef(2, 1)))
        assertNull(Episodes.next(seasons, null))
        assertNull(Episodes.next(seasons, EpisodeRef(5, 1)))
    }

    @Test fun opensOnTheNextEpisodesSeason() {
        assertEquals(2, Episodes.openingSeason(seasons, EpisodeRef(1, 2))?.number)
        assertEquals(2, Episodes.openingSeason(seasons, EpisodeRef(2, 1))?.number)
        assertEquals(1, Episodes.openingSeason(seasons, null)?.number)
    }
}
