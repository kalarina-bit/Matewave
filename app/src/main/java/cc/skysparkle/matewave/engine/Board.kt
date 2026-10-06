package cc.skysparkle.matewave.engine

data class Board(
    val squares: Array<Piece?>,
    val sideToMove: PieceColor,
    val whiteKingSideCastle: Boolean,
    val whiteQueenSideCastle: Boolean,
    val blackKingSideCastle: Boolean,
    val blackQueenSideCastle: Boolean,
    val enPassantTarget: Int?,
    val halfmoveClock: Int,
    val fullmoveNumber: Int
) {
    fun pieceAt(sq: Int): Piece? = squares[sq]

    fun deepCopySquares(): Array<Piece?> = squares.copyOf()

    fun kingSquare(color: PieceColor): Int {
        for (i in 0 until 64) {
            val p = squares[i]
            if (p != null && p.type == PieceType.KING && p.color == color) return i
        }
        return -1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Board) return false
        return squares.contentEquals(other.squares) &&
            sideToMove == other.sideToMove &&
            whiteKingSideCastle == other.whiteKingSideCastle &&
            whiteQueenSideCastle == other.whiteQueenSideCastle &&
            blackKingSideCastle == other.blackKingSideCastle &&
            blackQueenSideCastle == other.blackQueenSideCastle &&
            enPassantTarget == other.enPassantTarget
    }

    override fun hashCode(): Int {
        var result = squares.contentHashCode()
        result = 31 * result + sideToMove.hashCode()
        return result
    }

    companion object {
        fun startPosition(): Board = fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")

        fun fromFen(fen: String): Board {
            val parts = fen.trim().split(" ")
            val rows = parts[0].split("/")
            val squares = arrayOfNulls<Piece?>(64)
            for (r in 0 until 8) {
                val rank = 7 - r
                var file = 0
                for (c in rows[r]) {
                    if (c.isDigit()) {
                        file += c.digitToInt()
                    } else {
                        val color = if (c.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                        val type = when (c.uppercaseChar()) {
                            'P' -> PieceType.PAWN
                            'N' -> PieceType.KNIGHT
                            'B' -> PieceType.BISHOP
                            'R' -> PieceType.ROOK
                            'Q' -> PieceType.QUEEN
                            'K' -> PieceType.KING
                            else -> throw IllegalArgumentException("Bad FEN char $c")
                        }
                        squares[rank * 8 + file] = Piece(type, color)
                        file++
                    }
                }
            }
            val side = if (parts.getOrElse(1) { "w" } == "w") PieceColor.WHITE else PieceColor.BLACK
            val castling = parts.getOrElse(2) { "-" }
            val ep = parts.getOrElse(3) { "-" }
            val halfmove = parts.getOrElse(4) { "0" }.toIntOrNull() ?: 0
            val fullmove = parts.getOrElse(5) { "1" }.toIntOrNull() ?: 1
            return Board(
                squares = squares,
                sideToMove = side,
                whiteKingSideCastle = castling.contains('K'),
                whiteQueenSideCastle = castling.contains('Q'),
                blackKingSideCastle = castling.contains('k'),
                blackQueenSideCastle = castling.contains('q'),
                enPassantTarget = if (ep == "-") null else Move.squareFromName(ep),
                halfmoveClock = halfmove,
                fullmoveNumber = fullmove
            )
        }
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0 until 8) {
                val p = squares[r * 8 + f]
                if (p == null) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(p.fenChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (r != 0) sb.append('/')
        }
        sb.append(if (sideToMove == PieceColor.WHITE) " w " else " b ")
        val castle = buildString {
            if (whiteKingSideCastle) append('K')
            if (whiteQueenSideCastle) append('Q')
            if (blackKingSideCastle) append('k')
            if (blackQueenSideCastle) append('q')
        }
        sb.append(if (castle.isEmpty()) "-" else castle)
        sb.append(' ')
        sb.append(if (enPassantTarget == null) "-" else Move.squareName(enPassantTarget))
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }
}
