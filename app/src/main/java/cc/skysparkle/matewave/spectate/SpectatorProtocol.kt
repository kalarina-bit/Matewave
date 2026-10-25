package cc.skysparkle.matewave.spectate

import cc.skysparkle.matewave.engine.PieceColor
import org.json.JSONArray
import org.json.JSONObject

/** Most spectators one hosted game accepts at the same time. */
const val MAX_SPECTATORS = 20

data class SpectatorPlayer(val id: String, val name: String, val elo: Int)

data class SpectatorClock(val whiteMs: Long, val blackMs: Long, val running: PieceColor?) {
    /** The same clock [elapsedMs] later. */
    fun advancedBy(elapsedMs: Long): SpectatorClock {
        if (elapsedMs <= 0) return this
        return when (running) {
            PieceColor.WHITE -> copy(whiteMs = (whiteMs - elapsedMs).coerceAtLeast(0))
            PieceColor.BLACK -> copy(blackMs = (blackMs - elapsedMs).coerceAtLeast(0))
            null -> this
        }
    }
}

enum class SpectatorOutcome(val wire: String) {
    CHECKMATE("checkmate"),
    STALEMATE("stalemate"),
    FIFTY_MOVES("fifty"),
    REPETITION("repetition"),
    INSUFFICIENT_MATERIAL("material"),
    TIMEOUT("timeout"),
    TIMEOUT_DRAW("timeout_draw"),
    RESIGNATION("resign"),
    AGREEMENT("agreement"),
    LEFT("left"),
    INTERRUPTED("interrupted"),
    UNKNOWN("unknown");

    companion object {
        fun fromWire(value: String): SpectatorOutcome = entries.firstOrNull { it.wire == value } ?: UNKNOWN
    }
}

/** How a game ended; [winner] is null for draws and interrupted games. */
data class SpectatorResult(val outcome: SpectatorOutcome, val winner: PieceColor?)

/** Everything a spectator needs to show a game from any point: players, moves, clocks, result. */
data class SpectatorSnapshot(
    val gameId: String,
    val white: SpectatorPlayer,
    val black: SpectatorPlayer,
    val movesUci: List<String>,
    val initialSeconds: Int,
    val incrementSeconds: Int,
    val clock: SpectatorClock?,
    val result: SpectatorResult? = null
)

enum class RejectReason(val wire: String) {
    FULL("full"),
    UPDATE_REQUIRED("update"),
    NO_GAME("no_game"),
    OTHER("other");

    companion object {
        fun fromWire(value: String): RejectReason = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

sealed class SpectatorMessage {
    // Spectator to host.
    data class Hello(val minVersion: Int = SpectatorProtocol.MIN_COMPATIBLE_VERSION) : SpectatorMessage()
    data object StatusQuery : SpectatorMessage()
    data object Pong : SpectatorMessage()
    data object Resync : SpectatorMessage()
    data object Leave : SpectatorMessage()

    // Host to spectator.
    data class Welcome(val count: Int, val max: Int, val game: SpectatorSnapshot) : SpectatorMessage()
    data class Status(val inGame: Boolean, val count: Int, val max: Int, val minVersion: Int) : SpectatorMessage()
    data class Reject(val reason: RejectReason, val minVersion: Int) : SpectatorMessage()
    data class MovePlayed(val gameId: String, val ply: Int, val uci: String, val clock: SpectatorClock?) : SpectatorMessage()
    data class GameOver(val gameId: String, val result: SpectatorResult, val clock: SpectatorClock?) : SpectatorMessage()
    data class NewGame(val game: SpectatorSnapshot) : SpectatorMessage()
    data class Count(val count: Int, val max: Int) : SpectatorMessage()
    data class Ping(val clock: SpectatorClock?) : SpectatorMessage()
    data object Closing : SpectatorMessage()
}

/** One decoded line: the sender's protocol version and the message, or null for an unknown type. */
data class SpectatorEnvelope(val version: Int, val message: SpectatorMessage?)

/**
 * Wire format between a hosted game and its spectators: one JSON object per line, each carrying
 * the sender's protocol version in "sv". Unknown types are skipped, never fatal.
 */
object SpectatorProtocol {
    /**
     * Bump on incompatible changes and raise [MIN_COMPATIBLE_VERSION] to the oldest version still
     * understood. hello, status and reject keep their shape so any two versions can still tell
     * each other that an update is required.
     */
    const val VERSION = 1
    const val MIN_COMPATIBLE_VERSION = 1

    const val MAX_HOST_LINE_CHARS = 64_000
    const val MAX_CLIENT_LINE_CHARS = 1_024

    private const val MAX_NAME_CHARS = 40
    private const val MAX_PLIES = 2_000

    fun encode(message: SpectatorMessage, version: Int = VERSION): String {
        val o = JSONObject()
        o.put("sv", version)
        when (message) {
            is SpectatorMessage.Hello -> { o.put("type", "hello"); o.put("min", message.minVersion) }
            SpectatorMessage.StatusQuery -> o.put("type", "query")
            SpectatorMessage.Pong -> o.put("type", "pong")
            SpectatorMessage.Resync -> o.put("type", "resync")
            SpectatorMessage.Leave -> o.put("type", "leave")
            is SpectatorMessage.Welcome -> {
                o.put("type", "welcome"); o.put("n", message.count); o.put("max", message.max)
                o.put("game", snapshotJson(message.game))
            }
            is SpectatorMessage.Status -> {
                o.put("type", "status"); o.put("live", message.inGame)
                o.put("n", message.count); o.put("max", message.max); o.put("min", message.minVersion)
            }
            is SpectatorMessage.Reject -> {
                o.put("type", "reject"); o.put("reason", message.reason.wire); o.put("min", message.minVersion)
            }
            is SpectatorMessage.MovePlayed -> {
                o.put("type", "move"); o.put("gid", message.gameId); o.put("ply", message.ply); o.put("uci", message.uci)
                message.clock?.let { o.put("clock", clockJson(it)) }
            }
            is SpectatorMessage.GameOver -> {
                o.put("type", "end"); o.put("gid", message.gameId); o.put("result", resultJson(message.result))
                message.clock?.let { o.put("clock", clockJson(it)) }
            }
            is SpectatorMessage.NewGame -> { o.put("type", "game"); o.put("game", snapshotJson(message.game)) }
            is SpectatorMessage.Count -> { o.put("type", "count"); o.put("n", message.count); o.put("max", message.max) }
            is SpectatorMessage.Ping -> {
                o.put("type", "ping")
                message.clock?.let { o.put("clock", clockJson(it)) }
            }
            SpectatorMessage.Closing -> o.put("type", "closing")
        }
        return o.toString()
    }

    /** Null when the line is not a protocol message at all. */
    fun decode(line: String): SpectatorEnvelope? {
        val o = runCatching { JSONObject(line) }.getOrNull() ?: return null
        val type = o.optString("type", "")
        if (type.isEmpty()) return null
        val version = o.optInt("sv", 0)
        val message = runCatching { decodeMessage(type, o) }.getOrNull()
        return SpectatorEnvelope(version, message)
    }

    private fun decodeMessage(type: String, o: JSONObject): SpectatorMessage? = when (type) {
        "hello" -> SpectatorMessage.Hello(o.optInt("min", o.optInt("sv", 0)))
        "query" -> SpectatorMessage.StatusQuery
        "pong" -> SpectatorMessage.Pong
        "resync" -> SpectatorMessage.Resync
        "leave" -> SpectatorMessage.Leave
        "welcome" -> SpectatorMessage.Welcome(o.getInt("n"), o.getInt("max"), parseSnapshot(o.getJSONObject("game")))
        "status" -> SpectatorMessage.Status(o.getBoolean("live"), o.getInt("n"), o.getInt("max"), o.optInt("min", 0))
        "reject" -> SpectatorMessage.Reject(RejectReason.fromWire(o.getString("reason")), o.optInt("min", 0))
        "move" -> SpectatorMessage.MovePlayed(
            o.getString("gid"), o.getInt("ply"), o.getString("uci"), o.optJSONObject("clock")?.let(::parseClock)
        )
        "end" -> SpectatorMessage.GameOver(
            o.getString("gid"), parseResult(o.getJSONObject("result")), o.optJSONObject("clock")?.let(::parseClock)
        )
        "game" -> SpectatorMessage.NewGame(parseSnapshot(o.getJSONObject("game")))
        "count" -> SpectatorMessage.Count(o.getInt("n"), o.getInt("max"))
        "ping" -> SpectatorMessage.Ping(o.optJSONObject("clock")?.let(::parseClock))
        "closing" -> SpectatorMessage.Closing
        else -> null
    }

    private fun sideWire(side: PieceColor?): String = when (side) {
        PieceColor.WHITE -> "w"
        PieceColor.BLACK -> "b"
        null -> ""
    }

    private fun sideOf(wire: String): PieceColor? = when (wire) {
        "w" -> PieceColor.WHITE
        "b" -> PieceColor.BLACK
        else -> null
    }

    private fun clockJson(c: SpectatorClock) = JSONObject().apply {
        put("w", c.whiteMs); put("b", c.blackMs)
        if (c.running != null) put("r", sideWire(c.running))
    }

    private fun parseClock(o: JSONObject) = SpectatorClock(
        whiteMs = o.getLong("w").coerceAtLeast(0),
        blackMs = o.getLong("b").coerceAtLeast(0),
        running = sideOf(o.optString("r", ""))
    )

    private fun resultJson(r: SpectatorResult) = JSONObject().apply {
        put("o", r.outcome.wire)
        if (r.winner != null) put("w", sideWire(r.winner))
    }

    private fun parseResult(o: JSONObject) = SpectatorResult(
        outcome = SpectatorOutcome.fromWire(o.getString("o")),
        winner = sideOf(o.optString("w", ""))
    )

    private fun playerJson(p: SpectatorPlayer) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("elo", p.elo)
    }

    private fun parsePlayer(o: JSONObject) = SpectatorPlayer(
        id = o.optString("id", "").take(64),
        name = o.optString("name", "").take(MAX_NAME_CHARS),
        elo = o.optInt("elo", 0)
    )

    private fun snapshotJson(s: SpectatorSnapshot) = JSONObject().apply {
        put("gid", s.gameId)
        put("wp", playerJson(s.white))
        put("bp", playerJson(s.black))
        put("moves", JSONArray().also { array -> s.movesUci.forEach { array.put(it) } })
        put("ti", s.initialSeconds)
        put("tx", s.incrementSeconds)
        s.clock?.let { put("clock", clockJson(it)) }
        s.result?.let { put("result", resultJson(it)) }
    }

    private fun parseSnapshot(o: JSONObject): SpectatorSnapshot {
        val array = o.getJSONArray("moves")
        require(array.length() <= MAX_PLIES)
        return SpectatorSnapshot(
            gameId = o.getString("gid"),
            white = parsePlayer(o.getJSONObject("wp")),
            black = parsePlayer(o.getJSONObject("bp")),
            movesUci = List(array.length()) { array.getString(it) },
            initialSeconds = o.optInt("ti", 0),
            incrementSeconds = o.optInt("tx", 0),
            clock = o.optJSONObject("clock")?.let(::parseClock),
            result = o.optJSONObject("result")?.let(::parseResult)
        )
    }
}
