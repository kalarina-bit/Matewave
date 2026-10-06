package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.SectionCaption
import cc.skysparkle.matewave.ui.components.TimeControlGrid
import cc.skysparkle.matewave.ui.components.displayName

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import cc.skysparkle.matewave.ui.components.SetupSegmented
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ai.AiDifficulty
import cc.skysparkle.matewave.clock.TimeControl
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.components.GlassScaffold

@Composable
private fun difficultyLabel(difficulty: AiDifficulty): String = stringResource(difficultyName(difficulty))

@Composable
fun VsAiSetupScreen(
    onBack: () -> Unit,
    onStart: (AiDifficulty, PieceColor?, TimeControl) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val last = remember { cc.skysparkle.matewave.settings.LastGameSetup(context) }
    var difficulty by remember { mutableStateOf(last.aiDifficulty) }
    var color by remember { mutableStateOf(last.aiColor) }
    var timeControl by remember { mutableStateOf(last.aiTime) }
    val levels = AiDifficulty.entries

    GlassScaffold(
        title = stringResource(R.string.vsai_title),
        onBack = onBack,
        // The start button stays at the bottom, in reach of the thumb, with a summary of the choice.
        bottomBar = {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                GlassButton(
                    onClick = {
                        last.aiDifficulty = difficulty
                        last.aiColor = color
                        last.aiTime = timeControl
                        onStart(difficulty, color, timeControl)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.vsai_play_summary, difficultyLabel(difficulty), timeControl.displayName()),
                        color = ink,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1
                    )
                }
            }
        }
    ) {
        // Who you are playing: changes with the level.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(52.dp).clip(CircleShape).background(ink.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_ai_app, size = 28.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.game_ai_name, difficultyLabel(difficulty)) + "  ≈" +
                        cc.skysparkle.matewave.data.ProfileStore.aiNominalElo(difficulty),
                    color = ink,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(aiDescription(difficulty)), color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCaption(stringResource(R.string.difficulty_label))
        SetupSegmented(
            options = levels.map { difficultyLabel(it) },
            selectedIndex = levels.indexOf(difficulty)
        ) { difficulty = levels[it] }
        Row(modifier = Modifier.fillMaxWidth()) {
            levels.forEach {
                Text(
                    "≈" + cc.skysparkle.matewave.data.ProfileStore.aiNominalElo(it),
                    color = ink.copy(alpha = if (it == difficulty) 0.8f else 0.45f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCaption(stringResource(R.string.your_color))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorChip(R.drawable.ic_white_colour, stringResource(R.string.color_white), color == PieceColor.WHITE) { color = PieceColor.WHITE }
            ColorChip(R.drawable.ic_black_colour, stringResource(R.string.color_black), color == PieceColor.BLACK) { color = PieceColor.BLACK }
            ColorChip(R.drawable.ic_random_colour, stringResource(R.string.color_random), color == null) { color = null }
        }

        SectionCaption(stringResource(R.string.time_control_label))
        TimeControlGrid(selected = timeControl, onSelect = { timeControl = it })
    }
}

private fun aiDescription(d: AiDifficulty): Int = when (d) {
    AiDifficulty.EASY -> R.string.ai_desc_easy
    AiDifficulty.MEDIUM -> R.string.ai_desc_medium
    AiDifficulty.HARD -> R.string.ai_desc_hard
    AiDifficulty.EXPERT -> R.string.ai_desc_expert
}

@Composable
private fun ColorChip(iconRes: Int, text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) ChessGreen.copy(alpha = 0.35f) else ink.copy(alpha = 0.10f))
            .border(1.dp, if (selected) ChessGreen else PanelBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Text(text, color = ink, style = MaterialTheme.typography.bodySmall)
    }
}
