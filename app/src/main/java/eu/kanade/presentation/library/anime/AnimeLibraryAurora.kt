package eu.kanade.presentation.library.anime

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
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryItem
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.util.plus
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems

@Composable
fun AnimeLibraryAuroraContent(
    items: List<AnimeLibraryItem>,
    selection: List<LibraryAnime>,
    searchQuery: String?,
    hasActiveFilters: Boolean,
    displayMode: LibraryDisplayMode,
    columns: Int,
    onAnimeClicked: (Long) -> Unit,
    onToggleSelection: (LibraryAnime) -> Unit,
    onToggleRangeSelection: (LibraryAnime) -> Unit,
    onContinueWatchingClicked: ((LibraryAnime) -> Unit)?,
    onGlobalSearchClicked: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (items.isEmpty()) {
        AnimeLibraryAuroraEmptyScreen(
            searchQuery = searchQuery,
            hasActiveFilters = hasActiveFilters,
            contentPadding = contentPadding,
            onGlobalSearchClicked = onGlobalSearchClicked,
        )
        return
    }

    val safeColumns = columns.coerceAtLeast(1)
    val isSelectionMode = selection.isNotEmpty()
    val onClickAnime = { anime: LibraryAnime ->
        if (isSelectionMode) {
            onToggleSelection(anime)
        } else {
            onAnimeClicked(anime.anime.id)
        }
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            AnimeLibraryAuroraList(
                items = items,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                onClick = onClickAnime,
                onLongClick = onToggleRangeSelection,
                onClickContinueWatching = onContinueWatchingClicked,
            )
        }

        LibraryDisplayMode.CompactGrid,
        -> {
            AnimeLibraryCompactGrid(
                items = items,
                showTitle = true,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                onClick = onClickAnime,
                onLongClick = onToggleRangeSelection,
                onClickContinueWatching = onContinueWatchingClicked,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
            )
        }

        LibraryDisplayMode.CoverOnlyGrid -> {
            AnimeLibraryAuroraCardGrid(
                items = items,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                showMetadata = false,
                onClick = onClickAnime,
                onLongClick = onToggleRangeSelection,
                onClickContinueWatching = onContinueWatchingClicked,
            )
        }

        LibraryDisplayMode.ComfortableGrid -> {
            AnimeLibraryAuroraCardGrid(
                items = items,
                columns = safeColumns,
                contentPadding = contentPadding,
                selection = selection,
                searchQuery = searchQuery,
                onGlobalSearchClicked = onGlobalSearchClicked,
                showMetadata = true,
                onClick = onClickAnime,
                onLongClick = onToggleRangeSelection,
                onClickContinueWatching = onContinueWatchingClicked,
            )
        }
    }
}

@Composable
private fun AnimeLibraryAuroraList(
    items: List<AnimeLibraryItem>,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
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
            contentType = { "anime_library_aurora_list_item" },
        ) { libraryItem ->
            val libraryAnime = libraryItem.libraryAnime
            val anime = libraryAnime.anime
            val subtitle = if (libraryAnime.totalCount > 0) {
                "${libraryAnime.seenCount}/${libraryAnime.totalCount} ${stringResource(AYMR.strings.episodes)}"
            } else {
                null
            }
            val hasBadge = libraryItem.downloadCount > 0 ||
                libraryItem.unseenCount > 0 ||
                libraryItem.isLocal ||
                libraryItem.sourceLanguage.isNotBlank()

            AuroraCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2.2f),
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
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
                            if (libraryItem.unseenCount > 0) {
                                Badge(
                                    text = libraryItem.unseenCount.toString(),
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
                onClick = { onClick(libraryAnime) },
                onLongClick = { onLongClick(libraryAnime) },
                onClickContinueViewing = if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
                    { onClickContinueWatching(libraryAnime) }
                } else {
                    null
                },
                isSelected = selection.fastAny { it.id == libraryAnime.id },
                coverHeightFraction = 0.62f,
                titleMaxLines = 1,
            )
        }
    }
}

@Composable
private fun AnimeLibraryAuroraCardGrid(
    items: List<AnimeLibraryItem>,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryAnime>,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
    showMetadata: Boolean,
    onClick: (LibraryAnime) -> Unit,
    onLongClick: (LibraryAnime) -> Unit,
    onClickContinueWatching: ((LibraryAnime) -> Unit)?,
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
                    "anime_library_aurora_comfortable_grid_item"
                } else {
                    "anime_library_aurora_cover_only_grid_item"
                }
            },
        ) { libraryItem ->
            val libraryAnime = libraryItem.libraryAnime
            val anime = libraryAnime.anime
            val subtitle = if (showMetadata && libraryAnime.totalCount > 0) {
                "${libraryAnime.seenCount}/${libraryAnime.totalCount} ${stringResource(AYMR.strings.episodes)}"
            } else {
                null
            }
            val hasBadge = libraryItem.downloadCount > 0 ||
                libraryItem.unseenCount > 0 ||
                libraryItem.isLocal ||
                libraryItem.sourceLanguage.isNotBlank()

            AuroraCard(
                modifier = Modifier.aspectRatio(if (showMetadata) 0.66f else 0.6f),
                title = anime.title,
                coverData = AnimeCover(
                    animeId = anime.id,
                    sourceId = anime.source,
                    isAnimeFavorite = anime.favorite,
                    url = anime.thumbnailUrl,
                    lastModified = anime.coverLastModified,
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
                            if (libraryItem.unseenCount > 0) {
                                Badge(
                                    text = libraryItem.unseenCount.toString(),
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
                onClick = { onClick(libraryAnime) },
                onLongClick = { onLongClick(libraryAnime) },
                onClickContinueViewing = if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
                    { onClickContinueWatching(libraryAnime) }
                } else {
                    null
                },
                isSelected = selection.fastAny { it.id == libraryAnime.id },
                coverHeightFraction = if (showMetadata) 0.68f else 1f,
                titleMaxLines = if (showMetadata) 1 else 2,
            )
        }
    }
}

@Composable
private fun AnimeLibraryAuroraEmptyScreen(
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
