package cc.skysparkle.matewave.spectate

/**
 * What this device adds to its network announcement while it plays a watchable LAN game: a short
 * tag shared by both players of the game, and, on the host, the spectator port. Older app versions
 * ignore these attributes.
 */
object SpectatorAdvert {
    const val KEY_GAME = "g"
    const val KEY_PORT = "sp"

    data class State(val gameTag: String = "", val spectatePort: Int = 0)

    @Volatile var current = State()
        private set

    /** Returns true when the announcement has to be published again. */
    @Synchronized
    fun update(state: State): Boolean {
        if (state == current) return false
        current = state
        return true
    }

    fun clear(): Boolean = update(State())

    fun attributes(state: State = current): Map<String, String> = buildMap {
        if (state.gameTag.isNotEmpty()) put(KEY_GAME, state.gameTag)
        if (state.gameTag.isNotEmpty() && state.spectatePort > 0) put(KEY_PORT, state.spectatePort.toString())
    }

    /** Reads the attributes of a discovered player; anything malformed counts as absent. */
    fun parse(attribute: (String) -> String): State {
        val tag = attribute(KEY_GAME).takeIf { TAG_PATTERN.matches(it) } ?: return State()
        val port = attribute(KEY_PORT).toIntOrNull()?.takeIf { it in 1..65_535 } ?: 0
        return State(tag, port)
    }

    /** Short tag of a game id, so the game id itself is not announced. */
    fun tagOf(gameId: String): String {
        if (gameId.isEmpty()) return ""
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(gameId.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    private val TAG_PATTERN = Regex("^[0-9a-f]{8}$")
}
