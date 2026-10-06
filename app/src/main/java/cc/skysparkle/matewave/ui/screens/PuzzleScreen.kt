package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.puzzles.Puzzle
import cc.skysparkle.matewave.puzzles.PuzzleRepository
import cc.skysparkle.matewave.puzzles.themeTitleRes
import cc.skysparkle.matewave.stats.Stats
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.ChessBoardView
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.PromotionDialog
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.viewmodel.GameViewModel
import kotlinx.coroutines.delay

private val WrongRed = Color(0xFFFF6B6B)

@Composable
fun PuzzleSolveScreen(viewModel: GameViewModel, onBack: () -> Unit, onNextPuzzle: (Puzzle) -> Unit) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val puzzle = state.puzzle

    // Confirmation on the board: the destination of a correct move flashes green; a wrong
    // move stays red until it is taken back.
    var greenFlash by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(state.puzzleMoveIndex, state.puzzleSolved) {
        if ((state.puzzleMoveIndex > 0 || state.puzzleSolved) && !state.puzzleFailedAttempt) {
            greenFlash = state.lastMove?.to
            delay(450)
            greenFlash = null
        }
    }
    val redSquare = if (state.puzzleFailedAttempt && state.puzzleAutoMoving) state.lastMove?.to else null

    GlassScaffold(
        title = puzzle?.let { stringResource(R.string.puzzle_title_rating, it.rating) } ?: stringResource(R.string.puzzle_title_fallback),
        onBack = onBack,
        actions = {
            if (puzzle != null) {
                Text(
                    stringResource(puzzle.themeTitleRes()).uppercase(),
                    color = ChessGreen,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ChessGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    ) {
        // On tablets the board is capped and centred, so the task and buttons fit without scrolling.
        ChessBoardView(
            board = state.board,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargetsFromSelected,
            kingInCheckSquare = if (ChessEngine.isInCheck(state.board, state.board.sideToMove)) {
                state.board.kingSquare(state.board.sideToMove)
            } else null,
            flipped = state.myColor == PieceColor.BLACK,
            onSquareClick = viewModel::onSquareClicked,
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 560.dp),
            lastMove = state.lastMove,
            hintSquare = state.puzzleHintFrom,
            interactionEnabled = state.puzzleStarted && !state.puzzleAutoMoving && !state.puzzleSolved && state.pendingPromotion == null,
            flashSquare = redSquare ?: greenFlash,
            flashColor = if (redSquare != null) WrongRed.copy(alpha = 0.55f) else ChessGreen.copy(alpha = 0.55f)
        )

        // The task, in the same format as the status in a game: a dot in the side's colour.
        val (text, color) = when {
            state.puzzleSolved -> "✓ " + stringResource(R.string.puzzle_solved) to ChessGreen
            state.puzzleFailedAttempt -> "✗ " + stringResource(R.string.puzzle_wrong_move) to WrongRed
            state.puzzleAutoMoving -> stringResource(R.string.opponent_turn) to ink.copy(alpha = 0.7f)
            state.myColor == PieceColor.WHITE -> stringResource(R.string.puzzle_find_best_white) to ink
            else -> stringResource(R.string.puzzle_find_best_black) to ink
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (state.myColor == PieceColor.WHITE) Color(0xFFF0F0F0) else Color(0xFF222222))
                    .border(1.dp, ink.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Text(text, color = color, style = MaterialTheme.typography.titleMedium)
        }

        // Progress through multi-move puzzles: one dot per move of the solver.
        if (puzzle != null) {
            val total = (puzzle.solutionUci.size + 1) / 2
            if (total > 1) {
                val done = if (state.puzzleSolved) total else state.puzzleMoveIndex / 2
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(total) { i ->
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (i < done) ChessGreen else ink.copy(alpha = 0.2f))
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.puzzle_move_of, minOf(done + 1, total), total),
                        color = ink.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        if (state.puzzleSolutionRevealed && !state.puzzleSolved) {
            val expectedUci = puzzle?.solutionUci?.getOrNull(state.puzzleMoveIndex)
            val san = expectedUci?.let { uci ->
                ChessEngine.legalMoves(state.board).firstOrNull { it.toUci() == uci }?.let { Notation.toSan(state.board, it, withCheck = true) }
            }
            Text(
                text = if (san != null) stringResource(R.string.puzzle_solution_prefix, san) else stringResource(R.string.puzzle_solution_unavailable),
                color = ink.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.puzzleSolved) {
            val next = remember(puzzle?.id) {
                PuzzleRepository.pickForRating(
                    context,
                    cc.skysparkle.matewave.data.ProfileStore.get().elo,
                    Stats.solvedPuzzles(),
                    exclude = puzzle?.id
                )
            }
            if (next != null) {
                GlassButton(onClick = { onNextPuzzle(next) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.puzzle_next), color = ink, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                stringResource(R.string.puzzle_solved_count, Stats.solvedPuzzles().size),
                color = ink.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else if (state.puzzleStarted) {
            // The hint is the action; showing the solution is giving up, so it is a quiet link.
            GlassOutlinedButton(
                onClick = { viewModel.showPuzzleHint() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.puzzleAutoMoving
            ) {
                ButtonIcon(R.drawable.ic_hint)
                Text(stringResource(R.string.puzzle_hint), color = ink)
            }
            Text(
                stringResource(R.string.puzzle_show_solution),
                color = ink.copy(alpha = if (state.puzzleAutoMoving) 0.3f else 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !state.puzzleAutoMoving) { viewModel.revealPuzzleSolution() }
                    .padding(vertical = 8.dp)
            )
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(
            color = state.board.sideToMove,
            onChoose = viewModel::choosePromotion,
            onDismiss = viewModel::cancelPromotion
        )
    }
}
