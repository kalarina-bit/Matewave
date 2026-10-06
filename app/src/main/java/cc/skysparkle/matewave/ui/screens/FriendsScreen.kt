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
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.theme.ChessGreen

@Composable
fun FriendsScreen(
    presence: LocalPresence,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onPlay: () -> Unit = {}
) {
    var onlineOnly by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    var opened by remember { mutableStateOf<FriendEntry?>(null) }

    val myProfile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }

    val peers by presence.peers.collectAsState()
    val friends = remember(peers, version) { FriendsStore.list() }

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
            isOnline = presence::isOnline,
            onOpen = { opened = it },
            onToggleFavorite = { FriendsStore.toggleFavorite(it.userId); version++ },
            onPlay = onPlay
        )
        FriendGroup(
            title = if (favorites.isNotEmpty()) stringResource(R.string.friends_group_others) else null,
            list = others,
            isOnline = presence::isOnline,
            onOpen = { opened = it },
            onToggleFavorite = { FriendsStore.toggleFavorite(it.userId); version++ },
            onPlay = onPlay
        )

        if (strangers.isNotEmpty()) {
            SectionCaption(stringResource(R.string.friends_group_nearby))
            ListGroup {
                strangers.sortedBy { it.name }.forEachIndexed { index, peer ->
                    if (index > 0) ListDivider(inset = 68)
                    ListRow(
                        title = displayName(peer.name, peer.playerId),
                        subtitle = stringResource(R.string.friends_rating, peer.elo),
                        leading = { AvatarCircle(base64 = null, size = 40.dp) },
                        trailing = { PlayChip(onPlay) }
                    )
                }
            }
        }

        if (visible.isEmpty() && strangers.isEmpty()) {
            Text(stringResource(R.string.friends_filter_empty), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        }

        opened?.let { friend ->
            FriendCard(friend = friend, online = presence.isOnline(friend.userId), onDismiss = { opened = null })
        }
    }
}

@Composable
private fun FriendGroup(
    title: String?,
    list: List<FriendEntry>,
    isOnline: (String) -> Boolean,
    onOpen: (FriendEntry) -> Unit,
    onToggleFavorite: (FriendEntry) -> Unit,
    onPlay: () -> Unit
) {
    if (list.isEmpty()) return
    if (title != null) SectionCaption(title)
    ListGroup {
        list.forEachIndexed { index, friend ->
            if (index > 0) ListDivider(inset = 68)
            FriendRow(
                friend = friend,
                online = isOnline(friend.userId),
                onOpen = { onOpen(friend) },
                onToggleFavorite = { onToggleFavorite(friend) },
                onPlay = onPlay
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
private fun FriendCard(friend: FriendEntry, online: Boolean, onDismiss: () -> Unit) {
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
                    if (online) stringResource(R.string.friends_online)
                    else lastSeenText(maxOf(friend.lastPlayedMs, friend.lastSeenMs)),
                    color = if (online) Color(0xFF7BE495) else ink.copy(alpha = 0.6f),
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
    online: Boolean,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    ListRow(
        title = displayName(friend.name, friend.userId),
        subtitle = stringResource(R.string.friends_rating, friend.elo) + " · " +
            (if (online) stringResource(R.string.friends_online) else lastSeenText(maxOf(friend.lastPlayedMs, friend.lastSeenMs))),
        subtitleColor = if (online) ChessGreen else ink.copy(alpha = 0.55f),
        leading = {
            Box {
                AvatarCircle(base64 = friend.avatarBase64, size = 40.dp)
                if (online) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(11.dp)
                            .clip(CircleShape)
                            .background(ChessGreen)
                    )
                }
            }
        },
        trailing = {
            if (online) PlayChip(onPlay)
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
