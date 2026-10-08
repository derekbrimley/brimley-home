package home.brimley.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import home.brimley.model.JobsBlock
import home.brimley.model.TodayBounty
import home.brimley.model.TodayJob
import home.brimley.ui.CardTitle
import home.brimley.ui.CatIllustration
import home.brimley.ui.InkButton
import home.brimley.ui.InkCard
import home.brimley.ui.InkCheckbox
import home.brimley.ui.Star
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.Rules

@Composable
fun JobsCard(
    blocks: List<JobsBlock>,
    celebrateKid: String?,
    onToggle: (TodayJob) -> Unit,
    onClaimBounty: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One kid per card for now; a second child gets a second card later.
    val block = blocks.firstOrNull()
    val inverted = block?.allDone == true
    InkCard(modifier, inverted = inverted) {
        if (block == null) {
            CardTitle("Jobs")
            Text("No jobs today.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            return@InkCard
        }
        Box(Modifier.fillMaxSize()) {
            Column {
                CardTitle("${block.kid}'s jobs", inverted = inverted, trailing = "${block.doneCount} of ${block.jobs.size}")
                Spacer(Modifier.height(8.dp))
                if (inverted) {
                    AllDoneStamp(Modifier.padding(top = 12.dp, start = 4.dp))
                } else {
                    block.jobs.forEach { job ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            InkCheckbox(checked = job.done, onClick = { onToggle(job) })
                            Spacer(Modifier.width(14.dp))
                            Text(job.title, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                StarsRow(block.starsThisWeek, inverted)
                block.bounty?.let { Spacer(Modifier.height(8.dp)); BountyRow(it, inverted, onClaimBounty) }
            }
            CatIllustration(
                Modifier.align(Alignment.TopEnd).size(76.dp),
                ink = if (inverted) Paper else Ink,
                paper = if (inverted) Ink else Paper,
                happy = inverted,
            )
        }
    }
}

@Composable
private fun StarsRow(stars: Int, inverted: Boolean) {
    val ink = if (inverted) Paper else Ink
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(5) { i -> Star(Modifier.size(24.dp), filled = i < stars, ink = ink) }
        Spacer(Modifier.width(6.dp))
        Text("this week", style = MaterialTheme.typography.labelMedium, color = ink)
    }
}

@Composable
private fun BountyRow(b: TodayBounty, inverted: Boolean, onClaim: (Int) -> Unit) {
    val ink = if (inverted) Paper else Ink
    val dollars = "$" + (if (b.amountCents % 100 == 0) (b.amountCents / 100).toString() else "%.2f".format(b.amountCents / 100.0))
    Row(
        Modifier
            .fillMaxWidth()
            .border(BorderStroke(Rules.thin, ink), RoundedCornerShape(16.dp))
            .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildString { append("Extra job: "); append(b.title); append(" · "); append(dollars) },
            style = MaterialTheme.typography.bodySmall, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        when (b.status) {
            "open" -> InkButton("I did it", onClick = { onClaim(b.row) }, onPaper = !inverted)
            "waiting" -> Text("waiting for a grown-up", style = MaterialTheme.typography.labelMedium, color = ink)
            else -> Text("paid", style = MaterialTheme.typography.labelMedium, color = ink)
        }
    }
}

@Composable
fun AllDoneStamp(modifier: Modifier = Modifier) {
    Box(
        modifier
            .rotate(-8f)
            .border(BorderStroke(5.dp, Paper), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text("ALL DONE", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Paper)
    }
}
