package eu.kanade.presentation.entries.anime

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.kanade.domain.ui.model.AnimeMetadataSource
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
import kotlinx.coroutines.launch
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
    onRetryMetadata: () -> Unit,
    onSettingsClicked: (() -> Unit)?,
) {
    val anime = state.anime
    val episodes = state.episodeListItems
    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    // Get the metadata source preference to determine cover URL
    val metadataSource = remember {
        Injekt.get<eu.kanade.domain.ui.UiPreferences>().animeMetadataSource().get()
    }

    val resolvedCover = remember(
        state.anime,
        state.isMetadataLoading,
        state.metadataError,
        state.animeMetadata,
        metadataSource,
    ) {
        resolveCoverUrl(state, metadataSource != eu.kanade.domain.ui.model.AnimeMetadataSource.NONE)
    }

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val statsBringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()

    // State for episodes expansion
    var episodesExpanded by remember { mutableStateOf(false) }
    val episodesToShow = if (episodesExpanded) episodes else episodes.take(5)

    // State for description and genres expansion
    var descriptionExpanded by remember { mutableStateOf(false) }
    var genresExpanded by remember { mutableStateOf(false) }

    // Check if metadata auth hint was already shown (persistent)
    val metadataAuthHintShown = remember {
        Injekt.get<eu.kanade.domain.ui.UiPreferences>().metadataAuthHintShown()
    }
    var metadataHintDismissed by remember { mutableStateOf(false) }

    // One-time Snackbar when metadata source is not authenticated
    LaunchedEffect(state.metadataError) {
        if (state.metadataError == AnimeScreenModel.MetadataError.NotAuthenticated &&
            !metadataAuthHintShown.get() &&
            !metadataHintDismissed &&
            onTrackingClicked != null
        ) {
            val result = snackbarHostState.showSnackbar(
                message = "Авторизуйтесь в сервисе для рейтинга, типа и обложки",
                actionLabel = "Войти",
                withDismissAction = true, // Add dismiss button
                duration = SnackbarDuration.Long,
            )
            when (result) {
                SnackbarResult.ActionPerformed -> {
                    onTrackingClicked.invoke()
                    metadataAuthHintShown.set(true) // Don't show again
                }
                SnackbarResult.Dismissed -> {
                    metadataHintDismissed = true
                    metadataAuthHintShown.set(true) // Don't show again
                }
            }
        }
    }

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
            resolvedCoverUrl = resolvedCover.url,
            resolvedCoverUrlFallback = resolvedCover.fallbackUrl,
        )

        // Scrollable content
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = 100.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Spacer for poster/hero area
            item {
                Spacer(modifier = Modifier.height(screenHeight))
            }

            // Info and Action cards merged into one item for layout stability
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            alignment = Alignment.TopStart,
                        ),
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    AnimeInfoCard(
                        anime = anime,
                        episodeCount = episodes.size,
                        nextUpdate = nextUpdate,
                        onTagSearch = onTagSearch,
                        descriptionExpanded = descriptionExpanded,
                        genresExpanded = genresExpanded,
                        onToggleDescription = {
                            descriptionExpanded = !descriptionExpanded
                            if (descriptionExpanded) {
                                coroutineScope.launch {
                                    statsBringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        onToggleGenres = {
                            genresExpanded = !genresExpanded
                        },
                        animeMetadata = state.animeMetadata,
                        isMetadataLoading = state.isMetadataLoading,
                        metadataError = state.metadataError,
                        onRetryMetadata = onRetryMetadata,
                        onLoginClick = {
                            onTrackingClicked?.invoke()
                        },

                        statsRequester = statsBringIntoViewRequester,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    AnimeActionCard(
                        anime = anime,
                        trackingCount = state.trackingCount,
                        onAddToLibraryClicked = onAddToLibraryClicked,
                        onWebViewClicked = onWebViewClicked,
                        onTrackingClicked = onTrackingClicked,
                        onShareClicked = onShareClicked,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                        animeMetadata = state.animeMetadata,
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
            // Back button - Aurora glassmorphism style
            AuroraActionButton(
                onClick = navigateUp,
                icon = Icons.Filled.ArrowBack,
                contentDescription = null,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Filter button - Aurora glassmorphism style
            AuroraActionButton(
                onClick = onFilterButtonClicked,
                icon = Icons.Default.FilterList,
                contentDescription = null,
            )

            // Download menu - Aurora glassmorphism style
            if (onDownloadActionClicked != null) {
                var downloadExpanded by remember { mutableStateOf(false) }
                Box(contentAlignment = Alignment.TopEnd) {
                    AuroraActionButton(
                        onClick = { downloadExpanded = !downloadExpanded },
                        icon = Icons.Filled.Download,
                        contentDescription = null,
                    )
                    EntryDownloadDropdownMenu(
                        expanded = downloadExpanded,
                        onDismissRequest = { downloadExpanded = false },
                        onDownloadClicked = { onDownloadActionClicked.invoke(it) },
                        isManga = false,
                    )
                }
            }

            // More menu - Aurora glassmorphism style
            var showMenu by remember { mutableStateOf(false) }
            Box(contentAlignment = Alignment.TopEnd) {
                AuroraActionButton(
                    onClick = { showMenu = !showMenu },
                    icon = Icons.Default.MoreVert,
                    contentDescription = null,
                )
                AuroraDropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    AuroraDropdownMenuItem(
                        text = stringResource(MR.strings.action_webview_refresh),
                        onClick = {
                            onRefresh()
                            showMenu = false
                        },
                    )
                    if (onShareClicked != null) {
                        AuroraDropdownMenuItem(
                            text = stringResource(MR.strings.action_share),
                            onClick = {
                                onShareClicked()
                                showMenu = false
                            },
                        )
                    }
                    if (onSettingsClicked != null) {
                        AuroraDropdownMenuItem(
                            text = stringResource(MR.strings.action_settings),
                            onClick = {
                                onSettingsClicked()
                                showMenu = false
                            },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.systemBars.asPaddingValues()),
        )
    }
}

data class ResolvedCover(
    val url: String?,
    val fallbackUrl: String?,
)

private fun resolveCoverUrl(
    state: AnimeScreenModel.State.Success,
    useMetadataCovers: Boolean,
): ResolvedCover {
    if (!useMetadataCovers) {
        return ResolvedCover(state.anime.thumbnailUrl, null)
    }

    if (state.isMetadataLoading) {
        return ResolvedCover(null, null)
    }

    val metadataCoverUrl = state.animeMetadata?.coverUrl?.takeIf { it.isNotBlank() }
    val metadataCoverUrlFallback = state.animeMetadata?.coverUrlFallback?.takeIf { it.isNotBlank() }

    return when (state.metadataError) {
        null -> {
            if (metadataCoverUrl != null) {
                ResolvedCover(metadataCoverUrl, metadataCoverUrlFallback ?: state.anime.thumbnailUrl)
            } else {
                ResolvedCover(state.anime.thumbnailUrl, null)
            }
        }
        AnimeScreenModel.MetadataError.NetworkError,
        AnimeScreenModel.MetadataError.NotFound,
        AnimeScreenModel.MetadataError.NotAuthenticated,
        AnimeScreenModel.MetadataError.Disabled,
        -> ResolvedCover(state.anime.thumbnailUrl, null)
    }
}

/**
 * Aurora-styled action button with glassmorphism effect
 */
@Composable
private fun AuroraActionButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.surface.copy(alpha = 0.9f),
                        colors.surface.copy(alpha = 0.6f),
                    ),
                    center = Offset(0.3f, 0.3f),
                    radius = 0.8f,
                ),
            )
            .drawBehind {
                // Subtle inner glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.accent.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.3f),
                        radius = size.width * 0.6f,
                    ),
                )
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.accent.copy(alpha = 0.95f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Aurora-styled dropdown menu container
 */
@Composable
private fun AuroraDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = AuroraTheme.colors

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        modifier = modifier,
    ) {
        content()
    }
}

/**
 * Aurora-styled dropdown menu item
 */
@Composable
private fun AuroraDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors

    androidx.compose.material3.DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = colors.textPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        },
        onClick = onClick,
        modifier = modifier,
        colors = androidx.compose.material3.MenuDefaults.itemColors(
            textColor = colors.textPrimary,
        ),
    )
}
