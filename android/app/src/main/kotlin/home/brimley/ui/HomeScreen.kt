package home.brimley.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import home.brimley.model.Card
import home.brimley.model.MusicItem
import home.brimley.model.MusicShelf
import home.brimley.model.Playing
import home.brimley.model.Today
import home.brimley.model.TodayJob
import home.brimley.ui.cards.JobsCard
import home.brimley.ui.cards.MusicCard
import home.brimley.ui.cards.TodayCard
import home.brimley.ui.cards.WatchCard
import home.brimley.ui.cards.YotoCard
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.Rules
import home.brimley.ui.theme.TileShape
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    today: Today,
    celebrateKid: String?,
    error: String?,
    onToggleJob: (TodayJob) -> Unit,
    onClaimBounty: (Int) -> Unit,
    crate: MusicShelf?,
    crateError: String?,
    musicStatus: String?,
    onPlayAlbum: (MusicItem) -> Unit,
    onPickWatch: () -> Unit,
    onPickStory: () -> Unit,
    playing: List<Playing>,
    tvSetUp: Boolean,
    onControl: (String, String) -> Unit,
    onSetUpTv: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Band(today, error)
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Jobs holds two kids' lists and gets most of the column; Today fits three events.
                TodayCard(today.events, Modifier.weight(1f).fillMaxWidth())
                JobsCard(
                    blocks = today.jobs,
                    celebrateKid = celebrateKid,
                    onToggle = onToggleJob,
                    onClaimBounty = onClaimBounty,
                    modifier = Modifier.weight(2.5f).fillMaxWidth(),
                )
            }
            // The note card (NoteCard) and the fridge are off the home screen for now.
            MusicCard(crate, crateError, musicStatus, playing, onPlayAlbum, onControl, Modifier.weight(1.15f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                WatchCard(playing, tvSetUp, onPickWatch, onControl, onSetUpTv, Modifier.weight(1f).fillMaxWidth())
                YotoCard(playing, onPickStory, onControl, Modifier.weight(1f).fillMaxWidth())
            }
        }
        Footer(today)
    }
    if (celebrateKid != null) Box(Modifier.fillMaxSize()) { AllDoneBurst() }
}

@Composable
private fun Band(today: Today, error: String?) {
    val date = LocalDate.parse(today.date)
    val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
    val month = date.month.getDisplayName(TextStyle.FULL, Locale.US)
    val time = LocalTime.now()
    val daypart = when {
        time.hour < 12 -> "in the morning"
        time.hour < 17 -> "in the afternoon"
        else -> "in the evening"
    }
    val timeText = time.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)) + " " + daypart

    Row(
        Modifier.fillMaxWidth().height(132.dp).background(Ink).padding(horizontal = 34.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(weekday, style = MaterialTheme.typography.displayLarge, color = Paper)
            Text(
                "$month ${date.dayOfMonth} · $timeText" + (if (error != null) "  ·  $error" else ""),
                style = MaterialTheme.typography.bodyMedium, color = Paper, maxLines = 1,
            )
        }
        today.weather?.let { w ->
            Column(horizontalAlignment = Alignment.End) {
                Text("${w.tempF}°", style = MaterialTheme.typography.headlineLarge, color = Paper)
                Text(w.summary, style = MaterialTheme.typography.bodySmall, color = Paper, textAlign = TextAlign.End)
                Text(w.kidLine, style = MaterialTheme.typography.bodySmall, color = Paper, textAlign = TextAlign.End)
            }
            Spacer(Modifier.width(18.dp))
            WeatherIllustration(w.icon, Modifier.size(84.dp))
        }
    }
}

// The footer is the day-card slot: the week's lunch menu on Mondays (Zo posts it;
// the card's window is that Monday), else the next thing on the calendar.
@Composable
private fun Footer(today: Today) {
    val lunch = today.cards.firstOrNull { it.kind == "lunch_menu" }
    Row(
        Modifier.fillMaxWidth().height(86.dp).background(Ink).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (lunch != null) {
            Column {
                Text(lunch.title, style = MaterialTheme.typography.titleMedium, color = Paper)
                Text("this week", style = MaterialTheme.typography.labelMedium, color = Paper)
            }
            LunchWeek(lunch, LocalDate.parse(today.date))
        } else {
            val next = today.tomorrowFirst
            Column {
                Text(if (next != null) "Tomorrow" else "Tonight", style = MaterialTheme.typography.titleMedium, color = Paper)
                Text("next up", style = MaterialTheme.typography.labelMedium, color = Paper)
            }
            Text(
                next?.let { "${it.title}${it.who?.let { w -> " · $w" } ?: ""}" } ?: "Nothing on the calendar. Sleep well.",
                style = MaterialTheme.typography.bodyLarge, color = Paper,
            )
        }
    }
}

private data class LunchDay(val day: String, val menu: String, val photo: String?)

// Each day is a button: tapping it opens that day's lunch with its photo from the school's menu site.
@Composable
private fun LunchWeek(card: Card, date: LocalDate) {
    val days = runCatching {
        card.data?.get("days")?.jsonArray?.map { d ->
            val o = d.jsonObject
            LunchDay(
                day = o["day"]?.jsonPrimitive?.content ?: "",
                menu = o["menu"]?.jsonPrimitive?.content ?: "",
                photo = o["photo"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            )
        }
    }.getOrNull().orEmpty()
    val todayAbbrev = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    var open by remember(card.id) { mutableStateOf<LunchDay?>(null) }
    Row(
        Modifier.fillMaxWidth().fillMaxHeight().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        days.forEach { d ->
            val isToday = d.day.equals(todayAbbrev, ignoreCase = true)
            val fg = if (isToday) Ink else Paper
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(shape)
                    .background(if (isToday) Paper else Ink)
                    .border(BorderStroke(Rules.thin, Paper), shape)
                    .clickable { open = d }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(d.day.uppercase(), style = MaterialTheme.typography.labelMedium, color = fg)
                Text(d.menu, style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    open?.let { LunchPhoto(it, onClose = { open = null }) }
}

// One day's lunch, big: the photo when the menu site has one, else the tray drawing.
@Composable
private fun LunchPhoto(day: LunchDay, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        InkCard(Modifier.fillMaxWidth(0.72f).fillMaxHeight(0.86f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayName(day.day), style = MaterialTheme.typography.titleMedium, color = Ink)
                    Text(day.menu, style = MaterialTheme.typography.headlineMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(16.dp))
                InkButton("Done", onClick = onClose, modifier = Modifier.height(64.dp))
            }
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().clip(TileShape).border(BorderStroke(Rules.thin, Ink), TileShape),
                contentAlignment = Alignment.Center,
            ) {
                var failed by remember(day.photo) { mutableStateOf(day.photo == null) }
                if (failed) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LunchTrayIllustration(Modifier.size(180.dp))
                        Text("No picture of this one", style = MaterialTheme.typography.bodyLarge, color = Ink)
                    }
                } else {
                    AsyncImage(
                        model = day.photo,
                        contentDescription = day.menu,
                        contentScale = ContentScale.Fit,
                        onError = { failed = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

private fun dayName(abbrev: String): String =
    java.time.DayOfWeek.entries.firstOrNull { it.getDisplayName(TextStyle.SHORT, Locale.US).equals(abbrev, ignoreCase = true) }
        ?.getDisplayName(TextStyle.FULL, Locale.US) ?: abbrev
