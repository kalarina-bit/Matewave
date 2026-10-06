package cc.skysparkle.matewave.network

import org.json.JSONObject

enum class TransportType { BLUETOOTH, LAN }

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

/**
 * Wire protocol shared by all transports: one JSON object per message.
 * Game messages travel inside [SignedMsg] once the handshake is done; only
 * [ProfileMsg] and [PingMsg] are sent in the clear. Bump [PROTOCOL_VERSION] on any
 * incompatible change: peers with an older version are rejected during the handshake.
 */
sealed class GameMessage {
    data class MoveMsg(
        val uci: String,
        val remainingMillisSelf: Long,
        val gameId: String = "",
        val ply: Int = 0
    ) : GameMessage()

    data class ResignMsg(val byWhite: Boolean, val gameId: String = "") : GameMessage()

    data class DrawOfferMsg(val accept: Boolean? = null, val gameId: String = "") : GameMessage()

    data class ChatMsg(val text: String? = null, val imageBase64: String? = null, val gameId: String = "") : GameMessage()

    data class ProfileMsg(

        val userId: String,
        val name: String,
        val avatarBase64: String?,
        val backgroundBase64: String?,
        val elo: Int,

        val publicKey: String = "",
        val signature: String = "",

        val protocolVersion: Int = PROTOCOL_VERSION,

        val ephemeralKey: String = "",

        val initialSeconds: Int = 0,
        val incrementSeconds: Int = 0,

        val gameId: String = ""
    ) : GameMessage()

    data class TimeoutMsg(val flaggedWhite: Boolean, val gameId: String = "") : GameMessage()

    data class LeaveMsg(val byWhite: Boolean, val gameId: String = "") : GameMessage()

    data class ProfileImagesMsg(
        val avatarBase64: String? = null,
        val backgroundBase64: String? = null,
        val gameId: String = ""
    ) : GameMessage()

    data class PingMsg(val reply: Boolean = false, val sessionId: Int = 0) : GameMessage()

    data class SignedMsg(val inner: String, val tag: String, val seq: Int = 0) : GameMessage()

    data class RematchMsg(
        val action: String,
        val gameId: String = "",
        val newGameId: String = ""
    ) : GameMessage()

    fun toJson(): String {
        val o = JSONObject()
        when (this) {
            is MoveMsg -> {
                o.put("type", "move"); o.put("uci", uci); o.put("t", remainingMillisSelf)
                o.put("gameId", gameId); o.put("ply", ply)
            }
            is ResignMsg -> { o.put("type", "resign"); o.put("white", byWhite); o.put("gameId", gameId) }
            is DrawOfferMsg -> {
                o.put("type", "draw"); o.put("gameId", gameId)
                if (accept != null) o.put("accept", accept)
            }
            is ChatMsg -> {
                o.put("type", "chat"); o.put("gameId", gameId)
                if (text != null) o.put("text", text)
                if (imageBase64 != null) o.put("image", imageBase64)
            }
            is LeaveMsg -> { o.put("type", "leave"); o.put("byWhite", byWhite); o.put("gameId", gameId) }
            is ProfileImagesMsg -> {
                o.put("type", "profile_images"); o.put("gameId", gameId)
                avatarBase64?.let { o.put("avatar", it) }
                backgroundBase64?.let { o.put("background", it) }
            }
            is PingMsg -> { o.put("type", "ping"); o.put("reply", reply); o.put("sid", sessionId) }
            is TimeoutMsg -> { o.put("type", "timeout"); o.put("white", flaggedWhite); o.put("gameId", gameId) }
            is SignedMsg -> {
                o.put("type", "signed"); o.put("inner", inner); o.put("tag", tag); o.put("seq", seq)
            }
            is RematchMsg -> {
                o.put("type", "rematch"); o.put("action", action); o.put("gameId", gameId)
                if (newGameId.isNotEmpty()) o.put("newGameId", newGameId)
            }
            is ProfileMsg -> {
                o.put("type", "profile")
                o.put("userId", userId)
                o.put("name", name)
                o.put("pv", protocolVersion)
                if (initialSeconds > 0) {
                    o.put("ti", initialSeconds)
                    o.put("tx", incrementSeconds)
                }
                if (ephemeralKey.isNotEmpty()) o.put("ek", ephemeralKey)
                if (gameId.isNotEmpty()) o.put("gid", gameId)
                if (publicKey.isNotEmpty()) o.put("pk", publicKey)
                if (signature.isNotEmpty()) o.put("sig", signature)
                if (avatarBase64 != null) o.put("avatar", avatarBase64)
                if (backgroundBase64 != null) o.put("background", backgroundBase64)
                o.put("elo", elo)
            }
        }
        return o.toString()
    }

    companion object {
        const val PROTOCOL_VERSION = 5

        fun fromJson(raw: String): GameMessage {
            val o = JSONObject(raw)
            return when (o.getString("type")) {
                "move" -> MoveMsg(
                    o.getString("uci"), o.optLong("t", 0),
                    o.optString("gameId", ""), o.optInt("ply", 0)
                )
                "resign" -> ResignMsg(o.getBoolean("white"), o.optString("gameId", ""))
                "draw" -> DrawOfferMsg(
                    if (o.has("accept")) o.getBoolean("accept") else null,
                    o.optString("gameId", "")
                )
                "chat" -> ChatMsg(
                    text = if (o.has("text")) o.getString("text") else null,
                    imageBase64 = if (o.has("image")) o.getString("image") else null,
                    gameId = o.optString("gameId", "")
                )
                "leave" -> LeaveMsg(o.getBoolean("byWhite"), o.optString("gameId", ""))
                "profile_images" -> ProfileImagesMsg(
                    if (o.has("avatar")) o.getString("avatar") else null,
                    if (o.has("background")) o.getString("background") else null,
                    gameId = o.optString("gameId", "")
                )
                "ping" -> PingMsg(o.optBoolean("reply", false), o.optInt("sid", 0))
                "timeout" -> TimeoutMsg(o.getBoolean("white"), o.optString("gameId", ""))
                "signed" -> SignedMsg(o.getString("inner"), o.getString("tag"), o.optInt("seq", 0))
                "rematch" -> RematchMsg(
                    o.getString("action"),
                    o.optString("gameId", ""),
                    o.optString("newGameId", "")
                )
                "profile" -> ProfileMsg(

                    userId = o.optString("userId", "").ifBlank { o.getString("name") },
                    protocolVersion = o.optInt("pv", 0),
                    initialSeconds = o.optInt("ti", 0),
                    incrementSeconds = o.optInt("tx", 0),
                    ephemeralKey = o.optString("ek", ""),
                    gameId = o.optString("gid", ""),
                    publicKey = o.optString("pk", ""),
                    signature = o.optString("sig", ""),
                    name = o.getString("name"),
                    avatarBase64 = if (o.has("avatar")) o.getString("avatar") else null,
                    backgroundBase64 = if (o.has("background")) o.getString("background") else null,
                    elo = o.optInt("elo", 1200)
                )
                else -> throw IllegalArgumentException("Unknown message type")
            }
        }
    }
}

/** A single peer-to-peer game connection (Bluetooth LE or LAN). */
interface ConnectionTransport {
    val type: TransportType
    val connectionState: kotlinx.coroutines.flow.StateFlow<ConnectionState>

    fun startHosting()
    fun startDiscoveryAndConnect(targetId: String? = null)

    fun send(message: GameMessage)

    fun addMessageListener(listener: (GameMessage) -> Unit): () -> Unit

    fun disconnect()
}
