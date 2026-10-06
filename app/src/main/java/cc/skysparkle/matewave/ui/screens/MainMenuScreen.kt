package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import cc.skysparkle.matewave.ui.components.ListDivider
import cc.skysparkle.matewave.ui.components.ListGroup
import cc.skysparkle.matewave.ui.components.ListRow
import cc.skysparkle.matewave.ui.components.SectionCaption

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.DialogText
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.stats.Stats
import cc.skysparkle.matewave.ui.components.AppBottomBar
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.TabletMaxContentWidth
import cc.skysparkle.matewave.ui.theme.UiCornerRadius
import cc.skysparkle.matewave.ui.theme.glass
import cc.skysparkle.matewave.viewmodel.GameViewModel
import cc.skysparkle.matewave.viewmodel.ResumeInfo
import cc.skysparkle.matewave.ui.components.displayName

@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    studyViewModel: cc.skysparkle.matewave.viewmodel.OpeningStudyViewModel,
    onContinueGame: () -> Unit,
    onQuickGame: () -> Unit,
    onVsAiClick: () -> Unit,
    onPuzzlesClick: () -> Unit,
    onStudyClick: () -> Unit,
    onVsPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,
    onFriendsClick: () -> Unit
) {
    val context = LocalContext.current
    val resumable by viewModel.resumable.collectAsState()
    val ongoing = resumable
    val hasOngoingGame = ongoing != null
    val profile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }
    val lastSetup = remember { cc.skysparkle.matewave.settings.LastGameSetup(context) }
    LaunchedEffect(Unit) { studyViewModel.init(context) }
    val study by studyViewModel.overview.collectAsState()

    var showRatingGate by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = TabletMaxContentWidth)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, start = 20.dp, end = 20.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(R.drawable.logo_knight),
                    contentDescription = null,
                    modifier = Modifier.size(width = 30.dp, height = 44.dp)
                )
                Spacer(Modifier.width(12.dp))
                Greeting(modifier = Modifier.weight(1f))
                // The rating is always in sight.
                Text(
                    "${profile.elo}",
                    color = ink,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ink.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // The most urgent thing first: an unfinished game.
                if (ongoing != null) ContinueCard(ongoing, onContinueGame)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    ModeTile(
                        iconRes = R.drawable.ic_ai_app,
                        title = stringResource(R.string.mode_ai_title),
                        subtitle = stringResource(R.string.mode_ai_subtitle),
                        modifier = Modifier.weight(1f),
                        onClick = { if (profile.levelChosen) onVsAiClick() else showRatingGate = true }
                    )
                    ModeTile(
                        iconRes = R.drawable.ic_profile_photo,
                        title = stringResource(R.string.mode_online_title),
                        subtitle = stringResource(R.string.mode_online_subtitle),
                        modifier = Modifier.weight(1f),
                        onClick = { if (profile.levelChosen) onVsPlayerClick() else showRatingGate = true }
                    )
                }

                SectionCaption(stringResource(R.string.home_today))
                ListGroup {
                    if (!hasOngoingGame) {
                        ListRow(
                            title = stringResource(R.string.quick_game_title),
                            subtitle = stringResource(
                                R.string.quick_game_subtitle,
                                stringResource(difficultyName(lastSetup.aiDifficulty)),
                                lastSetup.aiTime.displayName()
                            ),
                            icon = R.drawable.ic_quick_game,
                            iconTint = ChessGreen,
                            chevron = true,
                            onClick = { if (profile.levelChosen) onQuickGame() else showRatingGate = true }
                        )
                        ListDivider()
                    }
                    if (study.dueCards > 0) {
                        ListRow(
                            title = stringResource(R.string.study_review_title),
                            subtitle = stringResource(R.string.study_review_summary, study.dueCards, study.dueCourses),
                            icon = R.drawable.ic_review,
                            iconTint = cc.skysparkle.matewave.ui.screens.StarGold,
                            chevron = true,
                            onClick = onStudyClick
                        )
                    } else {
                        ListRow(
                            title = stringResource(R.string.study_title),
                            subtitle = stringResource(R.string.study_menu_subtitle),
                            icon = R.drawable.ic_crown,
                            chevron = true,
                            onClick = onStudyClick
                        )
                    }
                    ListDivider()
                    ListRow(
                        title = stringResource(R.string.puzzles_title),
                        subtitle = stringResource(R.string.puzzles_subtitle),
                        icon = R.drawable.ic_puzzle,
                        chevron = true,
                        onClick = onPuzzlesClick
                    )
                }
            }

            Box(modifier = Modifier.navigationBarsPadding()) {
                AppBottomBar(
                    current = BottomTab.HOME,
                    onHomeClick = { },
                    onFriendsClick = onFriendsClick,
                    onSettingsClick = onSettingsClick,
                    onAccountClick = onAccountClick,
                    accountAvatarBase64 = profile.avatarBase64
                )
            }
        }
    }

    if (showRatingGate) {
        RatingGateDialog(
            onOpenAccount = { showRatingGate = false; onAccountClick() },
            onDismiss = { showRatingGate = false }
        )
    }
}

@Composable
private fun Greeting(modifier: Modifier = Modifier) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        in 18..22 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(greeting, color = ink, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(6.dp))
            Text("👋", fontSize = 20.sp)
        }
        Text(stringResource(R.string.greeting_ready), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
    }
}

/** An unfinished game against the AI: move number, whose turn and the clock, one tap to resume. */
@Composable
private fun ContinueCard(info: ResumeInfo, onClick: () -> Unit) {
    val turn = stringResource(if (info.myTurn) R.string.home_your_move else R.string.home_ai_move)
    val clock = info.myMillis?.let { ms ->
        val s = ms.coerceAtLeast(0) / 1000
        "%d:%02d".format(s / 60, s % 60)
    }
    val details = stringResource(R.string.home_continue_details, info.moveNumber, turn) + (clock?.let { " · $it" } ?: "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(UiCornerRadius))
            .border(1.dp, ChessGreen.copy(alpha = 0.6f), RoundedCornerShape(UiCornerRadius))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.continue_game_title), color = ink, style = MaterialTheme.typography.titleMedium)
            Text(details, color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
        }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(ChessGreen),
            contentAlignment = Alignment.Center
        ) {
            cc.skysparkle.matewave.ui.components.AppIcon(R.drawable.ic_arrow_forward, size = 20.dp, tint = Color.White)
        }
    }
}

/** One of the two equal ways to start a game. */
@Composable
private fun ModeTile(iconRes: Int, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .glass(RoundedCornerShape(UiCornerRadius))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ink),
            modifier = Modifier.size(24.dp)
        )
        Text(title, color = ink, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun RatingGateDialog(onOpenAccount: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.rating_gate_title),
        actions = {
            DialogAction(stringResource(R.string.account_cancel), onDismiss, color = ink.copy(alpha = 0.75f))
            DialogAction(stringResource(R.string.rating_gate_open), onOpenAccount)
        }
    ) {
        DialogText(stringResource(R.string.rating_gate_text))
    }
}

@Composable
internal fun StatsDialog(onDismiss: () -> Unit) {
    val curve = remember { Stats.ratingCurve(cc.skysparkle.matewave.data.ProfileStore.get().elo) }
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.stats_dialog_title),
        actions = { DialogAction(stringResource(R.string.chat_close), onDismiss) }
    ) {
        DialogText(stringResource(R.string.stats_dialog_summary, Stats.wins(), Stats.winPercent(), Stats.streak()))
        MiniRatingGraph(points = curve, modifier = Modifier.fillMaxWidth().height(140.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.stats_min, curve.min()), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.stats_now, curve.last()), color = ink, style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.stats_max, curve.max()), color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
        Text(
            stringResource(R.string.stats_graph_caption),
            color = ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun MiniRatingGraph(points: List<Int>, modifier: Modifier = Modifier) {
    val lineInk = ink
    Canvas(modifier = modifier) {
        if (points.size < 2) return@Canvas
        val minV = points.min().toFloat()
        val maxV = points.max().toFloat()
        val range = (maxV - minV).coerceAtLeast(1f)
        val stepX = size.width / (points.size - 1)

        fun yOf(v: Int) = size.height - ((v - minV) / range) * size.height

        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height / gridLines * i
            drawLine(lineInk.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        // Reference line: the rating at the start of the shown history.
        val zeroY = yOf(points.first())
        drawLine(lineInk.copy(alpha = 0.3f), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)

        val line = Path().apply {
            moveTo(0f, yOf(points[0]))
            points.forEachIndexed { i, v -> if (i > 0) lineTo(i * stepX, yOf(v)) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(
            fill,
            brush = Brush.verticalGradient(listOf(ChessGreen.copy(alpha = 0.35f), ChessGreen.copy(alpha = 0f)))
        )
        drawPath(line, color = ChessGreen, style = Stroke(width = 4f, cap = StrokeCap.Round))

        points.forEachIndexed { i, v ->
            val center = Offset(i * stepX, yOf(v))
            drawCircle(color = lineInk, radius = 5f, center = center)
            drawCircle(color = ChessGreen, radius = 3f, center = center)
        }
    }
}
