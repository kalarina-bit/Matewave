package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.components.ListDivider
import cc.skysparkle.matewave.ui.components.ListGroup
import cc.skysparkle.matewave.ui.components.ListRow
import cc.skysparkle.matewave.ui.components.SectionCaption
import cc.skysparkle.matewave.ui.components.SetupSegmented
import cc.skysparkle.matewave.ui.components.GlassButton
import androidx.compose.foundation.layout.Spacer

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.ui.window.Dialog
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.data.FriendEntry
import cc.skysparkle.matewave.data.FriendsStore
import cc.skysparkle.matewave.presence.LocalPresence
import cc.skysparkle.matewave.ui.components.AppBottomBar
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.spectate.FriendGameStatus
import cc.skysparkle.matewave.spectate.FriendStatusResolver
import cc.skysparkle.matewave.spectate.NearbyPlayerGame
import cc.skysparkle.matewave.spectate.PresenceStatus
import cc.skysparkle.matewave.spectate.SpectateTarget
import cc.skysparkle.matewave.spectate.SpectatorMessage
import cc.skysparkle.matewave.spectate.SpectatorProbe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

@Composable
fun FriendsScreen(
    presence: LocalPresence,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onPlay: () -> Unit = {},
    onWatch: (SpectateTarget) -> Unit = {}
) {
    var onlineOnly by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var opened by remember { mutableStateOf<FriendEntry?>(null) }

    val myProfile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }

    val peers by presence.peers.collectAsState()
    val friends = remember(peers, version) { FriendsStore.list() }

    // Who plays a LAN game, and live spectator counts from each host while the list is open.
    val nearbyGames = remember(peers) {
        peers.values.associate { it.playerId to NearbyPlayerGame(it.playerId, it.host, it.gameTag, it.spectatePort) }
    }
    val hosts = remember(nearbyGames) { FriendStatusResolver.targets(nearbyGames.values) }
    var probes by remember { mutableStateOf<Map<SpectateTarget, SpectatorMessage.Status>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(hosts) {
        if (hosts.isEmpty()) {
            probes = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            probes = kotlinx.coroutines.coroutineScope {
                hosts.map { host ->
                    async(kotlinx.coroutines.Dispatchers.IO) { SpectatorProbe.query(host.host, host.port)?.let { host to it } }
                }.awaitAll().filterNotNull().toMap()
            }
            kotlinx.coroutines.delay(PROBE_INTERVAL_MS)
        }
    }
    val statusOf: (String) -> FriendGameStatus = { id -> FriendStatusResolver.resolve(id, nearbyGames, probes) }

    // Fresh search on opening, then refresh "5 min ago" labels while the screen is visible.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        presence.refresh()
        while (true) {
            kotlinx.coroutines.delay(30_000)
            version++
        }
    }

    GlassScaffold(
        title = stringResource(R.string.friends_title),
        onBack = null,
        bottomBar = {
            AppBottomBar(
                current = BottomTab.FRIENDS,
                onHomeClick = onHomeClick,
                onFriendsClick = { },
                onSettingsClick = onSettingsClick,
                onAccountClick = onAccountClick,
                accountAvatarBase64 = myProfile.avatarBase64
            )
        }
    ) {
        val savedIds = friends.map { it.userId }.toSet()
        val strangers = peers.values.filter { it.playerId !in savedIds }

        if (friends.isEmpty() && strangers.isEmpty()) {
            // Empty state with the action that fills the list.
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.friends_nobody_title), color = ink, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.friends_nobody_hint), color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
            GlassButton(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
                ButtonIcon(R.drawable.ic_play)
                Text(stringResource(R.string.friends_play_nearby), color = ink)
            }
            return@GlassScaffold
        }

        SetupSegmented(
            options = listOf(stringResource(R.string.friends_filter_all), stringResource(R.string.friends_filter_online)),
            selectedIndex = if (onlineOnly) 1 else 0
        ) { onlineOnly = it == 1 }

        val visible = friends
            .filter { !onlineOnly || presence.isOnline(it.userId) }
            .sortedWith(compareByDescending<FriendEntry> { presence.isOnline(it.userId) }.thenByDescending { it.lastPlayedMs })
        val favorites = visible.filter { it.favorite }
        val others = visible.filter { !it.favorite }

        FriendGroup(
            title = stringResource(R.string.friends_group_favorites),
            list = favorites,
            statusOf = statusOf,
            onOpen = { opened = it },
            onToggleFavorite = { FriendsStore.toggleFavorite(it.userId); version++ },
            onPlay = onPlay,
            onWatch = onWatch
        )
        FriendGroup(
            title = if (favorites.isNotEmpty()) stringResource(R.string.friends_group_others) else null,
            list = others,
            statusOf = statusOf,
            onOpen = { opened = it },
            onToggleFavorite = { FriendsStore.toggleFavorite(it.userId); version++ },
            onPlay = onPlay,
            onWatch = onWatch
        )

        if (strangers.isNotEmpty()) {
            SectionCaption(stringResource(R.string.friends_group_nearby))
            ListGroup {
                strangers.sortedBy { it.name }.forEachIndexed { index, peer ->
                    if (index > 0) ListDivider(inset = 68)
                    val status = statusOf(peer.playerId)
                    ListRow(
                        title = displayName(peer.name, peer.playerId),
                        subtitle = stringResource(R.string.friends_rating, peer.elo) + " · " + statusText(status.status, 0),
                        subtitleColor = statusColor(status.status),
                        leading = { AvatarCircle(base64 = null, size = 40.dp) },
                        trailing = { StatusAction(status, onPlay, onWatch) }
                    )
                }
            }
        }

        if (visible.isEmpty() && strangers.isEmpty()) {
            Text(stringResource(R.string.friends_filter_empty), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }

        opened?.let { friend ->
            FriendCard(friend = friend, status = statusOf(friend.userId).status, onDismiss = { opened = null })
        }
    }
}

@Composable
private fun FriendGroup(
    title: String?,
    list: List<FriendEntry>,
    statusOf: (String) -> FriendGameStatus,
    onOpen: (FriendEntry) -> Unit,
    onToggleFavorite: (FriendEntry) -> Unit,
    onPlay: () -> Unit,
    onWatch: (SpectateTarget) -> Unit
) {
    if (list.isEmpty()) return
    if (title != null) SectionCaption(title)
    ListGroup {
        list.forEachIndexed { index, friend ->
            if (index > 0) ListDivider(inset = 68)
            FriendRow(
                friend = friend,
                status = statusOf(friend.userId),
                onOpen = { onOpen(friend) },
                onToggleFavorite = { onToggleFavorite(friend) },
                onPlay = onPlay,
                onWatch = onWatch
            )
        }
    }
}

/**
 * Players who never set a name are all called "Player"; a short piece of their id tells them
 * apart in the list.
 */
@Composable
private fun displayName(name: String, id: String): String {
    val default = stringResource(R.string.player_default_name)
    val generic = name.isBlank() || name == default || name == "Player"
    return if (generic) "${name.ifBlank { default }} · ${id.filter { it.isLetterOrDigit() }.take(4).uppercase()}" else name
}

@Composable
private fun PlayChip(onClick: () -> Unit) {
    Text(
        stringResource(R.string.friends_play),
        color = Color.White,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ChessGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun FriendCard(friend: FriendEntry, status: PresenceStatus, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(cc.skysparkle.matewave.ui.components.DialogShape)
                .background(cc.skysparkle.matewave.ui.components.DialogSurface)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(cc.skysparkle.matewave.ui.theme.LocalInk provides Color.White) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val bg = cc.skysparkle.matewave.ui.components.rememberChatImage(friend.backgroundBase64)
                if (bg != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bg,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.bg_profile_default),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp).background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
                Row(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AvatarCircle(base64 = friend.avatarBase64, size = 64.dp)
                    Column {
                        Text(friend.name, color = ink, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.friends_rating, friend.elo),
                            color = ink.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            }
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    statusText(status, maxOf(friend.lastPlayedMs, friend.lastSeenMs)),
                    color = when (status) {
                        PresenceStatus.ONLINE -> Color(0xFF7BE495)
                        PresenceStatus.IN_GAME -> InGameColor
                        PresenceStatus.OFFLINE -> ink.copy(alpha = 0.6f)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    cc.skysparkle.matewave.ui.components.DialogAction(
                        stringResource(R.string.chat_close),
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendRow(
    friend: FriendEntry,
    status: FriendGameStatus,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onWatch: (SpectateTarget) -> Unit
) {
    ListRow(
        title = displayName(friend.name, friend.userId),
        subtitle = stringResource(R.string.friends_rating, friend.elo) + " · " +
            statusText(status.status, maxOf(friend.lastPlayedMs, friend.lastSeenMs)),
        subtitleColor = statusColor(status.status),
        leading = {
            Box {
                AvatarCircle(base64 = friend.avatarBase64, size = 40.dp)
                if (status.status != PresenceStatus.OFFLINE) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(if (status.status == PresenceStatus.IN_GAME) InGameColor else ChessGreen)
                    )
                }
            }
        },
        trailing = {
            StatusAction(status, onPlay, onWatch)
            AppIcon(
                R.drawable.ic_star,
                size = 20.dp,
                tint = if (friend.favorite) StarGold else ink.copy(alpha = 0.25f),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFavorite)
                    .padding(6.dp)
            )
        },
        onClick = onOpen
    )
}

private val InGameColor = Color(0xFFFFB86B)

private const val PROBE_INTERVAL_MS = 3_000L

/** Play for someone who is free, Watch (with the live count) for someone in a LAN game. */
@Composable
private fun StatusAction(status: FriendGameStatus, onPlay: () -> Unit, onWatch: (SpectateTarget) -> Unit) {
    when (status.status) {
        PresenceStatus.ONLINE -> PlayChip(onPlay)
        PresenceStatus.IN_GAME -> status.target?.let { target -> WatchChip(status) { onWatch(target) } }
        PresenceStatus.OFFLINE -> Unit
    }
}

@Composable
private fun WatchChip(status: FriendGameStatus, onClick: () -> Unit) {
    val enabled = !status.full
    val spectators = status.spectators
    val label = when {
        status.full -> stringResource(R.string.friends_watch_full)
        spectators != null -> stringResource(R.string.friends_watch_count, spectators, status.max)
        else -> stringResource(R.string.friends_watch)
    }
    Text(
        label,
        color = if (enabled) Color.White else ink.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) ChessGreen else ink.copy(alpha = 0.12f))
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun statusText(status: PresenceStatus, lastSeenMs: Long): String = when (status) {
    PresenceStatus.ONLINE -> stringResource(R.string.friends_online)
    PresenceStatus.IN_GAME -> stringResource(R.string.friends_in_game)
    PresenceStatus.OFFLINE ->
        if (lastSeenMs <= 0) stringResource(R.string.friends_offline)
        else stringResource(R.string.friends_offline) + " · " + lastSeenText(lastSeenMs)
}

@Composable
private fun statusColor(status: PresenceStatus): Color = when (status) {
    PresenceStatus.ONLINE -> ChessGreen
    PresenceStatus.IN_GAME -> InGameColor
    PresenceStatus.OFFLINE -> ink.copy(alpha = 0.55f)
}

@Composable
private fun lastSeenText(lastSeenMs: Long): String {
    if (lastSeenMs <= 0) return stringResource(R.string.friends_offline)
    val minutes = (System.currentTimeMillis() - lastSeenMs) / 60_000
    return when {
        minutes < 60 -> stringResource(R.string.friends_minutes_ago, minutes.coerceAtLeast(1).toInt())
        minutes < 60 * 24 -> stringResource(R.string.friends_hours_ago, (minutes / 60).toInt())
        minutes < 60 * 48 -> stringResource(R.string.friends_yesterday)
        else -> stringResource(R.string.friends_days_ago, (minutes / (60 * 24)).toInt())
    }
}
