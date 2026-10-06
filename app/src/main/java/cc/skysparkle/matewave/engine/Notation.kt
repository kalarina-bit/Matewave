package cc.skysparkle.matewave.engine

/**
 * Standard Algebraic Notation. SAN produced here matches Lichess notation without check marks
 * (e.g. "Nbd7", "exd6", "e8=Q", "O-O"), which is the key format of the opening data.
 */
object Notation {

    fun isCastle(board: Board, move: Move): Boolean {
        if (move.isCastleKingSide || move.isCastleQueenSide) return true
        val piece = board.squares[move.from] ?: return false
        return piece.type == PieceType.KING && kotlin.math.abs(move.to % 8 - move.from % 8) == 2
    }

    fun toSan(
        board: Board,
        move: Move,
        legal: List<Move> = ChessEngine.legalMoves(board),
        withCheck: Boolean = false
    ): String {
        val piece = board.squares[move.from] ?: return move.toUci()
        val base = if (isCastle(board, move)) {
            if (move.to % 8 > move.from % 8) "O-O" else "O-O-O"
        } else {
            val capture = board.squares[move.to] != null || move.isEnPassant
            val dest = Move.squareName(move.to)
            buildString {
                if (piece.type == PieceType.PAWN) {
                    if (capture) append('a' + move.from % 8).append('x')
                    append(dest)
                    move.promotion?.let { append('=').append(it.symbol) }
                } else {
                    append(piece.type.symbol)
                    val rivals = legal.filter {
                        it.to == move.to && it.from != move.from &&
                            board.squares[it.from]?.type == piece.type
                    }
                    if (rivals.isNotEmpty()) {
                        val sameFile = rivals.any { it.from % 8 == move.from % 8 }
                        val sameRank = rivals.any { it.from / 8 == move.from / 8 }
                        when {
                            !sameFile -> append('a' + move.from % 8)
                            !sameRank -> append('1' + move.from / 8)
                            else -> append(Move.squareName(move.from))
                        }
                    }
                    if (capture) append('x')
                    append(dest)
                }
            }
        }
        if (!withCheck) return base
        val after = ChessEngine.applyMove(board, move)
        if (!ChessEngine.isInCheck(after, after.sideToMove)) return base
        return base + if (ChessEngine.legalMoves(after).isEmpty()) "#" else "+"
    }

    /** Strips check marks, annotations and "=" so SAN from any source compares equal. */
    fun normalizeSan(san: String): String =
        san.trim()
            .replace("0-0-0", "O-O-O").replace("0-0", "O-O")
            .trimEnd('+', '#', '!', '?')
            .replace("=", "")
            .trim()

    fun fromSan(board: Board, san: String, legal: List<Move> = ChessEngine.legalMoves(board)): Move? {
        val wanted = normalizeSan(san)
        if (wanted.isEmpty()) return null
        legal.firstOrNull { normalizeSan(toSan(board, it, legal)) == wanted }?.let { return it }

        // Over-disambiguated or loosely written SAN, e.g. "Ngf3" or "e8Q".
        val m = Regex("^([KQRBN])?([a-h])?([1-8])?x?([a-h][1-8])([QRBN])?$").find(wanted) ?: return null
        val type = when (m.groupValues[1]) {
            "K" -> PieceType.KING; "Q" -> PieceType.QUEEN; "R" -> PieceType.ROOK
            "B" -> PieceType.BISHOP; "N" -> PieceType.KNIGHT; else -> PieceType.PAWN
        }
        val fromFile = m.groupValues[2].firstOrNull()?.let { it - 'a' }
        val fromRank = m.groupValues[3].firstOrNull()?.let { it - '1' }
        val to = Move.squareFromName(m.groupValues[4])
        val promo = when (m.groupValues[5]) {
            "Q" -> PieceType.QUEEN; "R" -> PieceType.ROOK; "B" -> PieceType.BISHOP; "N" -> PieceType.KNIGHT
            else -> null
        }
        return legal.filter {
            it.to == to && board.squares[it.from]?.type == type && it.promotion == promo &&
                (fromFile == null || it.from % 8 == fromFile) &&
                (fromRank == null || it.from / 8 == fromRank)
        }.singleOrNull()
    }

    /** Position part of a FEN without move counters, used to compare positions. */
    fun positionKey(board: Board): String =
        board.toFen().substringBeforeLast(' ').substringBeforeLast(' ')

    fun isStandardStart(board: Board): Boolean = positionKey(board) == positionKey(Board.startPosition())
}
