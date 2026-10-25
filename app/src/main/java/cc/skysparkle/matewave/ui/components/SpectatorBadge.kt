package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.ink

/** Small, quiet spectator counter for the top bar: an eye and "7" or "7/20". */
@Composable
fun SpectatorBadge(count: Int, max: Int? = null, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.spectators_description, count)
    Row(
        modifier = modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AppIcon(R.drawable.ic_spectators, size = 16.dp, tint = ink.copy(alpha = 0.7f))
        Text(
            if (max != null) "$count/$max" else count.toString(),
            color = ink.copy(alpha = 0.8f),
            style = MaterialTheme.typography.labelMedium
        )
    }
}
