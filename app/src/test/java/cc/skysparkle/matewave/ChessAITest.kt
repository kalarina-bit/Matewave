package cc.skysparkle.matewave

import cc.skysparkle.matewave.ai.AiDifficulty
import cc.skysparkle.matewave.ai.ChessAI
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class ChessAITest {
    private fun sq(name: String) = Move.squareFromName(name)

    @Test
    fun findsBackRankMateInOne() {
        val board = Board.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1")
        val move = ChessAI.bestMove(board, AiDifficulty.HARD)
        assertNotNull(move)
        assertEquals(sq("d1"), move!!.from)
        assertEquals(sq("d8"), move.to)
    }

    @Test
    fun easyNeverMissesMateInOne() {
        val board = Board.fromFen("6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1")
        repeat(10) { seed ->
            val move = ChessAI.bestMove(board, AiDifficulty.EASY, random = Random(seed.toLong()))
            assertEquals("seed $seed", sq("d8"), move!!.to)
        }
    }

    @Test
    fun capturesUndefendedQueen() {
        val board = Board.fromFen("4k3/8/8/3q4/8/8/8/3QK3 w - - 0 1")
        val move = ChessAI.bestMove(board, AiDifficulty.MEDIUM, random = Random(1))
        assertEquals(sq("d1"), move!!.from)
        assertEquals(sq("d5"), move.to)
    }

    @Test
    fun doesNotTakeDefendedPawnWithQueen() {
        val board = Board.fromFen("4k3/8/2p5/3p4/8/8/8/3QK3 w - - 0 1")
        for (level in AiDifficulty.entries) {
            val move = ChessAI.bestMove(board, level, random = Random(7))
            assertNotNull(move)
            val grabsPawn = move!!.from == sq("d1") && move.to == sq("d5")
            assertTrue("level $level grabbed a defended pawn with the queen", !grabsPawn)
        }
    }

    @Test
    fun respectsClockBudget() {
        val started = System.currentTimeMillis()
        ChessAI.bestMove(Board.startPosition(), AiDifficulty.EXPERT, clockMillis = 3_000L)
        val elapsed = System.currentTimeMillis() - started
        assertTrue("AI thought for $elapsed ms with 3 s on the clock", elapsed < 1_500)
    }

    @Test
    fun returnsNullWithoutLegalMoves() {
        val board = Board.fromFen("3R2k1/5ppp/8/8/8/8/5PPP/6K1 b - - 0 1")
        assertEquals(null, ChessAI.bestMove(board, AiDifficulty.MEDIUM))
    }

    @Test
    fun differentSeedsCanVaryOpening() {
        val first = (0 until 15).map {
            ChessAI.bestMove(Board.startPosition(), AiDifficulty.EASY, random = Random(it.toLong()))!!.toUci()
        }.toSet()
        assertNotEquals(1, first.size)
    }
}
