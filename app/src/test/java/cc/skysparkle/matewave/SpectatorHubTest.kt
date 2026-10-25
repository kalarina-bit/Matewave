package cc.skysparkle.matewave

import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.spectate.MAX_SPECTATORS
import cc.skysparkle.matewave.spectate.RejectReason
import cc.skysparkle.matewave.spectate.SpectatorBroadcaster
import cc.skysparkle.matewave.spectate.SpectatorClient
import cc.skysparkle.matewave.spectate.SpectatorClient.Event
import cc.skysparkle.matewave.spectate.SpectatorClient.Failure
import cc.skysparkle.matewave.spectate.SpectatorClock
import cc.skysparkle.matewave.spectate.SpectatorGame
import cc.skysparkle.matewave.spectate.SpectatorHub
import cc.skysparkle.matewave.spectate.SpectatorMessage
import cc.skysparkle.matewave.spectate.SpectatorOutcome
import cc.skysparkle.matewave.spectate.SpectatorPlayer
import cc.skysparkle.matewave.spectate.SpectatorProbe
import cc.skysparkle.matewave.spectate.SpectatorProtocol
import cc.skysparkle.matewave.spectate.SpectatorResults
import cc.skysparkle.matewave.spectate.SpectatorSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.BufferedReader
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class SpectatorHubTest {
    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val hubTiming = SpectatorHub.Timing(helloTimeoutMs = 1_000, pingIntervalMs = 150, silenceTimeoutMs = 900)
    private val clientTiming = SpectatorClient.Timing(connectTimeoutMs = 2_000, silenceTimeoutMs = 900)

    private val hubs = CopyOnWriteArrayList<SpectatorHub>()
    private val clients = CopyOnWriteArrayList<SpectatorClient>()
    private val closeables = CopyOnWriteArrayList<Closeable>()

    @After
    fun tearDown() {
        clients.forEach { it.stop() }
        hubs.forEach { it.close() }
        closeables.forEach { runCatching { it.close() } }
    }

    private fun snapshot(
        moves: List<String> = emptyList(),
        clock: SpectatorClock? = SpectatorClock(300_000, 300_000, PieceColor.WHITE),
        gameId: String = "g1"
    ) = SpectatorSnapshot(
        gameId = gameId,
        white = SpectatorPlayer("u1", "Alice", 1500),
        black = SpectatorPlayer("u2", "Bob", 1400),
        movesUci = moves,
        initialSeconds = 300,
        incrementSeconds = 0,
        clock = clock
    )

    private fun hub(initial: SpectatorSnapshot? = snapshot()): SpectatorHub {
        val hub = SpectatorHub(timing = hubTiming)
        hubs += hub
        hub.start(loopback)
        if (initial != null) hub.publish(initial)
        return hub
    }

    private class Recorder {
        val events = LinkedBlockingQueue<Event>()

        fun next(timeoutMs: Long = 3_000): Event =
            events.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: throw AssertionError("No event within $timeoutMs ms")

        /** Skips pings and counts until an event matching [predicate]. */
        fun awaitEvent(timeoutMs: Long = 3_000, predicate: (Event) -> Boolean): Event {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (true) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0) throw AssertionError("Expected event did not arrive")
                val event = events.poll(left, TimeUnit.MILLISECONDS) ?: continue
                if (predicate(event)) return event
            }
        }

        fun awaitMessage(timeoutMs: Long = 3_000, predicate: (SpectatorMessage) -> Boolean): SpectatorMessage =
            (awaitEvent(timeoutMs) { it is Event.Received && predicate(it.message) } as Event.Received).message
    }

    private fun watch(
        port: Int,
        recorder: Recorder = Recorder(),
        version: Int = SpectatorProtocol.VERSION,
        minVersion: Int = SpectatorProtocol.MIN_COMPATIBLE_VERSION
    ): Pair<SpectatorClient, Recorder> {
        val client = SpectatorClient("127.0.0.1", port, { recorder.events.add(it) }, clientTiming, version, minVersion)
        clients += client
        client.start()
        return client to recorder
    }

    private fun awaitCount(hub: SpectatorHub, expected: Int, timeoutMs: Long = 3_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (hub.count.value != expected) {
            if (System.currentTimeMillis() > deadline) fail("Spectator count ${hub.count.value}, expected $expected")
            Thread.sleep(10)
        }
    }

    private fun joined(recorder: Recorder): SpectatorMessage.Welcome {
        val event = recorder.next()
        assertTrue("Expected to join, got $event", event is Event.Joined)
        return (event as Event.Joined).welcome
    }

    @Test
    fun twentySpectatorsJoinAndTheTwentyFirstIsRefused() {
        val hub = hub()
        val watchers = List(MAX_SPECTATORS) { watch(hub.port).second }
        watchers.forEach { joined(it) }
        awaitCount(hub, MAX_SPECTATORS)

        val (_, late) = watch(hub.port)
        assertEquals(Event.JoinFailed(Failure.FULL), late.next())
        assertEquals(MAX_SPECTATORS, hub.count.value)

        // Everyone already watching still gets the game.
        hub.publish(snapshot(listOf("e2e4")), listOf(SpectatorMessage.MovePlayed("g1", 1, "e2e4", null)))
        watchers.forEach { recorder ->
            val move = recorder.awaitMessage { it is SpectatorMessage.MovePlayed } as SpectatorMessage.MovePlayed
            assertEquals("e2e4", move.uci)
        }
    }

    @Test
    fun simultaneousJoinsNeverExceedTheLimit() {
        val hub = hub()
        val start = CountDownLatch(1)
        val recorders = List(MAX_SPECTATORS + 10) { Recorder() }
        val threads = recorders.map { r -> Thread { start.await(); watch(hub.port, r) }.apply { start() } }
        start.countDown()
        threads.forEach { it.join() }

        val outcomes = recorders.map { it.next(5_000) }
        assertEquals(MAX_SPECTATORS, outcomes.count { it is Event.Joined })
        assertEquals(10, outcomes.count { it == Event.JoinFailed(Failure.FULL) })
        assertEquals(MAX_SPECTATORS, hub.count.value)
    }

    @Test
    fun joiningMidGameGetsFullStateThenFollowsLive() {
        val hub = hub()
        val broadcaster = SpectatorBroadcaster(hub)
        val opening = listOf("e2e4", "e7e5", "g1f3")
        broadcaster.update(snapshot())
        broadcaster.update(snapshot(opening, SpectatorClock(290_000, 295_000, PieceColor.BLACK)))

        val (_, recorder) = watch(hub.port)
        val welcome = joined(recorder)
        assertEquals(1, welcome.count)
        assertEquals(opening, welcome.game.movesUci)
        assertEquals("Alice", welcome.game.white.name)
        assertEquals(300, welcome.game.initialSeconds)
        val clock = welcome.game.clock!!
        assertEquals(PieceColor.BLACK, clock.running)
        assertEquals(290_000, clock.whiteMs)
        assertTrue(clock.blackMs in 290_000..295_000)

        val game = SpectatorGame()
        assertTrue(game.load(welcome.game))
        assertEquals(PieceColor.BLACK, game.view!!.board.sideToMove)
        assertEquals(listOf("e4", "e5", "Nf3"), game.view!!.moveSans)

        broadcaster.update(snapshot(opening + "b8c6", SpectatorClock(290_000, 293_000, PieceColor.WHITE)))
        val move = recorder.awaitMessage { it is SpectatorMessage.MovePlayed } as SpectatorMessage.MovePlayed
        assertEquals(4, move.ply)
        assertEquals(SpectatorGame.Applied.APPLIED, game.onMove(move))
        assertEquals(PieceColor.WHITE, game.view!!.board.sideToMove)
        assertEquals("Nc6", game.view!!.moveSans.last())
    }

    @Test
    fun hostLeavingMidGameShowsTheResultThenSaysGoodbye() {
        val hub = hub()
        val broadcaster = SpectatorBroadcaster(hub)
        val playing = snapshot(listOf("e2e4"))
        broadcaster.update(playing)
        val (_, recorder) = watch(hub.port)
        joined(recorder)

        broadcaster.close(playing.copy(result = SpectatorResults.leftBy(PieceColor.WHITE)))
        val end = recorder.awaitMessage { it is SpectatorMessage.GameOver } as SpectatorMessage.GameOver
        assertEquals(SpectatorOutcome.LEFT, end.result.outcome)
        assertEquals(PieceColor.BLACK, end.result.winner)
        assertEquals(Event.Disconnected(hostClosed = true), recorder.awaitEvent { it is Event.Disconnected })
    }

    @Test
    fun hostDroppingOffIsSeenAsConnectionLost() {
        val hub = hub()
        val link = FlakyLink(hub.port)
        val (_, recorder) = watch(link.port)
        joined(recorder)

        link.cut()
        assertEquals(Event.Disconnected(hostClosed = false), recorder.awaitEvent { it is Event.Disconnected })
    }

    @Test
    fun wifiSwitchingOffIsDetectedOnBothSides() {
        val hub = hub()
        val link = FlakyLink(hub.port)
        val (_, cutOff) = watch(link.port)
        val (_, direct) = watch(hub.port)
        joined(cutOff)
        joined(direct)
        awaitCount(hub, 2)

        // No more packets either way, but no connection reset: what a dead Wi-Fi link looks like.
        link.silent = true

        assertEquals(Event.Disconnected(hostClosed = false), cutOff.awaitEvent { it is Event.Disconnected })
        awaitCount(hub, 1)
        // The remaining spectator's counter follows.
        assertNotNull(direct.awaitMessage { it is SpectatorMessage.Count && it.count == 1 })

        // The spectator who still has a connection keeps watching.
        hub.publish(snapshot(listOf("d2d4")), listOf(SpectatorMessage.MovePlayed("g1", 1, "d2d4", null)))
        assertNotNull(direct.awaitMessage { it is SpectatorMessage.MovePlayed })
    }

    @Test
    fun spectatorsLeavingFreeTheirSlotsWhileOthersKeepWatching() {
        val hub = hub()
        val watchers = List(MAX_SPECTATORS) { watch(hub.port) }
        watchers.forEach { joined(it.second) }
        awaitCount(hub, MAX_SPECTATORS)

        watchers.take(5).forEach { it.first.stop() }
        awaitCount(hub, MAX_SPECTATORS - 5)

        val (_, newcomer) = watch(hub.port)
        assertEquals(MAX_SPECTATORS - 4, joined(newcomer).count)

        hub.publish(snapshot(listOf("c2c4")), listOf(SpectatorMessage.MovePlayed("g1", 1, "c2c4", null)))
        (watchers.drop(5).map { it.second } + newcomer).forEach {
            assertNotNull(it.awaitMessage { m -> m is SpectatorMessage.MovePlayed })
        }
    }

    @Test
    fun stalledSpectatorsNeverSlowDownTheHost() {
        val hub = hub()
        val link = FlakyLink(hub.port)
        repeat(5) { joined(watch(link.port).second) }
        link.silent = true

        val started = System.nanoTime()
        repeat(2_000) { i ->
            hub.publish(snapshot(), listOf(SpectatorMessage.Count(i, MAX_SPECTATORS)))
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("publish took $elapsedMs ms", elapsedMs < 1_000)
    }

    @Test
    fun outdatedSpectatorIsToldToUpdate() {
        val hub = hub()
        val (_, recorder) = watch(hub.port, version = 0, minVersion = 0)
        assertEquals(Event.JoinFailed(Failure.UPDATE_THIS_APP), recorder.next())
        assertEquals(0, hub.count.value)
    }

    @Test
    fun outdatedHostIsReported() {
        val hub = hub()
        val newer = SpectatorProtocol.VERSION + 1
        val (_, recorder) = watch(hub.port, version = newer, minVersion = newer)
        assertEquals(Event.JoinFailed(Failure.UPDATE_HOST_APP), recorder.next())
    }

    @Test
    fun rawOldClientGetsAClearRejectInsteadOfACrash() {
        val hub = hub()
        Socket(loopback, hub.port).use { s ->
            s.soTimeout = 2_000
            s.getOutputStream().write("{\"type\":\"hello\",\"sv\":0}\n".toByteArray())
            val reply = BufferedReader(InputStreamReader(s.getInputStream())).readLine()
            val reject = SpectatorProtocol.decode(reply)!!.message as SpectatorMessage.Reject
            assertEquals(RejectReason.UPDATE_REQUIRED, reject.reason)
            assertEquals(SpectatorProtocol.MIN_COMPATIBLE_VERSION, reject.minVersion)
        }
    }

    @Test
    fun garbageAndOversizedInputCloseOnlyThatConnection() {
        val hub = hub()
        val (_, watcher) = watch(hub.port)
        joined(watcher)

        for (payload in listOf("hello?\n", "x".repeat(SpectatorProtocol.MAX_CLIENT_LINE_CHARS + 10) + "\n")) {
            Socket(loopback, hub.port).use { s ->
                s.soTimeout = 2_000
                s.getOutputStream().write(payload.toByteArray())
                // Closed with unread input the host may answer with a reset instead of a clean close.
                val read = try { s.getInputStream().read() } catch (e: IOException) { -1 }
                assertEquals(-1, read)
            }
        }
        assertEquals(1, hub.count.value)
        hub.publish(snapshot(listOf("e2e4")), listOf(SpectatorMessage.MovePlayed("g1", 1, "e2e4", null)))
        assertNotNull(watcher.awaitMessage { it is SpectatorMessage.MovePlayed })
    }

    @Test
    fun silentConnectionThatNeverSaysHelloIsClosed() {
        val hub = hub()
        Socket(loopback, hub.port).use { s ->
            s.soTimeout = 3_000
            assertEquals(-1, s.getInputStream().read())
        }
        assertEquals(0, hub.count.value)
    }

    @Test
    fun noGameYetIsRefused() {
        val hub = hub(initial = null)
        val (_, recorder) = watch(hub.port)
        assertEquals(Event.JoinFailed(Failure.NO_GAME), recorder.next())
    }

    @Test
    fun statusProbeReportsLiveCountWithoutTakingASlot() {
        val hub = hub()
        repeat(3) { joined(watch(hub.port).second) }
        awaitCount(hub, 3)
        val status = SpectatorProbe.query("127.0.0.1", hub.port)!!
        assertTrue(status.inGame)
        assertEquals(3, status.count)
        assertEquals(MAX_SPECTATORS, status.max)
        assertEquals(3, hub.count.value)

        hub.publish(snapshot().copy(result = SpectatorResults.interrupted))
        assertFalse(SpectatorProbe.query("127.0.0.1", hub.port)!!.inGame)
    }

    @Test
    fun probeOfAClosedHostIsNull() {
        val hub = hub()
        val port = hub.port
        hub.close()
        assertEquals(null, SpectatorProbe.query("127.0.0.1", port, timeoutMs = 500))
    }

    @Test
    fun resyncSendsAFreshSnapshot() {
        val hub = hub(snapshot(listOf("e2e4", "c7c5")))
        val (client, recorder) = watch(hub.port)
        joined(recorder)
        client.requestResync()
        val fresh = recorder.awaitMessage { it is SpectatorMessage.NewGame } as SpectatorMessage.NewGame
        assertEquals(listOf("e2e4", "c7c5"), fresh.game.movesUci)
    }

    @Test
    fun rematchReachesSpectatorsAsANewGame() {
        val hub = hub()
        val broadcaster = SpectatorBroadcaster(hub)
        val first = snapshot(listOf("f2f3", "e7e5", "g2g4", "d8h4"))
        broadcaster.update(first)
        val (_, recorder) = watch(hub.port)
        joined(recorder)
        broadcaster.update(first.copy(result = SpectatorResults.of(
            cc.skysparkle.matewave.engine.GameStatus.CHECKMATE, PieceColor.WHITE, null, null, false
        )))
        val end = recorder.awaitMessage { it is SpectatorMessage.GameOver } as SpectatorMessage.GameOver
        assertEquals(PieceColor.BLACK, end.result.winner)

        broadcaster.update(snapshot(gameId = "g2"))
        val next = recorder.awaitMessage { it is SpectatorMessage.NewGame } as SpectatorMessage.NewGame
        assertEquals("g2", next.game.gameId)
    }

    @Test
    fun clocksKeepRunningBetweenHostUpdates() {
        val hub = hub(snapshot(clock = SpectatorClock(60_000, 60_000, PieceColor.WHITE)))
        Thread.sleep(400)
        val (_, recorder) = watch(hub.port)
        val clock = joined(recorder).game.clock!!
        assertTrue("white ${clock.whiteMs}", clock.whiteMs in 50_000..59_700)
        assertEquals(60_000, clock.blackMs)
        val ping = recorder.awaitMessage { it is SpectatorMessage.Ping } as SpectatorMessage.Ping
        assertTrue(ping.clock!!.whiteMs <= clock.whiteMs)
    }

    @Test
    fun closingTheHubEndsEveryoneAndRefusesNewcomers() {
        val hub = hub()
        val recorders = List(3) { watch(hub.port).second }
        recorders.forEach { joined(it) }
        val port = hub.port
        hub.close()
        recorders.forEach { assertEquals(Event.Disconnected(hostClosed = true), it.awaitEvent { e -> e is Event.Disconnected }) }
        assertEquals(0, hub.count.value)
        val (_, late) = watch(port)
        assertEquals(Event.JoinFailed(Failure.UNREACHABLE), late.next())
    }

    /** A TCP relay that can go silent (dead Wi-Fi) or drop every connection (host gone). */
    private inner class FlakyLink(target: Int) : Closeable {
        private val server = ServerSocket(0, 50, loopback)
        private val sockets = CopyOnWriteArrayList<Socket>()
        @Volatile var silent = false
        val port: Int get() = server.localPort

        init {
            closeables += this
            Thread {
                while (!server.isClosed) {
                    val inbound = try { server.accept() } catch (e: IOException) { break }
                    val outbound = Socket(loopback, target)
                    sockets += inbound
                    sockets += outbound
                    pump(inbound, outbound)
                    pump(outbound, inbound)
                }
            }.apply { isDaemon = true }.start()
        }

        private fun pump(from: Socket, to: Socket) = Thread {
            val buffer = ByteArray(8_192)
            try {
                while (true) {
                    val n = from.getInputStream().read(buffer)
                    if (n < 0) break
                    if (!silent) to.getOutputStream().write(buffer, 0, n)
                }
            } catch (_: IOException) {
            } finally {
                runCatching { from.close() }
                runCatching { to.close() }
            }
        }.apply { isDaemon = true }.start()

        fun cut() = sockets.forEach { runCatching { it.close() } }

        override fun close() {
            runCatching { server.close() }
            cut()
        }
    }
}
