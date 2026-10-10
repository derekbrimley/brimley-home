package home.brimley.audio

import org.xmlpull.v1.XmlPullParser
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Episode(val title: String, val audioUrl: String, val published: String?, val durationSeconds: Int?)
data class Podcast(val title: String, val imageUrl: String?, val episodes: List<Episode>)

// Reads a podcast RSS feed: the show's title and art, and the newest
// episodes that have audio. Namespaces are not processed, so iTunes tags
// arrive as "itunes:duration".
object FeedReader {
    fun parse(p: XmlPullParser, max: Int = 5): Podcast {
        var showTitle: String? = null
        var showImage: String? = null
        val episodes = mutableListOf<Episode>()
        var inItem = false
        var title: String? = null
        var url: String? = null
        var date: String? = null
        var duration: Int? = null
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT && episodes.size < max) {
            if (event == XmlPullParser.START_TAG) when (p.name) {
                "item" -> { inItem = true; title = null; url = null; date = null; duration = null }
                "title" -> { val t = p.nextText().trim(); if (inItem) title = t else if (showTitle == null) showTitle = t }
                "enclosure" -> if (inItem) url = p.getAttributeValue(null, "url")
                "pubDate" -> if (inItem) date = p.nextText().trim()
                "itunes:duration" -> if (inItem) duration = parseDuration(p.nextText())
                "itunes:image" -> if (!inItem && showImage == null) showImage = p.getAttributeValue(null, "href")
            } else if (event == XmlPullParser.END_TAG && p.name == "item") {
                inItem = false
                url?.takeIf { it.isNotBlank() }?.let { episodes += Episode(title ?: "Episode", it, date, duration) }
            }
            event = p.next()
        }
        return Podcast(showTitle ?: "Podcast", showImage, episodes)
    }

    // "1325", "22:05" or "1:02:03" -> seconds.
    fun parseDuration(text: String?): Int? {
        val parts = text?.trim()?.split(":")?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.isEmpty() || parts.size > 3) return null
        return parts.fold(0) { acc, n -> acc * 60 + n }
    }

    fun formatDuration(seconds: Int?): String? {
        if (seconds == null) return null
        val minutes = maxOf(1, (seconds + 30) / 60)
        return if (minutes < 60) "$minutes min" else if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60} h ${minutes % 60} min"
    }

    private val inputs = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.US),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.US),
    )
    private val output = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun formatDate(pubDate: String?): String? {
        if (pubDate == null) return null
        for (f in inputs) runCatching { return ZonedDateTime.parse(pubDate, f).format(output) }
        return null
    }
}
