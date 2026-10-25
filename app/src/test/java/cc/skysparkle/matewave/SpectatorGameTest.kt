package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.GameStatus
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType
import cc.skysparkle.matewave.spectate.SpectatorClock
import cc.skysparkle.matewave.spectate.SpectatorDiff
import cc.skysparkle.matewave.spectate.SpectatorGame
import cc.skysparkle.matewave.spectate.SpectatorGame.Applied
import cc.skysparkle.matewave.spectate.SpectatorMessage
import cc.skysparkle.matewave.spectate.SpectatorOutcome
import cc.skysparkle.matewave.spectate.SpectatorPlayer
import cc.skysparkle.matewave.spectate.SpectatorResult
import cc.skysparkle.matewave.spectate.SpectatorResults
import cc.skysparkle.matewave.spectate.SpectatorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectatorGameTest {
    private fun snapshot(moves: List<String>, gameId: String = "g1", result: SpectatorResult? = null) = SpectatorSnapshot(
        gameId = gameId,
        white = SpectatorPlayer("u1", "Alice", 1500),
        black = SpectatorPlayer("u2", "Bob", 1400),
        movesUci = moves,
        initialSeconds = 180,
        incrementSeconds = 2,
        clock = SpectatorClock(170_000, 175_000, if (moves.size % 2 == 0) PieceColor.WHITE else PieceColor.BLACK),
        result = result
    )

    private fun move(ply: Int, uci: String, gameId: String = "g1") = SpectatorMessage.MovePlayed(gameId, ply, uci, null)

    @Test
    fun loadReplaysTheMoveListWithTurnAndLastMove() {
        val game = SpectatorGame()
        assertTrue(game.load(snapshot(listOf("e2e4", "e7e5", "e1e2"))))
        val view = game.view!!
        assertEquals(listOf("e4", "e5", "Ke2"), view.moveSans)
        assertEquals(PieceColor.BLACK, view.board.sideToMove)
        assertEquals("e1e2", view.lastMove!!.toUci())
        assertEquals(3, view.ply)
        assertEquals(180, view.initialSeconds)
    }

    @Test
    fun illegalHistoryIsRefused() {
        assertFalse(SpectatorGame().load(snapshot(listOf("e2e5"))))
        assertFalse(SpectatorGame().load(snapshot(listOf("zz99"))))
    }

    @Test
    fun liveMovesFollowOnAndDuplicatesAreIgnored() {
        val game = SpectatorGame()
        game.load(snapshot(listOf("d2d4")))
        assertEquals(Applied.APPLIED, game.onMove(move(2, "d7d5")))
        assertEquals(Applied.IGNORED, game.onMove(move(2, "d7d5")))
        assertEquals(Applied.IGNORED, game.onMove(move(1, "d2d4")))
        assertEquals(listOf("d4", "d5"), game.view!!.moveSans)
    }

    @Test
    fun gapsIllegalMovesAndForeignGamesAskForResync() {
        val game = SpectatorGame()
        game.load(snapshot(listOf("d2d4")))
        assertEquals(Applied.NEEDS_RESYNC, game.onMove(move(3, "c2c4")))
        assertEquals(Applied.NEEDS_RESYNC, game.onMove(move(2, "d4d5")))
        assertEquals(Applied.NEEDS_RESYNC, game.onMove(move(2, "d7d5", gameId = "other")))
        assertEquals(Applied.NEEDS_RESYNC, SpectatorGame().onMove(move(1, "e2e4")))
        assertEquals(1, game.view!!.ply)
    }

    @Test
    fun promotionIsKept() {
        val game = SpectatorGame()
        val moves = listOf("h2h4", "g7g5", "h4g5", "h7h6", "g5h6", "g8f6", "h6h7", "f6g8")
        assertTrue(game.load(snapshot(moves)))
        assertEquals(Applied.APPLIED, game.onMove(move(9, "h7g8n")))
        assertEquals(PieceType.KNIGHT, game.view!!.lastMove!!.promotion)
        assertEquals("hxg8=N", game.view!!.moveSans.last())
    }

    @Test
    fun gameOverStopsTheClockAndLaterMovesAreIgnored() {
        val game = SpectatorGame()
        game.load(snapshot(listOf("f2f3", "e7e5", "g2g4", "d8h4")))
        val result = SpectatorResult(SpectatorOutcome.CHECKMATE, PieceColor.BLACK)
        assertEquals(Applied.APPLIED, game.onGameOver(SpectatorMessage.GameOver("g1", result, null)))
        assertEquals(result, game.view!!.result)
        assertNull(game.view!!.clock!!.running)
        assertEquals(Applied.IGNORED, game.onMove(move(5, "a2a3")))
        game.onClock(SpectatorClock(1, 1, PieceColor.WHITE))
        assertNull(game.view!!.clock!!.running)
    }

    @Test
    fun diffTurnsNewMovesIntoLiveMessagesWithTheLatestClock() {
        val before = snapshot(listOf("e2e4"))
        val after = snapshot(listOf("e2e4", "e7e5", "g1f3"))
        val events = SpectatorDiff.between(before, after)
        assertEquals(2, events.size)
        val first = events[0] as SpectatorMessage.MovePlayed
        val second = events[1] as SpectatorMessage.MovePlayed
        assertEquals(2 to "e7e5", first.ply to first.uci)
        assertNull(first.clock)
        assertEquals(3 to "g1f3", second.ply to second.uci)
        assertEquals(after.clock, second.clock)
    }

    @Test
    fun diffReportsTheEndOnceAndRestartsOnANewGame() {
        val playing = snapshot(listOf("e2e4"))
        val over = playing.copy(result = SpectatorResult(SpectatorOutcome.RESIGNATION, PieceColor.WHITE))
        assertEquals(listOf(SpectatorMessage.GameOver("g1", over.result!!, over.clock)), SpectatorDiff.between(playing, over))
        assertEquals(emptyList<SpectatorMessage>(), SpectatorDiff.between(over, over))
        val rematch = snapshot(emptyList(), gameId = "g2")
        assertEquals(listOf(SpectatorMessage.NewGame(rematch)), SpectatorDiff.between(over, rematch))
        assertEquals(emptyList<SpectatorMessage>(), SpectatorDiff.between(null, rematch))
        // Clock ticks alone are carried by pings, not extra messages.
        assertEquals(emptyList<SpectatorMessage>(), SpectatorDiff.between(playing, playing.copy(clock = SpectatorClock(1, 2, null))))
    }

    @Test
    fun diffFallsBackToAFullSnapshotWhenHistoryChanges() {
        val a = snapshot(listOf("e2e4", "e7e5"))
        val b = snapshot(listOf("d2d4"))
        assertEquals(listOf(SpectatorMessage.NewGame(b)), SpectatorDiff.between(a, b))
    }

    @Test
    fun resultsMapEveryWayAGameCanEnd() {
        fun of(status: GameStatus, timedOut: PieceColor? = null, resigned: PieceColor? = null, left: Boolean = false) =
            SpectatorResults.of(status, PieceColor.WHITE, timedOut, resigned, left)
        assertNull(of(GameStatus.ONGOING))
        assertEquals(SpectatorResult(SpectatorOutcome.CHECKMATE, PieceColor.BLACK), of(GameStatus.CHECKMATE))
        assertEquals(SpectatorResult(SpectatorOutcome.TIMEOUT, PieceColor.WHITE), of(GameStatus.TIMEOUT, timedOut = PieceColor.BLACK))
        assertEquals(SpectatorResult(SpectatorOutcome.RESIGNATION, PieceColor.BLACK), of(GameStatus.RESIGNATION, resigned = PieceColor.WHITE))
        assertEquals(SpectatorResult(SpectatorOutcome.LEFT, PieceColor.WHITE), of(GameStatus.RESIGNATION, resigned = PieceColor.BLACK, left = true))
        assertEquals(SpectatorResult(SpectatorOutcome.STALEMATE, null), of(GameStatus.STALEMATE))
        assertEquals(SpectatorResult(SpectatorOutcome.FIFTY_MOVES, null), of(GameStatus.DRAW_50_MOVES))
        assertEquals(SpectatorResult(SpectatorOutcome.REPETITION, null), of(GameStatus.DRAW_REPETITION))
        assertEquals(SpectatorResult(SpectatorOutcome.INSUFFICIENT_MATERIAL, null), of(GameStatus.DRAW_MATERIAL))
        assertEquals(SpectatorResult(SpectatorOutcome.TIMEOUT_DRAW, null), of(GameStatus.TIMEOUT_DRAW))
        assertEquals(SpectatorResult(SpectatorOutcome.AGREEMENT, null), of(GameStatus.DRAW_AGREEMENT))
        assertEquals(SpectatorResult(SpectatorOutcome.LEFT, PieceColor.BLACK), SpectatorResults.leftBy(PieceColor.WHITE))
    }
}
