package cc.skysparkle.matewave.spectate

enum class PresenceStatus { ONLINE, IN_GAME, OFFLINE }

/** Where to watch a game: the host's address and spectator port. */
data class SpectateTarget(val host: String, val port: Int, val gameTag: String)

/** A player seen on the network, reduced to what spectating needs. */
data class NearbyPlayerGame(
    val playerId: String,
    val host: String,
    val gameTag: String,
    val spectatePort: Int
)

data class FriendGameStatus(
    val status: PresenceStatus,
    val target: SpectateTarget? = null,
    /** Live count from the host, null until it has answered. */
    val spectators: Int? = null,
    val max: Int = MAX_SPECTATORS
) {
    val full: Boolean get() = spectators != null && spectators >= max
}

/**
 * Online, In game or Offline for one player, and where to watch. Both players of a game announce
 * the same tag, but only the host serves spectators, so the guest's game is watched through the
 * host. A host that says its game has ended overrides an announcement that is not updated yet.
 */
object FriendStatusResolver {
    fun resolve(
        playerId: String,
        nearby: Map<String, NearbyPlayerGame>,
        probes: Map<SpectateTarget, SpectatorMessage.Status>
    ): FriendGameStatus {
        val player = nearby[playerId] ?: return FriendGameStatus(PresenceStatus.OFFLINE)
        if (player.gameTag.isEmpty()) return FriendGameStatus(PresenceStatus.ONLINE)
        val host = hostOf(player, nearby) ?: return FriendGameStatus(PresenceStatus.IN_GAME)
        val target = SpectateTarget(host.host, host.spectatePort, host.gameTag)
        val probe = probes[target]
        if (probe != null && !probe.inGame) return FriendGameStatus(PresenceStatus.ONLINE)
        return FriendGameStatus(PresenceStatus.IN_GAME, target, probe?.count, probe?.max ?: MAX_SPECTATORS)
    }

    /** Every host worth asking for a live spectator count. */
    fun targets(nearby: Collection<NearbyPlayerGame>): Set<SpectateTarget> =
        nearby.filter { it.gameTag.isNotEmpty() && it.spectatePort > 0 && it.host.isNotEmpty() }
            .map { SpectateTarget(it.host, it.spectatePort, it.gameTag) }
            .toSet()

    private fun hostOf(player: NearbyPlayerGame, nearby: Map<String, NearbyPlayerGame>): NearbyPlayerGame? {
        if (player.spectatePort > 0 && player.host.isNotEmpty()) return player
        return nearby.values.firstOrNull {
            it.playerId != player.playerId && it.gameTag == player.gameTag && it.spectatePort > 0 && it.host.isNotEmpty()
        }
    }
}
