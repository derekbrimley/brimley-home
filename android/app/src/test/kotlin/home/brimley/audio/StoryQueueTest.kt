package home.brimley.audio

import home.brimley.model.StoryCard
import home.brimley.model.StoryTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryQueueTest {
    private val card = StoryCard("c1", "The Gruffalo", "Julia Donaldson", "g.jpg")

    @Test fun playsTracksInOrderUnderTheCardsName() {
        val q = storyQueue(card, listOf(StoryTrack("Intro", "Chapter 1", "https://a"), StoryTrack("Two", "Chapter 2", "https://b")))!!
        assertEquals(listOf("https://a", "https://b"), q.urls)
        assertEquals(PodcastNow("The Gruffalo", "Julia Donaldson", "g.jpg", true), q.now)
    }

    @Test fun noPlayableTracksMeansNothingToPlay() {
        assertNull(storyQueue(card, emptyList()))
        assertNull(storyQueue(card, listOf(StoryTrack("x", null, " "))))
    }

    @Test fun aCardWithNoAuthorSaysYotoCard() {
        assertEquals("Yoto card", storyQueue(card.copy(author = null), listOf(StoryTrack("x", null, "https://a")))!!.now.showTitle)
    }
}
