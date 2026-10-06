package cc.skysparkle.matewave.ui.screens

import androidx.compose.animation.core.animateFloat

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import cc.skysparkle.matewave.ui.components.ListDivider
import cc.skysparkle.matewave.ui.components.ListGroup
import cc.skysparkle.matewave.ui.components.ListRow
import cc.skysparkle.matewave.ui.components.SectionCaption
import cc.skysparkle.matewave.ui.components.TimeControlGrid
import cc.skysparkle.matewave.ui.components.shortLabel
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.clock.TimeControl
import cc.skysparkle.matewave.data.FriendsStore
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import cc.skysparkle.matewave.network.AppPermissions
import cc.skysparkle.matewave.network.NetworkRequirements
import kotlinx.coroutines.delay
import cc.skysparkle.matewave.network.NearbyPlayer
import cc.skysparkle.matewave.network.Transports
import cc.skysparkle.matewave.network.TransportType
import cc.skysparkle.matewave.presence.LocalPresence
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.rememberPermissionRequester
import cc.skysparkle.matewave.ui.theme.ChessGreen

private enum class Hint { BLUETOOTH_PERMISSION, BLUETOOTH_OFF, WIFI_OFF }

private val WarningAmber = Color(0xFFFFC46B)

/** A player found over either transport. */
private data class NearbyEntry(
    val name: String,
    val elo: Int?,
    val type: TransportType,
    val address: String,
    val avatar: String?
)

@Composable
fun ConnectionScreen(
    presence: LocalPresence,
    onStartHost: (TimeControl) -> Unit,
    onJoin: (TransportType, TimeControl, targetId: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val last = remember { cc.skysparkle.matewave.settings.LastGameSetup(context) }
    var timeControl by remember { mutableStateOf(last.nearbyTime) }
    var pickTime by remember { mutableStateOf(false) }
    var btPlayers by remember { mutableStateOf<List<NearbyPlayer>>(emptyList()) }
    var btAllowed by remember { mutableStateOf(AppPermissions.allGranted(context, AppPermissions.nearby)) }
    val profile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }
    val visible = remember { cc.skysparkle.matewave.presence.PresenceSettings(context).announceEnabled }
    var network by remember { mutableStateOf(NetworkRequirements.check(context)) }

    fun startBluetooth() {
        val ble = Transports.ble(context)
        runCatching {
            ble.startPresence()
            ble.discoverNearby { found -> btPlayers = found }
        }
    }

    // Wi-Fi and Bluetooth can be switched on while the screen is open: notice it and search again.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            val now = NetworkRequirements.check(context)
            if (btAllowed && now.bluetoothOn && !network.bluetoothOn) startBluetooth()
            if (!now.bluetoothOn) btPlayers = emptyList()
            if (now.wifiConnected && !network.wifiConnected) presence.refresh()
            network = now
        }
    }
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    val requestNearby = rememberPermissionRequester(
        permissions = AppPermissions.nearby,
        rationaleTitle = stringResource(R.string.mesh_permission_title),
        rationaleText = stringResource(R.string.mesh_permission_text),
        onResult = { granted -> btAllowed = granted }
    )

    // One search over both transports: Wi-Fi presence runs app-wide, Bluetooth while this screen is open.
    DisposableEffect(btAllowed) {
        if (btAllowed) startBluetooth()
        onDispose {
            if (btAllowed) runCatching {
                val ble = Transports.ble(context)
                ble.stopDiscovery()
                ble.stopPresenceIfIdle()
            }
        }
    }

    val peers by presence.peers.collectAsState()
    val friends = remember(peers) { FriendsStore.list() }
    val entries = remember(peers, btPlayers, friends) {
        val lan = peers.values.map { peer ->
            NearbyEntry(
                name = peer.name,
                elo = peer.elo,
                type = TransportType.LAN,
                address = peer.address,
                avatar = friends.firstOrNull { it.userId == peer.playerId }?.avatarBase64
            )
        }
        val lanFingerprints = peers.values.map { it.fingerprint }.filter { it.isNotEmpty() }
        // The same phone seen over both channels is listed once, over Wi-Fi. Bluetooth carries
        // a shorter fingerprint: the first bytes of the same key hash.
        val bt = btPlayers.filter { player ->
            val short = player.fingerprint
            short.isNullOrEmpty() || lanFingerprints.none { it.startsWith(short) }
        }.map {
            NearbyEntry(name = it.name, elo = null, type = TransportType.BLUETOOTH, address = it.address, avatar = null)
        }
        (lan + bt).sortedBy { it.name.lowercase() }
    }

    GlassScaffold(
        title = stringResource(R.string.vsplayer_nearby_title),
        onBack = onBack,
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    onClick = { last.nearbyTime = timeControl; onStartHost(timeControl) },
                    modifier = Modifier.weight(1f)
                ) {
                    ButtonIcon(R.drawable.ic_add, size = 20.dp)
                    Text(stringResource(R.string.nearby_create), color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                GlassOutlinedButton(onClick = { pickTime = true }) {
                    Text(timeControl.shortLabel() + " ▾", color = ink, maxLines = 1)
                }
            }
        }
    ) {
        SearchPulse()
        Text(
            if (visible) stringResource(R.string.nearby_visible_as, profile.name) else stringResource(R.string.nearby_hidden),
            color = ink.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // What keeps others from being found, each with the fix one tap away.
        val hints = listOfNotNull(
            Hint.BLUETOOTH_PERMISSION.takeIf { !btAllowed && network.bluetoothSupported },
            Hint.BLUETOOTH_OFF.takeIf { btAllowed && network.bluetoothSupported && !network.bluetoothOn },
            Hint.WIFI_OFF.takeIf { !network.wifiConnected }
        )
        if (hints.isNotEmpty()) {
            ListGroup {
                hints.forEachIndexed { index, hint ->
                    if (index > 0) ListDivider()
                    when (hint) {
                        Hint.BLUETOOTH_PERMISSION -> ListRow(
                            title = stringResource(R.string.nearby_enable_bt),
                            icon = R.drawable.ic_bluetooth,
                            chevron = true,
                            onClick = { requestNearby() }
                        )
                        Hint.BLUETOOTH_OFF -> ListRow(
                            title = stringResource(R.string.reason_bluetooth_off),
                            subtitle = stringResource(R.string.nearby_bt_off_hint),
                            icon = R.drawable.ic_bluetooth,
                            iconTint = WarningAmber,
                            chevron = true,
                            onClick = {
                                runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                            }
                        )
                        Hint.WIFI_OFF -> ListRow(
                            title = stringResource(R.string.reason_wifi_unavailable),
                            subtitle = stringResource(R.string.nearby_wifi_hint),
                            icon = R.drawable.ic_wifi,
                            iconTint = WarningAmber
                        )
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.nearby_empty),
                color = ink.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
        } else {
            SectionCaption(stringResource(R.string.nearby_players))
            ListGroup {
                entries.forEachIndexed { index, entry ->
                    if (index > 0) ListDivider(inset = 68)
                    val transport = stringResource(if (entry.type == TransportType.LAN) R.string.transport_wifi else R.string.transport_bluetooth)
                    ListRow(
                        title = entry.name.ifBlank { stringResource(R.string.players_unknown_name) },
                        subtitle = listOfNotNull(entry.elo?.toString(), transport).joinToString(" · "),
                        leading = { AvatarCircle(base64 = entry.avatar, size = 40.dp) },
                        trailing = {
                            AppIcon(
                                if (entry.type == TransportType.LAN) R.drawable.ic_wifi_local else R.drawable.ic_bluetooth,
                                size = 16.dp,
                                tint = ink.copy(alpha = 0.5f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.players_play),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(ChessGreen)
                                    .clickable { onJoin(entry.type, timeControl, entry.address) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    )
                }
            }
        }
    }

    if (pickTime) {
        AppDialog(
            onDismissRequest = { pickTime = false },
            title = stringResource(R.string.time_control_label),
            actions = { DialogAction(stringResource(R.string.chat_close), { pickTime = false }) }
        ) {
            TimeControlGrid(selected = timeControl, onSelect = { timeControl = it; last.nearbyTime = it; pickTime = false })
        }
    }
}

/** Concentric rings pulsing around the knight: the search is always running. */
@Composable
private fun SearchPulse() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "phase"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(120.dp)) {
                for (k in 0 until 3) {
                    val p = (phase + k / 3f) % 1f
                    drawCircle(
                        color = ChessGreen.copy(alpha = (1f - p) * 0.45f),
                        radius = size.minDimension / 2f * (0.3f + 0.7f * p),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }
            }
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.piece_white_knight),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(stringResource(R.string.nearby_searching), color = ink, style = MaterialTheme.typography.titleMedium)
    }
}
