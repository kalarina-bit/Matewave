package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.settings.BoardAppearance
import cc.skysparkle.matewave.settings.BoardTheme
import cc.skysparkle.matewave.ui.components.GlassCard
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.theme.GlassBorder

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appearance = remember { BoardAppearance(context) }

    var theme by remember { mutableStateOf(appearance.theme) }
    var coords by remember { mutableStateOf(appearance.showCoordinates) }
    var images by remember { mutableStateOf(appearance.useImagePieces) }
    var lastMove by remember { mutableStateOf(appearance.showLastMove) }
    var checkGlow by remember { mutableStateOf(appearance.showCheckHighlight) }
    var openingBar by remember { mutableStateOf(appearance.showOpening) }

    GlassScaffold(title = stringResource(R.string.appearance_title), onBack = onBack) {
        Text(
            stringResource(R.string.appearance_board_theme),
            color = ink,
            style = MaterialTheme.typography.titleMedium
        )

        BoardTheme.entries.forEach { candidate ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        theme = candidate
                        appearance.theme = candidate
                    }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ThemePreview(candidate, showPieces = images)
                    Text(
                        stringResource(candidate.titleRes),
                        color = ink,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (candidate == theme) {
                        cc.skysparkle.matewave.ui.components.AppIcon(
                            R.drawable.ic_check_box, size = 20.dp, tint = Color(0xFF7BE495)
                        )
                    }
                }
            }
        }

        AppearanceToggle(
            title = stringResource(R.string.appearance_coordinates),
            subtitle = stringResource(R.string.appearance_coordinates_hint),
            checked = coords
        ) { coords = it; appearance.showCoordinates = it }

        AppearanceToggle(
            title = stringResource(R.string.appearance_image_pieces),
            subtitle = stringResource(R.string.appearance_image_pieces_hint),
            checked = images
        ) { images = it; appearance.useImagePieces = it }

        AppearanceToggle(
            title = stringResource(R.string.appearance_last_move),
            subtitle = stringResource(R.string.appearance_last_move_hint),
            checked = lastMove
        ) { lastMove = it; appearance.showLastMove = it }

        AppearanceToggle(
            title = stringResource(R.string.appearance_check),
            subtitle = stringResource(R.string.appearance_check_hint),
            checked = checkGlow
        ) { checkGlow = it; appearance.showCheckHighlight = it }

        AppearanceToggle(
            title = stringResource(R.string.appearance_opening_in_game),
            subtitle = stringResource(R.string.appearance_opening_in_game_hint),
            checked = openingBar
        ) { openingBar = it; appearance.showOpening = it }

        Text(
            stringResource(R.string.appearance_restart_hint),
            color = ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ThemePreview(theme: BoardTheme, showPieces: Boolean) {
    val shape = RoundedCornerShape(6.dp)
    Column(modifier = Modifier.size(56.dp).clip(shape).border(1.dp, GlassBorder, shape)) {
        repeat(2) { row ->
            Row(modifier = Modifier.weight(1f)) {
                repeat(2) { col ->
                    val light = (row + col) % 2 == 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(if (light) theme.light else theme.dark),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showPieces && row == 0 && col == 0) {
                            Image(
                                painter = painterResource(R.drawable.piece_white_king),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (showPieces && row == 1 && col == 1) {
                            Image(
                                painter = painterResource(R.drawable.piece_black_knight),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = ink, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4F8A5B)
                )
            )
        }
    }
}
