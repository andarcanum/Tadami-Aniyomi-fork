package eu.kanade.presentation.library.manga

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import eu.kanade.presentation.components.AuroraCard
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.LocalTabState
import eu.kanade.presentation.theme.AuroraTheme
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun MangaLibraryAuroraContent(
    items: List<LibraryManga>,
    onMangaClicked: (Long) -> Unit,
    contentPadding: PaddingValues,
    onFilterClicked: () -> Unit,
    onRefresh: () -> Unit,
    onGlobalUpdate: () -> Unit,
    onOpenRandomEntry: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val gridState = rememberLazyGridState()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val filteredItems = if (searchQuery.isBlank()) {
        items
    } else {
        items.filter { it.manga.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundGradient),
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3), // Aniview: 3 columns
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            item(span = { GridItemSpan(3) }) {
                InlineLibraryHeader(
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchClick = { isSearchActive = true },
                    onSearchClose = {
                        searchQuery = ""
                        isSearchActive = false
                    },
                    onFilterClick = onFilterClicked,
                    onRefresh = onRefresh,
                    onGlobalUpdate = onGlobalUpdate,
                    onOpenRandomEntry = onOpenRandomEntry,
                )
            }

            items(filteredItems, key = { it.manga.id }) { item ->
                val context = LocalContext.current
                AuroraCard(

                    modifier = Modifier.aspectRatio(0.6f),

                    title = item.manga.title,
                    coverData = remember(item.manga.id, item.manga.thumbnailUrl, item.manga.coverLastModified) {
                        ImageRequest.Builder(context)
                            .data(item.manga.asMangaCover())
                            .placeholderMemoryCacheKey(item.manga.thumbnailUrl)
                            .build()
                    },
                    subtitle = "${item.totalChapters - item.unreadCount}/${item.totalChapters} гл.",

                    coverHeightFraction = 0.6f,

                    badge = if (item.unreadCount > 0) {
                        {
                            Text(
                                text = item.unreadCount.toString(),
                                color = colors.textOnAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(colors.accent, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = { onMangaClicked(item.manga.id) },
                )
            }
        }
    }
}

@Composable
private fun InlineLibraryHeader(
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onFilterClick: () -> Unit,
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
                // Search TextField
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поиск...", color = colors.textSecondary) },
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
                // Title
                Text(
                    text = stringResource(AYMR.strings.aurora_library),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                )

                // Icons row
                Row {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Filled.Search, null, tint = colors.accent)
                    }
                    IconButton(onClick = onFilterClick) {
                        Icon(Icons.Filled.FilterList, null, tint = colors.textSecondary)
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
                                leadingIcon = {
                                    Icon(Icons.Filled.Refresh, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.pref_category_library_update)) },
                                onClick = {
                                    onGlobalUpdate()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Refresh, null)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(MR.strings.action_open_random_manga)) },
                                onClick = {
                                    onOpenRandomEntry()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Shuffle, null)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Tab Switcher (only if more than 1 tab)
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
