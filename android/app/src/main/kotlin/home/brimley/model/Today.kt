package home.brimley.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

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

// A show's episodes, from GET /api/episodes?id=<catalog id>. Each has its own link.
@Serializable
data class ShowEpisode(val number: Int, val title: String, val link: String, val stillUrl: String? = null)

@Serializable
data class ShowSeason(val number: Int, val episodes: List<ShowEpisode>)

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

// target: "kitchen" | "tv" | "yoto" from the server; "podcast" (anything playing on the tablet) is added on the tablet (ui/MergePlaying.kt).
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

// An older note on the Fridge, from GET /api/notes. Drawings carry their PNG.
@Serializable
data class FridgeNote(val id: Int, val kind: String, val body: String? = null, val png: String? = null, val createdAt: String = "")

@Serializable
data class FridgeNotes(val notes: List<FridgeNote> = emptyList())

// Today's note as /api/today sends it, in the Fridge's shape. Null for other kinds.
fun Card.toFridgeNote(): FridgeNote? = when (kind) {
    "drawing" -> data?.get("png")?.jsonPrimitive?.contentOrNull?.let { FridgeNote(id, kind, png = it) }
    "note" -> body?.takeIf { it.isNotBlank() }?.let { FridgeNote(id, kind, body = it) }
    else -> null
}

@Serializable
data class Today(
    val generatedAt: String,
    val date: String,
    val tz: String,
    val weather: Weather? = null,
    val events: List<TodayEvent> = emptyList(),
    val tomorrowFirst: TodayEvent? = null,
    val jobs: List<JobsBlock> = emptyList(),
    val cards: List<Card> = emptyList(),       // at most one note or drawing: the newest
    val playing: List<Playing> = emptyList(),
    val catalog: List<CatalogItem> = emptyList(),
    val makes: List<Make> = emptyList(),
    val warnings: List<String> = emptyList(),
)

// Music shelves from GET /api/music (Crate's crates with their picks).
@Serializable
data class MusicShelf(val id: String, val name: String, val items: List<MusicItem>)

@Serializable
data class MusicItem(
    val id: Int,
    val title: String,
    val creator: String,
    val imageUrl: String? = null,
    val uri: String,
    val mediaType: String = "album",
)

// Stories from GET /api/yoto (the family's Yoto cards) and /api/yoto/cards/{id}.
// Mirrors StoryCard / YotoPlayer / StoryTrack in lib/yoto.ts.
@Serializable
data class StoryCard(val id: String, val title: String, val author: String? = null, val imageUrl: String? = null)

@Serializable
data class YotoPlayer(val id: String, val name: String, val online: Boolean = false)

@Serializable
data class Stories(val cards: List<StoryCard> = emptyList(), val player: YotoPlayer? = null)

@Serializable
data class StoryTrack(val title: String, val chapter: String? = null, val url: String, val durationSec: Int? = null)
