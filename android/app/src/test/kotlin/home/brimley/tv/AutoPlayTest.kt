package home.brimley.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPlayTest {
    @Test fun matchesMaxAndPrimeLinksByHost() {
        assertEquals("com.wbd.stream", AutoPlay.forLink("https://play.max.com/movie/abc")?.appPackage)
        assertEquals("com.wbd.stream", AutoPlay.forLink("https://play.hbomax.com/movie/abc")?.appPackage)
        assertEquals("com.amazon.amazonvideo.livingroom", AutoPlay.forLink("https://www.amazon.com/gp/video/detail/B0ABC")?.appPackage)
        assertEquals("com.amazon.amazonvideo.livingroom", AutoPlay.forLink("https://www.primevideo.com/detail/0ABC")?.appPackage)
    }

    @Test fun leavesEverythingElseAlone() {
        assertNull(AutoPlay.forLink("https://www.youtube.com/watch?v=abc"))
        assertNull(AutoPlay.forLink("https://www.disneyplus.com/series/bluey/1"))
        assertNull(AutoPlay.forLink("https://notmax.com/movie/abc"))
        assertNull(AutoPlay.forLink("not a link"))
    }
}
