package cc.skysparkle.matewave.engine

data class MaterialBalance(
    val capturedByWhite: List<PieceType>,
    val capturedByBlack: List<PieceType>,
    val advantage: Int
)

/** Captured pieces and material balance, shown next to the board. */
object Material {
    fun points(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT -> 3
        PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 0
    }

    private fun initialCount(type: PieceType): Int = when (type) {
        PieceType.PAWN -> 8
        PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK -> 2
        PieceType.QUEEN, PieceType.KING -> 1
    }

    private val DISPLAY_ORDER = listOf(
        PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN
    )

    fun balance(board: Board): MaterialBalance {
        val white = IntArray(PieceType.values().size)
        val black = IntArray(PieceType.values().size)
        var whitePoints = 0
        var blackPoints = 0
        for (piece in board.squares) {
            if (piece == null) continue
            if (piece.color == PieceColor.WHITE) {
                white[piece.type.ordinal]++
                whitePoints += points(piece.type)
            } else {
                black[piece.type.ordinal]++
                blackPoints += points(piece.type)
            }
        }
        return MaterialBalance(
            capturedByWhite = missing(black),
            capturedByBlack = missing(white),
            advantage = whitePoints - blackPoints
        )
    }

    private fun missing(counts: IntArray): List<PieceType> {
        val promoted = DISPLAY_ORDER.filter { it != PieceType.PAWN }
            .sumOf { maxOf(0, counts[it.ordinal] - initialCount(it)) }
        val result = ArrayList<PieceType>()
        for (type in DISPLAY_ORDER) {
            val gone = if (type == PieceType.PAWN) {
                initialCount(type) - counts[type.ordinal] - promoted
            } else {
                initialCount(type) - counts[type.ordinal]
            }
            repeat(maxOf(0, gone)) { result.add(type) }
        }
        return result
    }
}
