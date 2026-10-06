package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.Material
import cc.skysparkle.matewave.engine.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialTest {
    @Test
    fun startPositionIsEven() {
        val b = Material.balance(Board.startPosition())
        assertTrue(b.capturedByWhite.isEmpty())
        assertTrue(b.capturedByBlack.isEmpty())
        assertEquals(0, b.advantage)
    }

    @Test
    fun whiteUpAKnight() {
        val b = Material.balance(Board.fromFen("r1bqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"))
        assertEquals(listOf(PieceType.KNIGHT), b.capturedByWhite)
        assertEquals(3, b.advantage)
    }

    @Test
    fun blackAheadIsNegative() {
        val b = Material.balance(Board.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNB1KBNR w KQkq - 0 1"))
        assertEquals(listOf(PieceType.QUEEN), b.capturedByBlack)
        assertEquals(-9, b.advantage)
    }

    @Test
    fun promotionDoesNotCountAsCapturedPawn() {
        // The a-pawn became a second queen and the g1 knight is gone.
        val b = Material.balance(Board.fromFen("rnbqkbnr/pppppppp/8/8/8/8/1PPPPPPP/RNBQKBQR w Qkq - 0 1"))
        assertTrue(b.capturedByBlack.none { it == PieceType.PAWN })
        assertEquals(listOf(PieceType.KNIGHT), b.capturedByBlack)
        // A queen for a pawn (+8) minus the knight (-3).
        assertEquals(5, b.advantage)
    }

    @Test
    fun displayOrderIsLightToHeavy() {
        val b = Material.balance(Board.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1"))
        assertEquals(
            listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN),
            b.capturedByWhite.distinct()
        )
        assertEquals(15, b.capturedByWhite.size)
    }
}
