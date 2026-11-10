package cc.skysparkle.matewave.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val PanelFill = Surface1
val PanelBorder = Color.White.copy(alpha = 0.06f)

val UiCornerRadius = 22.dp
val GlassShape: Shape = RoundedCornerShape(UiCornerRadius)

val TabletMaxContentWidth = 600.dp

fun Modifier.glass(): Modifier = this
    .clip(GlassShape)
    .background(PanelFill)

val DialogScrimColor = Color.Black.copy(alpha = 0.70f)

val FloatingBarFill = Surface2
