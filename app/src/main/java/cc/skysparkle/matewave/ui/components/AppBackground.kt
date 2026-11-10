package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import cc.skysparkle.matewave.ui.theme.AppBackground
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.Lime

/** Near-black backdrop with a faint neon glow at the top and a lime one at the bottom. */
@Composable
fun AppBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .drawWithCache {
                val top = Brush.radialGradient(
                    colors = listOf(ChessGreen.copy(alpha = 0.16f), ChessGreen.copy(alpha = 0f)),
                    center = Offset(size.width * 0.85f, size.height * 0.02f),
                    radius = size.maxDimension * 0.55f
                )
                val bottom = Brush.radialGradient(
                    colors = listOf(Lime.copy(alpha = 0.06f), Lime.copy(alpha = 0f)),
                    center = Offset(0f, size.height),
                    radius = size.maxDimension * 0.5f
                )
                onDrawBehind {
                    drawRect(top)
                    drawRect(bottom)
                }
            }
    )
}
