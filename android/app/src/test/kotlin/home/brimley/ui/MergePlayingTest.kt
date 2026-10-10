package home.brimley.ui

import home.brimley.audio.PodcastNow
import home.brimley.model.Playing
import home.brimley.tv.StartedTitle
import home.brimley.tv.TvState
import org.junit.Assert.assertEquals
import org.junit.Test

class MergePlayingTest {
    private val kitchen = Playing(target = "kitchen", title = "Blue", isPlaying = true)

    @Test fun tvStartedByTheTabletShowsTheTitle() {
        val p = mergePlaying(listOf(kitchen), TvState.On("com.disney.disneyplus", StartedTitle("Bluey", "Disney+", "p.jpg")), null)
        assertEquals(listOf("tv" to "Bluey", "kitchen" to "Blue"), p.map { it.target to it.title })
        assertEquals("Disney+", p[0].subtitle)
        assertEquals("p.jpg", p[0].imageUrl)
    }

    @Test fun tvStartedElsewhereShowsTheAppOrJustOn() {
        assertEquals("Netflix", mergePlaying(emptyList(), TvState.On("com.netflix.ninja", null), null).single().title)
        assertEquals("TV on", mergePlaying(emptyList(), TvState.On(null, null), null).single().title)
    }

    @Test fun offOrUnpairedTvAddsNothing() {
        for (s in listOf(TvState.Off, TvState.NotPaired, TvState.Unreachable, TvState.Connecting)) {
            assertEquals(listOf(kitchen), mergePlaying(listOf(kitchen), s, null))
        }
    }

    @Test fun aPodcastComesFirst() {
        val p = mergePlaying(listOf(kitchen.copy(isPlaying = false)), TvState.Off, PodcastNow("Why Do Cats Purr?", "Wow in the World", null, true))
        assertEquals(listOf("podcast", "kitchen"), p.map { it.target })
        assertEquals("Wow in the World", p[0].subtitle)
    }
}
