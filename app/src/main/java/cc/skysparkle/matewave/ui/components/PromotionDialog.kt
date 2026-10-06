package cc.skysparkle.matewave.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.engine.Piece
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType
import cc.skysparkle.matewave.ui.theme.PanelBorder
import cc.skysparkle.matewave.ui.theme.UiCornerRadius
import cc.skysparkle.matewave.ui.theme.ink

/** The four promotion pieces, drawn with the same images as the board. */
@Composable
fun PromotionDialog(color: PieceColor, onChoose: (PieceType) -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.promotion_title),
        actions = {
            DialogAction(stringResource(R.string.account_cancel), onDismiss, color = ink.copy(alpha = 0.75f))
        }
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                val optionShape = RoundedCornerShape(UiCornerRadius)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(optionShape)
                        .background(ink.copy(alpha = 0.10f))
                        .border(1.dp, PanelBorder, optionShape)
                        .clickable { onChoose(type) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(Piece(type, color).drawableRes()),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                }
            }
        }
    }
}
