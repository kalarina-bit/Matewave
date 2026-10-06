package cc.skysparkle.matewave

import cc.skysparkle.matewave.network.GameMessage
import org.junit.Assert.assertEquals
import org.junit.Test

class GameMessageTest {
    private fun roundTrip(msg: GameMessage) = assertEquals(msg, GameMessage.fromJson(msg.toJson()))

    @Test fun move() = roundTrip(GameMessage.MoveMsg("e2e4", 295_000, "g1", 1))
    @Test fun resign() = roundTrip(GameMessage.ResignMsg(true, "g1"))
    @Test fun drawOffer() = roundTrip(GameMessage.DrawOfferMsg(null, "g1"))
    @Test fun drawAccept() = roundTrip(GameMessage.DrawOfferMsg(true, "g1"))
    @Test fun chatText() = roundTrip(GameMessage.ChatMsg(text = "Привет", gameId = "g1"))
    @Test fun leave() = roundTrip(GameMessage.LeaveMsg(false, "g1"))
    @Test fun timeout() = roundTrip(GameMessage.TimeoutMsg(true, "g1"))
    @Test fun ping() = roundTrip(GameMessage.PingMsg(true, 42))
    @Test fun signed() = roundTrip(GameMessage.SignedMsg("abc", "tag", 7))
    @Test fun rematch() = roundTrip(GameMessage.RematchMsg("accept", "g1", "g2"))
    @Test fun images() = roundTrip(GameMessage.ProfileImagesMsg("a", null, "g1"))

    @Test
    fun profileCarriesGameIdAndVersion() {
        val msg = GameMessage.ProfileMsg(
            userId = "u1", name = "Vladimir", avatarBase64 = null, backgroundBase64 = null, elo = 1350,
            publicKey = "pk", signature = "sig", ephemeralKey = "ek",
            initialSeconds = 300, incrementSeconds = 2, gameId = "g1"
        )
        roundTrip(msg)
        assertEquals(GameMessage.PROTOCOL_VERSION, (GameMessage.fromJson(msg.toJson()) as GameMessage.ProfileMsg).protocolVersion)
    }
}
