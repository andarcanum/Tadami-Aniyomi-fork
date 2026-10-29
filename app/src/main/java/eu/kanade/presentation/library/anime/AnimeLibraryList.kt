package eu.kanade.presentation.library.anime

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.library.anime.components.SeriesStackedCoverCard
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.PinnedBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.idsToHashSet
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.series.model.SeriesCoverMode
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun AnimeLibraryList(
    items: List<AnimeLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<AnimeLibraryItem>,
    selectedIds: Set<Long> = selection.idsToHashSet { it.id },
    state: LazyListState = rememberLazyListState(),
    onClick: (AnimeLibraryItem) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (AnimeLibraryItem) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
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
            contentType = { "anime_library_list_item" },
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
            EntryListItem(
                isSelected = isSelected,
                title = title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
                ),
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
                    UnviewedBadge(count = libraryItem.unseenCount)
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
                        hasContinueAction = onClickContinueWatching != null,
                        remainingCount = libraryItem.unseenCount,
                    )
                ) {
                    { onClickContinueWatching?.invoke(targetAnime) }
                } else {
                    null
                },
                entries = entries,
                containerHeight = containerHeight,
            )
        }
    }
}
