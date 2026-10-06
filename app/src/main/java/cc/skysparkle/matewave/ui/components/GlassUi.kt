package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import cc.skysparkle.matewave.ui.theme.LocalInk
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.PanelFill
import cc.skysparkle.matewave.ui.theme.TabletMaxContentWidth
import cc.skysparkle.matewave.ui.theme.UiCornerRadius
import cc.skysparkle.matewave.ui.theme.glass

private val UiShape: Shape = RoundedCornerShape(UiCornerRadius)

@Composable
fun GlassScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,

    bottomBar: (@Composable () -> Unit)? = null,
    /** Replaces the plain title, e.g. with the opening name and its status badge. */
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = TabletMaxContentWidth)) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            AppIcon(
                                R.drawable.ic_arrow_back,
                                size = 24.dp,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }
                    if (titleContent != null) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = titleContent)
                    } else {
                        Text(
                            title,
                            color = ink,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    actions()
                }

                val scrollState = rememberScrollState()

                val contentModifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .then(if (scrollable) Modifier.verticalScroll(scrollState) else Modifier)
                    .then(if (bottomBar == null) Modifier.navigationBarsPadding() else Modifier)
                    .padding(bottom = 16.dp)

                Column(
                    modifier = contentModifier,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = content
                )

                if (bottomBar != null) {
                    Box(modifier = Modifier.navigationBarsPadding()) { bottomBar() }
                }
            }
        }
    }
}

@Composable
private fun Modifier.pressScale(interactionSource: MutableInteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "pressScale"
    )
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }

    CompositionLocalProvider(LocalInk provides Color.White) {
        Row(
            modifier = modifier
                .pressScale(interaction)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(UiShape)
                .background(ChessGreen)
                .then(
                    if (enabled) Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    ) else Modifier
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
fun GlassOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .pressScale(interaction)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(UiShape)
            .background(PanelFill)
            .border(1.dp, PanelBorder, UiShape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = UiShape, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .glass(shape)
            .border(1.dp, PanelBorder, shape)
            .padding(14.dp),
        content = content
    )
}

@Composable
fun GlassProgress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = ink, modifier = Modifier.padding(2.dp))
        Text(text, color = ink, style = MaterialTheme.typography.bodyMedium)
    }
}
