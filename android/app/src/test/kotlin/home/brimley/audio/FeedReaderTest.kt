package home.brimley.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.kxml2.io.KXmlParser
import java.io.StringReader

private const val FEED = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
<channel>
  <title>Wow in the World</title>
  <itunes:image href="https://img.example/wow.jpg"/>
  <image><url>https://img.example/small.jpg</url><title>Wow in the World</title></image>
  <item>
    <title>Why Do Cats Purr?</title>
    <pubDate>Wed, 07 Oct 2026 04:00:00 -0000</pubDate>
    <itunes:duration>1325</itunes:duration>
    <enclosure url="https://cdn.example/purr.mp3" type="audio/mpeg" length="1"/>
  </item>
  <item>
    <title>Trailer with no audio</title>
    <pubDate>Tue, 06 Oct 2026 04:00:00 GMT</pubDate>
  </item>
  <item>
    <title>The Moon's Wobble</title>
    <pubDate>Sat, 03 Oct 2026 21:00:00 PDT</pubDate>
    <itunes:duration>1:02:03</itunes:duration>
    <enclosure url="https://cdn.example/moon.mp3" type="audio/mpeg" length="1"/>
  </item>
  <item>
    <title>Snails</title>
    <pubDate>sometime last week</pubDate>
    <enclosure url="https://cdn.example/snails.mp3" type="audio/mpeg" length="1"/>
  </item>
</channel>
</rss>"""

class FeedReaderTest {
    private fun parse(max: Int = 5) = FeedReader.parse(KXmlParser().apply { setInput(StringReader(FEED)) }, max)

    @Test fun readsTheShowAndPlayableEpisodes() {
        val p = parse()
        assertEquals("Wow in the World", p.title)
        assertEquals("https://img.example/wow.jpg", p.imageUrl)
        assertEquals(listOf("Why Do Cats Purr?", "The Moon's Wobble", "Snails"), p.episodes.map { it.title })
        assertEquals("https://cdn.example/purr.mp3", p.episodes[0].audioUrl)
        assertEquals(1325, p.episodes[0].durationSeconds)
        assertEquals(3723, p.episodes[1].durationSeconds)
        assertNull(p.episodes[2].durationSeconds)
    }

    @Test fun stopsAtMax() { assertEquals(2, parse(max = 2).episodes.size) }

    @Test fun durations() {
        assertEquals(1325, FeedReader.parseDuration("22:05"))
        assertEquals(90, FeedReader.parseDuration(" 90 "))
        assertNull(FeedReader.parseDuration("about an hour"))
        assertEquals("22 min", FeedReader.formatDuration(1325))
        assertEquals("1 h 2 min", FeedReader.formatDuration(3723))
        assertEquals("1 h", FeedReader.formatDuration(3600))
        assertEquals("1 min", FeedReader.formatDuration(20))
        assertNull(FeedReader.formatDuration(null))
    }

    @Test fun dates() {
        assertEquals("Oct 7", FeedReader.formatDate("Wed, 07 Oct 2026 04:00:00 -0000"))
        assertEquals("Oct 6", FeedReader.formatDate("Tue, 06 Oct 2026 04:00:00 GMT"))
        assertEquals("Oct 3", FeedReader.formatDate("Sat, 03 Oct 2026 21:00:00 PDT"))
        assertNull(FeedReader.formatDate("sometime last week"))
        assertNull(FeedReader.formatDate(null))
    }

    @Test fun plainHttpAudioIsUpgraded() {
        // The app forbids cleartext traffic; many feeds still list http:// enclosures.
        val xml = """<rss><channel><title>Show</title><item><title>One</title><enclosure url="http://dts.podtrac.com/redirect.mp3/cdn.example/one.mp3"/></item></channel></rss>"""
        val p = FeedReader.parse(KXmlParser().apply { setInput(StringReader(xml)) })
        assertEquals("https://dts.podtrac.com/redirect.mp3/cdn.example/one.mp3", p.episodes.single().audioUrl)
    }
}
