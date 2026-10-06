package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.ChessEngine
import cc.skysparkle.matewave.engine.Move
import cc.skysparkle.matewave.engine.Notation
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.openings.CourseStatus
import cc.skysparkle.matewave.openings.OpeningCourse
import cc.skysparkle.matewave.openings.OpeningSrs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningSrsTest {

    private class MemoryStore : OpeningSrs.CardStore {
        val map = HashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
    }

    private fun course(name: String, line: String, side: PieceColor = PieceColor.WHITE): OpeningCourse {
        val sans = line.split(' ')
        var board = Board.startPosition()
        val moves = ArrayList<Move>()
        for (san in sans) {
            val m = Notation.fromSan(board, san)!!
            moves.add(m)
            board = ChessEngine.applyMove(board, m)
        }
        return OpeningCourse(name, side, "C00", name, sans, moves)
    }

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val italian = course("Italian", "e4 e5 Nf3 Nc6 Bc4")   // White plies: 0, 2, 4
    private val french = course("French", "e4 e6 d4 d5", PieceColor.BLACK) // Black plies: 1, 3

    @Test
    fun newCourseIsNewAndLearningProgressIsTracked() {
        val srs = OpeningSrs(MemoryStore())
        assertEquals(CourseStatus.NEW, srs.info(italian, 0, false).status)
        srs.learn(italian, 0, 0)
        val info = srs.info(italian, 0, false)
        assertEquals(CourseStatus.LEARNING, info.status)
        assertEquals(1, info.learned)
        assertEquals(3, info.total)
    }

    @Test
    fun otherNewCoursesAreLockedWhileOneIsInProgress() {
        val srs = OpeningSrs(MemoryStore())
        assertEquals(CourseStatus.LOCKED, srs.info(french, 0, anotherInProgress = true).status)
        // A course already learned is never locked: its reviews stay available.
        srs.learn(french, 1, 0); srs.learn(french, 3, 0)
        assertEquals(CourseStatus.WAITING, srs.info(french, 0, anotherInProgress = true).status)
    }

    @Test
    fun learnedCourseBecomesDueAfterTheFirstInterval() {
        val srs = OpeningSrs(MemoryStore())
        listOf(0, 2, 4).forEach { srs.learn(italian, it, 0) }
        assertEquals(CourseStatus.WAITING, srs.info(italian, 3 * hour, false).status)
        val later = srs.info(italian, 5 * hour, false)
        assertEquals(CourseStatus.DUE, later.status)
        assertEquals(3, later.dueCards)
        assertEquals(1, later.stars)
    }

    @Test
    fun successOnlyCountsWhenDue() {
        val srs = OpeningSrs(MemoryStore())
        srs.learn(italian, 0, 0)
        srs.success(italian, 0, hour)          // too early: practice does not skip reviews
        assertEquals(1, srs.level(italian, 0))
        srs.success(italian, 0, 5 * hour)      // due: level up, next interval one day
        assertEquals(2, srs.level(italian, 0))
        assertFalse(srs.isDue(italian, 0, 5 * hour + day - 1))
        assertTrue(srs.isDue(italian, 0, 5 * hour + day))
    }

    @Test
    fun failureSendsOnlyThatMoveBack() {
        val srs = OpeningSrs(MemoryStore())
        listOf(0, 2, 4).forEach { srs.learn(italian, it, 0) }
        var t = 5 * hour
        repeat(3) { listOf(0, 2, 4).forEach { p -> srs.success(italian, p, t) }; t += 10 * day }
        assertEquals(4, srs.level(italian, 2))
        srs.fail(italian, 2, t)
        assertEquals(1, srs.level(italian, 2))
        assertEquals(4, srs.level(italian, 0))
        assertTrue(srs.isDue(italian, 2, t + OpeningSrs.RELEARN_MS))
    }

    private fun learned(): OpeningSrs {
        val srs = OpeningSrs(MemoryStore())
        listOf(1, 3).forEach { srs.learn(french, it, 0) }
        return srs
    }

    @Test
    fun secondAndThirdStarsNeedThreeFlawlessReviewsEach() {
        val srs = learned()
        assertEquals(1, srs.info(french, 0, false).stars)
        repeat(2) { srs.recordReview(french, flawless = true) }
        assertEquals(1, srs.info(french, 0, false).stars)
        assertEquals(1, srs.info(french, 0, false).reviewsToNext)
        val second = srs.recordReview(french, flawless = true)
        assertEquals(1, second.before)
        assertEquals(2, second.after)
        repeat(3) { srs.recordReview(french, flawless = true) }
        val info = srs.info(french, 0, false)
        assertEquals(3, info.stars)
        assertTrue(info.mastered)
        assertEquals(0, info.reviewsToNext)
    }

    @Test
    fun mistakeCostsAStarThatMustBeEarnedAgain() {
        val srs = learned()
        repeat(6) { srs.recordReview(french, flawless = true) }
        val lost = srs.recordReview(french, flawless = false)
        assertEquals(3, lost.before)
        assertEquals(2, lost.after)
        assertEquals(3, lost.reviewsToNext)
        srs.recordReview(french, flawless = false)
        assertEquals(1, srs.info(french, 0, false).stars)
    }

    @Test
    fun firstStarIsNeverLost() {
        val srs = learned()
        srs.recordReview(french, flawless = true)
        val change = srs.recordReview(french, flawless = false)
        assertEquals(1, change.after)
        assertEquals(3, change.reviewsToNext) // progress towards the second star starts over
    }

    @Test
    fun unfinishedCourseHasNoStars() {
        val srs = OpeningSrs(MemoryStore())
        srs.learn(italian, 0, 0)
        srs.recordReview(italian, flawless = true)
        assertEquals(0, srs.info(italian, 0, false).stars)
    }
}
