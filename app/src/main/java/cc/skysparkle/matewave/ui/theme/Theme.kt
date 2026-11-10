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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Neon green: the brand accent for highlights, selected states and progress. */
val ChessGreen = Color(0xFF35E07E)
val ChessGreenDark = Color(0xFF123D25)

/** Lime: the one colour for the main action on a screen. */
val Lime = Color(0xFFE3F84B)

/** Text and icons on top of [ChessGreen] or [Lime]. */
val OnAccent = Color(0xFF0B1610)

val AppBackground = Color(0xFF0C0F0E)
val Surface1 = Color(0xFF161B19)
val Surface2 = Color(0xFF1F2623)

val HighlightMove = Color(0x8858A6FF)
val HighlightCheck = Color(0x88E53935)
val HighlightLastMove = Color(0x664FC3F7)
val HighlightHint = Color(0x88FFC107)

private val DarkScheme = darkColorScheme(
    primary = ChessGreen,
    onPrimary = OnAccent,
    secondary = Lime,
    onSecondary = OnAccent,
    background = AppBackground,
    surface = Surface1,
    surfaceVariant = Surface2
)

private val base = Typography()

/** Heavy, tightly set headings like the mockups; body text stays the system default. */
private val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium)
)

/** Large hero heading on the home and profile screens. */
val HeroTextStyle = TextStyle(
    fontSize = 52.sp,
    lineHeight = 52.sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-2).sp
)

/** The app has a single dark theme; system bars are set up in MainActivity. */
@Composable
fun MatewaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, typography = AppTypography) {
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
