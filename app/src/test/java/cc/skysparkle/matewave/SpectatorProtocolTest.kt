package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.spectate.MAX_SPECTATORS
import cc.skysparkle.matewave.spectate.RejectReason
import cc.skysparkle.matewave.spectate.SpectatorClock
import cc.skysparkle.matewave.spectate.SpectatorMessage
import cc.skysparkle.matewave.spectate.SpectatorOutcome
import cc.skysparkle.matewave.spectate.SpectatorPlayer
import cc.skysparkle.matewave.spectate.SpectatorProtocol
import cc.skysparkle.matewave.spectate.SpectatorResult
import cc.skysparkle.matewave.spectate.SpectatorSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectatorProtocolTest {
    private val clock = SpectatorClock(295_000, 300_000, PieceColor.BLACK)
    private val snapshot = SpectatorSnapshot(
        gameId = "g1",
        white = SpectatorPlayer("u1", "Владимир", 1350),
        black = SpectatorPlayer("u2", "Bob", 1200),
        movesUci = listOf("e2e4", "e7e5", "g1f3"),
        initialSeconds = 300,
        incrementSeconds = 2,
        clock = clock
    )

    private fun roundTrip(message: SpectatorMessage) {
        val envelope = SpectatorProtocol.decode(SpectatorProtocol.encode(message))
        assertNotNull(envelope)
        assertEquals(SpectatorProtocol.VERSION, envelope!!.version)
        assertEquals(message, envelope.message)
    }

    @Test fun hello() = roundTrip(SpectatorMessage.Hello(1))
    @Test fun query() = roundTrip(SpectatorMessage.StatusQuery)
    @Test fun pong() = roundTrip(SpectatorMessage.Pong)
    @Test fun resync() = roundTrip(SpectatorMessage.Resync)
    @Test fun leave() = roundTrip(SpectatorMessage.Leave)
    @Test fun welcome() = roundTrip(SpectatorMessage.Welcome(7, MAX_SPECTATORS, snapshot))
    @Test fun status() = roundTrip(SpectatorMessage.Status(true, 7, MAX_SPECTATORS, 1))
    @Test fun reject() = roundTrip(SpectatorMessage.Reject(RejectReason.FULL, 1))
    @Test fun move() = roundTrip(SpectatorMessage.MovePlayed("g1", 4, "b8c6", clock))
    @Test fun moveWithoutClock() = roundTrip(SpectatorMessage.MovePlayed("g1", 4, "e7e8q", null))
    @Test fun count() = roundTrip(SpectatorMessage.Count(3, MAX_SPECTATORS))
    @Test fun ping() = roundTrip(SpectatorMessage.Ping(clock))
    @Test fun closing() = roundTrip(SpectatorMessage.Closing)
    @Test fun newGame() = roundTrip(SpectatorMessage.NewGame(snapshot.copy(movesUci = emptyList(), clock = null)))

    @Test
    fun gameOverKeepsWinnerAndDraws() {
        roundTrip(SpectatorMessage.GameOver("g1", SpectatorResult(SpectatorOutcome.CHECKMATE, PieceColor.WHITE), clock.copy(running = null)))
        roundTrip(SpectatorMessage.GameOver("g1", SpectatorResult(SpectatorOutcome.AGREEMENT, null), null))
        roundTrip(SpectatorMessage.GameOver("g1", SpectatorResult(SpectatorOutcome.INTERRUPTED, null), null))
    }

    @Test
    fun everyMessageCarriesTheSenderVersion() {
        val line = SpectatorProtocol.encode(SpectatorMessage.Pong, version = 7)
        assertEquals(7, SpectatorProtocol.decode(line)!!.version)
    }

    @Test
    fun unknownTypeFromANewerVersionIsSkippedNotFatal() {
        val envelope = SpectatorProtocol.decode("""{"type":"emote","sv":9,"what":"wave"}""")
        assertNotNull(envelope)
        assertEquals(9, envelope!!.version)
        assertNull(envelope.message)
    }

    @Test
    fun unknownOutcomeAndRejectReasonDegradeGracefully() {
        val end = SpectatorProtocol.decode("""{"type":"end","sv":2,"gid":"g1","result":{"o":"abandoned"}}""")!!.message
        assertEquals(SpectatorOutcome.UNKNOWN, (end as SpectatorMessage.GameOver).result.outcome)
        val reject = SpectatorProtocol.decode("""{"type":"reject","sv":2,"reason":"banned","min":2}""")!!.message
        assertEquals(RejectReason.OTHER, (reject as SpectatorMessage.Reject).reason)
    }

    @Test
    fun malformedInputNeverThrows() {
        assertNull(SpectatorProtocol.decode("not json"))
        assertNull(SpectatorProtocol.decode("{}"))
        assertNull(SpectatorProtocol.decode(""))
        // Known type with missing fields: skipped like an unknown one.
        assertNull(SpectatorProtocol.decode("""{"type":"move","sv":1}""")!!.message)
        assertNull(SpectatorProtocol.decode("""{"type":"welcome","sv":1,"n":1}""")!!.message)
    }

    @Test
    fun oldHelloWithoutMinimumStillParses() {
        val hello = SpectatorProtocol.decode("""{"type":"hello","sv":1}""")!!.message
        assertEquals(SpectatorMessage.Hello(1), hello)
    }

    @Test
    fun clockAdvancesOnlyForTheRunningSide() {
        val later = clock.advancedBy(1_500)
        assertEquals(295_000, later.whiteMs)
        assertEquals(298_500, later.blackMs)
        assertEquals(0, SpectatorClock(1_000, 5_000, PieceColor.WHITE).advancedBy(9_000).whiteMs)
        assertEquals(clock.copy(running = null), clock.copy(running = null).advancedBy(5_000))
    }

    @Test
    fun hugeMoveListIsRejected() {
        val moves = (1..2_001).joinToString(",") { "\"e2e4\"" }
        val line = """{"type":"game","sv":1,"game":{"gid":"g","wp":{},"bp":{},"moves":[$moves]}}"""
        assertNull(SpectatorProtocol.decode(line)!!.message)
        assertTrue(SpectatorProtocol.MAX_CLIENT_LINE_CHARS < SpectatorProtocol.MAX_HOST_LINE_CHARS)
    }
}
