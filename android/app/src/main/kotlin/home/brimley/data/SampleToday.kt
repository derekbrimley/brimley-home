package home.brimley.data

import home.brimley.model.CatalogItem
import home.brimley.model.JobsBlock
import home.brimley.model.Playing
import home.brimley.model.Today
import home.brimley.model.TodayBounty
import home.brimley.model.TodayEvent
import home.brimley.model.TodayJob
import home.brimley.model.Weather

// The Monday from the mockups. Shown until the app is pointed at a backend,
// and used by Compose previews.
object SampleToday {
    fun today(): Today = Today(
        generatedAt = "2026-10-12T13:42:00Z",
        date = "2026-10-12",
        tz = "America/Denver",
        weather = Weather(tempF = 48, highF = 61, summary = "cloudy, clearing later", kidLine = "jacket weather", icon = "cloud"),
        events = listOf(
            TodayEvent("2026-10-12T09:00:00-06:00", "2026-10-12T15:00:00-06:00", "School", "Sylvie"),
            TodayEvent("2026-10-12T12:30:00-06:00", "2026-10-12T13:30:00-06:00", "Dentist", "Dad"),
            TodayEvent("2026-10-12T16:00:00-06:00", "2026-10-12T16:45:00-06:00", "Piano lesson", "Sylvie"),
            TodayEvent("2026-10-12T17:30:00-06:00", null, "Dinner at Grandma's", null),
            TodayEvent("2026-10-12", "2026-10-13", "Recycling out", null, allDay = true),
        ),
        tomorrowFirst = TodayEvent("2026-10-13T09:00:00-06:00", null, "School", "Sylvie"),
        jobs = listOf(
            JobsBlock(
                kid = "Sylvie",
                jobs = listOf(
                    TodayJob("sylvie|feed-juniper", "Sylvie", "Feed Juniper", true),
                    TodayJob("sylvie|piano-15-minutes", "Sylvie", "Piano, 15 minutes", true),
                    TodayJob("sylvie|pack-library-books", "Sylvie", "Pack library books", false),
                ),
                doneCount = 2, allDone = false, starsThisWeek = 2,
                bounty = TodayBounty(row = 2, title = "vacuum the basement", amountCents = 100, status = "open"),
            )
        ),
        playing = listOf(Playing(target = "kitchen", title = "Blue", subtitle = "Joni Mitchell · California", isPlaying = true, positionMs = 134_000, durationMs = 228_000)),
        catalog = listOf(
            CatalogItem("bluey", "shows", "Bluey", "disneyplus", "Disney+"),
            CatalogItem("octonauts", "shows", "Octonauts", "netflix", "Netflix"),
            CatalogItem("hilda", "shows", "Hilda", "netflix", "Netflix"),
            CatalogItem("kiki", "movies", "Kiki's Delivery Service", "max", "Max", runtimeMinutes = 103, year = 1989),
            CatalogItem("totoro", "movies", "My Neighbor Totoro", "max", "Max", runtimeMinutes = 86, year = 1988),
            CatalogItem("paddington2", "movies", "Paddington 2", "netflix", "Netflix", runtimeMinutes = 104, year = 2017),
            CatalogItem("squirrel", "videos", "Backyard squirrel maze", "youtube", "YouTube"),
            CatalogItem("wow", "listen", "Wow in the World", "podcast", "Podcast"),
            CatalogItem("brains", "listen", "Brains On!", "podcast", "Podcast"),
        ),
    )
}
