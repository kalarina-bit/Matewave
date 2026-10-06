package cc.skysparkle.matewave.engine

data class Move(
    val from: Int,
    val to: Int,
    val promotion: PieceType? = null,
    val isCastleKingSide: Boolean = false,
    val isCastleQueenSide: Boolean = false,
    val isEnPassant: Boolean = false
) {
    fun toUci(): String {
        val promo = promotion?.symbol?.lowercaseChar()?.toString() ?: ""
        return squareName(from) + squareName(to) + promo
    }

    companion object {
        fun squareName(sq: Int): String {
            val file = sq % 8
            val rank = sq / 8
            return "${'a' + file}${rank + 1}"
        }

        fun squareFromName(name: String): Int {
            val file = name[0] - 'a'
            val rank = name[1] - '1'
            return rank * 8 + file
        }

        fun fromUci(uci: String): Move {
            val from = squareFromName(uci.substring(0, 2))
            val to = squareFromName(uci.substring(2, 4))
            val promo = if (uci.length > 4) {
                when (uci[4].lowercaseChar()) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> null
                }
            } else null
            return Move(from, to, promo)
        }
    }
}
