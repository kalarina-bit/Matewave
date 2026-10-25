package cc.skysparkle.matewave.spectate

import cc.skysparkle.matewave.network.LineResult
import cc.skysparkle.matewave.network.readBoundedLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Runs on the host player's device and streams the game to up to [maxSpectators] spectators on
 * its own listening socket; the players' connection is never touched. Each spectator gets its own
 * bounded queue and writer thread, so a slow or vanished spectator is dropped without delaying
 * the game or anyone else. Spectators can only listen: anything they send besides keepalives and
 * resync requests is ignored.
 */
class SpectatorHub(
    private val maxSpectators: Int = MAX_SPECTATORS,
    private val timing: Timing = Timing(),
    private val log: (String) -> Unit = {}
) {
    data class Timing(
        val helloTimeoutMs: Int = 5_000,
        val pingIntervalMs: Long = 5_000,
        /** A spectator that answers no ping for this long is gone (e.g. its Wi-Fi went off). */
        val silenceTimeoutMs: Int = 20_000,
        val queueCapacity: Int = 512
    )

    private val lock = Any()
    private val spectators = LinkedHashSet<Spectator>()
    private var latest: SpectatorSnapshot? = null
    private var latestAtNanos = 0L
    private var closed = false
    private var server: ServerSocket? = null
    private val handshaking = AtomicInteger()

    private val threads = Executors.newCachedThreadPool(daemonThreads("spectator"))
    private val ticker = Executors.newSingleThreadScheduledExecutor(daemonThreads("spectator-ping"))

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    val port: Int get() = server?.localPort ?: 0

    /** Opens the spectator socket on [bindAddress] (all interfaces by default) and returns its port. */
    fun start(bindAddress: InetAddress? = null): Int {
        val socket = synchronized(lock) {
            check(!closed) { "Hub is closed" }
            server?.let { return it.localPort }
            ServerSocket().also {
                it.reuseAddress = true
                it.bind(InetSocketAddress(bindAddress, 0))
                server = it
            }
        }
        threads.execute { acceptLoop(socket) }
        ticker.scheduleAtFixedRate(
            { runCatching { sendPings() } },
            timing.pingIntervalMs, timing.pingIntervalMs, TimeUnit.MILLISECONDS
        )
        return socket.localPort
    }

    /** Replaces the current game state and sends [events] to every spectator, atomically. */
    fun publish(snapshot: SpectatorSnapshot, events: List<SpectatorMessage> = emptyList()) {
        synchronized(lock) {
            if (closed) return
            latest = snapshot
            latestAtNanos = System.nanoTime()
            if (events.isEmpty() || spectators.isEmpty()) return
            val targets = spectators.toList()
            events.forEach { deliver(targets, SpectatorProtocol.encode(it)) }
        }
    }

    /** Sends [finalEvents] and a goodbye to everyone, then stops accepting and closes. */
    fun close(finalEvents: List<SpectatorMessage> = emptyList()) {
        val targets = synchronized(lock) {
            if (closed) return
            closed = true
            spectators.toList().also { spectators.clear() }
        }
        _count.value = 0
        val lines = finalEvents.map { SpectatorProtocol.encode(it) } + SpectatorProtocol.encode(SpectatorMessage.Closing)
        targets.forEach { spectator ->
            lines.forEach { spectator.offer(it) }
            spectator.finish()
        }
        closeQuietly(server)
        ticker.shutdownNow()
        threads.shutdown()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (!socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (e: IOException) {
                if (socket.isClosed) return
                log("Spectator accept failed: ${e.message}")
                continue
            }
            if (handshaking.incrementAndGet() > MAX_HANDSHAKING) {
                handshaking.decrementAndGet()
                closeQuietly(client)
                continue
            }
            try {
                threads.execute { serve(client) }
            } catch (e: RejectedExecutionException) {
                handshaking.decrementAndGet()
                closeQuietly(client)
            }
        }
    }

    private fun serve(client: Socket) {
        var spectator: Spectator? = null
        var reader: BufferedReader? = null
        try {
            client.tcpNoDelay = true
            client.soTimeout = timing.helloTimeoutMs
            reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
            val first = readBoundedLine(reader, SpectatorProtocol.MAX_CLIENT_LINE_CHARS)
            val envelope = (first as? LineResult.Line)?.let { SpectatorProtocol.decode(it.text) }
            when (envelope?.message) {
                SpectatorMessage.StatusQuery -> reply(client, status())
                is SpectatorMessage.Hello -> {
                    if (envelope.version < SpectatorProtocol.MIN_COMPATIBLE_VERSION) {
                        log("Spectator with protocol ${envelope.version} told to update")
                        reply(client, SpectatorMessage.Reject(RejectReason.UPDATE_REQUIRED, SpectatorProtocol.MIN_COMPATIBLE_VERSION))
                    } else {
                        spectator = admit(client)
                    }
                }
                else -> Unit
            }
        } catch (e: IOException) {
            // Timed out or closed before saying hello.
        } finally {
            handshaking.decrementAndGet()
            if (spectator == null) closeQuietly(client)
        }
        val admitted = spectator ?: return
        listen(admitted, reader ?: return)
    }

    private fun admit(client: Socket): Spectator? {
        var reject: RejectReason? = null
        var spectator: Spectator? = null
        synchronized(lock) {
            val game = latest
            when {
                closed || game == null -> reject = RejectReason.NO_GAME
                spectators.size >= maxSpectators -> reject = RejectReason.FULL
                else -> {
                    val joined = Spectator(client, timing.queueCapacity)
                    val others = spectators.toList()
                    spectators.add(joined)
                    val n = spectators.size
                    _count.value = n
                    joined.offer(SpectatorProtocol.encode(SpectatorMessage.Welcome(n, maxSpectators, liveSnapshot(game))))
                    deliver(others, SpectatorProtocol.encode(SpectatorMessage.Count(n, maxSpectators)))
                    spectator = joined
                }
            }
        }
        reject?.let {
            reply(client, SpectatorMessage.Reject(it, SpectatorProtocol.MIN_COMPATIBLE_VERSION))
            return null
        }
        val joined = spectator ?: return null
        try {
            threads.execute { write(joined) }
        } catch (e: RejectedExecutionException) {
            drop(joined)
            return null
        }
        return joined
    }

    private fun listen(spectator: Spectator, reader: BufferedReader) {
        try {
            spectator.socket.soTimeout = timing.silenceTimeoutMs
            while (true) {
                val read = readBoundedLine(reader, SpectatorProtocol.MAX_CLIENT_LINE_CHARS)
                if (read !is LineResult.Line) break
                when (SpectatorProtocol.decode(read.text)?.message) {
                    SpectatorMessage.Resync -> synchronized(lock) {
                        val game = latest
                        if (game != null && !closed) {
                            deliver(listOf(spectator), SpectatorProtocol.encode(SpectatorMessage.NewGame(liveSnapshot(game))))
                        }
                    }
                    SpectatorMessage.Leave -> break
                    else -> Unit
                }
            }
        } catch (e: SocketTimeoutException) {
            log("Spectator silent for ${timing.silenceTimeoutMs / 1000} s, dropped")
        } catch (e: IOException) {
            // Connection reset.
        } finally {
            drop(spectator)
        }
    }

    private fun write(spectator: Spectator) {
        try {
            val out = BufferedWriter(OutputStreamWriter(spectator.socket.getOutputStream(), Charsets.UTF_8))
            while (true) {
                val item = spectator.queue.take()
                if (item === POISON) break
                out.write(item as String)
                out.write("\n")
                if (spectator.queue.isEmpty()) out.flush()
            }
            out.flush()
        } catch (e: IOException) {
            // Spectator went away.
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            drop(spectator)
            closeQuietly(spectator.socket)
        }
    }

    private fun sendPings() {
        val line: String
        val targets: List<Spectator>
        synchronized(lock) {
            if (closed || spectators.isEmpty()) return
            line = SpectatorProtocol.encode(SpectatorMessage.Ping(liveClock()))
            targets = spectators.toList()
        }
        deliver(targets, line)
    }

    private fun deliver(targets: List<Spectator>, line: String) {
        for (spectator in targets) {
            if (!spectator.offer(line)) {
                log("Spectator too slow, dropped")
                drop(spectator)
            }
        }
    }

    private fun drop(spectator: Spectator) {
        val others: List<Spectator>
        val remaining: Int
        synchronized(lock) {
            // Already dropped, or the hub is closing and its writer is flushing the goodbye.
            if (!spectators.remove(spectator)) return
            remaining = spectators.size
            _count.value = remaining
            others = spectators.toList()
        }
        spectator.shutdown()
        if (!closed) deliver(others, SpectatorProtocol.encode(SpectatorMessage.Count(remaining, maxSpectators)))
    }

    private fun status(): SpectatorMessage.Status = synchronized(lock) {
        val game = latest
        SpectatorMessage.Status(
            inGame = !closed && game != null && game.result == null,
            count = spectators.size,
            max = maxSpectators,
            minVersion = SpectatorProtocol.MIN_COMPATIBLE_VERSION
        )
    }

    private fun elapsedMs(): Long = (System.nanoTime() - latestAtNanos) / 1_000_000L

    private fun liveClock(): SpectatorClock? = latest?.clock?.advancedBy(elapsedMs())

    private fun liveSnapshot(game: SpectatorSnapshot): SpectatorSnapshot =
        if (game.clock == null) game else game.copy(clock = game.clock.advancedBy(elapsedMs()))

    private fun reply(client: Socket, message: SpectatorMessage) {
        try {
            val out = client.getOutputStream()
            out.write((SpectatorProtocol.encode(message) + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (e: IOException) {
            // Nothing more to tell a peer that is gone.
        }
    }

    /** One spectator's outgoing queue; one slot is kept free for the end marker. */
    private class Spectator(val socket: Socket, private val capacity: Int) {
        val queue = ArrayBlockingQueue<Any>(capacity + 1)
        private var open = true

        @Synchronized
        fun offer(line: String): Boolean = open && queue.size < capacity && queue.offer(line)

        /** Lets the writer send what is queued, then close. */
        @Synchronized
        fun finish() {
            if (!open) return
            open = false
            queue.offer(POISON)
        }

        /** Stops at once, discarding anything unsent. */
        @Synchronized
        fun shutdown() {
            open = false
            queue.clear()
            queue.offer(POISON)
            closeQuietly(socket)
        }
    }

    private companion object {
        val POISON = Any()

        /** Connections allowed to sit in the hello phase at once. */
        const val MAX_HANDSHAKING = 32

        fun closeQuietly(c: Closeable?) {
            try { c?.close() } catch (_: IOException) {}
        }

        fun daemonThreads(name: String): ThreadFactory {
            val n = AtomicInteger()
            return ThreadFactory { r -> Thread(r, "$name-${n.incrementAndGet()}").apply { isDaemon = true } }
        }
    }
}

/** Feeds a hub from the host's game: each new snapshot becomes the messages spectators need. */
class SpectatorBroadcaster(val hub: SpectatorHub) {
    private var last: SpectatorSnapshot? = null

    fun update(snapshot: SpectatorSnapshot) {
        val events = SpectatorDiff.between(last, snapshot)
        last = snapshot
        hub.publish(snapshot, events)
    }

    /** Stops spectating; [final] is the last state, e.g. the result of a game left midway. */
    fun close(final: SpectatorSnapshot? = null) {
        val events = if (final != null) SpectatorDiff.between(last, final) else emptyList()
        hub.close(events)
    }
}
