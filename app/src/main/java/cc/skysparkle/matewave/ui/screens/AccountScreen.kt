package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.components.ListDivider
import cc.skysparkle.matewave.ui.components.ListGroup
import cc.skysparkle.matewave.ui.components.ListRow
import cc.skysparkle.matewave.ui.components.SectionCaption
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState

import cc.skysparkle.matewave.ui.theme.ink

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.DialogText
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.AppBottomBar
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.data.ProfileStore
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.rememberChatImage
import cc.skysparkle.matewave.ui.components.encodeImageForChat
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.whiteOutlinedTextFieldColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AccountScreen(
    studyViewModel: cc.skysparkle.matewave.viewmodel.OpeningStudyViewModel,
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onStudyClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(ProfileStore.get()) }
    var showLevelPicker by remember { mutableStateOf(false) }
    var showForgetConfirm by remember { mutableStateOf(false) }
    var editingName by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = withContext(Dispatchers.IO) { encodeImageForChat(context, uri) }
            if (encoded != null) {
                ProfileStore.setBackground(encoded)
                profile = ProfileStore.get()
            }
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val encoded = withContext(Dispatchers.IO) {
                encodeImageForChat(
                    context, uri,
                    cc.skysparkle.matewave.ui.components.AVATAR_MAX_DIMENSION_PX,
                    cc.skysparkle.matewave.ui.components.AVATAR_JPEG_QUALITY
                )
            }
            if (encoded != null) {
                ProfileStore.setAvatar(encoded)
                profile = ProfileStore.get()
            }
        }
    }

    val myFingerprint = remember {
        cc.skysparkle.matewave.security.DeviceKeys.publicKeyFingerprint(context)
    }

    LaunchedEffect(Unit) { studyViewModel.init(context) }
    val study by studyViewModel.overview.collectAsState()
    var showStats by remember { mutableStateOf(false) }
    var showSecurity by remember { mutableStateOf(false) }

    GlassScaffold(
        title = stringResource(R.string.account_title),
        onBack = null,
        bottomBar = {
            AppBottomBar(
                current = BottomTab.ACCOUNT,
                onHomeClick = onHomeClick,
                onFriendsClick = onFriendsClick,
                onSettingsClick = onSettingsClick,
                onAccountClick = { },
                accountAvatarBase64 = profile.avatarBase64
            )
        }
    ) {
        androidx.compose.runtime.CompositionLocalProvider(cc.skysparkle.matewave.ui.theme.LocalInk provides Color.White) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { backgroundPicker.launch("image/*") }
        ) {
            val bgBitmap = rememberChatImage(profile.backgroundBase64)
            if (bgBitmap != null) {
                Image(
                    bitmap = bgBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.bg_profile_default),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(168.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(168.dp).background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.30f),
                        0.55f to Color.Black.copy(alpha = 0.45f),
                        1.0f to Color.Black.copy(alpha = 0.72f)
                    )
                )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { backgroundPicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_add_photo, size = 20.dp)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box {
                        AvatarCircle(
                            base64 = profile.avatarBase64,
                            size = 76.dp,
                            modifier = Modifier.clickable { avatarPicker.launch("image/*") }
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(ChessGreen)
                                .clickable { avatarPicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            AppIcon(R.drawable.ic_edit, size = 14.dp)
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                profile.name,
                                color = ink,
                                fontSize = 23.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            AppIcon(
                                R.drawable.ic_edit,
                                size = 18.dp,
                                tint = ink.copy(alpha = 0.75f),
                                modifier = Modifier.clickable { editingName = true }
                            )
                        }
                        Text(
                            stringResource(R.string.account_header_summary, profile.elo, cc.skysparkle.matewave.stats.Stats.gamesPlayed()),
                            color = ink.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        }

        if (profile.avatarBase64 != null || profile.backgroundBase64 != null) {
            Text(
                stringResource(R.string.account_reset_avatar),
                color = ink.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable {
                    ProfileStore.resetAvatarToDefault()
                    ProfileStore.setBackground(null)
                    profile = ProfileStore.get()
                }
            )
        }

        if (!profile.levelChosen) {
            GlassOutlinedButton(onClick = { showLevelPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.account_choose_level), color = ink)
            }
        }

        // Progress: the reason to open a profile. The rating is shown here once, with its trend.
        SectionCaption(stringResource(R.string.account_progress))
        ListGroup {
            val curve = remember(profile.elo) { cc.skysparkle.matewave.stats.Stats.ratingCurve(profile.elo) }
            ListRow(
                title = stringResource(R.string.account_rating_row),
                icon = R.drawable.ic_license,
                iconTint = ChessGreen,
                value = "${profile.elo}",
                valueColor = ink,
                trailing = { MiniRatingGraph(points = curve, modifier = Modifier.width(64.dp).height(24.dp)) },
                chevron = true,
                onClick = { showStats = true }
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.study_title),
                icon = R.drawable.ic_crown,
                value = "${study.learnedCount}/${study.courses.size} · ★${study.masteredCount}",
                chevron = true,
                onClick = onStudyClick
            )
            ListDivider()
            ListRow(
                title = stringResource(R.string.puzzles_title),
                icon = R.drawable.ic_puzzle,
                value = stringResource(R.string.puzzle_solved_count, cc.skysparkle.matewave.stats.Stats.solvedPuzzles().size)
            )
        }

        // Security folded into one row; the details live in a dialog.
        if (myFingerprint != null) {
            SectionCaption(stringResource(R.string.account_section_security))
            ListGroup {
                ListRow(
                    title = stringResource(R.string.account_device_key),
                    icon = R.drawable.ic_key,
                    value = myFingerprint.take(4).uppercase() + "…" + myFingerprint.takeLast(4).uppercase(),
                    chevron = true,
                    onClick = { showSecurity = true }
                )
            }
        }
    }

    if (showStats) StatsDialog(onDismiss = { showStats = false })

    if (showSecurity && myFingerprint != null) {
        AppDialog(
            onDismissRequest = { showSecurity = false; copied = false },
            title = stringResource(R.string.account_section_security),
            actions = { DialogAction(stringResource(R.string.chat_close), { showSecurity = false; copied = false }) }
        ) {
            Text(stringResource(R.string.account_fingerprint), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(groupFingerprint(myFingerprint), color = ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                AppIcon(
                    R.drawable.ic_copy,
                    size = 20.dp,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("fingerprint", myFingerprint))
                        copied = true
                    }
                )
                AppIcon(
                    R.drawable.ic_share,
                    size = 20.dp,
                    modifier = Modifier.clickable {
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, groupFingerprint(myFingerprint))
                        }
                        context.startActivity(android.content.Intent.createChooser(send, null))
                    }
                )
            }
            if (copied) {
                Text(stringResource(R.string.account_fingerprint_copied), color = ChessGreen, style = MaterialTheme.typography.bodySmall)
            }
            DialogText(stringResource(R.string.account_fingerprint_explain))
            DialogText(stringResource(R.string.account_trusted_keys_hint))
            Text(
                stringResource(R.string.account_forget_keys),
                color = Color(0xFFFF9B9B),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .clickable { showSecurity = false; showForgetConfirm = true }
                    .padding(vertical = 6.dp)
            )
        }
    }

    if (editingName) {
        EditNameDialog(
            initial = profile.name,
            onSave = { newName ->
                ProfileStore.setName(newName)
                profile = ProfileStore.get()
                // Nearby players should see the new name right away.
                cc.skysparkle.matewave.network.Transports.lan(context).republishProfile()
                cc.skysparkle.matewave.network.Transports.ble(context).republishProfile()
                editingName = false
            },
            onDismiss = { editingName = false }
        )
    }

    if (showForgetConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.account_forget_keys),
            text = stringResource(R.string.account_forget_keys_confirm),
            onConfirm = {
                cc.skysparkle.matewave.security.KnownPeers(context).forgetAll()
                showForgetConfirm = false
            },
            onDismiss = { showForgetConfirm = false }
        )
    }

    if (showLevelPicker) {
        LevelPickerDialog(
            onChoose = { elo ->
                ProfileStore.setStartingElo(elo)
                profile = ProfileStore.get()
                cc.skysparkle.matewave.network.Transports.lan(context).republishProfile()
                showLevelPicker = false
            },
            onDismiss = { showLevelPicker = false }
        )
    }
}

private const val MAX_NAME_LENGTH = 20

@Composable
private fun EditNameDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.account_name),
        actions = {
            DialogAction(stringResource(R.string.account_cancel), onDismiss, color = ink.copy(alpha = 0.75f))
            DialogAction(
                stringResource(R.string.account_save_name),
                onClick = { if (value.isNotBlank()) onSave(value.trim()) },
                enabled = value.isNotBlank()
            )
        }
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) value = it },
            singleLine = true,
            colors = whiteOutlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "${value.length} / $MAX_NAME_LENGTH",
            color = ink.copy(alpha = 0.45f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End
        )
    }
}

private fun groupFingerprint(fingerprint: String): String =
    fingerprint.uppercase().chunked(4).joinToString(" ")

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        actions = {
            DialogAction(stringResource(R.string.account_cancel), onDismiss, color = ink.copy(alpha = 0.75f))
            DialogAction(stringResource(R.string.account_confirm), onConfirm)
        }
    ) {
        DialogText(text)
    }
}

private data class SkillLevel(val labelRes: Int, val elo: Int)

private val SKILL_LEVELS = listOf(
    SkillLevel(R.string.skill_novice, 800),
    SkillLevel(R.string.skill_amateur, 1200),
    SkillLevel(R.string.skill_pro, 1800),
    SkillLevel(R.string.skill_master, 2200)
)

@Composable
private fun LevelPickerDialog(onChoose: (Int) -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.account_level_dialog_title),
        actions = {
            DialogAction(stringResource(R.string.account_cancel), onDismiss, color = ink.copy(alpha = 0.75f))
        }
    ) {
        SKILL_LEVELS.forEach { level ->
            val rowShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .background(ink.copy(alpha = 0.07f))
                    .clickable { onChoose(level.elo) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(level.labelRes), color = ink, style = MaterialTheme.typography.titleMedium)
                Text("~${level.elo}", color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AvatarCircle(base64: String?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = rememberChatImage(base64, cc.skysparkle.matewave.ui.components.AVATAR_MAX_DIMENSION_PX)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(ChessGreen.copy(alpha = 0.6f))
            .border(2.dp, PanelBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_default_avatar),
                contentDescription = null,
                colorFilter = ColorFilter.tint(ink),
                modifier = Modifier.size(size * 0.8f)
            )
        }
    }
}
