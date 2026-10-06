package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.layout.padding

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.DialogText
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.network.ConnectionState
import cc.skysparkle.matewave.ui.components.ChatDialog
import cc.skysparkle.matewave.ui.components.ChessBoardView
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.PromotionDialog
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.viewmodel.GameMode
import cc.skysparkle.matewave.viewmodel.GameUiState
import cc.skysparkle.matewave.viewmodel.GameViewModel

@Composable
fun GameScreen(viewModel: GameViewModel, onExit: () -> Unit) {
    val exitGame = {
        viewModel.leaveNetworkGame()
        onExit()
    }
    val state by viewModel.uiState.collectAsState()
    var showChat by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val showOpeningBar = remember { cc.skysparkle.matewave.settings.BoardAppearance(context).showOpening }

    // Leaving an ongoing network game counts as a loss, so ask first; a game against the AI is saved.
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmResign by remember { mutableStateOf(false) }
    val requestExit: () -> Unit = {
        val ongoing = state.status == GameStatus.ONGOING && state.gameStarted
        if (ongoing && state.mode == GameMode.VS_PLAYER_NETWORK) {
            confirmLeave = true
        } else {
            exitGame()
        }
    }
    androidx.activity.compose.BackHandler(onBack = requestExit)

    var gameOverHidden by remember(state.status, state.opponentLeft) { mutableStateOf(false) }

    // Opening shown in the top bar: "A40 · Englund Gambit" with a THEORY / out-of-book badge.
    val start = state.startBoard
    val sans = state.moveSans
    val showOpening = showOpeningBar && state.mode != GameMode.PUZZLE
    val fromStart = remember(start) { cc.skysparkle.matewave.engine.Notation.isStandardStart(start) }
    val opening = remember(sans, fromStart) {
        if (fromStart) cc.skysparkle.matewave.openings.OpeningBook.openingName(sans) else null
    }
    val bookPlies = remember(sans, fromStart) {
        if (fromStart) cc.skysparkle.matewave.openings.OpeningBook.bookDepth(sans) else 0
    }
    val inBook = sans.isNotEmpty() && bookPlies >= sans.size
    val bookStatus = when {
        !fromStart || sans.isEmpty() -> null
        inBook -> stringResource(R.string.opening_in_theory)
        bookPlies < cc.skysparkle.matewave.openings.OpeningBook.MAX_TREE_PLY ->
            stringResource(R.string.opening_out_of_book, bookPlies / 2 + 1)
        else -> null
    }

    GlassScaffold(
        title = "",
        onBack = requestExit,
        titleContent = if (showOpening && opening != null) {
            { OpeningTitle("${opening.eco} · ${opening.name}", bookStatus, inBook) }
        } else null
    ) {
        if (state.mode == GameMode.VS_PLAYER_NETWORK) ConnectionBanner(state)
        TrustBanners(state)

        val material = remember(state.board) { cc.skysparkle.matewave.engine.Material.balance(state.board) }
        val bottomColor = state.myColor
        val topColor = if (bottomColor == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
        val kingInCheckSquare = if (ChessEngine.isInCheck(state.board, state.board.sideToMove)) {
            state.board.kingSquare(state.board.sideToMove)
        } else null

        PlayerRow(state, topColor, isMe = false, material = material)

        ChessBoardView(
            board = state.board,
            selectedSquare = state.selectedSquare,
            legalTargets = state.legalTargetsFromSelected,
            kingInCheckSquare = kingInCheckSquare,
            flipped = state.myColor == PieceColor.BLACK,
            onSquareClick = viewModel::onSquareClicked,
            lastMove = state.lastMove,
            interactionEnabled = state.status == GameStatus.ONGOING && state.pendingPromotion == null &&
                (state.mode != GameMode.VS_PLAYER_NETWORK ||
                    (state.connectionState == ConnectionState.CONNECTED &&
                        state.handshake == cc.skysparkle.matewave.viewmodel.HandshakeState.PLAYING))
        )

        PlayerRow(state, bottomColor, isMe = true, material = material)

        // The move ribbon: only moves here, the opening name lives in the top bar.
        if (showOpening) {
            cc.skysparkle.matewave.ui.components.MovesBar(
                sans = sans,
                startBoard = start,
                currentIndex = sans.size,
                opening = null,
                bookPlies = bookPlies
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = state.opponentNotice == cc.skysparkle.matewave.viewmodel.OpponentNotice.DRAW_DECLINED,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_draw, size = 18.dp, tint = Color(0xFFFFB86B))
                Text(stringResource(R.string.draw_declined_notice), color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
        androidx.compose.runtime.LaunchedEffect(state.opponentNotice) {
            if (state.opponentNotice == cc.skysparkle.matewave.viewmodel.OpponentNotice.DRAW_DECLINED) {
                kotlinx.coroutines.delay(NOTICE_VISIBLE_MS)
                viewModel.dismissOpponentNotice()
            }
        }

        if (state.mode == GameMode.VS_PLAYER_NETWORK && state.status == GameStatus.ONGOING) {
            // Both actions neutral: resigning must not look like the main action.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassOutlinedButton(
                    onClick = { viewModel.offerDraw() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.drawOfferSentByMe
                ) {
                    Text(
                        if (state.drawOfferSentByMe) stringResource(R.string.draw_offer_waiting) else stringResource(R.string.offer_draw),
                        color = ink,
                        maxLines = 1
                    )
                }
                GlassOutlinedButton(onClick = { confirmResign = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.resign), color = ink, maxLines = 1)
                }
            }
            ChatButton(
                unreadCount = state.unreadChatCount,
                onClick = { viewModel.markChatRead(); showChat = true }
            )
        }

        // The result dialog was closed to look at the board: one tap brings it back.
        if (state.status != GameStatus.ONGOING && gameOverHidden) {
            GlassOutlinedButton(onClick = { gameOverHidden = false }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.game_over_title), color = ink, maxLines = 1)
            }
        }
    }

    if (confirmLeave) {
        AppDialog(
            onDismissRequest = { confirmLeave = false },
            title = stringResource(R.string.game_leave_title),
            actions = {
                DialogAction(stringResource(R.string.account_cancel), { confirmLeave = false }, color = ink.copy(alpha = 0.75f))
                DialogAction(stringResource(R.string.game_leave_confirm), { confirmLeave = false; exitGame() })
            }
        ) {
            DialogText(stringResource(R.string.game_leave_network))
        }
    }

    if (confirmResign) {
        AppDialog(
            onDismissRequest = { confirmResign = false },
            title = stringResource(R.string.game_resign_title),
            actions = {
                DialogAction(stringResource(R.string.account_cancel), { confirmResign = false }, color = ink.copy(alpha = 0.75f))
                DialogAction(stringResource(R.string.resign), { confirmResign = false; viewModel.resign() })
            }
        ) {
            DialogText(stringResource(R.string.game_resign_text))
        }
    }

    state.pendingPromotion?.let {
        PromotionDialog(
            color = state.board.sideToMove,
            onChoose = viewModel::choosePromotion,
            onDismiss = viewModel::cancelPromotion
        )
    }

    if (state.drawOfferFromOpponent) {
        IncomingDrawOfferDialog(
            onAccept = { viewModel.respondToDrawOffer(true) },
            onDecline = { viewModel.respondToDrawOffer(false) }
        )
    }

    if (showChat) {
        ChatDialog(
            messages = state.chatMessages,
            onSendText = viewModel::sendChatMessage,
            onSendImage = viewModel::sendChatImage,
            onDismiss = { showChat = false }
        )
    }

    if (state.status != GameStatus.ONGOING && !gameOverHidden) {
        GameOverDialog(
            state.status, state,
            onRematch = {
                viewModel.rematchVsAi()
            },
            onExit = exitGame,
            onDismiss = { gameOverHidden = true },
            onOfferRematch = { viewModel.offerRematch() }
        )
    }

    if (state.rematchOfferFromOpponent) {
        RematchOfferDialog(
            onAccept = { viewModel.acceptRematch() },
            onDecline = { viewModel.declineRematch() }
        )
    }
}

@Composable
private fun RematchOfferDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AppDialog(
        onDismissRequest = onDecline,
        title = stringResource(R.string.rematch_offer_title),
        actions = {
            DialogAction(stringResource(R.string.rematch_decline), onDecline, color = ink.copy(alpha = 0.75f))
            DialogAction(stringResource(R.string.rematch_accept), onAccept)
        }
    ) {
        DialogText(stringResource(R.string.rematch_offer_text))
    }
}

@Composable
private fun ChatButton(unreadCount: Int, onClick: () -> Unit) {
    Box {
        GlassOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(R.drawable.ic_chat)
            Text(stringResource(R.string.chat_button), color = ink)
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(ChessGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(unreadCount.toString(), color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun IncomingDrawOfferDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    AppDialog(
        onDismissRequest = onDecline,
        title = stringResource(R.string.draw_offer_received_title),
        actions = {
            DialogAction(stringResource(R.string.decline), onDecline, color = ink.copy(alpha = 0.75f))
            DialogAction(stringResource(R.string.accept), onAccept)
        }
    ) {
        DialogText(stringResource(R.string.draw_offer_received_text))
    }
}

@Composable
private fun GameOverDialog(
    status: GameStatus,
    state: GameUiState,
    onRematch: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit,
    onOfferRematch: () -> Unit = {}
) {
    val colorWhite = stringResource(R.string.color_white)
    val colorBlack = stringResource(R.string.color_black)
    val message = when (status) {
        GameStatus.CHECKMATE -> {
            val winner = if (state.board.sideToMove == PieceColor.WHITE) colorBlack else colorWhite
            stringResource(R.string.checkmate_winner, winner)
        }
        GameStatus.STALEMATE -> stringResource(R.string.stalemate)
        GameStatus.DRAW_50_MOVES -> stringResource(R.string.draw_50_moves)
        GameStatus.DRAW_REPETITION -> stringResource(R.string.draw_repetition)
        GameStatus.DRAW_MATERIAL -> stringResource(R.string.draw_material)
        GameStatus.TIMEOUT -> {
            val winner = if (state.timedOutSide == PieceColor.WHITE) colorBlack else colorWhite
            stringResource(R.string.timeout_winner, winner)
        }
        GameStatus.TIMEOUT_DRAW -> stringResource(R.string.timeout_draw)
        GameStatus.RESIGNATION -> {
            val winner = if (state.resignedSide == PieceColor.WHITE) colorBlack else colorWhite
            stringResource(R.string.resignation_winner, winner)
        }
        GameStatus.DRAW_AGREEMENT -> stringResource(R.string.draw_agreement)
        GameStatus.ONGOING -> return
    }

    val waitingForRematch = state.mode == GameMode.VS_PLAYER_NETWORK && !state.opponentLeft && state.rematchOfferedByMe

    val shownMessage = if (state.opponentLeftMidGame) stringResource(R.string.opponent_left_win) else message
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.game_over_title),
        actions = {
            DialogAction(stringResource(R.string.back_to_menu_short), onExit, color = ink.copy(alpha = 0.75f))
            if (state.mode == GameMode.VS_AI) {
                DialogAction(stringResource(R.string.play_again), onRematch)
            } else if (state.mode == GameMode.VS_PLAYER_NETWORK && !state.opponentLeft && !waitingForRematch) {
                DialogAction(stringResource(R.string.rematch_offer), onOfferRematch)
            }
        }
    ) {
        DialogText(shownMessage)
        if (waitingForRematch) {
            DialogText(stringResource(R.string.rematch_waiting))
        }

        if (state.opponentNotice == cc.skysparkle.matewave.viewmodel.OpponentNotice.REMATCH_DECLINED) {
            Text(
                stringResource(R.string.rematch_declined_notice),
                color = Color(0xFFFFB86B),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (state.opponentLeft && !state.opponentLeftMidGame) {
            Text(
                stringResource(R.string.opponent_left_after_game),
                color = Color(0xFFFFB86B),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private const val NO_LIMIT_THRESHOLD_SECONDS = 365L * 24 * 3600

private const val NOTICE_VISIBLE_MS = 4_000L

@Composable
private fun SideCaptured(material: cc.skysparkle.matewave.engine.MaterialBalance, side: PieceColor) {
    cc.skysparkle.matewave.ui.components.CapturedPiecesRow(
        captured = if (side == PieceColor.WHITE) material.capturedByWhite else material.capturedByBlack,
        capturedColor = if (side == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE,
        advantage = if (side == PieceColor.WHITE) material.advantage else -material.advantage
    )
}

/** Opening name in the top bar with a small status badge underneath. */
@Composable
private fun OpeningTitle(name: String, status: String?, inBook: Boolean) {
    Column(modifier = Modifier.padding(start = 4.dp)) {
        Text(
            name,
            color = ink,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        if (status != null) {
            val tint = if (inBook) ChessGreen else Color(0xFFFFB86B)
            Text(
                status.uppercase(),
                color = tint,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * A player strip: activity dot, name and rating, captured pieces, and a large clock. The side to
 * move gets a bright clock on a soft plate; the other side is dimmed. No cards around it.
 */
@Composable
private fun PlayerRow(
    state: GameUiState,
    side: PieceColor,
    isMe: Boolean,
    material: cc.skysparkle.matewave.engine.MaterialBalance
) {
    val active = state.status == GameStatus.ONGOING && state.board.sideToMove == side
    // Re-read after the game: the rating changes when it ends.
    val profile = remember(state.status) { cc.skysparkle.matewave.data.ProfileStore.get() }
    val (name, elo, avatar) = when {
        isMe -> Triple(profile.name, profile.elo, profile.avatarBase64)
        state.mode == GameMode.VS_AI -> Triple(
            stringResource(R.string.game_ai_name, stringResource(difficultyName(state.aiDifficulty))),
            cc.skysparkle.matewave.data.ProfileStore.aiNominalElo(state.aiDifficulty),
            null
        )
        else -> Triple(
            state.opponentProfile?.name ?: stringResource(R.string.game_opponent),
            state.opponentProfile?.elo,
            state.opponentProfile?.avatarBase64
        )
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name,
                    color = if (active) ink else ink.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!isMe && state.mode == GameMode.VS_PLAYER_NETWORK &&
                    state.handshake == cc.skysparkle.matewave.viewmodel.HandshakeState.PLAYING &&
                    !state.opponentUntrusted && !state.opponentUnverified
                ) {
                    AppIcon(R.drawable.ic_encrypted, size = 14.dp, tint = ChessGreen)
                }
            }
            if (elo != null) {
                Text(stringResource(R.string.friends_elo_value, elo), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
            SideCaptured(material, side)
        }
        state.clockState?.let { cs ->
            BigClock(if (side == PieceColor.WHITE) cs.whiteMillis else cs.blackMillis, active)
        }
    }
}

@Composable
private fun BigClock(millis: Long, active: Boolean) {
    val totalSeconds = millis / 1000
    val low = active && totalSeconds < 20
    val color = when {
        low -> Color(0xFFFF6B6B)
        active -> ink
        else -> ink.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) ink.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (totalSeconds >= NO_LIMIT_THRESHOLD_SECONDS) {
            AppIcon(R.drawable.ic_infinity, size = 28.dp, tint = color)
        } else {
            Text(
                "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                color = color,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ConnectionBanner(state: GameUiState) {
    when (state.connectionState) {
        ConnectionState.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(color = ink, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Text(stringResource(R.string.connecting_to_opponent), color = ink, style = MaterialTheme.typography.bodyMedium)
        }
        ConnectionState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(R.drawable.ic_error, size = 18.dp, tint = Color(0xFFFF6B6B))
            Text(stringResource(R.string.connection_failed), color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodyMedium)
        }
        ConnectionState.DISCONNECTED -> if (state.gameStarted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppIcon(R.drawable.ic_signal_disconnected, size = 18.dp, tint = Color(0xFFFF6B6B))
                Text(stringResource(R.string.connection_lost), color = Color(0xFFFF6B6B), style = MaterialTheme.typography.bodyMedium)
            }
        }
        ConnectionState.CONNECTED -> Unit
    }
}

@Composable
private fun TrustBanners(state: GameUiState) {
    val (icon, text, color) = when {
        state.opponentUntrusted -> Triple(R.drawable.ic_no_encryption, stringResource(R.string.trust_impersonation), Color(0xFFFF6B6B))
        state.opponentOutdated -> Triple(R.drawable.ic_error, stringResource(R.string.trust_outdated), Color(0xFFFF6B6B))
        state.opponentUnverified -> Triple(R.drawable.ic_no_encryption, stringResource(R.string.trust_unverified), Color(0xFFFFC46B))
        else -> Triple(0, null, Color.Unspecified)
    }
    if (text != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(icon, size = 16.dp, tint = color)
            Text(text, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
    state.newPeerFingerprint?.let { fingerprint ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon(R.drawable.ic_key, size = 16.dp, tint = Color(0xFFFFC46B))
            Text(stringResource(R.string.trust_first_meeting, fingerprint), color = Color(0xFFFFC46B), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Name of an AI level, shared by the setup, game and home screens. */
internal fun difficultyName(d: cc.skysparkle.matewave.ai.AiDifficulty): Int = when (d) {
    cc.skysparkle.matewave.ai.AiDifficulty.EASY -> R.string.difficulty_easy
    cc.skysparkle.matewave.ai.AiDifficulty.MEDIUM -> R.string.difficulty_medium
    cc.skysparkle.matewave.ai.AiDifficulty.HARD -> R.string.difficulty_hard
    cc.skysparkle.matewave.ai.AiDifficulty.EXPERT -> R.string.difficulty_expert
}
