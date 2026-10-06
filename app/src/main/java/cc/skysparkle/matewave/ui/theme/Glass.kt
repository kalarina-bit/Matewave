package cc.skysparkle.matewave.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val PanelFill = Color(0xFF14171C).copy(alpha = 0.86f)
val PanelBorder = Color.White.copy(alpha = 0.10f)

val UiCornerRadius = 8.dp
val GlassShape: Shape = RoundedCornerShape(UiCornerRadius)

val TabletMaxContentWidth = 600.dp

fun Modifier.glass(shape: Shape = GlassShape): Modifier = this
    .clip(shape)
    .background(PanelFill)

val GlassBorder = PanelBorder

val PhotoScrim = Brush.verticalGradient(
    0.00f to Color.Black.copy(alpha = 0.84f),
    0.10f to Color.Black.copy(alpha = 0.72f),
    0.22f to Color.Black.copy(alpha = 0.58f),
    0.38f to Color.Black.copy(alpha = 0.48f),
    0.55f to Color.Black.copy(alpha = 0.47f),
    0.72f to Color.Black.copy(alpha = 0.55f),
    0.88f to Color.Black.copy(alpha = 0.68f),
    1.00f to Color.Black.copy(alpha = 0.80f)
)

val DialogScrimColor = Color.Black.copy(alpha = 0.62f)

val FloatingBarFill = Color.Black.copy(alpha = 0.25f)
