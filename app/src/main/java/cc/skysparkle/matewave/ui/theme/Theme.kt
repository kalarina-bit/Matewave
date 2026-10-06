package cc.skysparkle.matewave.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

val ChessGreen = Color(0xFF3E7C4A)
val ChessGreenDark = Color(0xFF1B4D2E)
val HighlightMove = Color(0x8858A6FF)
val HighlightCheck = Color(0x88E53935)
val HighlightLastMove = Color(0x664FC3F7)
val HighlightHint = Color(0x88FFC107)

private val DarkScheme = darkColorScheme(
    primary = ChessGreen,
    secondary = ChessGreenDark,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

/** The app has a single dark theme; system bars are set up in MainActivity. */
@Composable
fun MatewaveTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme) {
        CompositionLocalProvider(LocalInk provides Color.White, content = content)
    }
}

@Composable
fun whiteOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = ink,
    unfocusedTextColor = ink,
    focusedBorderColor = ink.copy(alpha = 0.6f),
    unfocusedBorderColor = ink.copy(alpha = 0.3f),
    focusedLabelColor = ink.copy(alpha = 0.8f),
    unfocusedLabelColor = ink.copy(alpha = 0.6f),
    cursorColor = ink
)
