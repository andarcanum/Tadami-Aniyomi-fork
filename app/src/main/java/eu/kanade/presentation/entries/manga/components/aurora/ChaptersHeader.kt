package eu.kanade.presentation.entries.manga.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.aurora.AuroraCoverSectionHeader
import eu.kanade.presentation.entries.components.aurora.AuroraHeaderFilterChip
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for the chapters section with title count and action filter/sort pills.
 */
@Composable
fun ChaptersHeader(
    chapterCount: Int,
    modifier: Modifier = Modifier,
    isDescending: Boolean = true,
    unreadFilter: TriState = TriState.DISABLED,
    filterActive: Boolean = false,
    filterText: String? = null,
    onClickSort: (() -> Unit)? = null,
    onLongClickSort: (() -> Unit)? = null,
    onClickFilter: (() -> Unit)? = null,
    onLongClickFilter: (() -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isRussian = context.resources.configuration.locales[0].language == "ru"

    val title = pluralStringResource(MR.plurals.manga_num_chapters, count = chapterCount, chapterCount)
    val sortText = if (isDescending) stringResource(MR.strings.action_desc) else stringResource(MR.strings.action_asc)
    val displayFilterText = filterText ?: when (unreadFilter) {
        TriState.ENABLED_IS -> if (isRussian) "Непрочитанные" else stringResource(MR.strings.action_filter_unread)
        TriState.ENABLED_NOT -> if (isRussian) "Прочитанные" else "Read"
        TriState.DISABLED -> if (isRussian) "Все" else "All"
    }
    val isFilterChipActive = unreadFilter != TriState.DISABLED || filterActive

    AuroraCoverSectionHeader(
        title = title,
        icon = null,
        modifier = modifier,
        trailingContent = {
            if (onClickSort != null || onClickFilter != null || onLongClickSort != null || onLongClickFilter != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AuroraHeaderFilterChip(
                        text = sortText,
                        icon = Icons.AutoMirrored.Filled.Sort,
                        onClick = onClickSort ?: onLongClickSort ?: {},
                        onLongClick = onLongClickSort,
                    )
                    AuroraHeaderFilterChip(
                        text = displayFilterText,
                        isActive = isFilterChipActive,
                        onClick = onClickFilter ?: onLongClickFilter ?: {},
                        onLongClick = onLongClickFilter,
                    )
                }
            }
        },
    )
}
