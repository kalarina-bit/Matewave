package cc.skysparkle.matewave.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalInk = compositionLocalOf { Color.White }

val ink: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalInk.current
