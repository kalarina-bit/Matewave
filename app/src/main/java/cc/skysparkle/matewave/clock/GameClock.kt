package cc.skysparkle.matewave.clock

import cc.skysparkle.matewave.engine.PieceColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ClockState(
    val whiteMillis: Long,
    val blackMillis: Long,
    val running: PieceColor?,
    val flagged: PieceColor? = null
)

/** Chess clock with increment; state is exposed as a StateFlow for the UI. */
class GameClock(private val timeControl: TimeControl, private val scope: CoroutineScope) {
    private val unlimited = timeControl.initialSeconds == Int.MAX_VALUE

    private var whiteLeft = timeControl.initialSeconds * 1000L
    private var blackLeft = timeControl.initialSeconds * 1000L
    private var running: PieceColor? = null
    private var flagged: PieceColor? = null
    private var lastAccountedNs = System.nanoTime()

    private val _state = MutableStateFlow(ClockState(whiteLeft, blackLeft, null))
    val state: StateFlow<ClockState> = _state

    private var tickJob: Job? = null

    private fun account() {
        val now = System.nanoTime()
        val elapsedMs = (now - lastAccountedNs) / 1_000_000L
        lastAccountedNs = now
        val side = running ?: return
        if (unlimited || flagged != null || elapsedMs <= 0) return
        if (side == PieceColor.WHITE) whiteLeft = (whiteLeft - elapsedMs).coerceAtLeast(0)
        else blackLeft = (blackLeft - elapsedMs).coerceAtLeast(0)
    }

    private fun publish() {
        _state.value = ClockState(whiteLeft, blackLeft, running, flagged)
    }

    fun start(side: PieceColor) {
        tickJob?.cancel()
        account()
        running = side
        publish()
        if (unlimited) return
        tickJob = scope.launch {
            while (isActive) {
                val current = running ?: break
                val left = if (current == PieceColor.WHITE) whiteLeft else blackLeft

                val sinceAccount = (System.nanoTime() - lastAccountedNs) / 1_000_000L
                delay(((left - sinceAccount) % 1000L + 1L).coerceIn(1L, 1000L))
                account()
                val nowLeft = if (current == PieceColor.WHITE) whiteLeft else blackLeft
                if (nowLeft <= 0L) {
                    flagged = current
                    publish()
                    break
                }
                publish()
            }
        }
    }

    fun onMoveMade(movedSide: PieceColor) {
        account()
        val incMs = timeControl.incrementSeconds * 1000L
        if (!unlimited) {
            if (movedSide == PieceColor.WHITE) whiteLeft += incMs else blackLeft += incMs
        }
        start(if (movedSide == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE)
    }

    fun applyRemoteTime(side: PieceColor, remainingMillis: Long) {
        if (remainingMillis < 0 || unlimited) return
        account()

        if (flagged == side && remainingMillis > 0) {
            // The opponent's move arrived in time after all: unflag and keep the clock ticking.
            flagged = null
            if (side == PieceColor.WHITE) whiteLeft = remainingMillis else blackLeft = remainingMillis
            lastAccountedNs = System.nanoTime()
            val current = running
            if (current != null) start(current) else publish()
            return
        }
        val local = if (side == PieceColor.WHITE) whiteLeft else blackLeft
        if (kotlin.math.abs(local - remainingMillis) < SYNC_THRESHOLD_MS) return
        if (side == PieceColor.WHITE) whiteLeft = remainingMillis else blackLeft = remainingMillis
        publish()
    }

    fun restore(whiteMillis: Long, blackMillis: Long) {
        if (unlimited) return
        account()
        if (whiteMillis >= 0) whiteLeft = whiteMillis
        if (blackMillis >= 0) blackLeft = blackMillis
        publish()
    }

    fun pause() {
        tickJob?.cancel()
        account()
        running = null
        publish()
    }

    fun stop() {
        tickJob?.cancel()
        account()
        running = null
        publish()
    }

    private companion object {
        const val SYNC_THRESHOLD_MS = 1_500L
    }
}
