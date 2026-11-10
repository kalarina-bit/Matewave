package cc.skysparkle.matewave.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.stats.Stats
import cc.skysparkle.matewave.ui.components.AppBottomBar
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.BottomTab
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.DialogText
import cc.skysparkle.matewave.ui.components.SectionCaption
import cc.skysparkle.matewave.ui.components.displayName
import cc.skysparkle.matewave.ui.components.wavePattern
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.GlassShape
import cc.skysparkle.matewave.ui.theme.HeroTextStyle
import cc.skysparkle.matewave.ui.theme.Lime
import cc.skysparkle.matewave.ui.theme.OnAccent
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.Surface1
import cc.skysparkle.matewave.ui.theme.Surface2
import cc.skysparkle.matewave.ui.theme.TabletMaxContentWidth
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.viewmodel.GameViewModel
import cc.skysparkle.matewave.viewmodel.ResumeInfo

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
    val profile = remember { cc.skysparkle.matewave.data.ProfileStore.get() }
    val lastSetup = remember { cc.skysparkle.matewave.settings.LastGameSetup(context) }
    LaunchedEffect(Unit) { studyViewModel.init(context) }
    val study by studyViewModel.overview.collectAsState()

    var showRatingGate by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = TabletMaxContentWidth)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(ChessGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_knight),
                        contentDescription = null,
                        modifier = Modifier.size(width = 20.dp, height = 29.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "matewave",
                    color = ink,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp, letterSpacing = (-0.3).sp),
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                // The rating is always in sight.
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Surface2)
                        .clickable { showStats = true }
                        .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(R.drawable.ic_rating, size = 18.dp, tint = Lime)
                    Spacer(Modifier.width(6.dp))
                    Text("${profile.elo}", color = ink, style = MaterialTheme.typography.titleSmall)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Hero()

                Row(
                    modifier = Modifier.fillMaxWidth().height(268.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AiCard(
                        subtitle = stringResource(R.string.mode_ai_subtitle),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { if (profile.levelChosen) onVsAiClick() else showRatingGate = true }
                    )
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SmallModeCard(
                            iconRes = R.drawable.ic_versus,
                            title = stringResource(R.string.mode_online_title),
                            subtitle = stringResource(R.string.mode_online_subtitle),
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            onClick = { if (profile.levelChosen) onVsPlayerClick() else showRatingGate = true }
                        )
                        // The most urgent thing takes this slot: an unfinished game, otherwise a quick rematch.
                        if (ongoing != null) {
                            ContinueCard(ongoing, Modifier.weight(1f).fillMaxWidth(), onContinueGame)
                        } else {
                            SmallModeCard(
                                iconRes = R.drawable.ic_quick_game,
                                title = stringResource(R.string.quick_game_title),
                                subtitle = stringResource(difficultyName(lastSetup.aiDifficulty)) +
                                    " · " + lastSetup.aiTime.displayName(),
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                onClick = { if (profile.levelChosen) onQuickGame() else showRatingGate = true }
                            )
                        }
                    }
                }

                SectionCaption(stringResource(R.string.home_today))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val due = study.dueCards > 0
                    LearnCard(
                        iconRes = if (due) R.drawable.ic_review else R.drawable.ic_study,
                        title = stringResource(if (due) R.string.study_review_title else R.string.study_title),
                        subtitle = if (due) {
                            stringResource(R.string.study_review_summary, study.dueCards, study.dueCourses)
                        } else {
                            stringResource(R.string.study_menu_subtitle)
                        },
                        highlighted = due,
                        modifier = Modifier.weight(1f),
                        onClick = onStudyClick
                    )
                    LearnCard(
                        iconRes = R.drawable.ic_puzzle,
                        title = stringResource(R.string.puzzles_title),
                        subtitle = stringResource(R.string.puzzle_solved_count, Stats.solvedPuzzles().size),
                        highlighted = false,
                        modifier = Modifier.weight(1f),
                        onClick = onPuzzlesClick
                    )
                }

                ProgressPanel(elo = profile.elo, onClick = { showStats = true })
                Spacer(Modifier.height(4.dp))
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
    if (showStats) StatsDialog(onDismiss = { showStats = false })
}

@Composable
private fun Hero() {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 5..11 -> stringResource(R.string.greeting_morning)
        in 12..17 -> stringResource(R.string.greeting_afternoon)
        in 18..22 -> stringResource(R.string.greeting_evening)
        else -> stringResource(R.string.greeting_night)
    }
    Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(modifier = Modifier.padding(end = 76.dp)) {
            Text("$greeting 👋", color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.home_hero),
                color = ink,
                style = HeroTextStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Image(
            painter = painterResource(R.drawable.piece_white_knight),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(88.dp)
                .rotate(-14f)
        )
    }
}

/** The main mode: a tall neon card with a big piece, like the hero tile of the mockup. */
@Composable
private fun AiCard(subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(GlassShape)
            .background(ChessGreen)
            .wavePattern(OnAccent.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
    ) {
        Image(
            painter = painterResource(R.drawable.piece_black_king),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 4.dp)
                .size(108.dp)
                .rotate(10f)
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)
        ) {
            Text(
                stringResource(R.string.mode_ai_title),
                color = OnAccent,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp, lineHeight = 24.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = OnAccent.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SmallModeCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(GlassShape)
            .background(Surface1)
            .border(1.dp, PanelBorder, GlassShape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(ChessGreen.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(iconRes, size = 18.dp, tint = ChessGreen)
            }
            Spacer(Modifier.weight(1f))
            ArrowBubble(fill = Surface2, tint = ink)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle, color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An unfinished game against the AI: move number, whose turn and the clock, one tap to resume. */
@Composable
private fun ContinueCard(info: ResumeInfo, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val turn = stringResource(if (info.myTurn) R.string.home_your_move else R.string.home_ai_move)
    val clock = info.myMillis?.let { ms ->
        val s = ms.coerceAtLeast(0) / 1000
        "%d:%02d".format(s / 60, s % 60)
    }
    val details = stringResource(R.string.home_continue_details, info.moveNumber, turn) + (clock?.let { " · $it" } ?: "")
    Column(
        modifier = modifier
            .clip(GlassShape)
            .background(Lime)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(OnAccent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                AppIcon(R.drawable.ic_play, size = 18.dp, tint = OnAccent)
            }
            Spacer(Modifier.weight(1f))
            ArrowBubble(fill = OnAccent, tint = Lime)
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.continue_game_title),
            color = OnAccent,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(details, color = OnAccent.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArrowBubble(fill: Color, tint: Color) {
    Box(
        modifier = Modifier.size(30.dp).clip(CircleShape).background(fill),
        contentAlignment = Alignment.Center
    ) {
        AppIcon(R.drawable.ic_arrow_forward, size = 16.dp, tint = tint)
    }
}

@Composable
private fun LearnCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .height(148.dp)
            .clip(GlassShape)
            .background(Surface1)
            .border(1.dp, if (highlighted) ChessGreen.copy(alpha = 0.7f) else PanelBorder, GlassShape)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (highlighted) ChessGreen else Surface2),
            contentAlignment = Alignment.Center
        ) {
            AppIcon(iconRes, size = 22.dp, tint = if (highlighted) OnAccent else ChessGreen)
        }
        Spacer(Modifier.weight(1f))
        Text(title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Rating with its trend and the three numbers players look at; opens the statistics. */
@Composable
private fun ProgressPanel(elo: Int, onClick: () -> Unit) {
    val curve = remember(elo) { Stats.ratingCurve(elo) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .background(Surface1)
            .border(1.dp, PanelBorder, GlassShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.account_progress).uppercase(),
                    color = ink.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium
                )
                Text("$elo", color = ink, style = MaterialTheme.typography.headlineMedium)
            }
            MiniRatingGraph(points = curve, modifier = Modifier.width(132.dp).height(44.dp), showDots = false, showGrid = false)
        }
        StatTiles()
    }
}

/** Games, wins and streak, as three equal tiles. */
@Composable
internal fun StatTiles() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(R.drawable.ic_play, stringResource(R.string.stat_games), "${Stats.gamesPlayed()}", Modifier.weight(1f))
        StatTile(R.drawable.ic_trophy, stringResource(R.string.stat_wins), "${Stats.wins()}", Modifier.weight(1f))
        StatTile(R.drawable.ic_speed, stringResource(R.string.stat_streak), "${Stats.streak()}", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(iconRes: Int, label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(iconRes, size = 18.dp, tint = ChessGreen)
        Spacer(Modifier.height(6.dp))
        Text(label, color = ink.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = ink, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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
internal fun MiniRatingGraph(points: List<Int>, modifier: Modifier = Modifier, showDots: Boolean = true, showGrid: Boolean = true) {
    val lineInk = ink
    // Paths, gradient and stroke are built once per size and reused by every redraw.
    Spacer(
        modifier = modifier.drawWithCache {
            if (points.size < 2) return@drawWithCache onDrawBehind {}
            val minV = points.min().toFloat()
            val maxV = points.max().toFloat()
            val range = (maxV - minV).coerceAtLeast(1f)
            val stepX = size.width / (points.size - 1)

            fun yOf(v: Int) = size.height - ((v - minV) / range) * size.height

            // Reference line: the rating at the start of the shown history.
            val zeroY = yOf(points.first())
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
            val fillBrush = Brush.verticalGradient(listOf(ChessGreen.copy(alpha = 0.35f), ChessGreen.copy(alpha = 0f)))
            val lineBrush = Brush.horizontalGradient(listOf(ChessGreen, Lime))
            val lineStroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)

            onDrawBehind {
                if (showGrid) {
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = size.height / gridLines * i
                        drawLine(lineInk.copy(alpha = 0.06f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    drawLine(lineInk.copy(alpha = 0.25f), Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f)
                }

                drawPath(fill, brush = fillBrush)
                drawPath(line, brush = lineBrush, style = lineStroke)

                if (showDots) {
                    points.forEachIndexed { i, v ->
                        val center = Offset(i * stepX, yOf(v))
                        drawCircle(color = Surface1, radius = 5f, center = center)
                        drawCircle(color = ChessGreen, radius = 3f, center = center)
                    }
                } else {
                    drawCircle(color = Lime, radius = 4.dp.toPx(), center = Offset(size.width, yOf(points.last())))
                }
            }
        }
    )
}
