package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Notation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks the bundled opening courses; unit tests run with the module directory as working dir. */
class OpeningCoursesTest {

    private val lines: List<List<String>> =
        File("src/main/assets/openings/courses.tsv").readLines().filter { it.isNotBlank() }.map { it.split('\t') }

    @Test
    fun everyLineIsLegalAndEndsWithTheLearnersMove() {
        assertEquals(24, lines.size)
        for (f in lines) {
            val side = f[0]
            val sans = f[3].split(' ')
            var board = Board.startPosition()
            sans.forEachIndexed { i, san ->
                val move = Notation.fromSan(board, san)
                assertNotNull("${f[2]}: move ${i + 1} $san", move)
                board = ChessEngine.applyMove(board, move!!)
            }
            val learnerLast = (sans.size % 2 == 1) == (side == "W")
            assertTrue("${f[2]} must end with the learner's move", learnerLast)
        }
    }

    @Test
    fun bothSidesAreCovered() {
        assertTrue(lines.count { it[0] == "W" } >= 8)
        assertTrue(lines.count { it[0] == "B" } >= 8)
    }
}
