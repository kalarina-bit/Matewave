package cc.skysparkle.matewave.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Periodic ping that detects a silently dropped connection. */
class Heartbeat(
    private val scope: CoroutineScope,
    private val transport: ConnectionTransport
) {
    private var job: Job? = null

    private val sessionId = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, Int.MAX_VALUE)
    private var unsubscribe: (() -> Unit)? = null

    @Volatile private var lastReplyAt = 0L
    @Volatile private var lastTrafficAt = 0L

    fun start() {
        if (job != null) return
        lastReplyAt = System.currentTimeMillis()
        lastTrafficAt = lastReplyAt

        unsubscribe = transport.addMessageListener { msg ->
            if (msg is GameMessage.PingMsg) {
                if (msg.reply) {
                    if (msg.sessionId == sessionId) lastReplyAt = System.currentTimeMillis()
                } else {
                    transport.send(GameMessage.PingMsg(reply = true, sessionId = msg.sessionId))
                }
            } else {
                lastTrafficAt = System.currentTimeMillis()
            }
        }

        job = scope.launch {
            var wasConnected = false
            while (true) {
                delay(INTERVAL_MS)
                val connected = transport.connectionState.value == ConnectionState.CONNECTED
                if (!connected) {
                    wasConnected = false
                    continue
                }
                if (!wasConnected) {
                    val now = System.currentTimeMillis()
                    lastReplyAt = now
                    lastTrafficAt = now
                    wasConnected = true
                }
                val now = System.currentTimeMillis()
                val silence = now - lastReplyAt

                if (silence > TIMEOUT_MS && now - lastTrafficAt > TIMEOUT_MS) {
                    NetworkLog.reason(
                        transport.type.name,
                        DisconnectReason.REMOTE_DISCONNECTED,
                        "no reply for ${silence / 1000} s"
                    )
                    transport.disconnect()
                    continue
                }

                if (silence > TIMEOUT_MS) {
                    NetworkLog.log(transport.type.name, "No heartbeat reply for ${silence / 1000} s, but data is flowing")
                }
                transport.send(GameMessage.PingMsg(reply = false, sessionId = sessionId))
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        unsubscribe?.invoke()
        unsubscribe = null
    }

    private companion object {
        const val INTERVAL_MS = 5_000L

        const val TIMEOUT_MS = 16_000L
    }
}
