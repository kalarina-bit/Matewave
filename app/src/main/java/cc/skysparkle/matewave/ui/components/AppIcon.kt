package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun AppIcon(
    resId: Int,
    size: Dp = 20.dp,
    tint: Color = ink,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size)
    )
}

@Composable
fun ButtonIcon(resId: Int, tint: Color = ink, size: Dp = 18.dp) {
    AppIcon(resId, size = size, tint = tint, modifier = Modifier.padding(end = 8.dp))
}
