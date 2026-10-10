package home.brimley.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import home.brimley.model.CatalogItem
import home.brimley.tv.StartedTitle
import home.brimley.tv.TvController
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import kotlinx.coroutines.launch

// Debug builds only: send each catalog link to the TV to check it opens the
// right title on that service.
@Composable
fun TvDebugScreen(catalog: List<CatalogItem>, tv: TvController, onHome: () -> Unit, onPair: () -> Unit) {
    val state by tv.state.collectAsState()
    val results = remember { mutableStateMapOf<String, String>() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().height(120.dp).background(Ink).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            InkButton("← Home", onClick = onHome, filled = false, onPaper = false)
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text("TV link test", style = MaterialTheme.typography.headlineLarge, color = Paper)
                Text(state.toString(), style = MaterialTheme.typography.bodyMedium, color = Paper)
            }
            InkButton("Pair", onClick = onPair, filled = false, onPaper = false)
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 30.dp, vertical = 14.dp)) {
            items(catalog.filter { !it.link.isNullOrBlank() }, key = { it.id }) { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${item.title} · ${item.serviceLabel}", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Text(item.link!!, style = MaterialTheme.typography.bodyMedium, color = Ink)
                        results[item.id]?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = Ink) }
                    }
                    InkButton("Send", onClick = {
                        results[item.id] = "Sending…"
                        scope.launch {
                            results[item.id] = tv.play(item.link!!, StartedTitle(item.title, item.serviceLabel, item.posterUrl))
                                .fold({ "Sent" }, { it.message ?: "Failed" })
                        }
                    })
                }
                HorizontalDivider(thickness = 3.dp, color = Ink)
            }
        }
    }
}
