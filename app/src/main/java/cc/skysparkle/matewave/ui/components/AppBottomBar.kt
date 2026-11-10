package cc.skysparkle.matewave.ui.components

import cc.skysparkle.matewave.ui.theme.ink

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.theme.FloatingBarFill
import cc.skysparkle.matewave.ui.theme.Lime
import cc.skysparkle.matewave.ui.theme.OnAccent
import cc.skysparkle.matewave.ui.theme.PanelBorder

enum class BottomTab { HOME, FRIENDS, SETTINGS, ACCOUNT }

/** Floating pill; the active tab is a lime circle, the others are plain icons. */
@Composable
fun AppBottomBar(
    current: BottomTab,
    onHomeClick: () -> Unit,
    onFriendsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccountClick: () -> Unit,

    accountAvatarBase64: String? = null
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .clip(shape)
            .background(FloatingBarFill)
            .border(1.dp, PanelBorder, shape)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .height(52.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem(stringResource(R.string.nav_home), current == BottomTab.HOME, onHomeClick) { tint ->
            AppIcon(R.drawable.ic_home, size = 24.dp, tint = tint)
        }
        NavItem(stringResource(R.string.nav_friends), current == BottomTab.FRIENDS, onFriendsClick) { tint ->
            AppIcon(R.drawable.ic_friends, size = 24.dp, tint = tint)
        }
        NavItem(stringResource(R.string.nav_settings), current == BottomTab.SETTINGS, onSettingsClick) { tint ->
            AppIcon(R.drawable.ic_settings, size = 24.dp, tint = tint)
        }
        NavItem(stringResource(R.string.account_title), current == BottomTab.ACCOUNT, onAccountClick) { tint ->
            val bitmap = rememberChatImage(accountAvatarBase64, AVATAR_MAX_DIMENSION_PX)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(28.dp).clip(CircleShape)
                )
            } else {
                AppIcon(R.drawable.ic_default_avatar, size = 24.dp, tint = tint)
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
    val fill by animateColorAsState(if (active) Lime else Lime.copy(alpha = 0f), label = "navFill")
    Box(
        modifier = Modifier
            .weight(1f)
            .semantics {
                contentDescription = label
                selected = active
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center
        ) {
            icon(if (active) OnAccent else ink.copy(alpha = 0.6f))
        }
    }
}
