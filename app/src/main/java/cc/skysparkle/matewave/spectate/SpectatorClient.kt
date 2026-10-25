package cc.skysparkle.matewave.spectate

import cc.skysparkle.matewave.network.LineResult
import cc.skysparkle.matewave.network.readBoundedLine
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * One spectator's connection to a hosted game. Events arrive on a background thread, in order.
 * Silence longer than [Timing.silenceTimeoutMs] (the host pings every few seconds) means the host
 * is gone, which also covers Wi-Fi switching off on either side.
 */
class SpectatorClient(
    private val host: String,
    private val port: Int,
    private val onEvent: (Event) -> Unit,
    private val timing: Timing = Timing(),
    private val version: Int = SpectatorProtocol.VERSION,
    private val minVersion: Int = SpectatorProtocol.MIN_COMPATIBLE_VERSION
) {
    data class Timing(val connectTimeoutMs: Int = 5_000, val silenceTimeoutMs: Int = 15_000)

    enum class Failure { UNREACHABLE, FULL, NO_GAME, UPDATE_THIS_APP, UPDATE_HOST_APP }

    sealed class Event {
        data class Joined(val welcome: SpectatorMessage.Welcome) : Event()
        data class Received(val message: SpectatorMessage) : Event()
        data class JoinFailed(val failure: Failure) : Event()

        /** The connection ended after joining; [hostClosed] is true when the host said goodbye. */
        data class Disconnected(val hostClosed: Boolean) : Event()
    }

    @Volatile private var socket: Socket? = null
    @Volatile private var output: BufferedWriter? = null
    @Volatile private var stopped = false
    private val writer = Executors.newSingleThreadExecutor { r ->
        Thread(r, "spectator-send").apply { isDaemon = true }
    }

    fun start() {
        Thread({ run() }, "spectator-receive").apply { isDaemon = true }.start()
    }

    fun requestResync() = send(SpectatorMessage.Resync)

    fun stop() {
        if (stopped) return
        stopped = true
        try {
            writer.execute {
                writeLine(SpectatorMessage.Leave)
                closeSocket()
            }
        } catch (e: RejectedExecutionException) {
            closeSocket()
        }
        writer.shutdown()
        // A send stuck on a dead connection must not keep the socket open.
        Thread({
            try { Thread.sleep(STOP_GRACE_MS) } catch (_: InterruptedException) {}
            closeSocket()
        }, "spectator-stop").apply { isDaemon = true }.start()
    }

    private fun run() {
        val s = Socket()
        socket = s
        val reader: BufferedReader
        try {
            if (stopped) throw IOException("Stopped")
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), timing.connectTimeoutMs)
            s.soTimeout = timing.silenceTimeoutMs
            output = BufferedWriter(OutputStreamWriter(s.getOutputStream(), Charsets.UTF_8))
            reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
        } catch (e: IOException) {
            closeSocket()
            emit(Event.JoinFailed(Failure.UNREACHABLE))
            return
        }
        send(SpectatorMessage.Hello(minVersion))

        var joined = false
        var hostClosed = false
        try {
            while (!stopped) {
                val read = readBoundedLine(reader, SpectatorProtocol.MAX_HOST_LINE_CHARS)
                if (read !is LineResult.Line) break
                val envelope = SpectatorProtocol.decode(read.text) ?: continue
                val message = envelope.message
                if (!joined) {
                    // reject keeps its shape across versions, so read it before the version gate.
                    if (message is SpectatorMessage.Reject) {
                        emit(Event.JoinFailed(failureOf(message)))
                        return
                    }
                    if (envelope.version < minVersion) {
                        emit(Event.JoinFailed(Failure.UPDATE_HOST_APP))
                        return
                    }
                    if (message is SpectatorMessage.Welcome) {
                        joined = true
                        emit(Event.Joined(message))
                    }
                    continue
                }
                when (message) {
                    null -> Unit
                    SpectatorMessage.Closing -> hostClosed = true
                    is SpectatorMessage.Ping -> {
                        send(SpectatorMessage.Pong)
                        emit(Event.Received(message))
                    }
                    else -> emit(Event.Received(message))
                }
            }
        } catch (e: IOException) {
            // Silence timeout or connection reset.
        } finally {
            closeSocket()
        }
        if (joined) emit(Event.Disconnected(hostClosed)) else emit(Event.JoinFailed(Failure.UNREACHABLE))
    }

    private fun failureOf(reject: SpectatorMessage.Reject): Failure = when (reject.reason) {
        RejectReason.FULL -> Failure.FULL
        RejectReason.NO_GAME -> Failure.NO_GAME
        RejectReason.UPDATE_REQUIRED ->
            if (version < reject.minVersion) Failure.UPDATE_THIS_APP else Failure.UPDATE_HOST_APP
        RejectReason.OTHER -> Failure.UNREACHABLE
    }

    private fun emit(event: Event) {
        if (!stopped) onEvent(event)
    }

    private fun send(message: SpectatorMessage) {
        try {
            writer.execute { writeLine(message) }
        } catch (e: RejectedExecutionException) {
            // Stopped.
        }
    }

    private fun writeLine(message: SpectatorMessage) {
        val out = output ?: return
        try {
            out.write(SpectatorProtocol.encode(message, version))
            out.write("\n")
            out.flush()
        } catch (e: IOException) {
            closeSocket()
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: IOException) {}
    }

    private companion object {
        const val STOP_GRACE_MS = 500L
    }
}

/** One-shot status query, used to show live spectator counts in the Friends list. */
object SpectatorProbe {
    fun query(host: String, port: Int, timeoutMs: Int = 2_000): SpectatorMessage.Status? = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), timeoutMs)
            s.soTimeout = timeoutMs
            val out = s.getOutputStream()
            out.write((SpectatorProtocol.encode(SpectatorMessage.StatusQuery) + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val read = readBoundedLine(reader, SpectatorProtocol.MAX_HOST_LINE_CHARS)
            (read as? LineResult.Line)?.let { SpectatorProtocol.decode(it.text)?.message as? SpectatorMessage.Status }
        }
    } catch (e: IOException) {
        null
    }
}
