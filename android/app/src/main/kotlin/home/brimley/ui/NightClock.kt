package home.brimley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.brimley.model.Today
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// After bedtime: a big clock, black on paper, and tomorrow's first thing.
// The DC-1's amber backlight does the rest.
@Composable
fun NightClock(today: Today) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = LocalTime.now() } }
    Column(Modifier.fillMaxSize().background(Paper).padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
        Text(now.format(DateTimeFormatter.ofPattern("h:mm", Locale.US)), style = MaterialTheme.typography.displayLarge.copy(fontSize = 220.sp, lineHeight = 220.sp), color = Ink)
        today.tomorrowFirst?.let {
            Text("Tomorrow: ${it.title}${it.who?.let { w -> " · $w" } ?: ""}", style = MaterialTheme.typography.headlineMedium, color = Ink, modifier = Modifier.padding(top = 24.dp))
        }
    }
}
