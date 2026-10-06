package cc.skysparkle.matewave.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import cc.skysparkle.matewave.ui.theme.DialogScrimColor
import cc.skysparkle.matewave.ui.theme.ink
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

val DialogSurface = Color(0xFF262B29)
val DialogAccent = Color(0xFF93DBA3)
val DialogShape = RoundedCornerShape(28.dp)

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismissRequest,

        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let { w ->
                w.setDimAmount(0f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    runCatching {
                        if (w.windowManager.isCrossWindowBlurEnabled) {
                            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            w.attributes = w.attributes.apply { blurBehindRadius = 24 }
                        }
                    }
                }
            }
        }

        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val progress by animateFloatAsState(
            targetValue = if (shown) 1f else 0f,
            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
            label = "dialogAppear"
        )
        val scrim = DialogScrimColor

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(scrim.copy(alpha = scrim.alpha * progress)) }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .graphicsLayer {
                        alpha = progress
                        val s = 0.92f + 0.08f * progress
                        scaleX = s
                        scaleY = s
                        translationY = (1f - progress) * 24.dp.toPx()
                    }
                    .clip(DialogShape)
                    .background(DialogSurface)

                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(start = 24.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
            ) {
                if (title != null) {
                    Text(
                        title,
                        color = ink,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
                // Long content scrolls between the fixed title and buttons.
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
                if (actions != null) {
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
            }
        }
    }
}

@Composable
fun DialogText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = ink.copy(alpha = 0.78f),
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier
    )
}

@Composable
fun DialogAction(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    color: Color = DialogAccent
) {
    Text(
        text,
        color = if (enabled) color else color.copy(alpha = 0.4f),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}
