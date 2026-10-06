package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.UiCornerRadius
import cc.skysparkle.matewave.ui.theme.glass
import cc.skysparkle.matewave.ui.theme.ink

/** Small uppercase caption above a group. */
@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = ink.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier.padding(start = 4.dp, top = 6.dp)
    )
}

/** Rows of one kind in a single block, instead of a card per row. Separate rows with [ListDivider]. */
@Composable
fun ListGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(UiCornerRadius))
            .padding(vertical = 2.dp),
        content = content
    )
}

@Composable
fun ListDivider(inset: Int = 52) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset.dp)
            .height(1.dp)
            .background(ink.copy(alpha = 0.07f))
    )
}

/**
 * One row: optional icon or custom leading content, title with an optional subtitle, an
 * optional grey value on the right, custom trailing content and a chevron when it navigates.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: Int? = null,
    iconTint: Color = ink,
    titleColor: Color = ink,
    subtitleColor: Color = ink.copy(alpha = 0.6f),
    value: String? = null,
    valueColor: Color = ink.copy(alpha = 0.55f),
    chevron: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> { leading(); Spacer(Modifier.width(12.dp)) }
            icon != null -> { AppIcon(icon, size = 22.dp, tint = iconTint); Spacer(Modifier.width(14.dp)) }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = subtitleColor, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (chevron) {
            Spacer(Modifier.width(8.dp))
            AppIcon(R.drawable.ic_arrow_forward, size = 16.dp, tint = ink.copy(alpha = 0.5f))
        }
    }
}
