package eu.kanade.presentation.library.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.presentation.library.anime.components.SeriesStackedCoverCard
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.library.components.idsToHashSet
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.model.SeriesCoverMode

@Composable
fun AnimeLibraryCompactGrid(
    items: List<AnimeLibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<AnimeLibraryItem>,
    selectedIds: Set<Long> = selection.idsToHashSet { it.id },
    state: LazyGridState = rememberLazyGridState(),
    onClick: (AnimeLibraryItem) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (AnimeLibraryItem) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
    onTogglePinned: (AnimeLibraryItem) -> Unit,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    LazyLibraryGrid(
        state = state,
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            key = { it.id },
            contentType = { "anime_library_compact_grid_item" },
        ) { libraryItem ->
            val anime = libraryItem.coverAnime ?: libraryItem.libraryAnime.anime
            val isSeries = libraryItem is AnimeLibraryItem.Series
            val notSelectionMode = selection.isEmpty()
            val title = if (isSeries) libraryItem.title else anime.displayTitle
            val isSelected = selectedIds.contains(libraryItem.id)
            val targetAnime = if (isSeries) {
                libraryItem.librarySeries.entries.firstOrNull {
                    it.anime.id == libraryItem.librarySeries.activeAnime?.id
                } ?: libraryItem.libraryAnime
            } else {
                libraryItem.libraryAnime
            }
            val coverData = remember(anime) {
                AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                )
            }
            EntryCompactGridItem(
                isSelected = isSelected,
                title = title.takeIf { showTitle },
                coverData = coverData,
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
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unseenCount)
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
                        hasContinueAction = onClickContinueWatching != null,
                        remainingCount = libraryItem.unseenCount,
                    )
                ) {
                    { onClickContinueWatching?.invoke(targetAnime) }
                } else {
                    null
                },
            )
        }
    }
}
