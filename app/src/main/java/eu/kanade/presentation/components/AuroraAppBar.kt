package eu.kanade.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.auroraHeaderIconSurface
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
) {
    val appHaptics = LocalAppHaptics.current
    val colors = AuroraTheme.colors
    var showMenu by remember { mutableStateOf(false) }

    actions.filterIsInstance<AppBar.Action>().forEach { action ->
        val actionModifier = Modifier
            .padding(start = 4.dp)
            .size(40.dp)
            .auroraHeaderIconSurface(colors)
        IconButton(
            onClick = {
                appHaptics.tap()
                action.onClick()
            },
            enabled = action.enabled,
            modifier = actionModifier,
        ) {
            Icon(
                imageVector = action.icon,
                tint = action.iconTint ?: colors.textPrimary,
                contentDescription = action.title,
            )
        }
    }

    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    if (overflowActions.isNotEmpty()) {
        val overflowModifier = Modifier
            .padding(start = 4.dp)
            .size(40.dp)
            .auroraHeaderIconSurface(colors)
        IconButton(
            onClick = {
                appHaptics.tap()
                showMenu = !showMenu
            },
            modifier = overflowModifier,
        ) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(
                    MR.strings.action_menu_overflow_description,
                ),
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            overflowActions.forEach { action ->
                DropdownMenuItem(
                    onClick = {
                        appHaptics.tap()
                        action.onClick()
                        showMenu = false
                    },
                    text = { Text(action.title, fontWeight = FontWeight.Normal) },
                )
            }
        }
    }
}
