package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
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
        NavItem(iconRes = R.drawable.ic_home, label = stringResource(R.string.nav_home), active = current == BottomTab.HOME, onClick = onHomeClick)

        NavItem(iconRes = R.drawable.ic_friends, label = stringResource(R.string.nav_friends), active = current == BottomTab.FRIENDS, onClick = onFriendsClick)
        NavItem(iconRes = R.drawable.ic_settings, label = stringResource(R.string.nav_settings), active = current == BottomTab.SETTINGS, onClick = onSettingsClick)

        AccountNavItem(
            avatarBase64 = accountAvatarBase64,
            active = current == BottomTab.ACCOUNT,
            onClick = onAccountClick
        )
    }
}

@Composable
private fun RowScope.NavItem(iconRes: Int, label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(UiCornerRadius)

    val highlight by animateColorAsState(
        if (active) ink.copy(alpha = 0.14f) else ink.copy(alpha = 0f),
        animationSpec = tween(220),
        label = "navHighlight"
    )
    val itemColor by animateColorAsState(
        if (active) ink else ink.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "navColor"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(highlight, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(itemColor),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = if (active) itemColor else Color.Transparent,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun RowScope.AccountNavItem(avatarBase64: String?, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(UiCornerRadius)
    val bitmap = rememberChatImage(avatarBase64, AVATAR_MAX_DIMENSION_PX)
    val highlight by animateColorAsState(
        if (active) ink.copy(alpha = 0.14f) else ink.copy(alpha = 0f),
        animationSpec = tween(220),
        label = "navHighlight"
    )
    val itemColor by animateColorAsState(
        if (active) ink else ink.copy(alpha = 0.7f),
        animationSpec = tween(220),
        label = "navColor"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(highlight, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(24.dp).clip(CircleShape)
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_default_avatar),
                contentDescription = null,
                colorFilter = ColorFilter.tint(itemColor),
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.account_title),
            color = if (active) itemColor else Color.Transparent,
            style = MaterialTheme.typography.labelMedium
        )
    }
}
