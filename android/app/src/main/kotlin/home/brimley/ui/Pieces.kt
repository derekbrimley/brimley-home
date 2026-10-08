package home.brimley.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import home.brimley.ui.theme.CardShape
import home.brimley.ui.theme.Ink
import home.brimley.ui.theme.Paper
import home.brimley.ui.theme.PillShape
import home.brimley.ui.theme.Rules

// Shared building blocks: the bordered card, the pill, the black button, the
// square checkbox. All two-color, all thick-ruled.

@Composable
fun InkCard(modifier: Modifier = Modifier, inverted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val bg = if (inverted) Ink else Paper
    Column(
        modifier
            .clip(CardShape)
            .background(bg)
            .border(BorderStroke(Rules.card, Ink), CardShape)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, inverted: Boolean = false, trailing: String? = null) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = if (inverted) Paper else Ink)
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = if (inverted) Paper else Ink,
                modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun Pill(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onPaper: Boolean = true) {
    val ink = if (onPaper) Ink else Paper
    val bg = if (filled) ink else Color.Transparent
    val fg = if (filled) (if (onPaper) Paper else Ink) else ink
    Box(
        modifier
            .clip(PillShape)
            .background(bg)
            .border(BorderStroke(Rules.thin, ink), PillShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    shape: Shape = PillShape,
    padding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    onPaper: Boolean = true,
) {
    val ink = if (onPaper) Ink else Paper
    val paper = if (onPaper) Paper else Ink
    Box(
        modifier
            .clip(shape)
            .background(if (filled) ink else paper)
            .border(BorderStroke(Rules.thin, ink), shape)
            .clickable(onClick = onClick)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (filled) paper else ink, maxLines = 1)
    }
}

@Composable
fun InkCheckbox(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(40.dp)
            .clip(shape)
            .background(if (checked) Ink else Paper)
            .border(BorderStroke(4.dp, Ink), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Checkmark(Modifier.size(22.dp), color = Paper)
    }
}

@Composable
fun Checkmark(modifier: Modifier = Modifier, color: Color = Ink) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val p = Path().apply {
            moveTo(w * 0.12f, h * 0.55f); lineTo(w * 0.42f, h * 0.85f); lineTo(w * 0.9f, h * 0.2f)
        }
        drawPath(p, color, style = Stroke(width = w * 0.18f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize().background(Paper), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.headlineMedium, color = Ink)
    }
}
