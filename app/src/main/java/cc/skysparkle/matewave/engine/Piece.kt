package cc.skysparkle.matewave.engine

enum class PieceColor { WHITE, BLACK }

enum class PieceType(val symbol: Char) {
    PAWN('P'), KNIGHT('N'), BISHOP('B'), ROOK('R'), QUEEN('Q'), KING('K')
}

data class Piece(val type: PieceType, val color: PieceColor) {
    fun drawableRes(): Int = when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.PAWN -> cc.skysparkle.matewave.R.drawable.piece_white_pawn
            PieceType.KNIGHT -> cc.skysparkle.matewave.R.drawable.piece_white_knight
            PieceType.BISHOP -> cc.skysparkle.matewave.R.drawable.piece_white_bishop
            PieceType.ROOK -> cc.skysparkle.matewave.R.drawable.piece_white_rook
            PieceType.QUEEN -> cc.skysparkle.matewave.R.drawable.piece_white_queen
            PieceType.KING -> cc.skysparkle.matewave.R.drawable.piece_white_king
        }
        PieceColor.BLACK -> when (type) {
            PieceType.PAWN -> cc.skysparkle.matewave.R.drawable.piece_black_pawn
            PieceType.KNIGHT -> cc.skysparkle.matewave.R.drawable.piece_black_knight
            PieceType.BISHOP -> cc.skysparkle.matewave.R.drawable.piece_black_bishop
            PieceType.ROOK -> cc.skysparkle.matewave.R.drawable.piece_black_rook
            PieceType.QUEEN -> cc.skysparkle.matewave.R.drawable.piece_black_queen
            PieceType.KING -> cc.skysparkle.matewave.R.drawable.piece_black_king
        }
    }

    fun unicode(): String = when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.PAWN -> "\u2659"
            PieceType.KNIGHT -> "\u2658"
            PieceType.BISHOP -> "\u2657"
            PieceType.ROOK -> "\u2656"
            PieceType.QUEEN -> "\u2655"
            PieceType.KING -> "\u2654"
        }
        PieceColor.BLACK -> when (type) {
            PieceType.PAWN -> "\u265F"
            PieceType.KNIGHT -> "\u265E"
            PieceType.BISHOP -> "\u265D"
            PieceType.ROOK -> "\u265C"
            PieceType.QUEEN -> "\u265B"
            PieceType.KING -> "\u265A"
        }
    }

    fun fenChar(): Char =
        if (color == PieceColor.WHITE) type.symbol else type.symbol.lowercaseChar()
}

fun PieceColor.opposite(): PieceColor =
    if (this == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
