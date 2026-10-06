package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.settings.FeedbackSettings
import cc.skysparkle.matewave.network.AppPermissions
import cc.skysparkle.matewave.network.DisconnectReason
import cc.skysparkle.matewave.network.NetworkLog
import cc.skysparkle.matewave.network.NetworkRequirements
import cc.skysparkle.matewave.ui.components.GlassCard
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.rememberPermissionRequester

@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var tick by remember { mutableStateOf(0) }
    val status = remember(tick) { NetworkRequirements.check(context) }
    LaunchedEffect(Unit) {
        while (true) { kotlinx.coroutines.delay(2_000); tick++ }
    }

    val requestNearby = rememberPermissionRequester(
        permissions = AppPermissions.nearby,
        rationaleTitle = stringResource(R.string.mesh_permission_title),
        rationaleText = stringResource(R.string.mesh_permission_text),
        onResult = { tick++ }
    )

    GlassScaffold(title = stringResource(R.string.permissions_card_title), onBack = onBack) {
        PermissionItem(
            iconRes = R.drawable.ic_communication,
            granted = status.permissionsGranted,
            title = stringResource(R.string.perm_nearby_title),
            subtitle = stringResource(R.string.perm_nearby_text),
            onGrant = { requestNearby() }
        )

        val presenceSettings = remember { cc.skysparkle.matewave.presence.PresenceSettings(context) }
        var announce by remember { mutableStateOf(presenceSettings.announceEnabled) }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cc.skysparkle.matewave.ui.components.ButtonIcon(R.drawable.ic_online, size = 24.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.perm_announce_title),
                        color = ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.perm_announce_text),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = announce,
                    onCheckedChange = {
                        announce = it
                        presenceSettings.announceEnabled = it

                        cc.skysparkle.matewave.network.Transports.applyAnnounceSetting()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4F8A5B)
                    )
                )
            }
        }

        val feedbackSettings = remember { FeedbackSettings(context) }
        var vibration by remember { mutableStateOf(feedbackSettings.vibrationEnabled) }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cc.skysparkle.matewave.ui.components.ButtonIcon(R.drawable.ic_vibration, size = 24.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.perm_vibration_title),
                        color = ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.perm_vibration_text),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = vibration,
                    onCheckedChange = {
                        vibration = it
                        feedbackSettings.vibrationEnabled = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4F8A5B)
                    )
                )
            }
        }

        var background by remember { mutableStateOf(presenceSettings.backgroundEnabled) }
        val requestNotifications = rememberPermissionRequester(
            permissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                listOf(android.Manifest.permission.POST_NOTIFICATIONS)
            } else emptyList(),
            rationaleTitle = stringResource(R.string.perm_background_title),
            rationaleText = stringResource(R.string.perm_background_notification),
            onResult = { granted ->
                if (granted) {
                    cc.skysparkle.matewave.presence.PresenceService.start(context)
                } else {
                    background = false
                    presenceSettings.backgroundEnabled = false
                }
            }
        )
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cc.skysparkle.matewave.ui.components.ButtonIcon(R.drawable.ic_background_activity, size = 24.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.perm_background_title),
                        color = ink,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.perm_background_text),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = background,
                    onCheckedChange = {
                        background = it
                        presenceSettings.backgroundEnabled = it
                        if (it) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                                !cc.skysparkle.matewave.network.AppPermissions.allGranted(
                                    context, listOf(android.Manifest.permission.POST_NOTIFICATIONS)
                                )
                            ) {
                                requestNotifications()
                            } else {
                                cc.skysparkle.matewave.presence.PresenceService.start(context)
                            }
                        } else {
                            cc.skysparkle.matewave.presence.PresenceService.stop(context)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4F8A5B)
                    )
                )
            }

            if (background) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    cc.skysparkle.matewave.ui.components.ButtonIcon(
                        R.drawable.ic_notification, tint = Color(0xFFFFC46B), size = 16.dp
                    )
                    Text(
                        stringResource(R.string.perm_background_notification),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (background && !cc.skysparkle.matewave.network.BatteryOptimization.isIgnoring(context)) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.perm_battery_needed),
                    color = Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodySmall
                )
                GlassOutlinedButton(
                    onClick = { cc.skysparkle.matewave.network.BatteryOptimization.openSettings(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cc.skysparkle.matewave.ui.components.ButtonIcon(R.drawable.ic_go_settings)
                    Text(stringResource(R.string.perm_battery_open), color = ink)
                }
            }
        }

        val lastReason by NetworkLog.lastReason.collectAsState()
        if (lastReason != DisconnectReason.NONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cc.skysparkle.matewave.ui.components.ButtonIcon(
                    R.drawable.ic_error, tint = Color(0xFFFFC46B), size = 16.dp
                )
                Text(
                    stringResource(R.string.diag_last_reason, stringResource(lastReason.textRes)),
                    color = Color(0xFFFFC46B),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val metricsTick by cc.skysparkle.matewave.network.NetworkMetrics.updated.collectAsState()
        val metricsText = remember(metricsTick) {
            cc.skysparkle.matewave.network.NetworkMetrics.asText(context.resources)
        }
        Text(
            metricsText.trim(),
            color = ink.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )

        GlassOutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager

                val report = buildString {
                    appendLine(context.getString(R.string.diag_report_counters))
                    append(cc.skysparkle.matewave.network.NetworkMetrics.asText(context.resources))
                    appendLine()
                    appendLine(context.getString(R.string.diag_report_events))
                    append(NetworkLog.asText().ifBlank { context.getString(R.string.diag_log_empty) })
                }
                clipboard.setPrimaryClip(
                    android.content.ClipData.newPlainText("network report", report)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            cc.skysparkle.matewave.ui.components.ButtonIcon(R.drawable.ic_copy)
            Text(stringResource(R.string.diag_copy_log), color = ink)
        }
    }
}

@Composable
private fun PermissionItem(
    granted: Boolean,
    title: String,
    subtitle: String,
    onGrant: () -> Unit,
    iconRes: Int? = null
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cc.skysparkle.matewave.ui.components.AppIcon(
                resId = if (granted) R.drawable.ic_approve else R.drawable.ic_cancel,
                size = 20.dp,
                tint = if (granted) Color(0xFF7BE495) else Color(0xFFFF6B6B)
            )
            if (iconRes != null) {
                cc.skysparkle.matewave.ui.components.AppIcon(iconRes, size = 22.dp)
            }
            Text(title, color = ink, style = MaterialTheme.typography.titleMedium)
        }
        Text(subtitle, color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        if (!granted) {
            GlassOutlinedButton(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.perm_grant), color = ink)
            }
        }
    }
}
