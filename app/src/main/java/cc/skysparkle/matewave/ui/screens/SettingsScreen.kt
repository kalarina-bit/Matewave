package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.components.ListDivider
import cc.skysparkle.matewave.ui.components.ListGroup
import cc.skysparkle.matewave.ui.components.ListRow
import cc.skysparkle.matewave.ui.components.SectionCaption

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.DialogAccent
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.audio.RadioState
import cc.skysparkle.matewave.audio.SoundManager
import cc.skysparkle.matewave.ui.components.AppBottomBar
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.theme.ChessGreen

@Composable
fun SettingsScreen(
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenShare: () -> Unit
) {
    var musicOn by remember { mutableStateOf(SoundManager.musicEnabled) }
    var sfxOn by remember { mutableStateOf(SoundManager.sfxEnabled) }
    val context = LocalContext.current

    val myProfile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }
    val versionName = remember {
        runCatching {
            val pm = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName
        }.getOrNull() ?: "—"
    }

    var showAbout by remember { mutableStateOf(false) }
    var statusTick by remember { mutableStateOf(0) }
    val status = remember(statusTick) {
        cc.skysparkle.matewave.network.NetworkRequirements.check(context)
    }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(3_000); statusTick++ }
    }

    val feedback = remember { cc.skysparkle.matewave.settings.FeedbackSettings(context) }
    var vibrationOn by remember { mutableStateOf(feedback.vibrationEnabled) }
    val radioState by SoundManager.radioState.collectAsState()

    GlassScaffold(
        title = stringResource(R.string.nav_settings),
        onBack = null,
        bottomBar = {
            AppBottomBar(
                current = BottomTab.SETTINGS,
                onHomeClick = onHomeClick,
                onFriendsClick = onFriendsClick,
                onSettingsClick = { },
                onAccountClick = onAccountClick,
                accountAvatarBase64 = myProfile.avatarBase64
            )
        }
    ) {
        SectionCaption(stringResource(R.string.settings_section_sound))
        ListGroup {
            ListRow(
                title = stringResource(R.string.settings_radio),
                subtitle = stringResource(R.string.settings_radio_subtitle),
                icon = R.drawable.ic_music,
                // Radio status sits in the row instead of a stray line under it.
                value = if (!musicOn) null else when (radioState) {
                    RadioState.CONNECTING -> stringResource(R.string.settings_radio_connecting)
                    RadioState.ERROR -> stringResource(R.string.settings_radio_error)
                    else -> null
                },
                valueColor = if (radioState == RadioState.ERROR) Color(0xFFFF6B6B) else ink.copy(alpha = 0.55f),
                trailing = { SettingSwitch(musicOn) { musicOn = it; SoundManager.setMusicEnabled(it) } }
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.settings_sfx),
                subtitle = stringResource(R.string.settings_sfx_subtitle),
                icon = R.drawable.ic_sound_effect,
                trailing = { SettingSwitch(sfxOn) { sfxOn = it; SoundManager.setSfxEnabled(it) } }
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.perm_vibration_title),
                icon = R.drawable.ic_vibration,
                trailing = { SettingSwitch(vibrationOn) { vibrationOn = it; feedback.vibrationEnabled = it } }
            )
        }

        SectionCaption(stringResource(R.string.settings_section_game))
        ListGroup {
            ListRow(
                title = stringResource(R.string.appearance_title),
                subtitle = stringResource(R.string.appearance_subtitle),
                icon = R.drawable.ic_board_view,
                chevron = true,
                onClick = onOpenAppearance
            )
        }

        SectionCaption(stringResource(R.string.settings_section_system))
        ListGroup {
            ListRow(
                title = stringResource(R.string.permissions_card_title),
                icon = if (status.onlineBlocked) R.drawable.ic_permissions else R.drawable.ic_check_permission,
                iconTint = if (status.onlineBlocked) Color(0xFFFF6B6B) else ChessGreen,
                value = stringResource(if (status.onlineBlocked) R.string.permissions_card_blocked_short else R.string.permissions_card_ok_short),
                valueColor = if (status.onlineBlocked) Color(0xFFFF6B6B) else ink.copy(alpha = 0.55f),
                chevron = true,
                onClick = onOpenPermissions
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.privacy_policy),
                icon = R.drawable.ic_policy,
                chevron = true,
                onClick = { cc.skysparkle.matewave.settings.AppLinks.open(context, cc.skysparkle.matewave.settings.AppLinks.PRIVACY_POLICY) }
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.about_section),
                icon = R.drawable.ic_info,
                value = versionName,
                chevron = true,
                onClick = { showAbout = true }
            )
        }
    }

    if (showAbout) {
        AppDialog(
            onDismissRequest = { showAbout = false },
            title = stringResource(R.string.about_section),
            actions = { DialogAction(stringResource(R.string.chat_close), onClick = { showAbout = false }) }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_card),
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp))
                )
                Text(
                    stringResource(R.string.app_title),
                    color = ink,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    stringResource(R.string.settings_version, versionName),
                    color = DialogAccent,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    stringResource(R.string.settings_description),
                    color = ink.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            AboutRow(
                iconRes = R.drawable.ic_share,
                title = stringResource(R.string.share_row_title),
                subtitle = stringResource(R.string.share_row_subtitle),
                onClick = {
                    showAbout = false
                    onOpenShare()
                }
            )
            AboutRow(
                iconRes = R.drawable.ic_copyright,
                title = stringResource(R.string.licenses_title),
                subtitle = stringResource(R.string.licenses_subtitle),
                onClick = {
                    showAbout = false
                    onOpenLicenses()
                }
            )
            AboutRow(
                iconRes = R.drawable.ic_repo,
                title = stringResource(R.string.settings_repo_title),
                subtitle = cc.skysparkle.matewave.settings.AppLinks.REPOSITORY_LABEL,
                onClick = { cc.skysparkle.matewave.settings.AppLinks.open(context, cc.skysparkle.matewave.settings.AppLinks.REPOSITORY) }
            )
        }
    }
}

@Composable
private fun AboutRow(iconRes: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(ink.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cc.skysparkle.matewave.ui.components.AppIcon(iconRes, size = 28.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ink, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
        }
        cc.skysparkle.matewave.ui.components.AppIcon(
            R.drawable.ic_arrow_forward, size = 16.dp, tint = ink.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun SettingSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = ChessGreen,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = ink.copy(alpha = 0.15f),
            uncheckedThumbColor = Color.White.copy(alpha = 0.7f)
        )
    )
}
