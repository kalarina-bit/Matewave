package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.openings.OpeningBook
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.ChessBoardView
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.components.GlassCard
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.MovesBar
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.viewmodel.OpeningStudyViewModel
import cc.skysparkle.matewave.viewmodel.SessionMode
import cc.skysparkle.matewave.viewmodel.TrainerPhase

@Composable
fun OpeningTrainerScreen(viewModel: OpeningStudyViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.leave() } }
    val course = state.course ?: return
    val startBoard = remember { Board.startPosition() }
    val sans = state.sans
    val opening = remember(sans) { OpeningBook.openingName(sans) }

    GlassScaffold(title = course.name, onBack = onBack) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    when (state.mode) {
                        SessionMode.LEARN -> R.string.study_mode_learn
                        SessionMode.REVIEW -> R.string.study_mode_review
                        SessionMode.PRACTICE -> R.string.study_mode_practice
                    }
                ),
                color = ChessGreen,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            val asked = state.asked.size
            if (asked > 0 && state.phase != TrainerPhase.COMPLETE) {
                Text(
                    stringResource(R.string.study_progress, minOf(state.answered + 1, asked), asked),
                    color = ink.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        ProgressBar(state.answered.toFloat() / state.asked.size.coerceAtLeast(1))

        MovesBar(sans = sans, startBoard = startBoard, currentIndex = sans.size, opening = opening)

        ChessBoardView(
            board = state.board,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargets,
            kingInCheckSquare = null,
            flipped = course.side == PieceColor.BLACK,
            onSquareClick = { viewModel.onSquareClick(it) },
            lastMove = state.lastMove,
            hintSquare = state.hintSquare,
            interactionEnabled = state.phase == TrainerPhase.YOUR_MOVE || state.phase == TrainerPhase.WRONG
        )

        when (state.phase) {
            TrainerPhase.YOUR_MOVE -> Text(
                stringResource(if (state.mode == SessionMode.LEARN) R.string.study_learn_prompt else R.string.study_recall_prompt),
                color = ink,
                style = MaterialTheme.typography.titleMedium
            )
            TrainerPhase.AUTO_MOVE -> Text(
                stringResource(R.string.study_opponent),
                color = ink.copy(alpha = 0.6f),
                style = MaterialTheme.typography.titleMedium
            )
            TrainerPhase.WRONG -> Text(
                stringResource(R.string.study_wrong),
                color = Color(0xFFFF8A80),
                style = MaterialTheme.typography.titleMedium
            )
            TrainerPhase.COMPLETE -> {
                val result = state.result
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(if (state.mode == SessionMode.LEARN) R.string.study_result_learned else R.string.study_result_review),
                        color = ink,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(6.dp))
                    Stars(result?.stars ?: 0, size = 22.dp)
                    Spacer(Modifier.height(6.dp))
                    val change = state.starChange
                    val stars = result?.stars ?: 0
                    val (starText, starColor) = when {
                        change != null && change.after > change.before ->
                            stringResource(R.string.study_star_gained) to StarGold
                        change != null && change.after < change.before ->
                            stringResource(R.string.study_star_lost) to Color(0xFFFF8A80)
                        stars >= 3 -> stringResource(R.string.study_all_stars) to StarGold
                        state.mode == SessionMode.PRACTICE -> stringResource(R.string.study_practice_no_stars) to ink.copy(alpha = 0.65f)
                        stars > 0 -> stringResource(R.string.study_reviews_to_next, result?.reviewsToNext ?: 0) to ink.copy(alpha = 0.8f)
                        else -> "" to ink
                    }
                    if (starText.isNotEmpty()) {
                        Text(starText, color = starColor, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                    }
                    if (state.asked.isNotEmpty()) {
                        Text(
                            stringResource(R.string.study_result_first_try, state.firstTry, state.asked.size),
                            color = ink.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    result?.nextDueAt?.let {
                        Text(
                            stringResource(R.string.study_next_review, formatDueIn(it)),
                            color = ink.copy(alpha = 0.65f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassOutlinedButton(onClick = { viewModel.practiceAgain() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.study_again), color = ink, maxLines = 1)
                    }
                    GlassButton(onClick = { if (!viewModel.continueStudy()) onBack() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.study_next), color = ink, maxLines = 1)
                    }
                }
            }
        }

        if (state.phase != TrainerPhase.COMPLETE) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.mode != SessionMode.LEARN) {
                    GlassOutlinedButton(
                        onClick = { viewModel.showHint() },
                        enabled = state.phase == TrainerPhase.YOUR_MOVE,
                        modifier = Modifier.weight(1f)
                    ) {
                        ButtonIcon(R.drawable.ic_hint)
                        Text(stringResource(R.string.puzzle_hint), color = ink, maxLines = 1)
                    }
                }
                // A review is not restarted: it would only replay the moves still due.
                if (state.mode != SessionMode.REVIEW) {
                    GlassOutlinedButton(onClick = { viewModel.restart() }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.study_restart), color = ink, maxLines = 1)
                    }
                }
            }
        }
    }
}
