package eu.kanade.presentation.category.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.House
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.theme.AuroraTheme
import sh.calvin.reorderable.ReorderableCollectionItemScope
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun ReorderableCollectionItemScope.CategoryListItem(
    category: Category,
    onRename: () -> Unit,
    onHide: () -> Unit,
    onToggleHomeHub: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()
    val isAurora = theme.isAuroraStyle
    val colors = AuroraTheme.colors
    val textColor = if (isAurora) {
        if (category.hidden) colors.textSecondary else colors.textPrimary
    } else {
        Color.Unspecified
    }
    val actionColors = if (isAurora) {
        IconButtonDefaults.iconButtonColors(contentColor = colors.textSecondary)
    } else {
        IconButtonDefaults.iconButtonColors()
    }

    Card(
        modifier = modifier,
        shape = if (isAurora) RoundedCornerShape(20.dp) else MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (isAurora) {
                colors.glass
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        border = if (isAurora) {
            BorderStroke(
                width = 1.dp,
                color = when {
                    category.hidden -> colors.warning.copy(alpha = 0.35f)
                    category.hiddenFromHomeHub -> colors.textSecondary.copy(alpha = 0.3f)
                    else -> colors.accent.copy(alpha = 0.2f)
                },
            )
        } else {
            null
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAurora) 0.dp else 1.dp,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRename)
                .padding(vertical = MaterialTheme.padding.small),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = null,
                tint = if (isAurora) colors.textSecondary else Color.Unspecified,
                modifier = Modifier
                    .padding(
                        start = MaterialTheme.padding.medium,
                        end = MaterialTheme.padding.medium,
                    )
                    .draggableHandle(),
            )
            Text(
                text = category.name,
                modifier = Modifier.weight(1f),
                color = textColor,
            )
            IconButton(
                onClick = onRename,
                colors = actionColors,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(MR.strings.action_rename_category),
                    tint = if (isAurora) colors.accent else Color.Unspecified,
                )
            }
            IconButton(
                onClick = onHide,
                colors = actionColors,
                content = {
                    Icon(
                        imageVector = if (category.hidden) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(AYMR.strings.action_hide),
                        tint = if (isAurora && !category.hidden) {
                            colors.accent
                        } else {
                            Color.Unspecified
                        },
                    )
                },
            )
            IconButton(
                onClick = onToggleHomeHub,
                colors = actionColors,
                content = {
                    Icon(
                        imageVector = Icons.Outlined.House,
                        contentDescription = stringResource(AYMR.strings.action_hide_from_home_hub),
                        tint = if (isAurora && category.hiddenFromHomeHub) {
                            colors.textSecondary.copy(alpha = 0.4f)
                        } else {
                            colors.accent
                        },
                    )
                },
            )
            IconButton(
                onClick = onDelete,
                colors = actionColors,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(MR.strings.action_delete),
                    tint = if (isAurora) colors.error else Color.Unspecified,
                )
            }
        }
    }
}
