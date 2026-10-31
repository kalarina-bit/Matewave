package cc.skysparkle.matewave.engine

enum class GameStatus {
    ONGOING, CHECKMATE, STALEMATE, DRAW_50_MOVES, DRAW_REPETITION, DRAW_MATERIAL, TIMEOUT,
    /** A flag fell, but the other side has too little material to ever mate. */
    TIMEOUT_DRAW,
    RESIGNATION, DRAW_AGREEMENT
}

/**
 * Rules of chess: legal move generation (castling, en passant, promotion), move
 * application and game-status detection (mate, stalemate, 50-move rule, threefold
 * repetition, insufficient material).
 */
object ChessEngine {
    fun isSquareAttacked(board: Board, target: Int, byColor: PieceColor): Boolean =
        Position(board).isAttacked(target, byColor.ordinal)

    fun isInCheck(board: Board, color: PieceColor): Boolean {
        val kingSq = board.kingSquare(color)
        if (kingSq == -1) return false
        return isSquareAttacked(board, kingSq, color.opposite())
    }

    /** Square of the side to move's king while it is in check, for highlighting. */
    fun checkedKingSquare(board: Board): Int? =
        if (isInCheck(board, board.sideToMove)) board.kingSquare(board.sideToMove) else null

    fun legalMoves(board: Board): List<Move> {
        val position = Position(board)
        val moves = IntArray(Position.MAX_MOVES)
        val end = position.generate(moves, 0, tacticalOnly = false)
        val result = ArrayList<Move>(end)
        for (i in 0 until end) {
            if (position.isLegal(moves[i])) result.add(Position.toMove(moves[i]))
        }
        return result
    }

    fun legalMovesFrom(board: Board, from: Int): List<Move> = legalMoves(board).filter { it.from == from }

    fun applyMove(board: Board, move: Move): Board {
        if (board.squares[move.from] == null) return board
        val position = Position(board)
        position.make(Position.encode(move))
        return position.toBoard()
    }

    fun gameStatus(board: Board, positionHistory: List<String> = emptyList()): GameStatus {
        val moves = legalMoves(board)
        if (moves.isEmpty()) {
            return if (isInCheck(board, board.sideToMove)) GameStatus.CHECKMATE else GameStatus.STALEMATE
        }
        if (board.halfmoveClock >= 100) return GameStatus.DRAW_50_MOVES
        val key = Notation.positionKey(board)
        if (positionHistory.count { it == key } >= 3) return GameStatus.DRAW_REPETITION
        if (isDeadPosition(board)) return GameStatus.DRAW_MATERIAL
        return GameStatus.ONGOING
    }

    /**
     * Neither side can ever mate: kings alone, a single minor piece, or only bishops that all
     * stand on squares of one colour.
     */
    fun isDeadPosition(board: Board): Boolean = Position(board).isDeadPosition()

    /**
     * Whether [color] has enough material to mate at all. A player whose flag falls loses only if
     * the opponent could still mate; otherwise the game is drawn.
     */
    fun canMate(board: Board, color: PieceColor): Boolean {
        var minors = 0
        for (p in board.squares) {
            if (p == null || p.color != color) continue
            when (p.type) {
                PieceType.KING -> Unit
                PieceType.BISHOP, PieceType.KNIGHT -> minors++
                else -> return true
            }
        }
        return minors >= 2
    }
}
