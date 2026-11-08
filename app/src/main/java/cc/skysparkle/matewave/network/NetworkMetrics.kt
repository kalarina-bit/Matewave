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
}
