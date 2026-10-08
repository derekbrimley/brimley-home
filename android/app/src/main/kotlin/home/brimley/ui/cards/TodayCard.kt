package home.brimley.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import home.brimley.model.TodayEvent
import home.brimley.ui.CardTitle
import home.brimley.ui.InkCard
import home.brimley.ui.Pill
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Rules
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_LINES = 5

@Composable
fun TodayCard(events: List<TodayEvent>, modifier: Modifier = Modifier) {
    InkCard(modifier) {
        CardTitle("Today")
        Spacer(Modifier.height(6.dp))
        if (events.isEmpty()) {
            Text("Nothing on the calendar.", style = MaterialTheme.typography.bodyLarge, color = Ink)
        }
        val shown = if (events.size > MAX_LINES) events.take(MAX_LINES - 1) else events
        shown.forEachIndexed { i, e ->
            EventRow(e)
            if (i < shown.lastIndex || events.size > MAX_LINES) HorizontalDivider(thickness = Rules.thin, color = Ink)
        }
        if (events.size > MAX_LINES) {
            Text("and ${events.size - shown.size} more", style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun EventRow(e: TodayEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(timeLabel(e), style = MaterialTheme.typography.labelLarge, color = Ink, modifier = Modifier.width(88.dp))
        Text(e.title, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        e.who?.let {
            Spacer(Modifier.width(10.dp))
            Pill(it, filled = it.equals("Dad", true) || it.equals("Mom", true))
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm", Locale.US)

fun timeLabel(e: TodayEvent): String {
    if (e.allDay) return "All day"
    return runCatching { OffsetDateTime.parse(e.start).format(timeFmt) }.getOrDefault("")
}
