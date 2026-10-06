package cc.skysparkle.matewave.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hosts a game on every available transport at once (LAN and, with permission, Bluetooth), so
 * a guest can join over whichever channel found the host. The first transport to connect wins;
 * hosting on the others is stopped and from then on this behaves exactly like the winner.
 */
class DualHostTransport(transports: List<ConnectionTransport>) : ConnectionTransport {

    private val all = transports
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    @Volatile private var active: ConnectionTransport? = null
    private val listeners = CopyOnWriteArrayList<(GameMessage) -> Unit>()
    private val unsubscribes = mutableListOf<() -> Unit>()
    private var watchJob: Job? = null

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _state

    override val type: TransportType get() = active?.type ?: all.first().type

    override fun startHosting() {
        synchronized(lock) {
            active = null
            unsubscribes.forEach { it() }
            unsubscribes.clear()
            all.forEach { t ->
                unsubscribes += t.addMessageListener { message ->
                    // The first message can arrive before the state change is observed: claim here too.
                    if (active == null && t.connectionState.value == ConnectionState.CONNECTED) claim(t)
                    if (active === t) listeners.forEach { it(message) }
                }
            }
        }
        _state.value = ConnectionState.CONNECTING
        all.forEach { runCatching { it.startHosting() } }

        watchJob?.cancel()
        watchJob = scope.launch {
            combine(all.map { it.connectionState }) { it.toList() }.collect { states ->
                val current = active
                if (current == null) {
                    val index = states.indexOfFirst { it == ConnectionState.CONNECTED }
                    when {
                        index >= 0 -> claim(all[index])
                        states.all { it == ConnectionState.FAILED } -> _state.value = ConnectionState.FAILED
                        else -> _state.value = ConnectionState.CONNECTING
                    }
                } else {
                    _state.value = states[all.indexOf(current)]
                }
            }
        }
    }

    private fun claim(winner: ConnectionTransport) {
        val losers = synchronized(lock) {
            if (active != null) return
            active = winner
            all.filter { it !== winner }
        }
        losers.forEach { runCatching { it.disconnect() } }
        _state.value = ConnectionState.CONNECTED
    }

    override fun startDiscoveryAndConnect(targetId: String?) {
        all.first().startDiscoveryAndConnect(targetId)
    }

    override fun send(message: GameMessage) {
        active?.send(message)
    }

    override fun addMessageListener(listener: (GameMessage) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    override fun disconnect() {
        watchJob?.cancel()
        watchJob = null
        synchronized(lock) {
            unsubscribes.forEach { it() }
            unsubscribes.clear()
            active = null
        }
        all.forEach { runCatching { it.disconnect() } }
        _state.value = ConnectionState.DISCONNECTED
    }
}
