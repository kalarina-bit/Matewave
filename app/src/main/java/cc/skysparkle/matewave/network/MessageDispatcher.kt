package cc.skysparkle.matewave.network

import java.util.concurrent.CopyOnWriteArrayList

class MessageDispatcher {
    private val listeners = CopyOnWriteArrayList<(GameMessage) -> Unit>()

    fun add(listener: (GameMessage) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    fun dispatch(message: GameMessage) {
        for (listener in listeners) {
            try {
                listener(message)
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        listeners.clear()
    }
}
