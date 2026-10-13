package home.brimley.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import home.brimley.model.FridgeNote
import home.brimley.model.Make
import home.brimley.model.Today
import home.brimley.model.toFridgeNote
import home.brimley.ui.theme.Caveat
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.PaperBright
import home.brimley.ui.theme.Rules
import home.brimley.ui.theme.TileShape
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

// The Fridge, behind the Note card: the notes, newest first (the one up now outlined
// thick), and the family's makes from the sheet. Notes come from /api/notes when it
// opens; until then (or if that fails) today's note from /api/today stands alone.
@Composable
fun FridgeScreen(
    today: Today?,
    loadNotes: suspend () -> Result<List<FridgeNote>>,
    onHome: () -> Unit,
    onDrawNote: () -> Unit,
    onOpenMake: (Make) -> Unit,
) {
    val upNow = today?.cards.orEmpty().firstNotNullOfOrNull { it.toFridgeNote() }
    var notes by remember { mutableStateOf<List<FridgeNote>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<FridgeNote?>(null) }
    LaunchedEffect(Unit) { loadNotes().onSuccess { notes = it }.onFailure { failed = true } }

    val zone = runCatching { ZoneId.of(today?.tz) }.getOrDefault(ZoneId.systemDefault())
    val date = runCatching { LocalDate.parse(today?.date) }.getOrDefault(LocalDate.now(zone))
    val shown = notes ?: listOfNotNull(upNow)
    val makes = today?.makes.orEmpty()

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(120.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            InkButton("← Home", onClick = onHome, filled = false, onPaper = false)
            FridgeIllustration(Modifier.size(72.dp), ink = Paper, paper = Ink)
            Text("The fridge", style = MaterialTheme.typography.headlineLarge, color = Paper, modifier = Modifier.weight(1f))
            InkButton("Draw a note", onClick = onDrawNote, onPaper = false)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(240.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 30.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Notes", style = MaterialTheme.typography.headlineMedium, color = Ink)
                    val status = when {
                        failed -> "Couldn't reach the older notes"
                        notes == null -> "Getting the older ones…"
                        else -> null
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    itemsIndexed(shown, key = { _, n -> n.id }) { i, n ->
                        NoteTile(n, upNow = i == 0, label = noteDay(n.createdAt, date, zone), onClick = { open = n })
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Our makes", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(top = 12.dp))
            }
            if (makes.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Nothing on the fridge yet. Add things you made to the Makes tab of the family sheet.", style = MaterialTheme.typography.bodyLarge, color = Ink)
                }
            } else {
                items(makes) { m -> MakeTile(m, onClick = { onOpenMake(m) }) }
            }
        }
    }
    open?.let { n -> NoteLarge(n, label = noteDay(n.createdAt, date, zone), onBack = { open = null }) }
}

@Composable
private fun NoteTile(note: FridgeNote, upNow: Boolean, label: String?, onClick: () -> Unit) {
    Column(Modifier.width(220.dp).clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(220.dp)
                .clip(TileShape)
                .background(Paper)
                .border(BorderStroke(if (upNow) Rules.card else Rules.thin, Ink), TileShape)
                .padding(14.dp),
        ) {
            NoteFace(note, Modifier.fillMaxSize(), textSize = 34)
            if (upNow) Pill("Up now", filled = true, modifier = Modifier.align(Alignment.TopStart))
        }
        label?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.padding(top = 6.dp)) }
    }
}

@Composable
private fun NoteFace(note: FridgeNote, modifier: Modifier, textSize: Int) {
    if (note.kind == "drawing") {
        DrawingImage(note.png, note.id, modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.CenterStart) {
            Text(
                note.body.orEmpty(),
                fontFamily = Caveat, fontWeight = FontWeight.Bold, fontSize = textSize.sp, lineHeight = (textSize + 2).sp, color = Ink,
                modifier = Modifier.rotate(-3f), maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MakeTile(make: Make, onClick: () -> Unit) {
    val canOpen = !make.link.isNullOrBlank()
    Column(Modifier.fillMaxWidth().clickable(enabled = canOpen, onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(TileShape)
                .background(PaperBright)
                .border(BorderStroke(4.dp, Ink), TileShape),
        ) {
            if (make.imageUrl != null) {
                AsyncImage(model = make.imageUrl, contentDescription = make.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                FridgeIllustration(Modifier.fillMaxSize().padding(36.dp))
            }
            if (canOpen) Pill("Open", filled = true, modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(make.title, style = MaterialTheme.typography.labelLarge, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        make.who?.let { Text("by $it", style = MaterialTheme.typography.bodyMedium, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

// One note, as big as the screen allows. The strip is the navigation: no swiping.
@Composable
private fun NoteLarge(note: FridgeNote, label: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(
            Modifier.fillMaxWidth().height(110.dp).background(Ink).padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            InkButton("← Back to the fridge", onClick = onBack, filled = false, onPaper = false)
            label?.let { Text(it, style = MaterialTheme.typography.headlineLarge, color = Paper) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(24.dp)
                .clip(TileShape)
                .background(Paper)
                .border(BorderStroke(Rules.card, Ink), TileShape)
                .padding(28.dp),
        ) { NoteFace(note, Modifier.fillMaxSize(), textSize = 72) }
    }
}

// "Today", "Yesterday", a weekday within the week, else "Sep 30". Null when the
// date is missing (today's note as /api/today sends it carries none).
fun noteDay(createdAt: String, today: LocalDate, zone: ZoneId): String? {
    val day = runCatching { OffsetDateTime.parse(createdAt).atZoneSameInstant(zone).toLocalDate() }.getOrNull() ?: return null
    val ago = ChronoUnit.DAYS.between(day, today)
    return when {
        ago == 0L -> "Today"
        ago == 1L -> "Yesterday"
        ago in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US)
        else -> day.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
    }
}
