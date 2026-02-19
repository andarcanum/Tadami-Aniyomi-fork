package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.library.components.GlobalSearchItem
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
fun MangaLibraryAuroraContent(
    items: List<MangaLibraryItem>,
    selection: List<LibraryManga>,
    searchQuery: String?,
    hasActiveFilters: Boolean,
    displayMode: LibraryDisplayMode,
    columns: Int,
    onMangaClicked: (Long) -> Unit,
    onToggleSelection: (LibraryManga) -> Unit,
    onToggleRangeSelection: (LibraryManga) -> Unit,
    onContinueReadingClicked: ((LibraryManga) -> Unit)?,
    onGlobalSearchClicked: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (items.isEmpty()) {
        MangaLibraryAuroraEmptyScreen(
            searchQuery = searchQuery,
            hasActiveFilters = hasActiveFilters,
            contentPadding = contentPadding,
            onGlobalSearchClicked = onGlobalSearchClicked,
        )
        return
    }

    val safeColumns = columns.coerceAtLeast(1)
    val isSelectionMode = selection.isNotEmpty()
    val onClickManga = { manga: LibraryManga ->
        if (isSelectionMode) {
            onToggleSelection(manga)
        } else {
            onMangaClicked(manga.manga.id)
        }
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            MangaLibraryAuroraList(
                items = items,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                onClick = onClickManga,
                onLongClick = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }

        LibraryDisplayMode.CompactGrid,
        -> {
            MangaLibraryCompactGrid(
                items = items,
                showTitle = true,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                onClick = onClickManga,
                onLongClick = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
            )
        }

        LibraryDisplayMode.CoverOnlyGrid -> {
            MangaLibraryAuroraCardGrid(
                items = items,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                showMetadata = false,
                onClick = onClickManga,
                onLongClick = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }

        LibraryDisplayMode.ComfortableGrid -> {
            MangaLibraryAuroraCardGrid(
                items = items,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                showMetadata = true,
                onClick = onClickManga,
                onLongClick = onToggleRangeSelection,
                onClickContinueReading = onContinueReadingClicked,
            )
        }
    }
}

@Composable
private fun MangaLibraryAuroraList(
    items: List<MangaLibraryItem>,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    val colors = AuroraTheme.colors

    FastScrollLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

        listItems(
            items = items,
            contentType = { "manga_library_aurora_list_item" },
        ) { libraryItem ->
            val libraryManga = libraryItem.libraryManga
            val manga = libraryManga.manga
            val subtitle = if (libraryManga.totalChapters > 0) {
                "${libraryManga.readCount}/${libraryManga.totalChapters} ${stringResource(MR.strings.chapters)}"
            } else {
                null
            }
            val hasBadge = libraryItem.downloadCount > 0 ||
                libraryItem.unreadCount > 0 ||
                libraryItem.isLocal ||
                libraryItem.sourceLanguage.isNotBlank()

            AuroraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.2f),
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                subtitle = subtitle,
                badge = if (hasBadge) {
                    {
                        BadgeGroup {
                            if (libraryItem.downloadCount > 0) {
                                Badge(
                                    text = libraryItem.downloadCount.toString(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                            if (libraryItem.unreadCount > 0) {
                                Badge(
                                    text = libraryItem.unreadCount.toString(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                            if (libraryItem.isLocal) {
                                Badge(
                                    text = "LOCAL",
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            } else if (libraryItem.sourceLanguage.isNotBlank()) {
                                Badge(
                                    text = libraryItem.sourceLanguage.uppercase(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onClick = { onClick(libraryManga) },
                onLongClick = { onLongClick(libraryManga) },
                onClickContinueViewing = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryManga) }
                } else {
                    null
                },
                isSelected = selection.fastAny { it.id == libraryManga.id },
                coverHeightFraction = 0.62f,
                titleMaxLines = 1,
            )
        }
    }
}

@Composable
private fun MangaLibraryAuroraCardGrid(
    items: List<MangaLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    showMetadata: Boolean,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
) {
    val colors = AuroraTheme.colors

    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        gridItems(
            items = items,
            contentType = {
                if (showMetadata) {
                    "manga_library_aurora_comfortable_grid_item"
                } else {
                    "manga_library_aurora_cover_only_grid_item"
                }
            },
        ) { libraryItem ->
            val libraryManga = libraryItem.libraryManga
            val manga = libraryManga.manga
            val subtitle = if (showMetadata && libraryManga.totalChapters > 0) {
                "${libraryManga.readCount}/${libraryManga.totalChapters} ${stringResource(MR.strings.chapters)}"
            } else {
                null
            }
            val hasBadge = libraryItem.downloadCount > 0 ||
                libraryItem.unreadCount > 0 ||
                libraryItem.isLocal ||
                libraryItem.sourceLanguage.isNotBlank()

            AuroraCard(
                modifier = Modifier.aspectRatio(if (showMetadata) 0.66f else 0.6f),
                title = manga.title,
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                subtitle = subtitle,
                badge = if (hasBadge) {
                    {
                        BadgeGroup {
                            if (libraryItem.downloadCount > 0) {
                                Badge(
                                    text = libraryItem.downloadCount.toString(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                            if (libraryItem.unreadCount > 0) {
                                Badge(
                                    text = libraryItem.unreadCount.toString(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                            if (libraryItem.isLocal) {
                                Badge(
                                    text = "LOCAL",
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            } else if (libraryItem.sourceLanguage.isNotBlank()) {
                                Badge(
                                    text = libraryItem.sourceLanguage.uppercase(),
                                    color = colors.accent,
                                    textColor = colors.textOnAccent,
                                    shape = RoundedCornerShape(4.dp),
                                )
                            }
                        }
                    }
                } else {
                    null
                },
                onClick = { onClick(libraryManga) },
                onLongClick = { onLongClick(libraryManga) },
                onClickContinueViewing = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                    { onClickContinueReading(libraryManga) }
                } else {
                    null
                },
                isSelected = selection.fastAny { it.id == libraryManga.id },
                coverHeightFraction = if (showMetadata) 0.68f else 1f,
                titleMaxLines = if (showMetadata) 1 else 2,
            )
        }
    }
}

@Composable
private fun MangaLibraryAuroraEmptyScreen(
    searchQuery: String?,
    hasActiveFilters: Boolean,
    contentPadding: PaddingValues,
    onGlobalSearchClicked: () -> Unit,
) {
    val message = when {
        !searchQuery.isNullOrEmpty() -> MR.strings.no_results_found
        hasActiveFilters -> MR.strings.error_no_match
        else -> MR.strings.information_no_manga_category
    }

    Column(
        modifier = Modifier
            .padding(contentPadding + PaddingValues(8.dp))
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!searchQuery.isNullOrEmpty()) {
            eu.kanade.presentation.library.components.GlobalSearchItem(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
                searchQuery = searchQuery,
                onClick = onGlobalSearchClicked,
            )
        }

        EmptyScreen(
            stringRes = message,
            modifier = Modifier.weight(1f),
        )
    }
}
