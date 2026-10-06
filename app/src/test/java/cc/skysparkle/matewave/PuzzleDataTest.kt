package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.Move
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every bundled puzzle must be playable with the app's own rules engine. */
class PuzzleDataTest {
    private val puzzles = JSONArray(File("src/main/assets/puzzles.json").readText())

    @Test
    fun solutionsAreLegalAndMatesEndInMate() {
        assertTrue(puzzles.length() > 1000)
        val ids = HashSet<Int>()
        for (i in 0 until puzzles.length()) {
            val p = puzzles.getJSONObject(i)
            val id = p.getInt("id")
            assertTrue("duplicate id $id", ids.add(id))
            var board = Board.fromFen(p.getString("fen"))
            val solution = p.getJSONArray("solution")
            assertEquals("puzzle $id must end with the solver's move", 1, solution.length() % 2)
            for (j in 0 until solution.length()) {
                val wanted = Move.fromUci(solution.getString(j))
                val move = ChessEngine.legalMoves(board).firstOrNull {
                    it.from == wanted.from && it.to == wanted.to && it.promotion == wanted.promotion
                }
                assertNotNull("puzzle $id, move ${j + 1} ${solution.getString(j)} is illegal", move)
                board = ChessEngine.applyMove(board, move!!)
            }
            if (p.optString("theme").startsWith("mate_in_")) {
                assertEquals("puzzle $id", GameStatus.CHECKMATE, ChessEngine.gameStatus(board))
            }
        }
    }
}
