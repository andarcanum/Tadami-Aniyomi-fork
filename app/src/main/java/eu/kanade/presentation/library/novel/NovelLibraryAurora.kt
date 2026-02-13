package eu.kanade.presentation.library.novel

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.LocalTabState
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadManager
import tachiyomi.domain.library.novel.LibraryNovel
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun NovelLibraryAuroraContent(
    items: List<LibraryNovel>,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onNovelClicked: (Long) -> Unit,
    contentPadding: PaddingValues,
    hasActiveFilters: Boolean,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onGlobalUpdate: () -> Unit,
    onOpenRandomEntry: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
    val sourceManager = remember { Injekt.get<NovelSourceManager>() }
    val displayMode by libraryPreferences.displayMode().collectAsState()
    val columnPreference = remember(configuration.orientation) {
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            libraryPreferences.novelLandscapeColumns()
        } else {
            libraryPreferences.novelPortraitColumns()
        }
    }
    val columns by columnPreference.collectAsState()
    val displaySpec = remember(displayMode, columns) {
        resolveNovelLibraryAuroraDisplaySpec(
            displayMode = displayMode,
            columns = columns,
        )
    }
    val showDownloadBadge by libraryPreferences.downloadBadge().collectAsState()
    val showUnreadBadge by libraryPreferences.unreadBadge().collectAsState()
    val showLanguageBadge by libraryPreferences.languageBadge().collectAsState()
    val downloadedNovelIds = remember(items, showDownloadBadge) {
        if (!showDownloadBadge) return@remember emptySet()

        val downloadManager = NovelDownloadManager()
        items.asSequence()
            .mapNotNull { item ->
                item.novel.id.takeIf { downloadManager.hasAnyDownloadedChapter(item.novel) }
            }
            .toSet()
    }
    val sourceLanguageByNovelId = remember(items, showLanguageBadge) {
        if (!showLanguageBadge) return@remember emptyMap()

        items.associate { item ->
            item.novel.id to sourceManager.getOrStub(item.novel.source).lang
        }
    }
    var isSearchActive by remember(searchQuery) { mutableStateOf(!searchQuery.isNullOrBlank()) }

    val query = searchQuery.orEmpty()
    val filteredItems = if (query.isBlank()) {
        items
    } else {
        items.filter { it.novel.title.contains(query, ignoreCase = true) }
    }

    val gridCells = remember(displaySpec) {
        displaySpec.fixedColumns?.let { fixed ->
            GridCells.Fixed(fixed)
        } ?: GridCells.Adaptive(
            minSize = checkNotNull(displaySpec.adaptiveMinCellDp).dp,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        if (displaySpec.isList) {
            LazyColumn(
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                item {
                    InlineNovelLibraryHeader(
                        isSearchActive = isSearchActive,
                        searchQuery = query,
                        onSearchQueryChange = onSearchQueryChange,
                        onSearchClick = { isSearchActive = true },
                        onSearchClose = {
                            onSearchQueryChange(null)
                            isSearchActive = false
                        },
                        hasActiveFilters = hasActiveFilters,
                        onFilterClicked = onFilterClicked,
                        onRefresh = onRefresh,
                        onGlobalUpdate = onGlobalUpdate,
                        onOpenRandomEntry = onOpenRandomEntry,
                    )
                }

                listItems(filteredItems, key = { it.id }) { item ->
                    val badgeState = resolveNovelLibraryBadgeState(
                        item = item,
                        showDownloadBadge = showDownloadBadge,
                        downloadedNovelIds = downloadedNovelIds,
                        showUnreadBadge = showUnreadBadge,
                        showLanguageBadge = showLanguageBadge,
                        sourceLanguage = sourceLanguageByNovelId[item.novel.id].orEmpty(),
                    )
                    NovelLibraryAuroraCard(
                        item = item,
                        context = context,
                        badgeState = badgeState,
                        showMetadata = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2.2f),
                        coverHeightFraction = 0.68f,
                        onNovelClicked = onNovelClicked,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = gridCells,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(displaySpec.gridVerticalSpacingDp.dp),
                horizontalArrangement = Arrangement.spacedBy(displaySpec.gridHorizontalSpacingDp.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    InlineNovelLibraryHeader(
                        isSearchActive = isSearchActive,
                        searchQuery = query,
                        onSearchQueryChange = onSearchQueryChange,
                        onSearchClick = { isSearchActive = true },
                        onSearchClose = {
                            onSearchQueryChange(null)
                            isSearchActive = false
                        },
                        hasActiveFilters = hasActiveFilters,
                        onFilterClicked = onFilterClicked,
                        onRefresh = onRefresh,
                        onGlobalUpdate = onGlobalUpdate,
                        onOpenRandomEntry = onOpenRandomEntry,
                    )
                }

                gridItems(filteredItems, key = { it.id }) { item ->
                    val badgeState = resolveNovelLibraryBadgeState(
                        item = item,
                        showDownloadBadge = showDownloadBadge,
                        downloadedNovelIds = downloadedNovelIds,
                        showUnreadBadge = showUnreadBadge,
                        showLanguageBadge = showLanguageBadge,
                        sourceLanguage = sourceLanguageByNovelId[item.novel.id].orEmpty(),
                    )
                    NovelLibraryAuroraCard(
                        item = item,
                        context = context,
                        badgeState = badgeState,
                        showMetadata = displaySpec.showMetadata,
                        modifier = Modifier.aspectRatio(displaySpec.gridCardAspectRatio),
                        coverHeightFraction = displaySpec.gridCoverHeightFraction,
                        onNovelClicked = onNovelClicked,
                    )
                }
            }
        }
    }
}

@Composable
private fun NovelLibraryAuroraCard(
    item: LibraryNovel,
    context: Context,
    badgeState: NovelLibraryBadgeState,
    showMetadata: Boolean,
    modifier: Modifier,
    coverHeightFraction: Float,
    onNovelClicked: (Long) -> Unit,
) {
    val progressText = if (showMetadata && item.totalChapters > 0) {
        "${item.totalChapters - item.unreadCount}/${item.totalChapters} ${stringResource(
            MR.strings.chapters,
        )}"
    } else {
        null
    }
    val coverRequest = remember(item.novel.id, item.novel.thumbnailUrl, item.novel.coverLastModified) {
        ImageRequest.Builder(context)
            .data(item.novel.thumbnailUrl)
            .placeholderMemoryCacheKey(item.novel.thumbnailUrl)
            .build()
    }

    if (showMetadata) {
        val colors = AuroraTheme.colors
        AuroraCard(
            modifier = modifier,
            title = item.novel.title,
            coverData = coverRequest,
            subtitle = progressText,
            coverHeightFraction = coverHeightFraction,
            badge = if (badgeState.hasBadge()) {
                {
                    BadgeGroup {
                        if (badgeState.showDownloaded) {
                            Badge(
                                text = "DL",
                                color = colors.accent,
                                textColor = colors.textOnAccent,
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                        badgeState.unreadCount?.let {
                            Badge(
                                text = it.toString(),
                                color = colors.accent,
                                textColor = colors.textOnAccent,
                                shape = RoundedCornerShape(4.dp),
                            )
                        }
                        badgeState.language?.let {
                            Badge(
                                text = it.uppercase(),
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
            onClick = { onNovelClicked(item.novel.id) },
        )
    } else {
        NovelLibraryAuroraCoverOnlyCard(
            coverData = coverRequest,
            badgeState = badgeState,
            modifier = modifier,
            onClick = { onNovelClicked(item.novel.id) },
        )
    }
}

@Composable
private fun NovelLibraryAuroraCoverOnlyCard(
    coverData: Any?,
    badgeState: NovelLibraryBadgeState,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.glass),
        border = BorderStroke(
            width = 1.dp,
            color = if (colors.isDark) Color.Transparent else Color.LightGray.copy(alpha = 0.4f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.1f)),
        ) {
            AsyncImage(
                model = coverData,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                error = rememberVectorPainter(Icons.Filled.BrokenImage),
            )

            if (badgeState.hasBadge()) {
                BadgeGroup(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    if (badgeState.showDownloaded) {
                        Badge(
                            text = "DL",
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                    badgeState.unreadCount?.let {
                        Badge(
                            text = it.toString(),
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                    badgeState.language?.let {
                        Badge(
                            text = it.uppercase(),
                            color = colors.accent,
                            textColor = colors.textOnAccent,
                            shape = RoundedCornerShape(4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun NovelLibraryBadgeState.hasBadge(): Boolean {
    return showDownloaded || unreadCount != null || language != null
}

@Composable
private fun InlineNovelLibraryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String?) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    hasActiveFilters: Boolean,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onGlobalUpdate: () -> Unit,
    onOpenRandomEntry: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val tabState = LocalTabState.current
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = { value ->
                        onSearchQueryChange(value.ifBlank { null })
                    },
                    placeholder = {
                        Text(
                            text = stringResource(MR.strings.action_search),
                            color = colors.textSecondary,
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = colors.accent) },
                    trailingIcon = {
                        IconButton(onClick = onSearchClose) {
                            Icon(Icons.Filled.Close, null, tint = colors.textSecondary)
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.cardBackground,
                        unfocusedContainerColor = colors.cardBackground,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Text(
                    text = stringResource(AYMR.strings.aurora_library),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )

                Row {
                    IconButton(onClick = onFilterClicked) {
                        Icon(
                            Icons.Filled.FilterList,
                            null,
                            tint = if (hasActiveFilters) colors.accent else colors.textSecondary,
                        )
                    }
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, null, tint = colors.accent)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, null, tint = colors.textSecondary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_update_library)) },
                                onClick = {
                                    onRefresh()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.pref_category_library_update)) },
                                onClick = {
                                    onGlobalUpdate()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_open_random_manga)) },
                                onClick = {
                                    onOpenRandomEntry()
                                    showMenu = false
                                },
                                leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                            )
                        }
                    }
                }
            }
        }

        if (tabState != null && tabState.tabs.size > 1) {
            Spacer(Modifier.height(12.dp))
            AuroraTabRow(
                tabs = tabState.tabs,
                selectedIndex = tabState.selectedIndex,
                onTabSelected = tabState.onTabSelected,
                scrollable = false,
            )
        }
    }
}
