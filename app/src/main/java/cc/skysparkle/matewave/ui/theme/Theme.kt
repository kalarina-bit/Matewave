package cc.skysparkle.matewave.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import cc.skysparkle.matewave.R
import androidx.compose.ui.unit.sp

// The interface colours follow the selected style (see AppPalette); reading them in
// composition subscribes the screen, so a new style recolours everything at once.

/** Accent: highlights, selected states and progress. Neon green in the original style. */
val ChessGreen: Color get() = AppThemeState.palette.accent

/** The one colour for the main action on a screen. Lime in the original style. */
val Lime: Color get() = AppThemeState.palette.action

/** Text and icons on top of [ChessGreen] or [Lime]. */
val OnAccent: Color get() = AppThemeState.palette.onAccent

val AppBackground: Color get() = AppThemeState.palette.background
val Surface1: Color get() = AppThemeState.palette.surface1
val Surface2: Color get() = AppThemeState.palette.surface2

val HighlightMove = Color(0x8858A6FF)
val HighlightCheck = Color(0x88E53935)
val HighlightLastMove = Color(0x664FC3F7)
val HighlightHint = Color(0x88FFC107)

/** Wide display face for the big headings: the brand voice of the app. */
val DisplayFont = FontFamily(
    Font(R.font.unbounded_semibold, FontWeight.SemiBold),
    Font(R.font.unbounded_extrabold, FontWeight.ExtraBold)
)

/** Text face for everything else. Characters it lacks (CJK, symbols) fall back to the system font. */
val TextFont = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val base = Typography()

private fun TextStyle.display(weight: FontWeight, tracking: Double) =
    copy(fontFamily = DisplayFont, fontWeight = weight, letterSpacing = tracking.sp)

private fun TextStyle.text(weight: FontWeight) = copy(fontFamily = TextFont, fontWeight = weight)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.display(FontWeight.ExtraBold, -1.5),
    displayMedium = base.displayMedium.display(FontWeight.ExtraBold, -1.2),
    displaySmall = base.displaySmall.display(FontWeight.ExtraBold, -0.8),
    headlineLarge = base.headlineLarge.display(FontWeight.ExtraBold, -0.8),
    headlineMedium = base.headlineMedium.display(FontWeight.ExtraBold, -0.6),
    headlineSmall = base.headlineSmall.copy(fontSize = 21.sp, lineHeight = 28.sp).display(FontWeight.SemiBold, -0.4),
    titleLarge = base.titleLarge.text(FontWeight.Bold),
    titleMedium = base.titleMedium.text(FontWeight.Bold),
    titleSmall = base.titleSmall.text(FontWeight.Bold),
    bodyLarge = base.bodyLarge.text(FontWeight.Medium),
    bodyMedium = base.bodyMedium.text(FontWeight.Medium),
    bodySmall = base.bodySmall.text(FontWeight.Medium),
    labelLarge = base.labelLarge.text(FontWeight.Bold),
    labelMedium = base.labelMedium.text(FontWeight.Bold).copy(letterSpacing = 1.sp),
    labelSmall = base.labelSmall.text(FontWeight.SemiBold)
)

/** Large hero heading on the home screen. */
val HeroTextStyle = TextStyle(
    fontFamily = DisplayFont,
    fontSize = 46.sp,
    lineHeight = 50.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-1.5).sp
)

/** The app has a single dark theme; system bars are set up in MainActivity. */
@Composable
fun MatewaveTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = ChessGreen,
        onPrimary = OnAccent,
        secondary = Lime,
        onSecondary = OnAccent,
        background = AppBackground,
        surface = Surface1,
        surfaceVariant = Surface2
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography) {
        // Text and icons without their own colour are light, so nothing turns black on the dark background
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

@Composable
fun whiteOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ink,
    unfocusedTextColor = ink,
    focusedBorderColor = ChessGreen,
    unfocusedBorderColor = ink.copy(alpha = 0.2f),
    focusedLabelColor = ChessGreen,
    unfocusedLabelColor = ink.copy(alpha = 0.6f),
    cursorColor = ChessGreen
)
