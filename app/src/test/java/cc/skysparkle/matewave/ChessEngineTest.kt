package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessEngineTest {

    private fun perft(board: Board, depth: Int): Long {
        val moves = ChessEngine.legalMoves(board)
        if (depth == 1) return moves.size.toLong()
        return moves.sumOf { perft(ChessEngine.applyMove(board, it), depth - 1) }
    }

    /** Node counts from the standard perft suite: castling, en passant and promotions included. */
    @Test
    fun perftMatchesReferenceCounts() {
        assertEquals(8_902L, perft(Board.startPosition(), 3))
        assertEquals(97_862L, perft(Board.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"), 3))
        assertEquals(43_238L, perft(Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1"), 4))
        assertEquals(9_467L, perft(Board.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1"), 3))
        assertEquals(62_379L, perft(Board.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8"), 3))
    }

    @Test
    fun noCastlingWithoutTheRook() {
        // The castling flag is still set, but the h1 rook is gone.
        val board = Board.fromFen("4k3/8/8/8/8/8/8/4K3 w K - 0 1")
        assertTrue(ChessEngine.legalMoves(board).none { it.isCastleKingSide })
    }

    @Test
    fun deadPositionsAreDrawn() {
        assertTrue(ChessEngine.isDeadPosition(Board.fromFen("4k3/8/8/8/8/8/8/4K3 w - - 0 1")))
        assertTrue(ChessEngine.isDeadPosition(Board.fromFen("4k3/8/8/8/8/8/8/3NK3 w - - 0 1")))
        // Bishops on squares of one colour only: c1 and f4 are both dark.
        assertTrue(ChessEngine.isDeadPosition(Board.fromFen("4k3/8/8/8/5b2/8/8/2B1K3 w - - 0 1")))
        assertFalse(ChessEngine.isDeadPosition(Board.fromFen("4k3/8/8/8/4b3/8/8/2B1K3 w - - 0 1")))
        assertFalse(ChessEngine.isDeadPosition(Board.fromFen("4k3/8/8/8/8/8/8/2NNK3 w - - 0 1")))
        assertFalse(ChessEngine.isDeadPosition(Board.fromFen("4k3/7p/8/8/8/8/8/4K3 w - - 0 1")))
        assertEquals(GameStatus.DRAW_MATERIAL, ChessEngine.gameStatus(Board.fromFen("4k3/8/8/8/8/8/8/3BK3 b - - 0 1")))
    }

    @Test
    fun mateNeedsMoreThanOneMinorPiece() {
        val board = Board.fromFen("4k3/8/8/8/8/8/p7/3NK3 w - - 0 1")
        assertFalse(ChessEngine.canMate(board, PieceColor.WHITE))
        assertTrue(ChessEngine.canMate(board, PieceColor.BLACK))
        assertTrue(ChessEngine.canMate(Board.fromFen("4k3/8/8/8/8/8/8/2BNK3 w - - 0 1"), PieceColor.WHITE))
    }

    @Test
    fun threefoldRepetitionCountsTheStartingPosition() {
        var board = Board.startPosition()
        val history = mutableListOf(Notation.positionKey(board))
        var status = GameStatus.ONGOING
        // Knights out and back twice: the start position appears for the third time.
        for (san in listOf("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1", "Ng8")) {
            board = ChessEngine.applyMove(board, Notation.fromSan(board, san)!!)
            history.add(Notation.positionKey(board))
            status = ChessEngine.gameStatus(board, history)
        }
        assertEquals(GameStatus.DRAW_REPETITION, status)
    }

    @Test
    fun uciRoundTripKeepsPromotion() {
        val move = Move.fromUci("e7e8n")
        assertEquals("e7e8n", move.toUci())
    }
}
