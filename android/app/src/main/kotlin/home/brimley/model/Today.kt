package home.brimley.model

import kotlinx.serialization.Serializable

// Mirror of lib/types.ts in the backend. Keep the two in step.

@Serializable
data class TodayEvent(
    val start: String,
    val end: String? = null,
    val title: String,
    val who: String? = null,
    val allDay: Boolean = false,
)

@Serializable
data class TodayJob(val id: String, val kid: String, val title: String, val done: Boolean)

@Serializable
data class TodayBounty(
    val row: Int,
    val title: String,
    val amountCents: Int,
    val status: String,
    val kid: String? = null,
)

@Serializable
data class JobsBlock(
    val kid: String,
    val jobs: List<TodayJob>,
    val doneCount: Int,
    val allDone: Boolean,
    val starsThisWeek: Int,
    val bounty: TodayBounty? = null,
)

@Serializable
data class CatalogItem(
    val id: String,
    val shelf: String,
    val title: String,
    val service: String,
    val serviceLabel: String,
    val posterUrl: String? = null,
    val link: String? = null,
    val runtimeMinutes: Int? = null,
    val year: Int? = null,
)

@Serializable
data class Card(
    val id: Int,
    val kind: String,
    val title: String,
    val body: String? = null,
    val data: kotlinx.serialization.json.JsonObject? = null,
    val showFrom: String? = null,
    val showUntil: String? = null,
    val priority: Int = 0,
    val source: String = "",
)

@Serializable
data class Playing(
    val target: String,
    val title: String,
    val subtitle: String? = null,
    val imageUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long? = null,
    val durationMs: Long? = null,
)

@Serializable
data class Weather(
    val tempF: Int,
    val highF: Int? = null,
    val summary: String,
    val kidLine: String,
    val icon: String,
)

@Serializable
data class Make(val title: String, val who: String? = null, val link: String? = null, val imageUrl: String? = null)

@Serializable
data class Today(
    val generatedAt: String,
    val date: String,
    val tz: String,
    val weather: Weather? = null,
    val events: List<TodayEvent> = emptyList(),
    val tomorrowFirst: TodayEvent? = null,
    val jobs: List<JobsBlock> = emptyList(),
    val cards: List<Card> = emptyList(),
    val playing: List<Playing> = emptyList(),
    val catalog: List<CatalogItem> = emptyList(),
    val makes: List<Make> = emptyList(),
    val warnings: List<String> = emptyList(),
)
