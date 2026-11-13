package eu.kanade.tachiyomi.ui.reels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.animesource.model.FeedCategory
import eu.kanade.tachiyomi.ui.reels.components.ReelsLoginDialog
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.reels.anime.model.ReelsAlbum
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction

/**
 * Unified cross-plugin Reels Albums hub:
 * - Saved tab aggregates local saved albums from every plugin (`ReelsAlbumRepository.subscribeAll`),
 *   showing plugin source badges/icons, source filter chips, search, sort, missing-source cleanup,
 *   and deep links to [ReelsAlbumScreen] / [ReelsFeedScreen].
 * - Discover tab fans out browse (`AnimeFeedBrowseSource`) and search (`AnimeAlbumSearchSource` +
 *   local directory matching fallback) across all installed & enabled Reels plugins with isolated
 *   per-plugin status/auth/rate-limit cards and per-plugin Load More.
 */
data class ReelsAlbumsScreen(
    val sourceId: Long? = null,
    val initialTab: ReelsAlbumsTab = ReelsAlbumsTab.Saved,
) : Screen {

    override val key: String
        get() = "ReelsAlbumsScreen:${sourceId ?: "all"}:${initialTab.name}"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val screenModel = rememberScreenModel { ReelsAlbumsScreenModel(sourceId) }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val snackbarHostState = remember { SnackbarHostState() }

        var selectedTab by rememberSaveable { mutableStateOf(initialTab) }
        var showSortMenu by remember { mutableStateOf(false) }
        var confirmCleanup by remember { mutableStateOf(false) }

        LaunchedEffect(state.cleanupDeletedCount) {
            val count = state.cleanupDeletedCount ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(
                context.stringResource(MR.strings.reels_cleanup_missing_done, count),
            )
            screenModel.onCleanupNoticeShown()
        }

        if (confirmCleanup) {
            AuroraFrostDialog(
                onDismiss = { confirmCleanup = false },
                title = stringResource(MR.strings.reels_cleanup_missing),
                footer = {
                    AuroraFrostCancel(
                        label = stringResource(MR.strings.action_cancel),
                        onClick = { confirmCleanup = false },
                    )
                    AuroraFrostConfirm(
                        label = stringResource(MR.strings.action_remove),
                        onClick = {
                            confirmCleanup = false
                            screenModel.cleanupMissingSources()
                        },
                    )
                },
            ) {
                Text(
                    text = stringResource(MR.strings.reels_albums_cleanup_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraTheme.colors.textPrimary,
                )
            }
        }

        state.loginPrompt?.let { prompt ->
            ReelsLoginDialog(
                loggedInAccount = null,
                isLoggingIn = prompt.isLoggingIn,
                loginError = prompt.error,
                loginRejected = prompt.rejected,
                onLogin = screenModel::submitLogin,
                onLogout = {},
                onDismiss = screenModel::dismissLoginPrompt,
            )
        }

        Scaffold(
            topBar = {
                AppBar(
                    title = stringResource(MR.strings.reels_albums),
                    navigateUp = navigator::pop,
                    actions = {
                        if (selectedTab == ReelsAlbumsTab.Saved) {
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                                        contentDescription = stringResource(MR.strings.action_sort),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                ) {
                                    ReelsAlbumsSort.entries.forEach { option ->
                                        val label = when (option) {
                                            ReelsAlbumsSort.DateDesc -> stringResource(MR.strings.reels_sort_newest)
                                            ReelsAlbumsSort.DateAsc -> stringResource(MR.strings.reels_sort_oldest)
                                            ReelsAlbumsSort.Name -> stringResource(MR.strings.action_sort_alpha)
                                            ReelsAlbumsSort.Source -> stringResource(MR.strings.reels_sort_source)
                                        }
                                        DropdownMenuItem(
                                            text = { Text(label) },
                                            trailingIcon = if (state.savedSort == option) {
                                                { Icon(Icons.Filled.Check, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                screenModel.setSavedSort(option)
                                                showSortMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            if (state.missingSourceIds.isNotEmpty()) {
                                IconButton(onClick = { confirmCleanup = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteSweep,
                                        contentDescription = stringResource(MR.strings.reels_cleanup_missing),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            IconButton(onClick = { selectedTab = ReelsAlbumsTab.Discover }) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = stringResource(MR.strings.reels_album_add),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else {
                            IconButton(onClick = screenModel::refreshRemote) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = stringResource(MR.strings.action_entry_refresh),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                AlbumsModeTabsRow(
                    selectedTab = selectedTab,
                    savedCount = state.savedAlbums.size,
                    onSelectTab = { selectedTab = it },
                )

                if (state.filterSources.isNotEmpty()) {
                    SourceFilterChipsRow(
                        chips = state.filterSources,
                        sourceIcons = state.sourceIcons,
                        selectedSourceId = state.selectedSourceFilterId,
                        onSelectSource = screenModel::selectSourceFilter,
                    )
                }

                when (selectedTab) {
                    ReelsAlbumsTab.Saved -> SavedAlbumsTabContent(
                        state = state,
                        onSearchQueryChange = screenModel::setSavedSearchQuery,
                        onOpenAlbum = { album ->
                            navigator.push(
                                ReelsAlbumScreen(
                                    sourceId = album.sourceId,
                                    albumId = album.albumId,
                                    albumName = album.name,
                                ),
                            )
                        },
                        onPlayAlbum = { album ->
                            navigator.push(
                                ReelsFeedScreen(
                                    sourceId = album.sourceId,
                                    nicheId = album.albumId,
                                    nicheName = album.name,
                                ),
                            )
                        },
                        onRemoveAlbum = screenModel::removeAlbum,
                        onDiscoverClick = { selectedTab = ReelsAlbumsTab.Discover },
                    )

                    ReelsAlbumsTab.Discover -> DiscoverAlbumsTabContent(
                        state = state,
                        onSearchQueryChange = screenModel::setRemoteSearchQuery,
                        onSearchSubmit = screenModel::submitRemoteSearch,
                        onOpenCategory = { group, category ->
                            navigator.push(
                                ReelsAlbumScreen(
                                    sourceId = group.sourceId,
                                    albumId = category.id,
                                    albumName = category.name,
                                ),
                            )
                        },
                        onPlayCategory = { group, category ->
                            navigator.push(
                                ReelsFeedScreen(
                                    sourceId = group.sourceId,
                                    nicheId = category.id,
                                    nicheName = category.name,
                                ),
                            )
                        },
                        onToggleSave = screenModel::toggleSaveAlbum,
                        onLoadMore = screenModel::loadCatalogMore,
                        onRetrySource = screenModel::retryRemoteSource,
                        onSignInSource = { group ->
                            if (group.supportsWebLogin) {
                                navigator.push(ReelsFeedScreen(sourceId = group.sourceId))
                            } else {
                                screenModel.openLoginPrompt(group.sourceId)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumsModeTabsRow(
    selectedTab: ReelsAlbumsTab,
    savedCount: Int,
    onSelectTab: (ReelsAlbumsTab) -> Unit,
) {
    val colors = AuroraTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReelsAlbumsTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            val title = when (tab) {
                ReelsAlbumsTab.Saved -> {
                    val base = stringResource(MR.strings.reels_albums_tab_saved)
                    if (savedCount > 0) "$base ($savedCount)" else base
                }
                ReelsAlbumsTab.Discover -> stringResource(MR.strings.reels_albums_tab_discover)
            }
            val bg = if (selected) {
                if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
            val borderColor = if (selected) {
                if (colors.isEInk) colors.textPrimary else colors.accent.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            }
            val contentColor = if (selected) {
                if (colors.isEInk) colors.textOnAccent else colors.accent
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable { onSelectTab(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SourceFilterChipsRow(
    chips: List<AlbumFilterSourceChip>,
    sourceIcons: Map<Long, ImageBitmap>,
    selectedSourceId: Long?,
    onSelectSource: (Long?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedSourceId == null,
            onClick = { onSelectSource(null) },
            label = { Text(stringResource(MR.strings.reels_search_tab_all)) },
        )
        chips.forEach { chip ->
            val icon = sourceIcons[chip.sourceId]
            FilterChip(
                selected = selectedSourceId == chip.sourceId,
                onClick = {
                    val next = if (selectedSourceId == chip.sourceId) null else chip.sourceId
                    onSelectSource(next)
                },
                leadingIcon = if (icon != null) {
                    {
                        Image(
                            bitmap = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                } else {
                    null
                },
                label = {
                    Text(
                        text = chip.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun SavedAlbumsTabContent(
    state: ReelsAlbumsScreenModel.State,
    onSearchQueryChange: (String) -> Unit,
    onOpenAlbum: (ReelsAlbum) -> Unit,
    onPlayAlbum: (ReelsAlbum) -> Unit,
    onRemoveAlbum: (ReelsAlbum) -> Unit,
    onDiscoverClick: () -> Unit,
) {
    val visible = state.visibleSavedAlbums
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.savedAlbums.isNotEmpty() || state.savedSearchQuery.isNotEmpty()) {
            SearchFieldBar(
                query = state.savedSearchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmit = {},
                placeholder = stringResource(MR.strings.reels_albums_search_hint),
            )
        }
        when {
            state.savedAlbums.isEmpty() -> {
                EmptyScreen(
                    stringRes = MR.strings.reels_albums_empty,
                    modifier = Modifier.weight(1f),
                    actions = kotlinx.collections.immutable.persistentListOf(
                        EmptyScreenAction(
                            stringRes = MR.strings.reels_albums_discover_action,
                            icon = Icons.Outlined.PhotoLibrary,
                            onClick = onDiscoverClick,
                        ),
                    ),
                )
            }
            visible.isEmpty() -> {
                EmptyScreen(
                    stringRes = MR.strings.no_results_found,
                    modifier = Modifier.weight(1f),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = visible,
                        key = { "${it.sourceId}:${it.albumId}" },
                    ) { album ->
                        val sourceName = state.sourceNames[album.sourceId]
                            ?: stringResource(MR.strings.reels_unknown_source)
                        val isMissing = album.sourceId in state.missingSourceIds
                        SavedAlbumRow(
                            album = album,
                            sourceName = sourceName,
                            sourceIcon = state.sourceIcons[album.sourceId],
                            isMissingSource = isMissing,
                            onOpen = { onOpenAlbum(album) },
                            onPlay = { onPlayAlbum(album) },
                            onRemove = { onRemoveAlbum(album) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverAlbumsTabContent(
    state: ReelsAlbumsScreenModel.State,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onOpenCategory: (RemoteAlbumSourceGroup, FeedCategory) -> Unit,
    onPlayCategory: (RemoteAlbumSourceGroup, FeedCategory) -> Unit,
    onToggleSave: (Long, FeedCategory) -> Unit,
    onLoadMore: (Long) -> Unit,
    onRetrySource: (Long) -> Unit,
    onSignInSource: (RemoteAlbumSourceGroup) -> Unit,
) {
    val groups = state.visibleRemoteGroups
    val savedKeys = state.savedKeys

    Column(modifier = Modifier.fillMaxSize()) {
        SearchFieldBar(
            query = state.remoteSearchQuery,
            onQueryChange = onSearchQueryChange,
            onSubmit = onSearchSubmit,
            placeholder = stringResource(MR.strings.reels_albums_search_hint),
        )

        if (groups.isEmpty() && !state.isRemoteRefreshing) {
            EmptyScreen(
                stringRes = MR.strings.reels_albums_no_plugins,
                modifier = Modifier.weight(1f),
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            groups.forEach { group ->
                item(key = "header:${group.sourceId}") {
                    RemoteSourceHeader(
                        group = group,
                        icon = state.sourceIcons[group.sourceId],
                        onRetry = { onRetrySource(group.sourceId) },
                    )
                }

                when (val status = group.status) {
                    RemoteSourceStatus.Idle,
                    RemoteSourceStatus.Loading,
                    -> {
                        item(key = "loading:${group.sourceId}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                )
                            }
                        }
                    }

                    RemoteSourceStatus.NeedsAuth -> {
                        item(key = "auth:${group.sourceId}") {
                            RemoteSourceStatusBanner(
                                icon = Icons.Filled.Lock,
                                message = stringResource(MR.strings.reels_albums_login_required),
                                primaryLabel = stringResource(MR.strings.reels_login),
                                onPrimaryClick = { onSignInSource(group) },
                                secondaryLabel = stringResource(MR.strings.action_retry),
                                onSecondaryClick = { onRetrySource(group.sourceId) },
                            )
                        }
                    }

                    is RemoteSourceStatus.RateLimited -> {
                        item(key = "ratelimit:${group.sourceId}") {
                            RemoteSourceStatusBanner(
                                icon = Icons.Outlined.WarningAmber,
                                message = stringResource(MR.strings.reels_albums_rate_limited_short),
                                primaryLabel = stringResource(MR.strings.action_retry),
                                onPrimaryClick = { onRetrySource(group.sourceId) },
                            )
                        }
                    }

                    is RemoteSourceStatus.Error -> {
                        item(key = "error:${group.sourceId}") {
                            RemoteSourceStatusBanner(
                                icon = Icons.Outlined.WarningAmber,
                                message = status.message,
                                primaryLabel = if (status.canRetry) stringResource(MR.strings.action_retry) else null,
                                onPrimaryClick = { onRetrySource(group.sourceId) },
                            )
                        }
                    }

                    RemoteSourceStatus.Ready -> {
                        if (group.categories.isEmpty()) {
                            item(key = "empty:${group.sourceId}") {
                                Text(
                                    text = stringResource(
                                        if (state.remoteSearchQuery.isNotBlank()) {
                                            MR.strings.reels_niches_search_empty
                                        } else {
                                            MR.strings.reels_feed_empty
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                )
                            }
                        } else {
                            items(
                                items = group.categories,
                                key = { "cat:${group.sourceId}:${it.id}" },
                            ) { category ->
                                val isSaved = (group.sourceId to category.id) in savedKeys
                                RemoteCategoryRow(
                                    category = category,
                                    isSaved = isSaved,
                                    onOpen = { onOpenCategory(group, category) },
                                    onPlay = { onPlayCategory(group, category) },
                                    onToggleSave = { onToggleSave(group.sourceId, category) },
                                )
                            }

                            if (group.pagination.canLoadMore || group.isPageLoading || group.pageError != null) {
                                item(key = "footer:${group.sourceId}") {
                                    RemoteGroupFooter(
                                        group = group,
                                        onLoadMore = { onLoadMore(group.sourceId) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFieldBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(MR.strings.action_clear),
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun SavedAlbumRow(
    album: ReelsAlbum,
    sourceName: String,
    sourceIcon: ImageBitmap?,
    isMissingSource: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = album.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            SourceBadgePill(
                sourceName = sourceName,
                sourceIcon = sourceIcon,
                isMissing = isMissingSource,
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Outlined.DeleteOutline,
                contentDescription = stringResource(MR.strings.reels_album_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SourceBadgePill(
    sourceName: String,
    sourceIcon: ImageBitmap?,
    isMissing: Boolean,
) {
    val bgColor = if (isMissing) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val textColor = if (isMissing) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (sourceIcon != null) {
            Image(
                bitmap = sourceIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(13.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }
        Text(
            text = sourceName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemoteSourceHeader(
    group: RemoteAlbumSourceGroup,
    icon: ImageBitmap?,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = group.sourceName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (group.status !is RemoteSourceStatus.Loading) {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(MR.strings.action_entry_refresh),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RemoteSourceStatusBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    primaryLabel: String?,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (primaryLabel != null || secondaryLabel != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (primaryLabel != null) {
                        TextButton(
                            onClick = onPrimaryClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(primaryLabel)
                        }
                    }
                    if (secondaryLabel != null && onSecondaryClick != null) {
                        TextButton(
                            onClick = onSecondaryClick,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(secondaryLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteCategoryRow(
    category: FeedCategory,
    isSaved: Boolean,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onToggleSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!category.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = category.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            category.itemCount?.let { count ->
                Text(
                    text = stringResource(MR.strings.reels_albums_items_count, count.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = stringResource(MR.strings.reels_album_play_as_feed),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onToggleSave) {
            Icon(
                imageVector = if (isSaved) Icons.Filled.CheckCircle else Icons.Outlined.AddCircle,
                contentDescription = stringResource(
                    if (isSaved) MR.strings.reels_album_remove else MR.strings.reels_album_add,
                ),
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun RemoteGroupFooter(
    group: RemoteAlbumSourceGroup,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            group.isPageLoading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = stringResource(MR.strings.reels_loading_more),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            group.pageError != null -> {
                Text(
                    text = if (group.pageRateLimitedUntil != null) {
                        stringResource(MR.strings.reels_albums_rate_limited_short)
                    } else {
                        group.pageError
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onLoadMore) {
                    Text(stringResource(MR.strings.action_retry))
                }
            }
            group.pagination.canLoadMore -> {
                OutlinedButton(
                    onClick = onLoadMore,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(stringResource(MR.strings.reels_albums_load_more))
                }
            }
        }
    }
}
