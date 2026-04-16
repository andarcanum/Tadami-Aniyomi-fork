package eu.kanade.presentation.series.novel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.entries.novel.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.novel.components.aurora.NovelChapterCardCompactUi
import eu.kanade.presentation.series.novel.components.NovelSeriesEntryCard
import eu.kanade.presentation.series.novel.components.NovelSeriesHeader
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreenModel
import tachiyomi.domain.items.novelchapter.model.NovelChapter
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.plus
import tachiyomi.i18n.aniyomi.AYMR

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NovelSeriesAuroraContent(
    state: NovelSeriesScreenModel.State,
    onBackClicked: () -> Unit,
    onNovelClicked: (LibraryNovel) -> Unit,
    onChapterClicked: (LibraryNovel, NovelChapter) -> Unit,
    onRenameClicked: (String) -> Unit,
    onDeleteClicked: () -> Unit,
    onRemoveEntryClicked: (Long) -> Unit,
    onReorderEntries: (List<Long>) -> Unit,
) {
    val series = state.series ?: return
    val colors = AuroraTheme.colors

    val lazyListState = rememberLazyListState()

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(AYMR.strings.series_tab_titles),
        stringResource(AYMR.strings.series_tab_chapters),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Background
        series.coverNovels.firstOrNull()?.let {
            FullscreenPosterBackground(
                novel = it,
                scrollOffset = lazyListState.firstVisibleItemScrollOffset,
                firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Aurora style top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = colors.textPrimary,
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { showRenameDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = colors.textPrimary,
                        )
                    }

                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = colors.textPrimary,
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding + PaddingValues(bottom = 100.dp, start = 12.dp, end = 12.dp),
            ) {
                item {
                    NovelSeriesHeader(
                        series = series,
                        modifier = Modifier.padding(bottom = 32.dp, top = 24.dp),
                    )
                }

                stickyHeader {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = colors.textPrimary,
                            )
                        },
                    ) {
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        text = title,
                                        color = if (selectedTab == index) colors.textPrimary else colors.textSecondary,
                                    )
                                },
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    items(series.entries, key = { it.id }) { novel ->
                        NovelSeriesEntryCard(
                            novel = novel,
                            onRemove = { onRemoveEntryClicked(novel.id) },
                            onClick = { onNovelClicked(novel) },
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                } else {
                    state.chapters.forEach { (libraryNovel, chapters) ->
                        stickyHeader(key = "header_${libraryNovel.id}") {
                            ListGroupHeader(text = libraryNovel.novel.title)
                        }
                        items(chapters, key = { "ch_${it.id}" }) { chapter ->
                            NovelChapterCardCompactUi.Render(
                                novel = libraryNovel.novel,
                                chapter = chapter,
                                selected = false,
                                isNew = false,
                                selectionMode = false,
                                onClick = { onChapterClicked(libraryNovel, chapter) },
                                onLongClick = {},
                                onTranslateClick = {},
                                onTranslatedDownloadClick = {},
                                onTranslatedDownloadLongClick = {},
                                onTranslatedDownloadOpenFolder = {},
                                onToggleBookmark = {},
                                onToggleRead = {},
                                onToggleDownload = {},
                                chapterSwipeStartAction = LibraryPreferences.NovelSwipeAction.Disabled,
                                chapterSwipeEndAction = LibraryPreferences.NovelSwipeAction.Disabled,
                                onChapterSwipe = {},
                                downloaded = false,
                                downloading = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameSeriesDialog(
            initialTitle = series.title,
            onDismissRequest = { showRenameDialog = false },
            onConfirm = onRenameClicked,
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClicked()
                        showDeleteDialog = false
                    },
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = { Text(text = stringResource(AYMR.strings.action_delete_series)) },
            text = { Text(text = stringResource(AYMR.strings.confirm_delete_series)) },
        )
    }
}

@Composable
private fun RenameSeriesDialog(
    initialTitle: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(title)
                    onDismissRequest()
                },
            ) {
                Text(text = stringResource(MR.strings.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(MR.strings.action_cancel))
            }
        },
        title = { Text(text = stringResource(AYMR.strings.action_rename_series)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        },
    )
}
