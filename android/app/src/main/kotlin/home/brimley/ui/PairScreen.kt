package home.brimley.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import home.brimley.tv.PairResult
import home.brimley.tv.PairingSession
import home.brimley.tv.TvController
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.Rules
import kotlinx.coroutines.launch

private val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "A", "B", "C", "D", "E", "F")

// One-time pairing: the TV shows six characters, a kid or parent taps them.
@Composable
fun PairScreen(tv: TvController, onDone: () -> Unit) {
    var session by remember { mutableStateOf<PairingSession?>(null) }
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("Looking for the TV…") }
    var busy by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    suspend fun begin() {
        session?.close(); session = null; code = ""
        message = "Looking for the TV…"
        tv.beginPairing()
            .onSuccess { session = it; message = "Look at the TV and tap the code" }
            .onFailure { message = it.message ?: "Couldn't find the TV" }
    }

    suspend fun wobble() { for (x in listOf(18f, -18f, 12f, -12f, 0f)) shake.animateTo(x) }

    fun submit(s: PairingSession, entered: String) {
        busy = true
        scope.launch {
            when (tv.finishPairing(s, entered)) {
                PairResult.Paired -> { session = null; onDone() }
                PairResult.WrongCode -> { wobble(); code = ""; message = "That's not it. Try again" }
                PairResult.Failed -> { wobble(); message = "The TV wants a new code"; begin() }
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) { begin() }
    DisposableEffect(Unit) { onDispose { session?.close() } }

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().height(120.dp).background(Ink).padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
            InkButton("← Home", onClick = onDone, filled = false, onPaper = false)
            Spacer(Modifier.width(24.dp))
            Text("Set up the TV", style = MaterialTheme.typography.headlineLarge, color = Paper)
        }
        Row(Modifier.fillMaxSize().padding(40.dp), horizontalArrangement = Arrangement.spacedBy(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                TvIllustration(Modifier.size(200.dp))
                Spacer(Modifier.height(20.dp))
                Text(message, style = MaterialTheme.typography.titleLarge, color = Ink)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.offset(x = shake.value.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier.size(64.dp).border(Rules.thin, Ink, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) { Text(code.getOrNull(i)?.toString() ?: "", style = MaterialTheme.typography.headlineMedium, color = Ink) }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                keys.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { k ->
                            InkButton(k, onClick = {
                                val s = session
                                if (s != null && !busy && code.length < 6) {
                                    code += k
                                    if (code.length == 6) submit(s, code)
                                }
                            }, filled = false, modifier = Modifier.size(84.dp), shape = RoundedCornerShape(18.dp))
                        }
                    }
                }
                InkButton("⌫ Back", onClick = { if (!busy) code = code.dropLast(1) }, filled = false, modifier = Modifier.width(372.dp).height(72.dp), shape = RoundedCornerShape(18.dp))
            }
        }
    }
}
