package cc.skysparkle.matewave.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.openings.CourseInfo
import cc.skysparkle.matewave.openings.CourseStatus
import cc.skysparkle.matewave.openings.artRes
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.GlassButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.SetupSegmented
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.UiCornerRadius
import cc.skysparkle.matewave.ui.theme.glass
import cc.skysparkle.matewave.ui.theme.ink
import cc.skysparkle.matewave.viewmodel.OpeningStudyViewModel
import cc.skysparkle.matewave.viewmodel.StudyOverview

val StarGold = Color(0xFFFFC857)

@Composable
fun OpeningStudyScreen(viewModel: OpeningStudyViewModel, onBack: () -> Unit, onOpenTrainer: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }
    val overview by viewModel.overview.collectAsState()
    var side by rememberSaveable { mutableIntStateOf(0) }

    GlassScaffold(
        title = stringResource(R.string.study_title),
        onBack = onBack,
        actions = { HeaderProgress(overview) }
    ) {
        val total = overview.courses.size.coerceAtLeast(1)
        ProgressBar(overview.courses.sumOf { it.stars }.toFloat() / (total * 3), height = 4)

        NextAction(overview, viewModel, onOpenTrainer)

        SetupSegmented(
            options = listOf(stringResource(R.string.study_for_white), stringResource(R.string.study_for_black)),
            selectedIndex = side
        ) { side = it }

        val lockedMessage = overview.learning?.let { stringResource(R.string.study_locked_toast, it.course.name) }
        val sideColor = if (side == 0) PieceColor.WHITE else PieceColor.BLACK
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val columns = when {
                maxWidth < 360.dp -> 2
                maxWidth < 600.dp -> 3
                else -> 4
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                overview.courses.filter { it.course.side == sideColor }.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { info ->
                            CourseCard(info, Modifier.weight(1f)) {
                                if (viewModel.open(info.course)) onOpenTrainer()
                                else if (lockedMessage != null) Toast.makeText(context, lockedMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** "3/24  ★ 1" in the top bar. */
@Composable
private fun HeaderProgress(overview: StudyOverview) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Text("${overview.learnedCount}/${overview.courses.size}", color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(10.dp))
        AppIcon(R.drawable.ic_star_badge, size = 16.dp, tint = StarGold)
        Spacer(Modifier.width(4.dp))
        Text("${overview.masteredCount}", color = ink.copy(alpha = 0.8f), style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The one thing to do now, shown only when there is something to do: review, continue the
 * course in progress, or start the first course. Otherwise just the time of the next review.
 */
@Composable
private fun NextAction(overview: StudyOverview, viewModel: OpeningStudyViewModel, onOpenTrainer: () -> Unit) {
    val firstNew = overview.courses.firstOrNull { it.status == CourseStatus.NEW }
    val learning = overview.learning
    val nextDueAt = overview.nextDueAt
    when {
        overview.dueCards > 0 -> ActionCard(
            icon = R.drawable.ic_review,
            title = stringResource(R.string.study_review_title),
            subtitle = stringResource(R.string.study_review_summary, overview.dueCards, overview.dueCourses),
            button = stringResource(R.string.study_review_button)
        ) { if (viewModel.reviewNext()) onOpenTrainer() }
        learning != null -> ActionCard(
            icon = R.drawable.ic_book,
            title = stringResource(R.string.study_continue_title),
            subtitle = learning.course.name,
            button = stringResource(R.string.study_continue_button),
            progress = learning.learned.toFloat() / learning.total.coerceAtLeast(1)
        ) { if (viewModel.open(learning.course)) onOpenTrainer() }
        overview.learnedCount == 0 && firstNew != null -> ActionCard(
            icon = R.drawable.ic_book,
            title = stringResource(R.string.study_start_with, firstNew.course.name),
            subtitle = null,
            button = stringResource(R.string.study_start_button)
        ) { if (viewModel.open(firstNew.course)) onOpenTrainer() }
        nextDueAt != null -> Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_review, size = 16.dp, tint = ink.copy(alpha = 0.55f))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.study_next_review, formatDueIn(nextDueAt)), color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ActionCard(icon: Int, title: String, subtitle: String?, button: String, progress: Float? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(UiCornerRadius))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(icon, size = 22.dp, tint = StarGold)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ink, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, color = ink.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (progress != null) {
                Spacer(Modifier.height(6.dp))
                ProgressBar(progress, height = 4)
            }
        }
        Spacer(Modifier.width(12.dp))
        GlassButton(onClick = onClick) { Text(button, color = ink, maxLines = 1) }
    }
}

/** A course card without a frame: the knight on a soft glow, name, size and one status line. */
@Composable
private fun CourseCard(info: CourseInfo, modifier: Modifier, onClick: () -> Unit) {
    val locked = info.status == CourseStatus.LOCKED
    val glow = when {
        info.mastered -> StarGold.copy(alpha = 0.20f)
        info.status == CourseStatus.DUE -> ChessGreen.copy(alpha = 0.22f)
        else -> ink.copy(alpha = 0.08f)
    }
    val family = info.course.name.substringBefore(':').trim()
    val variation = info.course.name.substringAfter(':', "").trim()

    Column(
        modifier = modifier
            .alpha(if (locked) 0.4f else 1f)
            .clip(RoundedCornerShape(UiCornerRadius))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.85f)
                .background(Brush.radialGradient(listOf(glow, Color.Transparent))),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painter = painterResource(info.course.artRes()),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(6.dp)
            )
            when {
                info.mastered -> AppIcon(R.drawable.ic_star_badge, size = 22.dp, tint = StarGold, modifier = Modifier.align(Alignment.TopEnd))
                info.status == CourseStatus.DUE -> AppIcon(R.drawable.ic_review, size = 20.dp, tint = ChessGreen, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(family, color = ink, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            variation.ifEmpty { " " },
            color = ink.copy(alpha = 0.65f),
            style = MaterialTheme.typography.labelSmall,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_timer, size = 12.dp, tint = ink.copy(alpha = 0.55f))
            Spacer(Modifier.width(4.dp))
            Text(
                LocalContext.current.resources.getQuantityString(R.plurals.study_moves_count, info.total, info.total),
                color = ink.copy(alpha = 0.55f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
        // One status line of fixed height, so rows stay even; empty for a new course.
        Box(modifier = Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.CenterStart) {
            when (info.status) {
                CourseStatus.NEW -> Unit
                CourseStatus.LOCKED -> Text(stringResource(R.string.study_locked_short), color = ink.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                CourseStatus.LEARNING -> ProgressBar(info.learned.toFloat() / info.total.coerceAtLeast(1), height = 4)
                CourseStatus.DUE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(ChessGreen))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.study_status_due, info.dueCards), color = ChessGreen, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                CourseStatus.WAITING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (info.stars > 0) Stars(info.stars, size = 12.dp)
                    Spacer(Modifier.width(6.dp))
                    info.nextDueAt?.let {
                        Text(formatDueIn(it), color = ink.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
fun Stars(count: Int, size: androidx.compose.ui.unit.Dp = 16.dp) {
    Row {
        repeat(3) { i ->
            AppIcon(R.drawable.ic_star, size = size, tint = if (i < count) StarGold else ink.copy(alpha = 0.2f))
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, height: Int = 8) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape((height / 2).dp))
            .background(ink.copy(alpha = 0.12f))
    ) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape((height / 2).dp)).background(ChessGreen))
    }
}

/** "5 min", "4 h", "3 d" until [at]; "now" if already due. */
@Composable
fun formatDueIn(at: Long): String {
    val diff = at - System.currentTimeMillis()
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        diff <= 0 -> stringResource(R.string.study_due_now)
        diff < hour -> stringResource(R.string.study_minutes, ((diff + minute - 1) / minute).toInt())
        diff < day -> stringResource(R.string.study_hours, ((diff + hour - 1) / hour).toInt())
        else -> stringResource(R.string.study_days, ((diff + day - 1) / day).toInt())
    }
}
