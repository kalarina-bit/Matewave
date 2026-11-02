package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.engine.PieceColor
import cc.skysparkle.matewave.engine.PieceType

@Composable
fun CapturedPiecesRow(
    captured: List<PieceType>,
    capturedColor: PieceColor,
    advantage: Int
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            captured.groupBy { it }.forEach { (type, group) ->
                Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                    repeat(group.size) {
                        Image(
                            bitmap = PieceImages.of(context, type, capturedColor),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        if (advantage > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "+$advantage",
                color = ink.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
