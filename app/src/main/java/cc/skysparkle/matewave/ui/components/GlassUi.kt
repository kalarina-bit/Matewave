package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.ChessGreen
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.style.TextOverflow
import cc.skysparkle.matewave.ui.theme.Lime
import cc.skysparkle.matewave.ui.theme.OnAccent
import cc.skysparkle.matewave.ui.theme.Surface2
import cc.skysparkle.matewave.ui.theme.GlassShape
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.TabletMaxContentWidth
import cc.skysparkle.matewave.ui.theme.glass
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.min
import cc.skysparkle.matewave.ui.theme.LandscapeMaxContentWidth

/** Landscape: a phone on its side or a tablet held wide. Board screens then put the board on the left. */
@Composable
@ReadOnlyComposable
fun isLandscape(): Boolean {
    val config = LocalConfiguration.current
    return config.screenWidthDp > config.screenHeightDp
}

/** Side insets in landscape: the camera cutout and a 3-button navigation bar sit on the left or right. */
@Composable
fun Modifier.horizontalSafePadding(): Modifier =
    windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal))

@Composable
private fun ScaffoldTopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit,
    titleContent: (@Composable RowScope.() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                AppIcon(
                    R.drawable.ic_arrow_back,
                    size = 24.dp,
                    tint = ChessGreen,
                    contentDescription = stringResource(R.string.back)
                )
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        if (titleContent != null) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, content = titleContent)
        } else {
            Text(
                title,
                color = ink,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        actions()
    }
}

@Composable
fun GlassScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    /** Replaces the plain title, e.g. with the opening name and its status badge. */
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().horizontalSafePadding(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = TabletMaxContentWidth)) {
            ScaffoldTopBar(title, onBack, actions, titleContent)

            val scrollState = rememberScrollState()

            val contentModifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
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

/**
 * A screen with a chess board. Portrait: [above], the board and [below] in one scrolling column,
 * as before. Landscape: the board on the left as large as the height allows, [above] and [below]
 * in a scrolling column on the right. [board] gets the modifier that sizes it.
 */
@Composable
fun BoardScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleContent: (@Composable RowScope.() -> Unit)? = null,
    above: @Composable ColumnScope.() -> Unit = {},
    board: (@Composable (Modifier) -> Unit)?,
    below: @Composable ColumnScope.() -> Unit = {}
) {
    if (!isLandscape()) {
        GlassScaffold(title = title, onBack = onBack, actions = actions, titleContent = titleContent) {
            above()
            if (board != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { board(Modifier) }
            }
            below()
        }
        return
    }
    Box(modifier = Modifier.fillMaxSize().horizontalSafePadding(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = LandscapeMaxContentWidth)) {
            ScaffoldTopBar(title, onBack, actions, titleContent)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                if (board != null) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        board(Modifier.size(min(maxWidth, maxHeight)))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    above()
                    below()
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

private val PillShape = RoundedCornerShape(50)

/** The main action: a lime pill with dark text and icons. */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides OnAccent) {
        Row(
            modifier = modifier
                .pressScale(interaction)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(PillShape)
                .background(Lime)
                .then(
                    if (enabled) Modifier.clickable(
                        interactionSource = interaction,
                        indication = LocalIndication.current,
                        onClick = onClick
                    ) else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 15.dp),
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
            .clip(PillShape)
            .background(Surface2)
            .border(1.dp, PanelBorder, PillShape)
            .then(
                if (enabled) Modifier.clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick
                ) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .glass()
            .border(1.dp, PanelBorder, GlassShape)
            .padding(16.dp),
        content = content
    )
}

@Composable
fun GlassProgress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(color = ChessGreen, modifier = Modifier.padding(2.dp))
        Text(text, color = ink, style = MaterialTheme.typography.bodyMedium)
    }
}
