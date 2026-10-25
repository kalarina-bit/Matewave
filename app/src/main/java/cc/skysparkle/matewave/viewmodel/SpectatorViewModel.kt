package cc.skysparkle.matewave.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cc.skysparkle.matewave.network.NetworkLog
import cc.skysparkle.matewave.spectate.MAX_SPECTATORS
import cc.skysparkle.matewave.spectate.SpectateTarget
import cc.skysparkle.matewave.spectate.SpectatorClient
import cc.skysparkle.matewave.spectate.SpectatorGame
import cc.skysparkle.matewave.spectate.SpectatorMessage
import cc.skysparkle.matewave.spectate.SpectatorSnapshot
import cc.skysparkle.matewave.spectate.SpectatorView
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SpectatePhase { CONNECTING, WATCHING, ENDED, LOST, FAILED }

data class SpectateUiState(
    val phase: SpectatePhase = SpectatePhase.CONNECTING,
    val failure: SpectatorClient.Failure? = null,
    val view: SpectatorView? = null,
    val whiteMs: Long? = null,
    val blackMs: Long? = null,
    val spectators: Int = 0,
    val max: Int = MAX_SPECTATORS,
    /** Board turned for this spectator only. */
    val flipped: Boolean = false
)

/** Watches one LAN game read-only. Nothing here can reach the players. */
class SpectatorViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SpectateUiState())
    val uiState: StateFlow<SpectateUiState> = _uiState

    private val game = SpectatorGame()
    private val events = Channel<SpectatorClient.Event>(Channel.UNLIMITED)
    private var client: SpectatorClient? = null
    private var target: SpectateTarget? = null
    private var clockAtNanos = 0L
    private var ticker: Job? = null

    init {
        viewModelScope.launch { for (event in events) handle(event) }
    }

    fun start(target: SpectateTarget) {
        if (this.target == target) return
        this.target = target
        client?.stop()
        _uiState.value = SpectateUiState(flipped = _uiState.value.flipped)
        client = SpectatorClient(target.host, target.port, { events.trySend(it) }).also { it.start() }
    }

    fun flip() {
        _uiState.update { it.copy(flipped = !it.flipped) }
    }

    /** Wi-Fi went away on this device: no need to wait for the connection to time out. */
    fun onNetworkLost() {
        when (_uiState.value.phase) {
            SpectatePhase.CONNECTING -> fail(SpectatorClient.Failure.UNREACHABLE)
            SpectatePhase.WATCHING -> lose()
            else -> Unit
        }
    }

    private fun handle(event: SpectatorClient.Event) {
        when (event) {
            is SpectatorClient.Event.Joined -> {
                _uiState.update { it.copy(spectators = event.welcome.count, max = event.welcome.max) }
                load(event.welcome.game)
            }
            is SpectatorClient.Event.Received -> onMessage(event.message)
            is SpectatorClient.Event.JoinFailed -> fail(event.failure)
            is SpectatorClient.Event.Disconnected -> {
                // After the result the host may leave; the result stays on screen.
                if (_uiState.value.phase != SpectatePhase.ENDED) lose()
            }
        }
    }

    private fun onMessage(message: SpectatorMessage) {
        val phase = _uiState.value.phase
        if (phase == SpectatePhase.LOST || phase == SpectatePhase.FAILED) return
        when (message) {
            is SpectatorMessage.MovePlayed -> applied(game.onMove(message))
            is SpectatorMessage.GameOver -> applied(game.onGameOver(message))
            is SpectatorMessage.NewGame -> load(message.game)
            is SpectatorMessage.Count -> _uiState.update { it.copy(spectators = message.count, max = message.max) }
            is SpectatorMessage.Ping -> {
                game.onClock(message.clock)
                publish(clockUpdated = message.clock != null)
            }
            else -> Unit
        }
    }

    private fun applied(result: SpectatorGame.Applied) {
        when (result) {
            SpectatorGame.Applied.APPLIED -> publish(clockUpdated = true)
            SpectatorGame.Applied.NEEDS_RESYNC -> client?.requestResync()
            SpectatorGame.Applied.IGNORED -> Unit
        }
    }

    private fun load(snapshot: SpectatorSnapshot) {
        if (game.load(snapshot)) {
            publish(clockUpdated = true)
        } else {
            NetworkLog.log("SPECTATE", "Received game could not be replayed")
            lose()
        }
    }

    private fun publish(clockUpdated: Boolean) {
        val view = game.view ?: return
        if (clockUpdated) clockAtNanos = System.nanoTime()
        _uiState.update {
            it.copy(
                phase = if (view.result != null) SpectatePhase.ENDED else SpectatePhase.WATCHING,
                failure = null,
                view = view
            )
        }
        showClocks()
        if (ticker?.isActive != true) {
            ticker = viewModelScope.launch {
                while (isActive) {
                    delay(TICK_MS)
                    if (_uiState.value.phase == SpectatePhase.WATCHING) showClocks()
                }
            }
        }
    }

    /** Clocks count down locally between the host's updates. */
    private fun showClocks() {
        val view = game.view ?: return
        val clock = view.clock
        if (clock == null) {
            _uiState.update { it.copy(whiteMs = null, blackMs = null) }
            return
        }
        val shown = if (view.result == null) clock.advancedBy((System.nanoTime() - clockAtNanos) / 1_000_000L) else clock
        _uiState.update { it.copy(whiteMs = shown.whiteMs, blackMs = shown.blackMs) }
    }

    private fun lose() {
        client?.stop()
        _uiState.update { it.copy(phase = SpectatePhase.LOST) }
    }

    private fun fail(failure: SpectatorClient.Failure) {
        client?.stop()
        _uiState.update { it.copy(phase = SpectatePhase.FAILED, failure = failure) }
    }

    override fun onCleared() {
        client?.stop()
        client = null
        events.close()
        super.onCleared()
    }

    private companion object {
        const val TICK_MS = 250L
    }
}
