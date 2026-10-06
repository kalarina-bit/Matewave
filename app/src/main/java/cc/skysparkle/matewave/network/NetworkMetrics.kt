package cc.skysparkle.matewave.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object NetworkMetrics {
    private val connectAttempts = AtomicInteger()
    private val connectSuccesses = AtomicInteger()

    private val chunksSent = AtomicLong()
    private val chunkRetries = AtomicLong()
    private val duplicateChunks = AtomicLong()
    private val corruptedMessages = AtomicLong()
    private val messagesDropped = AtomicLong()

    private val lastConnectMillis = AtomicLong()

    private val _updated = MutableStateFlow(0)

    val updated: StateFlow<Int> = _updated

    private fun touch() { _updated.update { it + 1 } }

    fun connectStarted() { connectAttempts.incrementAndGet(); touch() }

    fun connectSucceeded(millis: Long) {
        connectSuccesses.incrementAndGet()
        lastConnectMillis.set(millis)
        touch()
    }

    fun chunkSent() { chunksSent.incrementAndGet() }
    fun chunkRetried(count: Int) { chunkRetries.addAndGet(count.toLong()); touch() }
    fun duplicateChunk() { duplicateChunks.incrementAndGet() }
    fun messageCorrupted() { corruptedMessages.incrementAndGet(); touch() }
    fun messageDropped() { messagesDropped.incrementAndGet(); touch() }

    private fun retryPercent(): Int {
        val sent = chunksSent.get()
        if (sent == 0L) return 0
        return ((chunkRetries.get() * 100) / sent).toInt()
    }

    fun asText(res: android.content.res.Resources): String = buildString {
        appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_connect_attempts,
            connectAttempts.get(), connectSuccesses.get()))
        val last = lastConnectMillis.get()
        if (last > 0) appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_last_connect, last))
        appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_chunks,
            chunksSent.get(), chunkRetries.get(), retryPercent()))
        appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_duplicates, duplicateChunks.get()))
        appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_corrupted, corruptedMessages.get()))
        appendLine(res.getString(cc.skysparkle.matewave.R.string.metrics_dropped, messagesDropped.get()))
    }
}
