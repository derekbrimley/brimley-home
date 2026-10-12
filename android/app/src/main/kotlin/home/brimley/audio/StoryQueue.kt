package home.brimley.audio

import home.brimley.model.StoryCard
import home.brimley.model.StoryTrack

// A Yoto card played on the tablet: its tracks in order, shown on the Playing
// card like a podcast episode (the card's title, the author under it).
data class StoryQueue(val now: PodcastNow, val urls: List<String>)

fun storyQueue(card: StoryCard, tracks: List<StoryTrack>): StoryQueue? {
    val urls = tracks.map { it.url }.filter { it.isNotBlank() }
    if (urls.isEmpty()) return null
    return StoryQueue(PodcastNow(episodeTitle = card.title, showTitle = card.author ?: "Yoto card", imageUrl = card.imageUrl, isPlaying = true), urls)
}
