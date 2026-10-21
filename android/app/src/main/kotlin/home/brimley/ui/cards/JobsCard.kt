package home.brimley.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
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
    // One section per kid, stacked. Extra jobs aren't assigned to anyone, so the
    // server repeats the same bounty in every block; show it once, at the bottom.
    val bounty = blocks.firstNotNullOfOrNull { it.bounty }
    InkCard(modifier) {
        if (blocks.isEmpty()) {
            CardTitle("Jobs")
            Text("No jobs today.", style = MaterialTheme.typography.bodyLarge, color = Ink)
            return@InkCard
        }
        blocks.forEachIndexed { i, block ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 14.dp), thickness = Rules.thin, color = Ink)
            KidSection(block, onToggle)
        }
        // The extra job sits on the bottom edge, apart from the daily lists.
        bounty?.let {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(12.dp))
            BountyRow(it, inverted = false, onClaimBounty)
        }
    }
}

@Composable
private fun KidSection(block: JobsBlock, onToggle: (TodayJob) -> Unit) {
    val inverted = block.allDone
    val ink = if (inverted) Paper else Ink
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (inverted) Ink else Paper)
            .padding(horizontal = if (inverted) 12.dp else 0.dp, vertical = if (inverted) 6.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(block.kid, style = MaterialTheme.typography.titleLarge, color = ink, maxLines = 1)
            if (block.jobs.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Text("${block.doneCount} of ${block.jobs.size}", style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1)
            }
            Spacer(Modifier.weight(1f))
            StarsRow(block.starsThisWeek, ink)
        }
        Spacer(Modifier.height(4.dp))
        when {
            block.jobs.isEmpty() ->
                Text("No jobs today.", style = MaterialTheme.typography.bodyLarge, color = ink, modifier = Modifier.padding(vertical = 4.dp))
            inverted -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                AllDoneStamp(Modifier.padding(start = 4.dp))
                Spacer(Modifier.weight(1f))
                CatIllustration(Modifier.size(64.dp), ink = Paper, paper = Ink, happy = true)
            }
            else -> block.jobs.forEach { job ->
                // The whole row is the tap target, not just the box.
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable { onToggle(job) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InkCheckbox(checked = job.done, onClick = { onToggle(job) })
                    Spacer(Modifier.width(14.dp))
                    Text(job.title, style = MaterialTheme.typography.bodyLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun StarsRow(stars: Int, ink: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i -> Star(Modifier.size(22.dp), filled = i < stars, ink = ink) }
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
        // The money is what a kid looks for, so it leads, big.
        Text(dollars, style = MaterialTheme.typography.headlineMedium, color = ink, maxLines = 1)
        Spacer(Modifier.width(12.dp))
        Text(
            b.title.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
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
