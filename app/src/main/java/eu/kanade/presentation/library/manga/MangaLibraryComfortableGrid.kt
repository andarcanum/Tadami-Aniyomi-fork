package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.presentation.library.manga.components.SeriesStackedCoverCard
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga

@Composable
internal fun MangaLibraryComfortableGrid(
    items: List<MangaLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<MangaLibraryItem>,
    onClick: (MangaLibraryItem) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (MangaLibraryItem) -> Unit,
    onTogglePinned: (MangaLibraryItem) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "manga_library_comfortable_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.coverManga ?: libraryItem.libraryManga.manga
            val isSeries = libraryItem is MangaLibraryItem.Series
            val notSelectionMode = selection.isEmpty()
            val title = if (isSeries) libraryItem.title else manga.title
            val isSelected = selection.fastAny { it.id == libraryItem.id }
            val targetManga = if (isSeries) {
                libraryItem.librarySeries.entries.firstOrNull {
                    it.manga.id == libraryItem.librarySeries.activeManga?.id
                } ?: libraryItem.libraryManga
            } else {
                libraryItem.libraryManga
            }
            EntryComfortableGridItem(
                isSelected = isSelected,
                title = title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                customCover = if (isSeries) {
                    {
                        SeriesStackedCoverCard(
                            covers = libraryItem.covers,
                            isSelected = isSelected,
                        )
                    }
                } else {
                    null
                },
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                topEndBadge = if (libraryItem.pinned) {
                    { PinnedBadge() }
                } else {
                    null
                },
                menuContent = null,
                onLongClick = { onLongClick(libraryItem) },
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
            )
        }
    }
}
