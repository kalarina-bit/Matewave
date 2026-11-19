package cc.skysparkle.matewave.engine

enum class PieceColor { WHITE, BLACK }

enum class PieceType(val symbol: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K')
}

data class Piece(val type: PieceType, val color: PieceColor) {
    fun fenChar(): Char =
        if (color == PieceColor.WHITE) type.symbol else type.symbol.lowercaseChar()
}

fun PieceColor.opposite(): PieceColor =
    if (this == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
