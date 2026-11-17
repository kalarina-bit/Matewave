package cc.skysparkle.matewave.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.data.ProfileStore
import cc.skysparkle.matewave.network.Transports
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.Surface2
import cc.skysparkle.matewave.ui.theme.TabletMaxContentWidth
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.ui.theme.whiteOutlinedTextFieldColors
import kotlinx.coroutines.launch

/**
 * First-launch guide. New players see it once; Settings → «Show the guide» opens it again.
 * Anyone who already played before this version (a name, a chosen level or games against the
 * AI on record) is treated as having seen it.
 */
object Onboarding {
    private const val PREFS = "onboarding_prefs"
    private const val KEY_DONE = "done"

    fun needed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return false
        val profile = context.getSharedPreferences("chess_profile_prefs", Context.MODE_PRIVATE)
        val stats = context.getSharedPreferences("chess_stats_prefs", Context.MODE_PRIVATE)
        val playedBefore = profile.contains("name") || profile.getBoolean("level_chosen", false) || stats.all.isNotEmpty()
        if (playedBefore) {
            markDone(context)
            return false
        }
        return true
    }

    fun markDone(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DONE, true).apply()
    }
}

private data class GuidePage(
    @param:DrawableRes val iconRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val textRes: Int
)

private val PAGES = listOf(
    GuidePage(R.drawable.ic_launcher_card, R.string.guide_welcome_title, R.string.guide_welcome_text),
    GuidePage(R.drawable.ic_puzzle, R.string.guide_play_title, R.string.guide_play_text),
    GuidePage(R.drawable.ic_bluetooth, R.string.guide_nearby_title, R.string.guide_nearby_text),
    // the last page asks for a name and a level
    GuidePage(R.drawable.ic_default_avatar, R.string.guide_you_title, R.string.guide_you_text)
)

private data class GuideLevel(@param:StringRes val labelRes: Int, val elo: Int)

// The same levels as in the account screen
private val LEVELS = listOf(
    GuideLevel(R.string.skill_novice, 800),
    GuideLevel(R.string.skill_amateur, 1200),
    GuideLevel(R.string.skill_pro, 1800),
    GuideLevel(R.string.skill_master, 2200)
)

private const val MAX_NAME_LENGTH = 20

/**
 * @param replay opened from Settings: back on the first page closes it, and the level is not
 * asked again once chosen.
 */
@Composable
fun OnboardingScreen(replay: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES.size })
    val last = pager.currentPage == PAGES.lastIndex

    val profile = remember { ProfileStore.get() }
    val defaultName = stringResource(R.string.player_default_name)
    var name by rememberSaveable { mutableStateOf(if (profile.name == defaultName) "" else profile.name) }
    var level by rememberSaveable { mutableStateOf<Int?>(null) }
    val askLevel = !profile.levelChosen

    val finish = {
        val trimmed = name.trim()
        var changed = false
        if (trimmed.isNotEmpty() && trimmed != profile.name) {
            ProfileStore.setName(trimmed)
            changed = true
        }
        val elo = level
        if (askLevel && elo != null) {
            ProfileStore.setStartingElo(elo)
            changed = true
        }
        // Nearby players see the new name and rating right away
        if (changed) {
            Transports.lan(context).republishProfile()
            Transports.ble(context).republishProfile()
        }
        Onboarding.markDone(context)
        onDone()
    }

    BackHandler(enabled = pager.currentPage > 0 || replay) {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = TabletMaxContentWidth)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (!last) {
                    Text(
                        text = stringResource(R.string.guide_skip),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(CircleShape)
                            // Skipping still lands on the last page: the name and level are asked there
                            .clickable(role = Role.Button) { scope.launch { pager.animateScrollToPage(PAGES.lastIndex) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("onboarding_skip")
                    )
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { index ->
                val page = PAGES[index]
                PageColumn {
                    PageIcon(page, isLogo = index == 0)
                    Spacer(Modifier.height(32.dp))
                    Text(
                        text = stringResource(page.titleRes),
                        color = ink,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(page.textRes),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        textAlign = TextAlign.Center
                    )
                    if (index == PAGES.lastIndex) {
                        Spacer(Modifier.height(24.dp))
                        AboutYou(
                            name = name,
                            onName = { if (it.length <= MAX_NAME_LENGTH) name = it },
                            askLevel = askLevel,
                            level = level,
                            onLevel = { level = it }
                        )
                    }
                }
            }

            Dots(count = PAGES.size, current = pager.currentPage)

            GlassButton(
                onClick = { if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .testTag("onboarding_next")
            ) {
                Text(
                    text = stringResource(if (last) R.string.guide_start else R.string.guide_next),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        content()
    }
}

@Composable
private fun PageIcon(page: GuidePage, isLogo: Boolean) {
    if (isLogo) {
        // The app icon picture, not a glyph
        Image(
            painter = painterResource(page.iconRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(32.dp))
                .border(1.dp, PanelBorder, RoundedCornerShape(32.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Surface2)
                .border(1.dp, PanelBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(page.iconRes, size = 52.dp, tint = ChessGreen)
        }
    }
}

@Composable
private fun AboutYou(
    name: String,
    onName: (String) -> Unit,
    askLevel: Boolean,
    level: Int?,
    onLevel: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.account_name), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.player_default_name), color = ink.copy(alpha = 0.4f)) },
            colors = whiteOutlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_name")
        )
        if (askLevel) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.account_level_dialog_title), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
            LEVELS.forEach { option ->
                val selected = option.elo == level
                val shape = RoundedCornerShape(16.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Surface2)
                        .border(if (selected) 2.dp else 1.dp, if (selected) ChessGreen else PanelBorder, shape)
                        .clickable(role = Role.RadioButton) { onLevel(option.elo) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(option.labelRes), color = ink, style = MaterialTheme.typography.titleMedium)
                    Text("~${option.elo}", color = if (selected) ChessGreen else ink.copy(alpha = 0.7f), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(stringResource(R.string.guide_level_hint), color = ink.copy(alpha = 0.5f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    val label = stringResource(R.string.guide_page_of, current + 1, count)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val selected = i == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "guide_dot")
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = width, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (selected) ChessGreen else ink.copy(alpha = 0.2f))
            )
        }
    }
}
