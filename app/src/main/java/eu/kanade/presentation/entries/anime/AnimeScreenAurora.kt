package eu.kanade.presentation.entries.anime

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.presentation.components.DropdownMenu
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.aurora.AnimeActionCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeEpisodeCardCompact
import eu.kanade.presentation.entries.anime.components.aurora.AnimeHeroContent
import eu.kanade.presentation.entries.anime.components.aurora.AnimeInfoCard
import eu.kanade.presentation.entries.anime.components.aurora.EpisodesHeader
import eu.kanade.presentation.entries.anime.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AnimeScreenAuroraImpl(
    state: AnimeScreenModel.State.Success,
    snackbarHostState: SnackbarHostState,
    nextUpdate: Instant?,
    isTabletUi: Boolean,
    episodeSwipeStartAction: LibraryPreferences.EpisodeSwipeAction,
    episodeSwipeEndAction: LibraryPreferences.EpisodeSwipeAction,
    showNextEpisodeAirTime: Boolean,
    alwaysUseExternalPlayer: Boolean,
    navigateUp: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onAddToLibraryClicked: () -> Unit,
    onWebViewClicked: (() -> Unit)?,
    onWebViewLongClicked: (() -> Unit)?,
    onTrackingClicked: (() -> Unit)?,
    onTagSearch: (String) -> Unit,
    onFilterButtonClicked: () -> Unit,
    onRefresh: () -> Unit,
    onContinueWatching: () -> Unit,
    onSearch: (query: String, global: Boolean) -> Unit,
    onCoverClicked: () -> Unit,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onMigrateClicked: (() -> Unit)?,
    changeAnimeSkipIntro: (() -> Unit)?,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    onEpisodeSwipe: (EpisodeList.Item, LibraryPreferences.EpisodeSwipeAction) -> Unit,
    onEpisodeSelected: (EpisodeList.Item, Boolean, Boolean, Boolean) -> Unit,
    onAllEpisodeSelected: (Boolean) -> Unit,
    onInvertSelection: () -> Unit,
    onSeasonClicked: ((aniyomi.domain.anime.SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((aniyomi.domain.anime.SeasonAnime) -> Unit)?,
    onDubbingClicked: (() -> Unit)?,
    selectedDubbing: String?,
    onDownloadLongClick: ((Episode) -> Unit)?,
) {
    val anime = state.anime
    val episodes = state.episodeListItems
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }

    // State for episodes expansion
    var episodesExpanded by remember { mutableStateOf(false) }
    val episodesToShow = if (episodesExpanded) episodes else episodes.take(5)

    // State for description and genres expansion
    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }

    // Check if there are unseen episodes
    val hasUnseenEpisodes = remember(episodes) {
        episodes.any { (it as? EpisodeList.Item)?.episode?.seen == false }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Fixed background poster
        FullscreenPosterBackground(
            anime = anime,
            scrollOffset = scrollOffset,
            firstVisibleItemIndex = firstVisibleItemIndex,
        )

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = screenHeight,
                bottom = 100.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Info card (description and stats)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                AnimeInfoCard(
                    anime = anime,
                    episodeCount = episodes.size,
                    nextUpdate = nextUpdate,
                    onTagSearch = onTagSearch,
                    descriptionExpanded = descriptionExpanded,
                    genresExpanded = genresExpanded,
                    onToggleDescription = { descriptionExpanded = !descriptionExpanded },
                    onToggleGenres = { genresExpanded = !genresExpanded },
                )
            }

            // Action buttons card
            item {
                Spacer(modifier = Modifier.height(12.dp))
                AnimeActionCard(
                    anime = anime,
                    trackingCount = state.trackingCount,
                    onAddToLibraryClicked = onAddToLibraryClicked,
                    onWebViewClicked = onWebViewClicked,
                    onTrackingClicked = onTrackingClicked,
                    onShareClicked = onShareClicked,
                )
            }

            // Episodes header
            item {
                Spacer(modifier = Modifier.height(20.dp))
                EpisodesHeader(episodeCount = episodes.size)
            }

            // Empty state for episodes
            if (episodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(MR.strings.no_chapters_error),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            // Episode list
            items(
                items = episodesToShow,
                key = { (it as? EpisodeList.Item)?.episode?.id ?: it.hashCode() },
                contentType = { "episode" },
            ) { item ->
                if (item is EpisodeList.Item) {
                    AnimeEpisodeCardCompact(
                        anime = anime,
                        item = item,
                        onEpisodeClicked = { episode -> onEpisodeClicked(episode, false) },
                        onDownloadEpisode = onDownloadEpisode,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Show More button if there are more than 5 episodes
            if (episodes.size > 5) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.08f),
                                        ),
                                    ),
                                )
                                .clickable { episodesExpanded = !episodesExpanded }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(
                                text = if (episodesExpanded) {
                                    "Показать меньше"
                                } else {
                                    "Показать все ${episodes.size} эпизодов"
                                },
                                color = colors.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // Hero content (fixed at bottom of first screen) - fades out on scroll
        val heroThreshold = (screenHeight.value * 0.7f).toInt()
        if (firstVisibleItemIndex == 0 && scrollOffset < heroThreshold) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 0.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)

                Box(modifier = Modifier.graphicsLayer { alpha = heroAlpha }) {
                    AnimeHeroContent(
                        anime = anime,
                        episodeCount = episodes.size,
                        hasUnseenEpisodes = hasUnseenEpisodes,
                        onContinueWatching = onContinueWatching,
                        onDubbingClicked = onDubbingClicked,
                        selectedDubbing = selectedDubbing,
                    )
                }
            }
        }

        // Floating Play button (shows after Hero Content is hidden)
        val showFab = firstVisibleItemIndex > 0 || scrollOffset > heroThreshold
        if (showFab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 20.dp, bottom = 20.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                FloatingActionButton(
                    onClick = onContinueWatching,
                    containerColor = colors.accent,
                    contentColor = colors.textOnAccent,
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        // Top header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Back button
            IconButton(
                onClick = navigateUp,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = colors.accent,
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Filter button
            IconButton(
                onClick = onFilterButtonClicked,
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.accent.copy(alpha = 0.2f), CircleShape),
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = null,
                    tint = colors.accent,
                )
            }

            // Download menu
            if (onDownloadActionClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { downloadExpanded = !downloadExpanded },
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = null,
                            tint = colors.accent,
                        )
                    }
                    DropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false },
                    ) {
                        EntryDownloadDropdownMenu(
                            expanded = true,
                            onDismissRequest = { downloadExpanded = false },
                            onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                            isManga = false,
                        )
                    }
                }
            }

            // More menu
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showMenu = !showMenu },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.accent.copy(alpha = 0.2f), CircleShape),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = colors.accent,
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            androidx.compose.material3.Text(text = stringResource(MR.strings.action_webview_refresh))
                        },
                        onClick = {
                            onRefresh()
                            showMenu = false
                        },
                    )
                    if (onShareClicked != null) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { androidx.compose.material3.Text(text = stringResource(MR.strings.action_share)) },
                            onClick = {
                                onShareClicked()
                                showMenu = false
                            },
                        )
                    }
                }
            }
        }
    }
}
