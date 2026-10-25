package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.data.FriendsStore
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Material
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.opposite
import cc.skysparkle.matewave.network.NetworkRequirements
import cc.skysparkle.matewave.openings.OpeningBook
import cc.skysparkle.matewave.spectate.SpectateTarget
import cc.skysparkle.matewave.spectate.SpectatorClient
import cc.skysparkle.matewave.spectate.SpectatorOutcome
import cc.skysparkle.matewave.spectate.SpectatorResult
import cc.skysparkle.matewave.spectate.SpectatorView
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.CapturedPiecesRow
import cc.skysparkle.matewave.ui.components.ChessBoardView
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.DialogText
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.MovesBar
import cc.skysparkle.matewave.ui.components.SpectatorBadge
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.viewmodel.SpectatePhase
import cc.skysparkle.matewave.viewmodel.SpectateUiState
import cc.skysparkle.matewave.viewmodel.SpectatorViewModel
import kotlinx.coroutines.delay

/** Read-only view of a friend's LAN game. */
@Composable
fun SpectatorScreen(target: SpectateTarget, onBackToFriends: () -> Unit) {
    val viewModel: SpectatorViewModel = viewModel()
    LaunchedEffect(target) { viewModel.start(target) }
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var left by remember { mutableStateOf(false) }
    val backToFriends: () -> Unit = {
        if (!left) {
            left = true
            onBackToFriends()
        }
    }
    androidx.activity.compose.BackHandler(onBack = backToFriends)

    // Wi-Fi switched off here: say so at once instead of waiting for the connection to time out.
    LaunchedEffect(Unit) {
        while (true) {
            delay(WIFI_CHECK_MS)
            if (!NetworkRequirements.check(context).wifiConnected) viewModel.onNetworkLost()
        }
    }

    val view = state.view
    var resultHidden by remember(view?.gameId, state.phase) { mutableStateOf(false) }

    GlassScaffold(
        title = stringResource(R.string.spectate_title),
        onBack = backToFriends,
        actions = { if (view != null) SpectatorBadge(state.spectators, state.max) }
    ) {
        if (state.phase == SpectatePhase.CONNECTING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.spectate_joining), color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (view == null) return@GlassScaffold

        val bottom = if (state.flipped) PieceColor.BLACK else PieceColor.WHITE
        val material = remember(view.board) { Material.balance(view.board) }
        val checkSquare = if (ChessEngine.isInCheck(view.board, view.board.sideToMove)) {
            view.board.kingSquare(view.board.sideToMove)
        } else null

        SpectatorPlayerRow(view, bottom.opposite(), state, material)
        ChessBoardView(
            board = view.board,
            selectedSquare = null,
            legalTargets = emptyList(),
            kingInCheckSquare = checkSquare,
            flipped = state.flipped,
            onSquareClick = {},
            lastMove = view.lastMove,
            interactionEnabled = false,
            highlightLastMove = true
        )
        SpectatorPlayerRow(view, bottom, state, material)

        val bookPlies = remember(view.moveSans) { OpeningBook.bookDepth(view.moveSans) }
        MovesBar(
            sans = view.moveSans,
            startBoard = view.startBoard,
            currentIndex = view.moveSans.size,
            opening = null,
            bookPlies = bookPlies
        )

        GlassOutlinedButton(onClick = viewModel::flip, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(R.drawable.ic_board_view)
            Text(stringResource(R.string.spectate_flip), color = ink, maxLines = 1)
        }

        if (state.phase == SpectatePhase.ENDED && resultHidden) {
            GlassOutlinedButton(onClick = { resultHidden = false }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.game_over_title), color = ink, maxLines = 1)
            }
        }
    }

    when (state.phase) {
        SpectatePhase.ENDED -> {
            val result = view?.result
            if (result != null && !resultHidden) {
                AppDialog(
                    onDismissRequest = { resultHidden = true },
                    title = stringResource(R.string.game_over_title),
                    actions = { DialogAction(stringResource(R.string.spectate_back_to_friends), backToFriends) }
                ) {
                    DialogText(resultText(result))
                }
            }
        }
        SpectatePhase.LOST -> {
            LaunchedEffect(Unit) {
                delay(LOST_RETURN_MS)
                backToFriends()
            }
            AppDialog(
                onDismissRequest = backToFriends,
                title = stringResource(R.string.connection_lost),
                actions = { DialogAction(stringResource(R.string.spectate_back_to_friends), backToFriends) }
            ) {
                DialogText(stringResource(R.string.spectate_lost_text))
            }
        }
        SpectatePhase.FAILED -> {
            val (title, text) = failureTexts(state.failure, state.max)
            AppDialog(
                onDismissRequest = backToFriends,
                title = title,
                actions = { DialogAction(stringResource(R.string.spectate_back_to_friends), backToFriends) }
            ) {
                DialogText(text)
            }
        }
        else -> Unit
    }
}

@Composable
private fun failureTexts(failure: SpectatorClient.Failure?, max: Int): Pair<String, String> = when (failure) {
    SpectatorClient.Failure.FULL ->
        stringResource(R.string.spectate_full_title) to stringResource(R.string.spectate_full_text, max)
    SpectatorClient.Failure.UPDATE_THIS_APP ->
        stringResource(R.string.spectate_update_title) to stringResource(R.string.spectate_update_this)
    SpectatorClient.Failure.UPDATE_HOST_APP ->
        stringResource(R.string.spectate_update_title) to stringResource(R.string.spectate_update_host)
    SpectatorClient.Failure.NO_GAME ->
        stringResource(R.string.spectate_failed_title) to stringResource(R.string.spectate_no_game)
    SpectatorClient.Failure.UNREACHABLE, null ->
        stringResource(R.string.spectate_failed_title) to stringResource(R.string.spectate_unreachable)
}

@Composable
private fun resultText(result: SpectatorResult): String {
    val white = stringResource(R.string.color_white)
    val black = stringResource(R.string.color_black)
    val winner = result.winner
    fun name(side: PieceColor) = if (side == PieceColor.WHITE) white else black
    return when (result.outcome) {
        SpectatorOutcome.CHECKMATE -> winner?.let { stringResource(R.string.checkmate_winner, name(it)) }
        SpectatorOutcome.TIMEOUT -> winner?.let { stringResource(R.string.timeout_winner, name(it)) }
        SpectatorOutcome.RESIGNATION -> winner?.let { stringResource(R.string.resignation_winner, name(it)) }
        SpectatorOutcome.LEFT -> winner?.let { stringResource(R.string.spectate_left_win, name(it.opposite()), name(it)) }
        SpectatorOutcome.STALEMATE -> stringResource(R.string.stalemate)
        SpectatorOutcome.FIFTY_MOVES -> stringResource(R.string.draw_50_moves)
        SpectatorOutcome.REPETITION -> stringResource(R.string.draw_repetition)
        SpectatorOutcome.INSUFFICIENT_MATERIAL -> stringResource(R.string.draw_material)
        SpectatorOutcome.TIMEOUT_DRAW -> stringResource(R.string.timeout_draw)
        SpectatorOutcome.AGREEMENT -> stringResource(R.string.draw_agreement)
        SpectatorOutcome.INTERRUPTED -> stringResource(R.string.spectate_interrupted)
        SpectatorOutcome.UNKNOWN -> null
    } ?: stringResource(R.string.game_over_title)
}

@Composable
private fun SpectatorPlayerRow(
    view: SpectatorView,
    side: PieceColor,
    state: SpectateUiState,
    material: cc.skysparkle.matewave.engine.MaterialBalance
) {
    val player = if (side == PieceColor.WHITE) view.white else view.black
    val active = view.result == null && view.board.sideToMove == side
    // Known friends get their saved avatar; nothing large travels to spectators.
    val avatar = remember(player.id) {
        if (player.id.isEmpty()) null else FriendsStore.list().firstOrNull { it.userId == player.id }?.avatarBase64
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) ChessGreen else ink.copy(alpha = 0.15f))
        )
        Spacer(Modifier.width(10.dp))
        AvatarCircle(base64 = avatar, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                player.name.ifBlank { stringResource(R.string.player_default_name) },
                color = if (active) ink else ink.copy(alpha = 0.75f),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (player.elo > 0) {
                Text(
                    stringResource(R.string.friends_elo_value, player.elo),
                    color = ink.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            CapturedPiecesRow(
                captured = if (side == PieceColor.WHITE) material.capturedByWhite else material.capturedByBlack,
                capturedColor = side.opposite(),
                advantage = if (side == PieceColor.WHITE) material.advantage else -material.advantage
            )
        }
        val millis = if (side == PieceColor.WHITE) state.whiteMs else state.blackMs
        if (millis != null) BigClock(millis, active)
    }
}

private const val WIFI_CHECK_MS = 2_000L
private const val LOST_RETURN_MS = 4_000L
