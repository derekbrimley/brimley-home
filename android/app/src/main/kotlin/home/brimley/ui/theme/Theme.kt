@file:OptIn(ExperimentalTextApi::class)

package home.brimley.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import home.brimley.R

// Woodcut on paper: two colors only. State is shown by fill, weight and
// outline, never by hue, because the DC-1 is a grayscale reflective screen.
val Ink = Color(0xFF141413)
val Paper = Color(0xFFE8E7E2)
val PaperBright = Color(0xFFFFFFFF)

private fun fredoka(weight: Int, fontWeight: FontWeight) =
    Font(R.font.fredoka, weight = fontWeight, variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Fredoka = FontFamily(
    fredoka(400, FontWeight.Normal),
    fredoka(500, FontWeight.Medium),
    fredoka(600, FontWeight.SemiBold),
    fredoka(700, FontWeight.Bold),
)

val Caveat = FontFamily(
    Font(R.font.caveat, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.caveat, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Sizes are for the DC-1 at ~1.2x density: the screen is 1600x1200 px, so
// 1 dp is about 1.2 px and the home screen is roughly 1333 x 1000 dp.
val BrimleyTypography = Typography(
    displayLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 72.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 48.sp),
    headlineMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    bodySmall = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp),
    labelMedium = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp),
)

object Rules {
    val card = 6.dp        // card border
    val thin = 3.dp        // list rules, pill borders
    val cardRadius = 28.dp
    val tileRadius = 18.dp
}

val CardShape = RoundedCornerShape(Rules.cardRadius)
val TileShape = RoundedCornerShape(Rules.tileRadius)
val PillShape = RoundedCornerShape(50)

private val scheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    outline = Ink,
)

@Composable
fun BrimleyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = BrimleyTypography, content = content)
}
