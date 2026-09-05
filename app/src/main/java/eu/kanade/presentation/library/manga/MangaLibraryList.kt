package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.idsToHashSet
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.presentation.library.manga.components.SeriesStackedCoverCard
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun MangaLibraryList(
    items: List<MangaLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<MangaLibraryItem>,
    selectedIds: Set<Long> = selection.idsToHashSet { it.id },
    state: LazyListState = rememberLazyListState(),
    onClick: (MangaLibraryItem) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (MangaLibraryItem) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        item {
            if (!searchQuery.isNullOrEmpty()) {
                GlobalSearchItem(
                    modifier = Modifier.fillMaxWidth(),
                    searchQuery = searchQuery,
                    onClick = onGlobalSearchClicked,
                )
            }
        }

        items(
            items = items,
            key = { it.id },
            contentType = { "manga_library_list_item" },
        ) { libraryItem ->
            val manga = libraryItem.coverManga ?: libraryItem.libraryManga.manga
            val isSeries = libraryItem is MangaLibraryItem.Series
            val notSelectionMode = selection.isEmpty()
            val title = if (isSeries) libraryItem.title else manga.displayTitle
            val isSelected = selectedIds.contains(libraryItem.id)
            val targetManga = if (isSeries) {
                libraryItem.librarySeries.entries.firstOrNull {
                    it.manga.id == libraryItem.librarySeries.activeManga?.id
                } ?: libraryItem.libraryManga
            } else {
                libraryItem.libraryManga
            }
            EntryListItem(
                isSelected = isSelected,
                title = title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                // D-M7: the stacked cover card belongs to AUTO cover mode only. ENTRY/CUSTOM
                // fall through to the standard coverData path, where the coil fetcher resolves
                // the cover entry's own (custom) cover by mangaId - previously the series'
                // coverMode was never consulted in the library and CUSTOM showed the stack.
                customCover = if (isSeries && libraryItem.librarySeries.series.coverMode == SeriesCoverMode.AUTO) {
                    {
                        SeriesStackedCoverCard(
                            covers = libraryItem.covers,
                            isSelected = isSelected,
                        )
                    }
                } else {
                    null
                },
                badge = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem) },
                topEndBadge = if (libraryItem.pinned) {
                    { PinnedBadge() }
                } else {
                    null
                },
                onClick = {
                    if (notSelectionMode && isSeries) {
                        onSeriesClicked(libraryItem.librarySeries.id)
                    } else {
                        onClick(libraryItem)
                    }
                },
                onClickContinueViewing = if (
                    shouldShowContinueViewingAction(
                        hasContinueAction = onClickContinueReading != null,
                        remainingCount = libraryItem.unreadCount,
                    )
                ) {
                    { onClickContinueReading?.invoke(targetManga) }
                } else {
                    null
                },
                entries = entries,
                containerHeight = containerHeight,
            )
        }
    }
}
