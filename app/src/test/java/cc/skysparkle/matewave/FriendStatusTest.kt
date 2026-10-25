package cc.skysparkle.matewave

import cc.skysparkle.matewave.spectate.FriendStatusResolver
import cc.skysparkle.matewave.spectate.MAX_SPECTATORS
import cc.skysparkle.matewave.spectate.NearbyPlayerGame
import cc.skysparkle.matewave.spectate.PresenceStatus
import cc.skysparkle.matewave.spectate.SpectateTarget
import cc.skysparkle.matewave.spectate.SpectatorAdvert
import cc.skysparkle.matewave.spectate.SpectatorMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendStatusTest {
    private val tag = SpectatorAdvert.tagOf("game-1")
    private val host = NearbyPlayerGame("host", "192.168.1.10", tag, 40_123)
    private val guest = NearbyPlayerGame("guest", "192.168.1.11", tag, 0)
    private val idle = NearbyPlayerGame("idle", "192.168.1.12", "", 0)
    private val nearby = listOf(host, guest, idle).associateBy { it.playerId }
    private val target = SpectateTarget("192.168.1.10", 40_123, tag)

    private fun status(count: Int, live: Boolean = true) = SpectatorMessage.Status(live, count, MAX_SPECTATORS, 1)

    @Test
    fun notVisibleIsOffline() {
        assertEquals(PresenceStatus.OFFLINE, FriendStatusResolver.resolve("nobody", nearby, emptyMap()).status)
    }

    @Test
    fun visibleWithoutAGameIsOnline() {
        val s = FriendStatusResolver.resolve("idle", nearby, emptyMap())
        assertEquals(PresenceStatus.ONLINE, s.status)
        assertNull(s.target)
    }

    @Test
    fun hostInGameCanBeWatchedWithTheLiveCount() {
        val s = FriendStatusResolver.resolve("host", nearby, mapOf(target to status(7)))
        assertEquals(PresenceStatus.IN_GAME, s.status)
        assertEquals(target, s.target)
        assertEquals(7, s.spectators)
        assertFalse(s.full)
    }

    @Test
    fun guestIsWatchedThroughTheHost() {
        val s = FriendStatusResolver.resolve("guest", nearby, mapOf(target to status(3)))
        assertEquals(PresenceStatus.IN_GAME, s.status)
        assertEquals(target, s.target)
        assertEquals(3, s.spectators)
    }

    @Test
    fun fullGameIsReportedAsFull() {
        assertTrue(FriendStatusResolver.resolve("host", nearby, mapOf(target to status(MAX_SPECTATORS))).full)
    }

    @Test
    fun countIsUnknownUntilTheHostAnswers() {
        val s = FriendStatusResolver.resolve("host", nearby, emptyMap())
        assertEquals(PresenceStatus.IN_GAME, s.status)
        assertNull(s.spectators)
        assertFalse(s.full)
    }

    @Test
    fun hostSayingTheGameEndedWinsOverAStaleAnnouncement() {
        val ended = mapOf(target to status(0, live = false))
        assertEquals(PresenceStatus.ONLINE, FriendStatusResolver.resolve("host", nearby, ended).status)
        assertEquals(PresenceStatus.ONLINE, FriendStatusResolver.resolve("guest", nearby, ended).status)
    }

    @Test
    fun guestWhoseHostIsHiddenIsInGameWithoutWatch() {
        val s = FriendStatusResolver.resolve("guest", mapOf("guest" to guest), emptyMap())
        assertEquals(PresenceStatus.IN_GAME, s.status)
        assertNull(s.target)
    }

    @Test
    fun onlyHostsAreProbed() {
        assertEquals(setOf(target), FriendStatusResolver.targets(nearby.values))
    }

    @Test
    fun advertRoundTripsAndRejectsJunk() {
        val state = SpectatorAdvert.State(tag, 40_123)
        val attrs = SpectatorAdvert.attributes(state)
        assertEquals(state, SpectatorAdvert.parse { attrs[it] ?: "" })
        assertEquals(SpectatorAdvert.State(), SpectatorAdvert.parse { "" })
        assertEquals(SpectatorAdvert.State(), SpectatorAdvert.parse { if (it == "g") "not-a-tag" else "1" })
        assertEquals(SpectatorAdvert.State(tag, 0), SpectatorAdvert.parse { if (it == "g") tag else "99999" })
        assertEquals(emptyMap<String, String>(), SpectatorAdvert.attributes(SpectatorAdvert.State()))
        assertEquals(8, tag.length)
        assertEquals("", SpectatorAdvert.tagOf(""))
    }
}
