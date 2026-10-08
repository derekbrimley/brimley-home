package home.brimley.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import home.brimley.model.Card
import home.brimley.model.Today
import home.brimley.model.TodayJob
import home.brimley.ui.cards.JobsCard
import home.brimley.ui.cards.NoteCard
import home.brimley.ui.cards.PlayingCard
import home.brimley.ui.cards.TodayCard
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
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
    onPlaySomething: () -> Unit,
    onDrawNote: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Band(today, error)
        Row(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TodayCard(today.events, Modifier.weight(1.05f).fillMaxWidth())
                JobsCard(
                    blocks = today.jobs,
                    celebrateKid = celebrateKid,
                    onToggle = onToggleJob,
                    onClaimBounty = onClaimBounty,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
            Column(Modifier.weight(1.4f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PlayingCard(today.playing, onPlaySomething, Modifier.weight(1.05f).fillMaxWidth())
                NoteCard(today.cards, onDrawNote, Modifier.weight(1f).fillMaxWidth())
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

// The footer is the day-card slot: the lunch menu when Zo posted one, else the
// next thing on the calendar.
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

@Composable
private fun LunchWeek(card: Card, date: LocalDate) {
    val days = runCatching {
        card.data?.get("days")?.jsonArray?.map { d ->
            val o = d.jsonObject
            (o["day"]?.jsonPrimitive?.content ?: "") to (o["menu"]?.jsonPrimitive?.content ?: "")
        }
    }.getOrNull().orEmpty()
    val todayAbbrev = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        days.forEach { (day, menu) ->
            val isToday = day.equals(todayAbbrev, ignoreCase = true)
            val fg = if (isToday) Ink else Paper
            Column(
                Modifier
                    .weight(1f)
                    .background(if (isToday) Paper else Ink, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(day.uppercase(), style = MaterialTheme.typography.labelMedium, color = fg)
                Text(menu, style = MaterialTheme.typography.bodySmall, color = fg, maxLines = 2)
            }
        }
    }
}
