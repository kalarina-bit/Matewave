package cc.skysparkle.matewave.presence

import android.content.Context
import cc.skysparkle.matewave.data.FriendsStore
import cc.skysparkle.matewave.network.LanPeer
import cc.skysparkle.matewave.network.NetworkLog
import cc.skysparkle.matewave.network.Transports
import cc.skysparkle.matewave.security.DeviceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DiscoveredPeer(
    val playerId: String,
    val name: String,
    val elo: Int,
    val fingerprint: String,

    val address: String
)

/**
 * Shows who is nearby on the same network. Announcements carry an ECDSA signature so a
 * known friend's name and rating cannot be overwritten by someone copying their ID.
 */
class LocalPresence(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val _peers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val peers: StateFlow<Map<String, DiscoveredPeer>> = _peers

    private var job: Job? = null
    private var gameWatchJob: Job? = null

    private val knownPeers by lazy { cc.skysparkle.matewave.security.KnownPeers(context) }

    /** Start of the current discovery session and time of its last result. */
    @Volatile private var sessionStart = 0L
    @Volatile private var lastResultAt = 0L
    private var pruneJob: Job? = null

    fun start() {
        if (job != null) return
        val lan = Transports.lan(context)

        if (PresenceSettings(context).announceEnabled) lan.startPresence()
        beginSession(lan)

        job = scope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)

                if (lanBusy(lan)) continue

                runCatching { restartDiscovery(lan) }
            }
        }

        gameWatchJob = scope.launch {
            var wasBusy = false
            lan.connectionState.collect { state ->
                val busy = state == cc.skysparkle.matewave.network.ConnectionState.CONNECTED ||
                    state == cc.skysparkle.matewave.network.ConnectionState.CONNECTING
                if (wasBusy && !busy) {
                    runCatching { restartDiscovery(lan) }
                }
                wasBusy = busy
            }
        }
        NetworkLog.log("PRESENCE", "LAN presence started")
    }

    private fun lanBusy(lan: cc.skysparkle.matewave.network.LanTransport): Boolean {
        val state = lan.connectionState.value
        return state == cc.skysparkle.matewave.network.ConnectionState.CONNECTED ||
            state == cc.skysparkle.matewave.network.ConnectionState.CONNECTING
    }

    /** Fresh discovery without clearing the list first, so statuses do not blink. */
    fun refresh() {
        val lan = Transports.lan(context)
        if (lanBusy(lan)) return
        scope.launch { runCatching { restartDiscovery(lan) } }
    }

    private suspend fun restartDiscovery(lan: cc.skysparkle.matewave.network.LanTransport) {
        lan.stopDiscovery()
        delay(RESTART_DELAY_MS)
        if (PresenceSettings(context).announceEnabled) lan.startPresence()
        beginSession(lan)
    }

    /**
     * Starts a discovery session. A new session reports only what it finds; if it finds nobody
     * it reports nothing at all, so without this check players who left stayed in the list.
     */
    private fun beginSession(lan: cc.skysparkle.matewave.network.LanTransport) {
        sessionStart = System.currentTimeMillis()
        lan.discoverPeers { found -> replaceAll(found) }
        pruneJob?.cancel()
        pruneJob = scope.launch {
            delay(PRUNE_AFTER_MS)
            if (lastResultAt < sessionStart) _peers.value = emptyMap()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        gameWatchJob?.cancel()
        gameWatchJob = null
        pruneJob?.cancel()
        pruneJob = null

        runCatching {
            val lan = Transports.lan(context)
            val busy = lan.connectionState.value == cc.skysparkle.matewave.network.ConnectionState.CONNECTED ||
                lan.connectionState.value == cc.skysparkle.matewave.network.ConnectionState.CONNECTING
            if (busy) lan.stopDiscovery() else lan.shutdown()
        }
        _peers.value = emptyMap()
        NetworkLog.log("PRESENCE", "LAN presence stopped")
    }

    private fun replaceAll(found: List<LanPeer>) {
        lastResultAt = System.currentTimeMillis()
        val mine = DeviceKeys.publicKeyFingerprint(context)
        val result = LinkedHashMap<String, DiscoveredPeer>()
        found.forEach { peer ->
            val id = peer.playerId.ifEmpty { peer.fingerprint }
            if (id.isEmpty()) return@forEach

            if (peer.fingerprint.isNotEmpty() && peer.fingerprint == mine) return@forEach

            val knownKey = knownPeers.publicKeyOf(id)
            if (knownKey != null) {
                val authentic = peer.signature.isNotEmpty() && DeviceKeys.verify(
                    knownKey,
                    cc.skysparkle.matewave.network.LanTransport.presencePayload(peer.playerId, peer.displayName, peer.elo),
                    peer.signature
                )
                if (!authentic) {
                    NetworkLog.log("PRESENCE", "Announcement for $id failed signature check, hidden")
                    return@forEach
                }
                FriendsStore.updateProfile(
                    userId = id,
                    name = peer.displayName.ifEmpty { peer.name },
                    elo = peer.elo
                )
                FriendsStore.markSeen(id, lastResultAt)
            }
            result[id] = DiscoveredPeer(
                playerId = id,
                name = peer.displayName.ifEmpty { peer.name },
                elo = peer.elo,
                fingerprint = peer.fingerprint,
                address = peer.id
            )
        }
        _peers.value = result
    }

    fun isOnline(playerId: String): Boolean = _peers.value.containsKey(playerId)

    private companion object {
        const val WATCHDOG_INTERVAL_MS = 30_000L

        /** A session that found nobody within this time means nobody is around. */
        const val PRUNE_AFTER_MS = 8_000L

        const val RESTART_DELAY_MS = 600L
    }
}
