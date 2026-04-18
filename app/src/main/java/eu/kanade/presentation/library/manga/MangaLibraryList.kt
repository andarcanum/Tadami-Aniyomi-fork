package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.shouldShowContinueViewingAction
import eu.kanade.presentation.library.manga.components.SeriesStackedCoverCard
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.util.plus

@Composable
internal fun MangaLibraryList(
    items: List<MangaLibraryItem>,
    entries: Int,
    containerHeight: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onSeriesClicked: (Long) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    FastScrollLazyColumn(
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
            contentType = { "manga_library_list_item" },
        ) { libraryItem ->
            val manga = libraryItem.coverManga ?: libraryItem.libraryManga.manga
            val isSeries = libraryItem is MangaLibraryItem.Series
            val notSelectionMode = selection.isEmpty()
            val title = if (isSeries) libraryItem.title else manga.title
            val selectionManga = libraryItem.libraryManga.takeUnless { isSeries }
            val targetManga = if (isSeries) {
                libraryItem.librarySeries.entries.firstOrNull {
                    it.manga.id == libraryItem.librarySeries.activeManga?.id
                } ?: libraryItem.libraryManga
            } else {
                libraryItem.libraryManga
            }
            EntryListItem(
                isSelected = selectionManga != null && selection.fastAny { it.id == selectionManga.id },
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
                            isSelected = false,
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
                onLongClick = if (selectionManga != null) {
                    { onLongClick(selectionManga) }
                } else {
                    {}
                },
                onClick = {
                    if (isSeries) {
                        if (notSelectionMode) {
                            onSeriesClicked(libraryItem.librarySeries.id)
                        }
                    } else {
                        onClick(libraryItem.libraryManga)
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
