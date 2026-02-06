package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun NovelChapterSettingsDialog(
    onDismissRequest: () -> Unit,
    novel: Novel?,
    onUnreadFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
) {
    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    TriStateItem(
                        label = stringResource(MR.strings.action_filter_unread),
                        state = novel?.unreadFilter ?: TriState.DISABLED,
                        onClick = onUnreadFilterChanged,
                    )
                    TriStateItem(
                        label = stringResource(MR.strings.action_filter_bookmarked),
                        state = novel?.bookmarkedFilter ?: TriState.DISABLED,
                        onClick = onBookmarkedFilterChanged,
                    )
                }
                1 -> {
                    val sortingMode = novel?.sorting ?: 0L
                    val sortDescending = novel?.sortDescending() ?: false
                    listOf(
                        MR.strings.sort_by_source to Novel.CHAPTER_SORTING_SOURCE,
                        MR.strings.sort_by_number to Novel.CHAPTER_SORTING_NUMBER,
                        MR.strings.sort_by_upload_date to Novel.CHAPTER_SORTING_UPLOAD_DATE,
                        MR.strings.action_sort_alpha to Novel.CHAPTER_SORTING_ALPHABET,
                    ).forEach { (titleRes, mode) ->
                        SortItem(
                            label = stringResource(titleRes),
                            sortDescending = sortDescending.takeIf { sortingMode == mode },
                            onClick = { onSortModeChanged(mode) },
                        )
                    }
                }
                2 -> {
                    val displayMode = novel?.displayMode ?: 0L
                    listOf(
                        MR.strings.show_title to Novel.CHAPTER_DISPLAY_NAME,
                        MR.strings.show_chapter_number to Novel.CHAPTER_DISPLAY_NUMBER,
                    ).forEach { (titleRes, mode) ->
                        RadioItem(
                            label = stringResource(titleRes),
                            selected = displayMode == mode,
                            onClick = { onDisplayModeChanged(mode) },
                        )
                    }
                }
            }
        }
    }
}
