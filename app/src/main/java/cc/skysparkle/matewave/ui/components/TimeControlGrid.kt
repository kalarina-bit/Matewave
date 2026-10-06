package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.clock.TimeControl
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.ink

/** "10+0" from "Rapid 10+0"; the infinity sign for no limit. */
fun TimeControl.shortLabel(): String = if (this == TimeControl.NO_LIMIT) "∞" else label.substringAfterLast(' ')

@Composable
fun TimeControl.displayName(): String =
    if (this == TimeControl.NO_LIMIT) stringResource(R.string.time_no_limit) else shortLabel()

/** Every time control visible at once, grouped by category, instead of a clipped scrolling row. */
@Composable
fun TimeControlGrid(selected: TimeControl, onSelect: (TimeControl) -> Unit) {
    val groups = listOf(
        R.string.time_cat_bullet to listOf(TimeControl.BULLET_1_0, TimeControl.BULLET_2_1),
        R.string.time_cat_blitz to listOf(TimeControl.BLITZ_3_0, TimeControl.BLITZ_5_0, TimeControl.BLITZ_5_3),
        R.string.time_cat_rapid to listOf(TimeControl.RAPID_10_0, TimeControl.RAPID_15_10),
        R.string.time_cat_classical to listOf(TimeControl.CLASSICAL_30_0, TimeControl.CLASSICAL_30_20),
        R.string.time_no_limit to listOf(TimeControl.NO_LIMIT)
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        groups.forEach { (title, options) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(title),
                    color = ink.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(110.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { tc ->
                        val active = tc == selected
                        val shape = RoundedCornerShape(50)
                        Text(
                            tc.shortLabel(),
                            color = ink,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clip(shape)
                                .background(if (active) ChessGreen.copy(alpha = 0.35f) else ink.copy(alpha = 0.08f))
                                .border(1.dp, if (active) ChessGreen else ink.copy(alpha = 0.12f), shape)
                                .clickable { onSelect(tc) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}
