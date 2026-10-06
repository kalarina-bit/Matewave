package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.ui.theme.ChessGreen

@Composable
fun SetupSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        color = ink.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
    )
}

@Composable
fun SetupSegmented(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(ink.copy(alpha = 0.08f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) ChessGreen.copy(alpha = 0.35f) else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = ink, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
