package eu.kanade.presentation.entries.anime.components.aurora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.components.aurora.AuroraCoverSectionHeader
import eu.kanade.presentation.entries.components.aurora.AuroraHeaderFilterChip
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.core.common.preference.TriState
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Header for episodes or seasons section in Aurora theme with action filter/sort pills.
 */
@Composable
fun EpisodesHeader(
    itemCount: Int,
    fetchType: FetchType = FetchType.Episodes,
    modifier: Modifier = Modifier,
    isDescending: Boolean = true,
    unseenFilter: TriState = TriState.DISABLED,
    filterActive: Boolean = false,
    filterText: String? = null,
    onClickSort: (() -> Unit)? = null,
    onLongClickSort: (() -> Unit)? = null,
    onClickFilter: (() -> Unit)? = null,
    onLongClickFilter: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val isRussian = context.resources.configuration.locales[0].language == "ru"

    val titleText = when (fetchType) {
        FetchType.Seasons -> pluralStringResource(AYMR.plurals.anime_num_seasons, count = itemCount, itemCount)
        FetchType.Episodes -> pluralStringResource(AYMR.plurals.anime_num_episodes, count = itemCount, itemCount)
    }
    val sortText = if (isDescending) stringResource(MR.strings.action_desc) else stringResource(MR.strings.action_asc)
    val displayFilterText = filterText ?: when (unseenFilter) {
        TriState.ENABLED_IS -> if (isRussian) "Непросмотренные" else stringResource(AYMR.strings.action_filter_unseen)
        TriState.ENABLED_NOT -> if (isRussian) "Просмотренные" else "Seen"
        TriState.DISABLED -> if (isRussian) "Все" else "All"
    }
    val isFilterChipActive = unseenFilter != TriState.DISABLED || filterActive

    AuroraCoverSectionHeader(
        title = titleText,
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
