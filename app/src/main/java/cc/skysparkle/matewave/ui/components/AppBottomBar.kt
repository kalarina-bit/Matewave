package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.UiCornerRadius

enum class BottomTab { HOME, FRIENDS, SETTINGS, ACCOUNT }

@Composable
fun AppBottomBar(
    current: BottomTab,
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,

    accountAvatarBase64: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
            .background(cc.skysparkle.matewave.ui.theme.FloatingBarFill, RoundedCornerShape(UiCornerRadius))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavItem(stringResource(R.string.nav_home), current == BottomTab.HOME, onHomeClick) { tint ->
            NavIcon(R.drawable.ic_home, tint)
        }
        NavItem(stringResource(R.string.nav_friends), current == BottomTab.FRIENDS, onFriendsClick) { tint ->
            NavIcon(R.drawable.ic_friends, tint)
        }
        NavItem(stringResource(R.string.nav_settings), current == BottomTab.SETTINGS, onSettingsClick) { tint ->
            NavIcon(R.drawable.ic_settings, tint)
        }
        NavItem(stringResource(R.string.account_title), current == BottomTab.ACCOUNT, onAccountClick) { tint ->
            val bitmap = rememberChatImage(accountAvatarBase64, AVATAR_MAX_DIMENSION_PX)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(24.dp).clip(CircleShape)
                )
            } else {
                NavIcon(R.drawable.ic_default_avatar, tint)
            }
        }
    }
}

/** Every screen has its own bar, so a tab never changes between active and inactive while shown. */
@Composable
private fun RowScope.NavItem(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit
) {
    val shape = RoundedCornerShape(UiCornerRadius)
    val itemColor = if (active) ink else ink.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (active) ink.copy(alpha = 0.14f) else ink.copy(alpha = 0f), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(itemColor)
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) itemColor else Color.Transparent,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun NavIcon(iconRes: Int, tint: Color) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = Modifier.size(24.dp)
    )
}
