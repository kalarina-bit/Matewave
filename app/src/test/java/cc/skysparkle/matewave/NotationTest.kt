package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotationTest {

    private val lichessGames = listOf(
        "1. b3 e6 2. Bb2 Qe7 3. g4 g6 4. Bxh8 f6 5. g5 b6 6. gxf6 Nxf6 7. Bxf6 Qxf6 8. Nc3 Bb7 9. f3 c6 10. Bg2 Na6 11. e4 Nc7 12. Qe2 O-O-O 13. O-O-O Ba6 14. d3 Qxc3 15. Rd2 Qa1# 0-1",
        "1. d4 d5 2. e3 Nc6 3. Bb5 Nf6 4. Bxc6+ bxc6 5. f3 Bf5 6. e4 dxe4 7. fxe4 Bxe4 8. Ne2 Bxg2 9. Rg1 Bf3 10. Rg3 Qd5 11. Nec3 Qe5+ 12. Kf1 Bxd1 13. Nxd1 Qf5+ 14. Ke1 Qxc2 15. Ndc3 Qxc1+ 16. Ke2 Qxb2+ 17. Ke3 Qxa1 0-1",
        "1. e4 c6 2. Nf3 d5 3. exd5 cxd5 4. d4 a6 5. Nc3 Bg4 6. h3 Bh5 7. Bd3 f6 8. Be3 e5 9. dxe5 fxe5 10. Qd2 e4 11. g4 Bg6 12. Bg5 Be7 13. Bxe7 Nxe7 14. Bxe4 dxe4 15. Qxd8+ Kxd8 16. O-O-O+ Nd7 17. Ne5 Be8 18. Nxe4 Kc7 19. Nxd7 Bxd7 20. Nc5 Bc6 21. Ne6+ Kb6 22. Rd6 Ka7 23. Rhd1 Nc8 24. Rd8 Rxd8 25. Rxd8 Nb6 26. Rxa8+ Kxa8 27. Nxg7 Bg2 28. h4 Nd7 29. f4 Nf6 30. g5 Ne4 31. h5 Kb8 32. h6 Kc7 33. g6 hxg6 34. h7 g5 35. fxg5 Nxg5 36. h8=Q Nf7 37. Qh7 Kd6 38. Nf5+ Ke6 39. Qg6+ 1-0",
        "1. e4 e5 2. Nf3 f6 3. Nc3 Nc6 4. Bc4 b5 5. Bd5 b4 6. Na4 Rb8 7. Bxg8 Rxg8 8. b3 Ba6 9. d3 d6 10. O-O g5 11. Ne1 h6 12. Qg4 Bc8 13. Qh5+ Kd7 14. Be3 Nd4 15. Bxd4 exd4 16. Nf3 c5 17. Qg4+ Ke7 18. Qh5 Bd7 19. Nd2 Be8 20. Qg4 Bd7 21. Qh5 Be8 22. Qf3 h5 23. Nc4 g4 24. Qf4 Kf7 25. Nab2 Bd7 26. g3 Bc8 27. Nd2 d5 28. Qxb8 Bd6 29. Qxa7+ Qd7 30. Qxd7+ Bxd7 31. exd5 Re8 32. Nbc4 Bc7 33. d6 Bd8 34. Rfe1 Re6 35. Rxe6 Bxe6 36. Re1 Bd7 37. Ne4 Kg6 38. Nxc5 Bf5 39. d7 h4 40. Re8 Bc7 41. d8=N Be5 42. a4 Kg7 43. a5 Bc7 44. a6 Bb8 45. Nc6 Kf7 46. Rxb8 Kg6 47. a7 Kf7 48. a8=N Bd7 49. Nd8+ Ke8 50. Ne4 Ke7 51. Nc7 Bf5 52. Nc5 h3 53. Nc6+ Kf7 54. Nd6+ Kg6 55. Ne7+ Kg5 56. Nexf5 Kg6 57. Ne7+ Kg5 58. N7e6+ Kh5 59. Ne8 f5 60. Nf6+ Kh6 61. Nfd5 f4 62. Ndxf4 Kh7 63. Nfg6 Kh6 64. Ne4 Kh5 65. Nf6+ Kh6 66. Nfg8+ Kh5 67. Ng7+ Kg5 68. Ne6+ Kh5 69. Nf6+ Kh6 70. Rh8# 1-0"
    )

    private fun sanTokens(movetext: String) = movetext.split(" ")
        .filter { !it.endsWith(".") && it !in setOf("1-0", "0-1", "1/2-1/2", "*") }

    /** Our SAN must be identical to Lichess SAN: the opening data is keyed by it. */
    @Test
    fun sanMatchesLichessNotation() {
        for (movetext in lichessGames) {
            var board = Board.startPosition()
            sanTokens(movetext).forEachIndexed { i, token ->
                val move = Notation.fromSan(board, token)
                assertNotNull("move ${i + 1} $token", move)
                assertEquals("move ${i + 1}", token, Notation.toSan(board, move!!, withCheck = true))
                board = ChessEngine.applyMove(board, move)
            }
        }
    }

    @Test
    fun looseSanIsAccepted() {
        val board = Board.startPosition()
        assertNotNull(Notation.fromSan(board, "Ngf3"))
        assertNotNull(Notation.fromSan(board, "e4!?"))
        val castle = Board.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        assertNotNull(Notation.fromSan(castle, "0-0-0"))
        val promo = Board.fromFen("8/4P3/8/8/8/8/k7/4K3 w - - 0 1")
        assertEquals(PieceType.QUEEN, Notation.fromSan(promo, "e8Q")?.promotion)
    }

    @Test
    fun illegalSanIsRejected() {
        assertEquals(null, Notation.fromSan(Board.startPosition(), "Ke3"))
    }

    @Test
    fun standardStartIsDetected() {
        assertTrue(Notation.isStandardStart(Board.startPosition()))
        val after = ChessEngine.applyMove(Board.startPosition(), Notation.fromSan(Board.startPosition(), "e4")!!)
        assertTrue(!Notation.isStandardStart(after))
    }
}
