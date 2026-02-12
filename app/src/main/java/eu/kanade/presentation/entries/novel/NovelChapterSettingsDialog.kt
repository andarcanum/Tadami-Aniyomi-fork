package eu.kanade.presentation.entries.novel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.domain.base.BasePreferences
import eu.kanade.presentation.components.TabbedDialog
import eu.kanade.presentation.components.TabbedDialogPaddings
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.RadioItem
import tachiyomi.presentation.core.components.SortItem
import tachiyomi.presentation.core.components.TriStateItem
import tachiyomi.presentation.core.i18n.stringResource
import eu.kanade.domain.entries.novel.model.downloadedFilter
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelChapterSettingsDialog(
    onDismissRequest: () -> Unit,
    novel: Novel?,
    onDownloadFilterChanged: (TriState) -> Unit,
    onUnreadFilterChanged: (TriState) -> Unit,
    onBookmarkedFilterChanged: (TriState) -> Unit,
    onSortModeChanged: (Long) -> Unit,
    onDisplayModeChanged: (Long) -> Unit,
    onSetAsDefault: (applyToExistingNovel: Boolean) -> Unit,
    onResetToDefault: () -> Unit,
) {
    var showSetAsDefaultDialog by rememberSaveable { mutableStateOf(false) }
    if (showSetAsDefaultDialog) {
        SetAsDefaultDialog(
            onDismissRequest = { showSetAsDefaultDialog = false },
            onConfirmed = onSetAsDefault,
        )
    }
    val downloadedOnly = remember { Injekt.get<BasePreferences>().downloadedOnly().get() }

    TabbedDialog(
        onDismissRequest = onDismissRequest,
        tabTitles = persistentListOf(
            stringResource(MR.strings.action_filter),
            stringResource(MR.strings.action_sort),
            stringResource(MR.strings.action_display),
        ),
        tabOverflowMenuContent = { closeMenu ->
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.set_chapter_settings_as_default)) },
                onClick = {
                    showSetAsDefaultDialog = true
                    closeMenu()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(MR.strings.action_reset)) },
                onClick = {
                    onResetToDefault()
                    closeMenu()
                },
            )
        },
    ) { page ->
        Column(
            modifier = Modifier
                .padding(vertical = TabbedDialogPaddings.Vertical)
                .verticalScroll(rememberScrollState()),
        ) {
            when (page) {
                0 -> {
                    TriStateItem(
                        label = stringResource(MR.strings.label_downloaded),
                        state = novel?.downloadedFilter ?: TriState.DISABLED,
                        onClick = onDownloadFilterChanged.takeUnless { downloadedOnly },
                    )
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

@Composable
private fun SetAsDefaultDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (optionalChecked: Boolean) -> Unit,
) {
    var optionalChecked by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(MR.strings.chapter_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = stringResource(MR.strings.confirm_set_chapter_settings))

                LabeledCheckbox(
                    label = stringResource(AYMR.strings.also_set_novel_chapter_settings_for_library),
                    checked = optionalChecked,
                    onCheckedChange = { optionalChecked = it },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmed(optionalChecked)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
    )
}
