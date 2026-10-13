package home.brimley.ui

import home.brimley.model.Card
import home.brimley.model.toFridgeNote
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FridgeTest {
    private val game = "https://scratch.mit.edu/projects/123/embed"

    @Test fun sameSiteNavigationIsAllowed() {
        assertTrue(sameHost(game, "https://scratch.mit.edu/projects/123/"))
        assertTrue(sameHost(game, "http://SCRATCH.mit.edu/x"))
    }

    @Test fun otherSitesAndSubdomainsAreBlocked() {
        assertFalse(sameHost(game, "https://www.youtube.com/watch?v=1"))
        assertFalse(sameHost(game, "https://cdn.scratch.mit.edu/x"))
    }

    @Test fun otherSchemesAreBlocked() {
        assertFalse(sameHost(game, "intent://scratch.mit.edu#Intent;end"))
        assertFalse(sameHost(game, "javascript:alert(1)"))
        assertFalse(sameHost(game, "about:blank"))
        assertFalse(sameHost("not a url", game))
    }

    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.parse("2026-10-13")   // a Tuesday

    @Test fun noteDaysReadLikeAPerson() {
        assertEquals("Today", noteDay("2026-10-13T15:00:00.123456+00:00", today, zone))
        assertEquals("Yesterday", noteDay("2026-10-13T03:00:00+00:00", today, zone))   // 9pm the 12th in Denver
        assertEquals("Thu", noteDay("2026-10-08T18:00:00Z", today, zone))
        assertEquals("Sep 30", noteDay("2026-09-30T18:00:00Z", today, zone))
    }

    @Test fun aMissingDateHasNoLabel() {
        assertNull(noteDay("", today, zone))
        assertNull(noteDay("soon", today, zone))
    }

    @Test fun todaysNoteBecomesAFridgeNote() {
        val drawing = Card(id = 7, kind = "drawing", title = "Note", data = buildJsonObject { put("png", JsonPrimitive("iVBOR")) })
        assertEquals("iVBOR", drawing.toFridgeNote()?.png)
        assertEquals("Hi", Card(id = 8, kind = "note", title = "Note", body = "Hi").toFridgeNote()?.body)
        assertNull(Card(id = 9, kind = "note", title = "Note", body = " ").toFridgeNote())
        assertNull(Card(id = 10, kind = "lunch_menu", title = "Lunch").toFridgeNote())
    }
}
