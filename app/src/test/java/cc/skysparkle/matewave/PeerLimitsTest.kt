package cc.skysparkle.matewave

import cc.skysparkle.matewave.network.GameMessage
import cc.skysparkle.matewave.network.PeerLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerLimitsTest {
    private fun profile(
        userId: String = "6f1c2a9e-0b3d-4c5e-8f70-1a2b3c4d5e6f",
        name: String = "Vladimir",
        elo: Int = 1350,
        initialSeconds: Int = 300,
        incrementSeconds: Int = 3
    ) = GameMessage.ProfileMsg(
        userId = userId, name = name, avatarBase64 = null, backgroundBase64 = null, elo = elo,
        initialSeconds = initialSeconds, incrementSeconds = incrementSeconds
    )

    @Test fun ordinaryProfileIsAccepted() = assertTrue(PeerLimits.isPlausibleProfile(profile()))

    @Test fun joiningPlayerWithoutTimeControlIsAccepted() =
        assertTrue(PeerLimits.isPlausibleProfile(profile(initialSeconds = 0, incrementSeconds = 0)))

    @Test fun unlimitedGameIsAccepted() =
        assertTrue(PeerLimits.isPlausibleProfile(profile(initialSeconds = Int.MAX_VALUE, incrementSeconds = 0)))

    @Test
    fun oversizedOrEmptyFieldsAreRefused() {
        assertFalse(PeerLimits.isPlausibleProfile(profile(name = "x".repeat(PeerLimits.MAX_NAME_CHARS + 1))))
        assertFalse(PeerLimits.isPlausibleProfile(profile(name = " ")))
        assertFalse(PeerLimits.isPlausibleProfile(profile(name = "a\u0000b")))
        assertFalse(PeerLimits.isPlausibleProfile(profile(userId = "u".repeat(PeerLimits.MAX_USER_ID_CHARS + 1))))
        assertFalse(PeerLimits.isPlausibleProfile(profile(userId = "")))
    }

    @Test
    fun ratingAndTimeControlMustBeInRange() {
        assertFalse(PeerLimits.isPlausibleProfile(profile(elo = 99)))
        assertFalse(PeerLimits.isPlausibleProfile(profile(elo = 3001)))
        assertFalse(PeerLimits.isPlausibleProfile(profile(initialSeconds = -1)))
        assertFalse(PeerLimits.isPlausibleProfile(profile(initialSeconds = PeerLimits.MAX_INITIAL_SECONDS + 1)))
        assertFalse(PeerLimits.isPlausibleProfile(profile(incrementSeconds = PeerLimits.MAX_INCREMENT_SECONDS + 1)))
    }

    @Test
    fun opponentClockMayNotGrowBeyondIncrementAndSlack() {
        assertEquals(47_000L, PeerLimits.maxOpponentClockAfterMove(42_000, 3_000, 2_000))
        // A flagged opponent (0 ms locally) may still have had a little time on their side.
        assertEquals(5_000L, PeerLimits.maxOpponentClockAfterMove(0, 3_000, 2_000))
        assertEquals(2_000L, PeerLimits.maxOpponentClockAfterMove(-50, 0, 2_000))
    }
}
