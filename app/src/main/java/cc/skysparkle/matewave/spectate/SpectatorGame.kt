package cc.skysparkle.matewave.spectate

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.opposite

/** What a spectator sees of a game. */
data class SpectatorView(
    val gameId: String,
    val white: SpectatorPlayer,
    val black: SpectatorPlayer,
    val startBoard: Board,
    val board: Board,
    val movesUci: List<String>,
    val moveSans: List<String>,
    val lastMove: Move?,
    val initialSeconds: Int,
    val incrementSeconds: Int,
    val clock: SpectatorClock?,
    val result: SpectatorResult?
) {
    val ply: Int get() = movesUci.size
}

/**
 * The spectator's copy of the game, rebuilt from a snapshot and moved forward by live moves. Moves
 * are checked against the rules; anything that does not follow on asks for a fresh snapshot.
 */
class SpectatorGame {
    enum class Applied { APPLIED, IGNORED, NEEDS_RESYNC }

    var view: SpectatorView? = null
        private set

    fun load(snapshot: SpectatorSnapshot): Boolean {
        val start = Board.startPosition()
        var board = start
        val sans = ArrayList<String>(snapshot.movesUci.size)
        var last: Move? = null
        for (uci in snapshot.movesUci) {
            val move = resolve(board, uci) ?: return false
            sans.add(Notation.toSan(board, move))
            board = ChessEngine.applyMove(board, move)
            last = move
        }
        view = SpectatorView(
            gameId = snapshot.gameId,
            white = snapshot.white,
            black = snapshot.black,
            startBoard = start,
            board = board,
            movesUci = snapshot.movesUci,
            moveSans = sans,
            lastMove = last,
            initialSeconds = snapshot.initialSeconds,
            incrementSeconds = snapshot.incrementSeconds,
            clock = snapshot.clock,
            result = snapshot.result
        )
        return true
    }

    fun onMove(message: SpectatorMessage.MovePlayed): Applied {
        val current = view ?: return Applied.NEEDS_RESYNC
        if (message.gameId != current.gameId) return Applied.NEEDS_RESYNC
        if (current.result != null || message.ply <= current.ply) return Applied.IGNORED
        if (message.ply != current.ply + 1) return Applied.NEEDS_RESYNC
        val move = resolve(current.board, message.uci) ?: return Applied.NEEDS_RESYNC
        view = current.copy(
            board = ChessEngine.applyMove(current.board, move),
            movesUci = current.movesUci + move.toUci(),
            moveSans = current.moveSans + Notation.toSan(current.board, move),
            lastMove = move,
            clock = message.clock ?: current.clock
        )
        return Applied.APPLIED
    }

    fun onGameOver(message: SpectatorMessage.GameOver): Applied {
        val current = view ?: return Applied.NEEDS_RESYNC
        if (message.gameId != current.gameId) return Applied.NEEDS_RESYNC
        view = current.copy(
            result = message.result,
            clock = (message.clock ?: current.clock)?.copy(running = null)
        )
        return Applied.APPLIED
    }

    fun onClock(clock: SpectatorClock?) {
        val current = view ?: return
        if (clock == null || current.result != null) return
        view = current.copy(clock = clock)
    }

    private fun resolve(board: Board, uci: String): Move? {
        if (uci.length !in 4..5) return null
        val candidate = runCatching { Move.fromUci(uci) }.getOrNull() ?: return null
        if (candidate.from !in 0..63 || candidate.to !in 0..63) return null
        val matches = ChessEngine.legalMoves(board).filter { it.from == candidate.from && it.to == candidate.to }
        return matches.firstOrNull { it.promotion == candidate.promotion } ?: matches.firstOrNull()
    }
}

/** Turns successive host snapshots into the messages spectators need to follow along. */
object SpectatorDiff {
    fun between(old: SpectatorSnapshot?, new: SpectatorSnapshot): List<SpectatorMessage> {
        if (old == null) return emptyList()
        if (old.gameId != new.gameId || old.white != new.white || old.black != new.black) {
            return listOf(SpectatorMessage.NewGame(new))
        }
        val events = ArrayList<SpectatorMessage>()
        val before = old.movesUci
        val after = new.movesUci
        if (after != before) {
            if (after.size <= before.size || after.subList(0, before.size) != before) {
                return listOf(SpectatorMessage.NewGame(new))
            }
            for (i in before.size until after.size) {
                val isLast = i == after.lastIndex
                events += SpectatorMessage.MovePlayed(new.gameId, i + 1, after[i], if (isLast) new.clock else null)
            }
        }
        when {
            old.result == null && new.result != null -> events += SpectatorMessage.GameOver(new.gameId, new.result, new.clock)
            old.result != null && new.result != old.result -> return listOf(SpectatorMessage.NewGame(new))
        }
        return events
    }
}

/** Result of a finished game as spectators are told it. */
object SpectatorResults {
    fun of(
        status: GameStatus,
        sideToMove: PieceColor,
        timedOutSide: PieceColor?,
        resignedSide: PieceColor?,
        leftMidGame: Boolean
    ): SpectatorResult? = when (status) {
        GameStatus.ONGOING -> null
        GameStatus.CHECKMATE -> SpectatorResult(SpectatorOutcome.CHECKMATE, sideToMove.opposite())
        GameStatus.STALEMATE -> SpectatorResult(SpectatorOutcome.STALEMATE, null)
        GameStatus.DRAW_50_MOVES -> SpectatorResult(SpectatorOutcome.FIFTY_MOVES, null)
        GameStatus.DRAW_REPETITION -> SpectatorResult(SpectatorOutcome.REPETITION, null)
        GameStatus.DRAW_MATERIAL -> SpectatorResult(SpectatorOutcome.INSUFFICIENT_MATERIAL, null)
        GameStatus.TIMEOUT -> SpectatorResult(SpectatorOutcome.TIMEOUT, timedOutSide?.opposite())
        GameStatus.TIMEOUT_DRAW -> SpectatorResult(SpectatorOutcome.TIMEOUT_DRAW, null)
        GameStatus.RESIGNATION -> SpectatorResult(
            if (leftMidGame) SpectatorOutcome.LEFT else SpectatorOutcome.RESIGNATION,
            resignedSide?.opposite()
        )
        GameStatus.DRAW_AGREEMENT -> SpectatorResult(SpectatorOutcome.AGREEMENT, null)
    }

    fun leftBy(side: PieceColor) = SpectatorResult(SpectatorOutcome.LEFT, side.opposite())

    val interrupted = SpectatorResult(SpectatorOutcome.INTERRUPTED, null)
}
