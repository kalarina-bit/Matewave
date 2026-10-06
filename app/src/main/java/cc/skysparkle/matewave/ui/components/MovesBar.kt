package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.Board
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.openings.OpeningName
import cc.skysparkle.matewave.ui.theme.ChessGreen
import cc.skysparkle.matewave.ui.theme.ink

private data class MoveToken(val text: String, val moveIndex: Int?)

private val BookTint = androidx.compose.ui.graphics.Color(0xFF93DBA3)
private val OutOfBookTint = androidx.compose.ui.graphics.Color(0xFFFFB86B)

/**
 * Compact move list that scrolls to the current move, with the opening name underneath.
 * [currentIndex] is the number of moves played to reach the shown position.
 */
@Composable
fun MovesBar(
    sans: List<String>,
    startBoard: Board,
    currentIndex: Int,
    opening: OpeningName?,
    modifier: Modifier = Modifier,
    /** Leading moves that are opening theory; they are tinted in the list. */
    bookPlies: Int = 0,
    /** "Theory" or "Out of book from move N"; null hides the status. */
    bookStatus: String? = null,
    inBook: Boolean = true,
    onMoveClick: ((Int) -> Unit)? = null
) {
    if (sans.isEmpty() && opening == null) return
    val tokens = remember(sans, startBoard) {
        buildList {
            sans.forEachIndexed { i, san ->
                val whiteMove = (startBoard.sideToMove == PieceColor.WHITE) == (i % 2 == 0)
                val number = startBoard.fullmoveNumber + (i + if (startBoard.sideToMove == PieceColor.BLACK) 1 else 0) / 2
                if (whiteMove) add(MoveToken("$number.", null))
                else if (i == 0) add(MoveToken("$number…", null))
                add(MoveToken(san, i))
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex, tokens.size) {
        val target = tokens.indexOfFirst { it.moveIndex == currentIndex - 1 }
        if (target >= 0) listState.animateScrollToItem(maxOf(0, target - 3))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (sans.isNotEmpty()) {
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(tokens) { _, token ->
                    val index = token.moveIndex
                    if (index == null) {
                        Text(
                            token.text,
                            color = ink.copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp)
                        )
                    } else {
                        val current = index == currentIndex - 1
                        Text(
                            token.text,
                            color = if (index < bookPlies) BookTint else ink,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (current) Modifier.background(ChessGreen.copy(alpha = 0.45f)) else Modifier)
                                .then(if (onMoveClick != null) Modifier.clickable { onMoveClick(index + 1) } else Modifier)
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        if (opening != null || bookStatus != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(R.drawable.ic_book, size = 14.dp, tint = if (inBook) BookTint else ink.copy(alpha = 0.5f))
                Spacer(Modifier.width(6.dp))
                Text(
                    opening?.let { "${it.eco} · ${it.name}" } ?: "",
                    color = ink.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (bookStatus != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        bookStatus,
                        color = if (inBook) BookTint else OutOfBookTint,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background((if (inBook) BookTint else OutOfBookTint).copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
