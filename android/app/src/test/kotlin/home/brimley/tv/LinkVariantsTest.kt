package home.brimley.tv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkVariantsTest {
    @Test fun offersShowAndMovieShapesWithAndWithoutUtm() {
        val id = "d28771bd-73ed-49a7-9259-bdb33a115cbc"
        val v = LinkVariants.forLink("https://play.hbomax.com/show/$id?utm_source=universal_search").map { it.second }
        assertEquals(
            listOf(
                "https://play.hbomax.com/show/$id",
                "https://play.hbomax.com/show/$id?utm_source=universal_search",
                "https://play.hbomax.com/movie/$id",
                "https://play.hbomax.com/movie/$id?utm_source=universal_search",
            ),
            v,
        )
    }

    @Test fun leavesOtherServicesAlone() {
        assertTrue(LinkVariants.forLink("https://www.disneyplus.com/browse/entity-c255a738-ee7f-4569-ba89-acacb6e5cefb").isEmpty())
        assertTrue(LinkVariants.forLink("not a link").isEmpty())
    }
}
