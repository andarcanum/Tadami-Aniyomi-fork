package eu.kanade.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
import eu.kanade.tachiyomi.ui.home.LocalHomeHazeState
import kotlinx.collections.immutable.ImmutableList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics

/**
 * Aurora-styled version of [AppBarActions].
 *
 * Renders [AppBar.Action] items as circular icon buttons with a white/glass
 * background matching the Aurora theme, and [AppBar.OverflowAction] items
 * under a similarly styled overflow menu trigger.
 *
 * Use this in screens that participate in the Aurora theme but render their
 * toolbar actions through the classic [AppBarActions] composable.
 */
@Composable
fun RowScope.AuroraAppBarActions(
    actions: ImmutableList<AppBar.AppBarAction>,
    hazeState: HazeState? = LocalHomeHazeState.current,
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    actions.filterIsInstance<AppBar.Action>().forEach { action ->
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .auroraHeaderIconSurface(colors = colors, hazeState = hazeState)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    enabled = action.enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 22.dp),
                    onClick = {
                        appHaptics.tap()
                        action.onClick()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = action.icon,
                tint = action.iconTint ?: colors.textPrimary,
                contentDescription = action.title,
                modifier = Modifier.size(22.dp),
            )
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflowActions.isNotEmpty()) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .auroraHeaderIconSurface(colors = colors, hazeState = hazeState)
                .size(44.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 22.dp),
                    onClick = {
                        appHaptics.tap()
                        showMenu = !showMenu
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(
                    MR.strings.action_menu_overflow_description,
                ),
                modifier = Modifier.size(22.dp),
            )
        }

        AuroraEntryDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            overflowActions.forEach { action ->
                AuroraEntryDropdownMenuItem(
                    text = action.title,
                    leadingIcon = action.icon,
                    onClick = {
                        action.onClick()
                        showMenu = false
                    },
                )
            }
        }
    }
}
