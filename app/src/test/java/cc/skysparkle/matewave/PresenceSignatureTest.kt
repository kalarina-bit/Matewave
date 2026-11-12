package cc.skysparkle.matewave

import cc.skysparkle.matewave.network.LanTransport
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresenceSignatureTest {
    private val now = 1_800_000_000_000L
    private val nowSeconds = now / 1000

    @Test fun recentAnnouncementIsFresh() =
        assertTrue(LanTransport.isFreshPresence(nowSeconds - 4 * 60, now))

    @Test fun slightlyFastClockIsTolerated() =
        assertTrue(LanTransport.isFreshPresence(nowSeconds + 5 * 60, now))

    @Test
    fun oldMissingOrFarFutureAnnouncementsAreRejected() {
        assertFalse(LanTransport.isFreshPresence(nowSeconds - 16 * 60, now))
        assertFalse(LanTransport.isFreshPresence(nowSeconds + 16 * 60, now))
        assertFalse(LanTransport.isFreshPresence(0, now))
    }

    @Test
    fun signingTimeIsPartOfTheSignedText() = assertNotEquals(
        LanTransport.presencePayload("u1", "Vladimir", 1350, nowSeconds),
        LanTransport.presencePayload("u1", "Vladimir", 1350, nowSeconds - 60)
    )
}
