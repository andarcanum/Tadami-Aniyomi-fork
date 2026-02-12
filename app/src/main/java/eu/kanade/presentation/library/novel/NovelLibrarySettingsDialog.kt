package eu.kanade.presentation.library.novel

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryScreenModel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.BaseSortItem
import tachiyomi.presentation.core.components.CheckboxItem
import tachiyomi.presentation.core.components.HeadingItem
import tachiyomi.presentation.core.components.SettingsChipRow
import tachiyomi.presentation.core.components.SliderItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelLibrarySettingsDialog(
    onDismissRequest: () -> Unit,
    screenModel: NovelLibraryScreenModel,
) {
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }

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
                0 -> FilterPage(screenModel)
                1 -> SortPage(screenModel)
                2 -> DisplayPage(libraryPreferences)
            }
        }
    }
}

@Composable
private fun ColumnScope.FilterPage(
    screenModel: NovelLibraryScreenModel,
) {
    val state by screenModel.state.collectAsState()

    TriStateItem(
        label = stringResource(MR.strings.label_downloaded),
        state = state.effectiveDownloadedFilter,
        enabled = !state.downloadedOnly,
        onClick = screenModel::setDownloadedFilter,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_unread),
        state = state.unreadFilter,
        onClick = screenModel::setUnreadFilter,
    )
    TriStateItem(
        label = stringResource(MR.strings.label_started),
        state = state.startedFilter,
        onClick = screenModel::setStartedFilter,
    )
    TriStateItem(
        label = stringResource(MR.strings.action_filter_bookmarked),
        state = state.bookmarkedFilter,
        onClick = screenModel::setBookmarkedFilter,
    )
    TriStateItem(
        label = stringResource(MR.strings.completed),
        state = state.completedFilter,
        onClick = screenModel::setCompletedFilter,
    )
}

@Composable
private fun ColumnScope.SortPage(
    screenModel: NovelLibraryScreenModel,
) {
    val state by screenModel.state.collectAsState()
    val sortingMode = state.sort.type
    val sortDescending = !state.sort.isAscending

    val options = listOf(
        MR.strings.action_sort_alpha to MangaLibrarySort.Type.Alphabetical,
        MR.strings.action_sort_total to MangaLibrarySort.Type.TotalChapters,
        MR.strings.action_sort_last_read to MangaLibrarySort.Type.LastRead,
        AYMR.strings.action_sort_last_manga_update to MangaLibrarySort.Type.LastUpdate,
        MR.strings.action_sort_unread_count to MangaLibrarySort.Type.UnreadCount,
        MR.strings.action_sort_latest_chapter to MangaLibrarySort.Type.LatestChapter,
        MR.strings.action_sort_chapter_fetch_date to MangaLibrarySort.Type.ChapterFetchDate,
        MR.strings.action_sort_date_added to MangaLibrarySort.Type.DateAdded,
        MR.strings.action_sort_random to MangaLibrarySort.Type.Random,
    )

    options.map { (titleRes, mode) ->
        if (mode == MangaLibrarySort.Type.Random) {
            BaseSortItem(
                label = stringResource(titleRes),
                icon = Icons.Default.Refresh.takeIf { sortingMode == MangaLibrarySort.Type.Random },
                onClick = {
                    screenModel.setSort(mode, MangaLibrarySort.Direction.Ascending)
                },
            )
            return@map
        }

        SortItem(
            label = stringResource(titleRes),
            sortDescending = sortDescending.takeIf { sortingMode == mode },
            onClick = {
                val isTogglingDirection = sortingMode == mode
                val direction = when {
                    isTogglingDirection -> if (sortDescending) {
                        MangaLibrarySort.Direction.Ascending
                    } else {
                        MangaLibrarySort.Direction.Descending
                    }
                    else -> if (sortDescending) {
                        MangaLibrarySort.Direction.Descending
                    } else {
                        MangaLibrarySort.Direction.Ascending
                    }
                }
                screenModel.setSort(mode, direction)
            },
        )
    }
}

private val displayModes = listOf(
    MR.strings.action_display_grid to LibraryDisplayMode.CompactGrid,
    MR.strings.action_display_comfortable_grid to LibraryDisplayMode.ComfortableGrid,
    MR.strings.action_display_cover_only_grid to LibraryDisplayMode.CoverOnlyGrid,
    MR.strings.action_display_list to LibraryDisplayMode.List,
)

@Composable
private fun ColumnScope.DisplayPage(
    libraryPreferences: LibraryPreferences,
) {
    val displayMode by libraryPreferences.displayMode().collectAsState()
    SettingsChipRow(MR.strings.action_display_mode) {
        displayModes.map { (titleRes, mode) ->
            FilterChip(
                selected = displayMode == mode,
                onClick = { libraryPreferences.displayMode().set(mode) },
                label = { Text(stringResource(titleRes)) },
            )
        }
    }

    val configuration = LocalConfiguration.current
    val columnPreference = remember(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            libraryPreferences.novelLandscapeColumns()
        } else {
            libraryPreferences.novelPortraitColumns()
        }
    }

    val columns by columnPreference.collectAsState()
    if (displayMode == LibraryDisplayMode.List) {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(AYMR.strings.pref_library_rows),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        SliderItem(
            value = columns,
            valueRange = 0..10,
            label = stringResource(MR.strings.pref_library_columns),
            valueText = if (columns > 0) {
                columns.toString()
            } else {
                stringResource(MR.strings.label_auto)
            },
            onChange = columnPreference::set,
            pillColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }

    HeadingItem(MR.strings.overlay_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_download_badge),
        pref = libraryPreferences.downloadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_unread_badge),
        pref = libraryPreferences.unreadBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_local_badge),
        pref = libraryPreferences.localBadge(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_language_badge),
        pref = libraryPreferences.languageBadge(),
    )
    CheckboxItem(
        label = stringResource(AYMR.strings.action_display_show_continue_reading_button),
        pref = libraryPreferences.showContinueViewingButton(),
    )

    HeadingItem(MR.strings.tabs_header)
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_tabs),
        pref = libraryPreferences.categoryTabs(),
    )
    CheckboxItem(
        label = stringResource(MR.strings.action_display_show_number_of_items),
        pref = libraryPreferences.categoryNumberOfItems(),
    )
}
