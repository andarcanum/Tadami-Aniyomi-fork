package eu.kanade.presentation.entries.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SelectAll
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import aniyomi.domain.anime.SeasonAnime
import eu.kanade.domain.metadata.model.MetadataLoadError
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.EntryDownloadDropdownMenu
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.TitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.anime.components.AnimeSeasonListItem
import eu.kanade.presentation.entries.anime.components.AnimeSeasonSwitcherAurora
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.entries.anime.components.aurora.AnimeActionCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeEpisodeCardCompact
import eu.kanade.presentation.entries.anime.components.aurora.AnimeHeroContent
import eu.kanade.presentation.entries.anime.components.aurora.AnimeInfoCard
import eu.kanade.presentation.entries.anime.components.aurora.AnimeStatsCard
import eu.kanade.presentation.entries.anime.components.aurora.EpisodesHeader
import eu.kanade.presentation.entries.anime.components.aurora.FullscreenPosterBackground
import eu.kanade.presentation.entries.anime.components.aurora.resolveAnimeDetailsSnapshot
import eu.kanade.presentation.entries.anime.components.resolveAnimeSeasonSwitcherItems
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.AuroraEntryHoldToRefresh
import eu.kanade.presentation.entries.components.EntryBottomActionMenu
import eu.kanade.presentation.entries.components.MissingItemCountListItem
import eu.kanade.presentation.entries.components.ResolvedCover
import eu.kanade.presentation.entries.components.aurora.AuroraTitleHeroActionFab
import eu.kanade.presentation.entries.components.aurora.AuroraZIndex
import eu.kanade.presentation.entries.components.aurora.auroraPosterLongPress
import eu.kanade.presentation.entries.components.aurora.auroraSpringClick
import eu.kanade.presentation.entries.components.normalizeAuroraGlobalSearchQuery
import eu.kanade.presentation.entries.components.resolveExternalMetadataCover
import eu.kanade.presentation.entries.reduceTitleFastScrollOverlayAccumulator
import eu.kanade.presentation.entries.resolveEntryAutoJumpTargetIndex
import eu.kanade.presentation.entries.resolveTitleListFastScrollSpec
import eu.kanade.presentation.entries.shouldShowTitleFastScrollFloatingActionButton
import eu.kanade.presentation.entries.shouldShowTitleFastScrollOverlayChrome
import eu.kanade.presentation.entries.translation.rememberAuroraEntryTranslation
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.resolveAuroraAdaptiveSpec
import eu.kanade.presentation.util.formatEpisodeNumber
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreenModel
import eu.kanade.tachiyomi.ui.entries.anime.AnimeSeasonItem
import eu.kanade.tachiyomi.ui.entries.anime.EpisodeList
import eu.kanade.tachiyomi.util.debugTitleCoverFlow
import eu.kanade.tachiyomi.util.previewTitleCoverUrl
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.metadata.model.MetadataSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.TwoPanelBox
import tachiyomi.presentation.core.components.VerticalFastScroller
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
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
    onSuggestionClick: (eu.kanade.tachiyomi.data.suggestions.SuggestionItem) -> Unit,
    onCoverClicked: () -> Unit,
    onPosterLongClicked: (() -> Unit)? = onCoverClicked,
    onShareClicked: (() -> Unit)?,
    onDownloadActionClicked: ((DownloadAction) -> Unit)?,
    onEditCategoryClicked: (() -> Unit)?,
    onEditFetchIntervalClicked: (() -> Unit)?,
    onEditNotesClicked: (() -> Unit)?,
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
    isAutoJumpToNextEnabled: Boolean,
    autoJumpToNextLabel: String,
    onToggleAutoJumpToNext: () -> Unit,
    onClickEditInfo: (() -> Unit)? = null,
    onRetrySuggestions: () -> Unit = {},
    onOpenSuggestions: () -> Unit = {},
) {
    val anime = state.anime
    val sourcePreferences = remember { Injekt.get<SourcePreferences>() }
    val uiPreferences = remember { Injekt.get<UiPreferences>() }
    val entrySuggestionsEnabled by sourcePreferences.entrySuggestionsEnabled().collectAsState()
    val entrySuggestionsExpandInline by uiPreferences.entrySuggestionsExpandInline().collectAsState()
    val entrySuggestionsInOverflow by uiPreferences.entrySuggestionsInOverflow().collectAsState()
    val globalSearchQuery = remember(anime.displayTitle) { normalizeAuroraGlobalSearchQuery(anime.displayTitle) }
    val episodes = state.episodeListItems
    val selectedEpisodes = remember(episodes) {
        episodes.filterIsInstance<EpisodeList.Item>().filter { it.selected }
    }
    val isAnyEpisodeSelected = selectedEpisodes.isNotEmpty()
    val seasons = remember(state) { state.processedSeasons }
    val seasonSwitcherItems = remember(seasons) {
        resolveAnimeSeasonSwitcherItems(
            currentAnimeId = anime.id,
            seasons = seasons.map { it.seasonAnime },
        )
    }

    val distinctVirtualSeasons = remember(episodes) {
        val items = episodes.filterIsInstance<EpisodeList.Item>()
        if (items.isEmpty()) {
            emptyList<String>()
        } else {
            val seasonRegex = Regex("""(?i)(?:^|\b|\s|\[|_)(?:s|season\s*)(\d+)(?:\s|e|x|\||-|\.|\b|\]|_|$)""")
            val specialKeywordsRegex =
                Regex(
                    """(?i)\b(ova|oav|ona|movie|pv|trailer|bonus|recap|summary|prologue|extra|special|omake|teaser|clip|interview|preview)s?\b""",
                )
            val seasonsList = items.map { item ->
                val name = item.episode.name
                val num = item.episode.episodeNumber
                val match = seasonRegex.find(name)
                val explicitSeason = if (match != null) {
                    val sn = match.groupValues[1].toIntOrNull()
                    if (sn != null) "Season $sn" else null
                } else {
                    null
                }

                val isSpecial = num < 0 ||
                    specialKeywordsRegex.containsMatchIn(name) ||
                    !name.any { it.isDigit() } ||
                    (num > 0 && num < 1.0)

                if (explicitSeason != null) {
                    explicitSeason
                } else if (isSpecial) {
                    if (specialKeywordsRegex.containsMatchIn(name)) "Specials" else "Extras"
                } else {
                    "Season 1"
                }
            }.distinct()

            seasonsList.sortedWith(
                Comparator { s1, s2 ->
                    fun getPriority(s: String): Int {
                        return when {
                            s.startsWith("Season", ignoreCase = true) -> 0
                            s.contains("Extra", ignoreCase = true) -> 1
                            s.contains("Special", ignoreCase = true) -> 2
                            else -> 3
                        }
                    }
                    val p1 = getPriority(s1)
                    val p2 = getPriority(s2)
                    if (p1 != p2) {
                        p1.compareTo(p2)
                    } else {
                        val n1 = s1.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val n2 = s2.filter { it.isDigit() }.toIntOrNull() ?: 0
                        if (n1 != n2) {
                            n1.compareTo(n2)
                        } else {
                            s1.compareTo(s2)
                        }
                    }
                },
            )
        }
    }

    val episodeIdToVirtualSeason = remember(episodes) {
        val items = episodes.filterIsInstance<EpisodeList.Item>()
        if (items.isEmpty()) {
            emptyMap<Long, String>()
        } else {
            val seasonRegex = Regex("""(?i)(?:^|\b|\s|\[|_)(?:s|season\s*)(\d+)(?:\s|e|x|\||-|\.|\b|\]|_|$)""")
            val specialKeywordsRegex =
                Regex(
                    """(?i)\b(ova|oav|ona|movie|pv|trailer|bonus|recap|summary|prologue|extra|special|omake|teaser|clip|interview|preview)s?\b""",
                )
            items.associate { item ->
                val name = item.episode.name
                val num = item.episode.episodeNumber
                val match = seasonRegex.find(name)
                val explicitSeason = if (match != null) {
                    val sn = match.groupValues[1].toIntOrNull()
                    if (sn != null) "Season $sn" else null
                } else {
                    null
                }

                val isSpecial = num < 0 ||
                    specialKeywordsRegex.containsMatchIn(name) ||
                    !name.any { it.isDigit() } ||
                    (num > 0 && num < 1.0)

                val seasonName = if (explicitSeason != null) {
                    explicitSeason
                } else if (isSpecial) {
                    if (specialKeywordsRegex.containsMatchIn(name)) "Specials" else "Extras"
                } else {
                    "Season 1"
                }
                item.episode.id to seasonName
            }
        }
    }

    var selectedVirtualSeason by remember(distinctVirtualSeasons) {
        mutableStateOf(distinctVirtualSeasons.firstOrNull() ?: "")
    }

    val filteredEpisodes = remember(episodes, selectedVirtualSeason, episodeIdToVirtualSeason, distinctVirtualSeasons) {
        if (distinctVirtualSeasons.size <= 1) {
            episodes
        } else {
            episodes.filter { item ->
                when (item) {
                    is EpisodeList.Item -> episodeIdToVirtualSeason[item.episode.id] == selectedVirtualSeason
                    is EpisodeList.MissingCount -> true
                }
            }
        }
    }

    val realEpisodeCount = remember(filteredEpisodes) {
        filteredEpisodes.count { it is EpisodeList.Item }
    }
    val missingEpisodesCount = remember(filteredEpisodes) {
        filteredEpisodes.filterIsInstance<EpisodeList.MissingCount>().sumOf { it.count }
    }

    val colors = AuroraTheme.colors
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val auroraAdaptiveSpec = remember(isTabletUi, configuration.screenWidthDp) {
        resolveAuroraAdaptiveSpec(
            isTabletUi = isTabletUi,
            containerWidthDp = configuration.screenWidthDp,
        )
    }
    val contentMaxWidthDp = auroraAdaptiveSpec.entryMaxWidthDp
    val useTwoPaneLayout = shouldUseAnimeAuroraTwoPane(auroraAdaptiveSpec.deviceClass)

    val metadataSource: MetadataSource = remember {
        Injekt.get<eu.kanade.domain.ui.UiPreferences>().metadataSource().get()
    }

    val resolvedCover = remember(
        state.anime,
        state.isMetadataLoading,
        state.metadataError,
        state.animeMetadata,
        metadataSource,
    ) {
        resolveCoverUrl(state, metadataSource != MetadataSource.NONE)
    }
    val refererUrl = remember(state.source) {
        (state.source as? HttpSource)?.baseUrl
    }
    LaunchedEffect(
        anime.id,
        state.isMetadataLoading,
        state.metadataError,
        resolvedCover.coverUrl,
        resolvedCover.coverUrlFallback,
    ) {
        val debugMessage = "id=${anime.id} loading=${state.isMetadataLoading} " +
            "error=${state.metadataError?.javaClass?.simpleName ?: "none"} " +
            "base=${previewTitleCoverUrl(anime.thumbnailUrl)} " +
            "resolved=${previewTitleCoverUrl(resolvedCover.coverUrl)} " +
            "fallback=${previewTitleCoverUrl(resolvedCover.coverUrlFallback)} " +
            "referer=${previewTitleCoverUrl(refererUrl)}"
        debugTitleCoverFlow(
            scope = "anime-screen",
            message = debugMessage,
        )
    }

    val lazyListState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { lazyListState.firstVisibleItemScrollOffset } }
    val firstVisibleItemIndex by remember { derivedStateOf { lazyListState.firstVisibleItemIndex } }
    val context = LocalContext.current
    val animeDetailsSnapshot = remember(
        anime,
        episodes,
        selectedDubbing,
        nextUpdate,
        state.animeMetadata,
        state.sourceRating,
        state.isMetadataLoading,
        state.metadataError,
    ) {
        resolveAnimeDetailsSnapshot(
            anime = anime,
            watchedCount = state.episodes.count { it.episode.seen || it.episode.lastSecondSeen > 0L },
            totalEpisodes = state.episodes.size,
            sourceName = state.source.name,
            selectedDubbing = selectedDubbing,
            nextUpdate = nextUpdate,
            sourceRating = state.sourceRating,
            animeMetadata = state.animeMetadata,
            isMetadataLoading = state.isMetadataLoading,
            metadataError = state.metadataError,
            context = context,
        )
    }

    // State for episodes expansion
    var episodesExpanded by remember { mutableStateOf(false) }
    var isReverseScrollingOverlay by remember { mutableStateOf(false) }
    LaunchedEffect(episodesExpanded) {
        if (episodesExpanded) {
            isReverseScrollingOverlay = false
        }
    }
    var isThumbFastScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            Triple(
                lazyListState.isScrollInProgress,
                lazyListState.firstVisibleItemIndex,
                lazyListState.firstVisibleItemScrollOffset,
            )
        }
            .conflate()
            .runningFold(
                TitleFastScrollOverlayAccumulator(
                    prevIndex = lazyListState.firstVisibleItemIndex,
                    prevOffset = lazyListState.firstVisibleItemScrollOffset,
                    revealed = false,
                ),
            ) { current, (isScrolling, index, offset) ->
                reduceTitleFastScrollOverlayAccumulator(
                    current = current,
                    isExpandedList = episodesExpanded,
                    isScrolling = isScrolling,
                    index = index,
                    offset = offset,
                )
            }
            .map { it.revealed }
            .distinctUntilChanged()
            .collect { isReverseScrollingOverlay = it }
    }
    val episodesToShow = remember(filteredEpisodes, episodesExpanded) {
        if (episodesExpanded) {
            filteredEpisodes
        } else {
            val result = mutableListOf<EpisodeList>()
            var visibleEpisodes = 0
            for (item in filteredEpisodes) {
                if (item is EpisodeList.Item) {
                    if (visibleEpisodes >= 5) break
                    visibleEpisodes++
                }
                result += item
            }
            result
        }
    }
    val haptic = LocalHapticFeedback.current
    val showAnimeOverlayChrome by remember {
        derivedStateOf {
            shouldShowTitleFastScrollOverlayChrome(
                isThumbDragged = isThumbFastScrolling,
                isExpandedList = episodesExpanded,
                isReverseScrolling = isReverseScrollingOverlay,
            )
        }
    }

    // Auto-scroll to target episode on initial load
    var hasScrolledToTarget: Boolean by remember { mutableStateOf(false) }
    LaunchedEffect(state.targetEpisodeIndex, isAutoJumpToNextEnabled) {
        val targetIndex = resolveEntryAutoJumpTargetIndex(
            enabled = isAutoJumpToNextEnabled,
            targetIndex = state.targetEpisodeIndex,
            restoredScrollIndex = state.scrollIndex,
        )
        if (targetIndex != null) {
            episodesExpanded = true
        }
    }

    LaunchedEffect(state.targetEpisodeIndex, episodesExpanded) {
        if (!hasScrolledToTarget && episodesExpanded) {
            hasScrolledToTarget = true
            val targetIndex = resolveEntryAutoJumpTargetIndex(
                enabled = isAutoJumpToNextEnabled,
                targetIndex = state.targetEpisodeIndex,
                restoredScrollIndex = state.scrollIndex,
            )
            if (targetIndex != null) {
                val fastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = useTwoPaneLayout,
                )
                lazyListState.animateScrollToItem(targetIndex + fastScrollBlockStartIndex)
            }
        }
    }

    BackHandler(onBack = {
        if (isAnyEpisodeSelected) {
            onAllEpisodeSelected(false)
        } else {
            navigateUp()
        }
    })

    LaunchedEffect(isAnyEpisodeSelected, episodesExpanded, filteredEpisodes.size) {
        if (isAnyEpisodeSelected &&
            shouldAutoExpandAuroraEpisodesList(
                episodesExpanded = episodesExpanded,
                totalEpisodes = filteredEpisodes.size,
            )
        ) {
            episodesExpanded = true
        }
    }

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
        if (state.metadataError == MetadataLoadError.NotAuthenticated &&
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

    // Check if user already has any watching progress
    val hasWatchingProgress = remember(episodes) {
        episodes.any { episodeItem ->
            val episode = (episodeItem as? EpisodeList.Item)?.episode ?: return@any false
            episode.seen || episode.lastSecondSeen > 0L
        }
    }
    val auroraTranslationPreferences = remember { Injekt.get<UiPreferences>() }
    val auroraEntryTranslationEnabled by auroraTranslationPreferences
        .auroraEntryTranslationEnabled()
        .collectAsState()
    val auroraEntryTranslationSourceLanguages by auroraTranslationPreferences
        .auroraEntryTranslationSourceLanguages()
        .collectAsState()
    val auroraEntryTranslation = rememberAuroraEntryTranslation(
        title = anime.displayTitle,
        description = anime.displayDescription,
        sourceLanguage = state.source.lang,
        enabled = auroraEntryTranslationEnabled,
        allowedSourceFamilies = auroraEntryTranslationSourceLanguages,
    )

    AuroraEntryHoldToRefresh(
        refreshing = state.isRefreshingData,
        onRefresh = onRefresh,
        enabled = !isAnyEpisodeSelected,
        modifier = Modifier.fillMaxSize(),
        indicatorPadding = WindowInsets.statusBars.asPaddingValues(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .auroraPosterLongPress(onPosterLongClicked ?: onCoverClicked),
        ) {
            // Fixed background poster
            FullscreenPosterBackground(
                anime = anime,
                scrollOffset = scrollOffset,
                firstVisibleItemIndex = firstVisibleItemIndex,
                resolvedCoverUrl = resolvedCover.coverUrl,
                resolvedCoverUrlFallback = resolvedCover.coverUrlFallback,
                refererUrl = refererUrl,
            )

            if (useTwoPaneLayout) {
                val topContentPadding = 96.dp
                val paneDensity = LocalDensity.current
                val paneTopPaddingPx = with(paneDensity) { topContentPadding.roundToPx() }
                val paneFastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = true,
                )
                val paneFastScrollSpec by remember(paneTopPaddingPx, paneFastScrollBlockStartIndex) {
                    derivedStateOf {
                        resolveTitleListFastScrollSpec(
                            baseTopPaddingPx = paneTopPaddingPx,
                            firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                            blockStartIndex = paneFastScrollBlockStartIndex,
                            blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == paneFastScrollBlockStartIndex }
                                ?.offset
                                ?.plus(with(paneDensity) { ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                        )
                    }
                }
                TwoPanelBox(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(AuroraZIndex.HERO),
                    startContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 12.dp, end = 6.dp, top = topContentPadding, bottom = 20.dp)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .auroraCenteredMaxWidth(420)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        alignment = Alignment.TopStart,
                                    ),
                            ) {
                                AnimeHeroContent(
                                    anime = anime,
                                    translation = auroraEntryTranslation,
                                    hasWatchingProgress = hasWatchingProgress,
                                    ratingText = animeDetailsSnapshot.ratingText,
                                    episodeCount = state.episodes.size,
                                    statusText = animeDetailsSnapshot.statusText,
                                    note = anime.notes,
                                    onEditNotesClicked = onEditNotesClicked,
                                    onContinueWatching = onContinueWatching,
                                    onDubbingClicked = onDubbingClicked,
                                    selectedDubbing = selectedDubbing,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AnimeStatsCard(
                                    snapshot = animeDetailsSnapshot,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AnimeInfoCard(
                                    anime = anime,
                                    translation = auroraEntryTranslation,
                                    onTagSearch = onTagSearch,
                                    descriptionExpanded = descriptionExpanded,
                                    genresExpanded = genresExpanded,
                                    onToggleDescription = {
                                        descriptionExpanded = !descriptionExpanded
                                    },
                                    onToggleGenres = { genresExpanded = !genresExpanded },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                AnimeActionCard(
                                    anime = anime,
                                    trackingCount = state.trackingCount,
                                    onAddToLibraryClicked = onAddToLibraryClicked,
                                    onAddToLibraryLongClicked = onEditCategoryClicked,
                                    onWebViewClicked = onWebViewClicked,
                                    onTrackingClicked = onTrackingClicked,
                                    onShareClicked = onShareClicked,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    },
                    endContent = {
                        VerticalFastScroller(
                            listState = lazyListState,
                            onThumbDragStarted = {
                                val shouldExpand =
                                    shouldAutoExpandAuroraEpisodesListForFastScroll(
                                        episodesExpanded,
                                        episodes.size,
                                    )
                                if (shouldExpand) {
                                    episodesExpanded = true
                                }
                            },
                            onThumbDragStateChanged = { isThumbFastScrolling = it },
                            thumbAllowed = { paneFastScrollSpec.thumbAllowed },
                            topContentPadding = with(paneDensity) { paneFastScrollSpec.topPaddingPx.toDp() },
                            endContentPadding = 12.dp,
                            modifier = Modifier.zIndex(AuroraZIndex.BASE),
                        ) {
                            LazyColumn(
                                state = lazyListState,
                                contentPadding = PaddingValues(top = topContentPadding, bottom = 100.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 6.dp, end = 12.dp),
                            ) {
                                item {
                                    if (seasons.size > 1) {
                                        AnimeSeasonRailAurora(
                                            anime = anime,
                                            seasons = seasons,
                                            onSeasonClicked = onSeasonClicked,
                                            onContinueWatchingClicked = onContinueWatchingClicked,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }

                                item {
                                    EpisodesHeader(
                                        itemCount = if (state.anime.fetchType == FetchType.Seasons) {
                                            seasons.size
                                        } else {
                                            filteredEpisodes.size
                                        },
                                        fetchType = state.anime.fetchType,
                                    )
                                }

                                item {
                                    if (seasonSwitcherItems.size > 1) {
                                        AnimeSeasonSwitcherAurora(
                                            items = seasonSwitcherItems,
                                            onSeasonClicked = { onSeasonClicked?.invoke(it) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }

                                item {
                                    if (distinctVirtualSeasons.size > 1) {
                                        VirtualSeasonSwitcherAurora(
                                            seasons = distinctVirtualSeasons,
                                            selectedSeason = selectedVirtualSeason,
                                            onSeasonClicked = { selectedVirtualSeason = it },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }

                                if (state.anime.fetchType == FetchType.Seasons) {
                                    items(
                                        items = seasons,
                                        key = { season -> season.seasonAnime.anime.id },
                                        contentType = { "season" },
                                    ) { item ->
                                        AnimeSeasonListItem(
                                            anime = anime,
                                            item = item,
                                            containerHeight = screenHeight.value.toInt(),
                                            onSeasonClicked = { onSeasonClicked?.invoke(it) },
                                            onClickContinueWatching = onContinueWatchingClicked,
                                            listItemModifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                    }
                                } else {
                                    if (missingEpisodesCount > 0) {
                                        item(key = "missing-episodes-warning") {
                                            MissingItemCountListItem(count = missingEpisodesCount)
                                        }
                                    }

                                    if (realEpisodeCount == 0) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(32.dp),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = stringResource(MR.strings.no_chapters_error),
                                                    color = colors.textPrimary.copy(alpha = 0.7f),
                                                    fontSize = 14.sp,
                                                )
                                            }
                                        }
                                    }

                                    items(
                                        items = episodesToShow,
                                        key = { (it as? EpisodeList.Item)?.episode?.id ?: it.hashCode() },
                                        contentType = {
                                            when (it) {
                                                is EpisodeList.Item -> "episode"
                                                is EpisodeList.MissingCount -> "missing-count"
                                            }
                                        },
                                    ) { item ->
                                        when (item) {
                                            is EpisodeList.Item -> {
                                                AnimeEpisodeCardCompact(
                                                    anime = anime,
                                                    item = item,
                                                    selected = item.selected,
                                                    isNew = item.episode.id in state.newEpisodeIds,
                                                    isAnyEpisodeSelected = isAnyEpisodeSelected,
                                                    episodeSwipeStartAction = episodeSwipeStartAction,
                                                    episodeSwipeEndAction = episodeSwipeEndAction,
                                                    showPreviews = state.showPreviews,
                                                    showSummaries = state.showSummaries,
                                                    onClick = {
                                                        when (
                                                            resolveAuroraEpisodeClickAction(
                                                                isEpisodeSelected = item.selected,
                                                                isAnyEpisodeSelected = isAnyEpisodeSelected,
                                                            )
                                                        ) {
                                                            AuroraEpisodeClickAction.OpenEpisode -> {
                                                                onEpisodeClicked(item.episode, false)
                                                            }
                                                            AuroraEpisodeClickAction.SelectEpisode -> {
                                                                onEpisodeSelected(item, true, true, false)
                                                                if (shouldAutoExpandAuroraEpisodesList(
                                                                        episodesExpanded,
                                                                        episodes.size,
                                                                    )
                                                                ) {
                                                                    episodesExpanded = true
                                                                }
                                                            }
                                                            AuroraEpisodeClickAction.UnselectEpisode -> {
                                                                onEpisodeSelected(item, false, true, false)
                                                            }
                                                        }
                                                    },
                                                    onLongClick = {
                                                        onEpisodeSelected(item, !item.selected, true, true)
                                                        val shouldExpand = shouldAutoExpandAuroraEpisodesList(
                                                            episodesExpanded,
                                                            episodes.size,
                                                        )
                                                        if (!item.selected && shouldExpand) {
                                                            episodesExpanded = true
                                                        }
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    },
                                                    onEpisodeSwipe = { action -> onEpisodeSwipe(item, action) },
                                                    onDownloadEpisode = onDownloadEpisode,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                                )
                                            }
                                            is EpisodeList.MissingCount -> {
                                                MissingItemCountListItem(count = item.count)
                                            }
                                        }
                                    }
                                }

                                if (realEpisodeCount > 5) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            AuroraEpisodeListToggleButton(
                                                text = if (episodesExpanded) {
                                                    stringResource(AYMR.strings.action_show_less)
                                                } else {
                                                    stringResource(
                                                        AYMR.strings.action_show_all_episodes,
                                                        realEpisodeCount,
                                                    )
                                                },
                                                onClick = { episodesExpanded = !episodesExpanded },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            } else {
                // Scrollable content
                val density = LocalDensity.current
                val fastScrollBaseTopPadding =
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp
                val fastScrollBaseTopPaddingPx = with(density) { fastScrollBaseTopPadding.roundToPx() }
                val fastScrollBlockStartIndex = resolveAnimeAuroraFastScrollBlockStartIndex(
                    useTwoPaneLayout = false,
                )
                val fastScrollSpec by remember(fastScrollBaseTopPaddingPx, fastScrollBlockStartIndex) {
                    derivedStateOf {
                        resolveTitleListFastScrollSpec(
                            baseTopPaddingPx = fastScrollBaseTopPaddingPx,
                            firstVisibleItemIndex = lazyListState.firstVisibleItemIndex,
                            blockStartIndex = fastScrollBlockStartIndex,
                            blockStartOffsetPx = lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == fastScrollBlockStartIndex }
                                ?.offset
                                ?.plus(with(density) { ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET.roundToPx() }),
                        )
                    }
                }
                VerticalFastScroller(
                    listState = lazyListState,
                    onThumbDragStarted = {
                        if (shouldAutoExpandAuroraEpisodesListForFastScroll(episodesExpanded, episodes.size)) {
                            episodesExpanded = true
                        }
                    },
                    onThumbDragStateChanged = { isThumbFastScrolling = it },
                    thumbAllowed = { fastScrollSpec.thumbAllowed },
                    topContentPadding = with(density) { fastScrollSpec.topPaddingPx.toDp() },
                    bottomContentPadding = 100.dp,
                    modifier = Modifier.zIndex(AuroraZIndex.BASE),
                ) {
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
                                    .auroraCenteredMaxWidth(contentMaxWidthDp)
                                    .animateContentSize(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessLow,
                                        ),
                                        alignment = Alignment.TopStart,
                                    ),
                            ) {
                                Spacer(modifier = Modifier.height(16.dp))
                                AnimeStatsCard(
                                    snapshot = animeDetailsSnapshot,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AnimeInfoCard(
                                    anime = anime,
                                    translation = auroraEntryTranslation,
                                    onTagSearch = onTagSearch,
                                    descriptionExpanded = descriptionExpanded,
                                    genresExpanded = genresExpanded,
                                    onToggleDescription = {
                                        descriptionExpanded = !descriptionExpanded
                                    },
                                    onToggleGenres = {
                                        genresExpanded = !genresExpanded
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                AnimeActionCard(
                                    anime = anime,
                                    trackingCount = state.trackingCount,
                                    onAddToLibraryClicked = onAddToLibraryClicked,
                                    onAddToLibraryLongClicked = onEditCategoryClicked,
                                    onWebViewClicked = onWebViewClicked,
                                    onTrackingClicked = onTrackingClicked,
                                    onShareClicked = onShareClicked,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item(
                            key = "anime-aurora-season-rail",
                            contentType = "anime-aurora-season-rail",
                        ) {
                            if (seasons.size > 1) {
                                AnimeSeasonRailAurora(
                                    anime = anime,
                                    seasons = seasons,
                                    onSeasonClicked = onSeasonClicked,
                                    onContinueWatchingClicked = onContinueWatchingClicked,
                                    modifier = Modifier
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }

                        if (entrySuggestionsEnabled) {
                            if (entrySuggestionsExpandInline) {
                                item(key = "suggestions_row") {
                                    eu.kanade.presentation.entries.components.aurora.AuroraSuggestionsRow(
                                        state = state.suggestions,
                                        onSuggestionClick = onSuggestionClick,
                                        onOpenSuggestions = onOpenSuggestions,
                                        onRetryClick = onRetrySuggestions,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .auroraCenteredMaxWidth(contentMaxWidthDp),
                                    )
                                }
                            } else if (!entrySuggestionsInOverflow) {
                                item(key = "suggestions_button") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .auroraCenteredMaxWidth(contentMaxWidthDp)
                                            .padding(horizontal = 16.dp, vertical = 6.dp)
                                            .background(
                                                brush = Brush.linearGradient(
                                                    colors = if (colors.isDark) {
                                                        listOf(
                                                            Color.White.copy(alpha = 0.12f),
                                                            Color.White.copy(alpha = 0.08f),
                                                        )
                                                    } else {
                                                        listOf(
                                                            colors.accent.copy(alpha = 0.15f),
                                                            colors.accent.copy(alpha = 0.10f),
                                                        )
                                                    },
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                            )
                                            .auroraSpringClick(onClick = onOpenSuggestions)
                                            .padding(vertical = 12.dp, horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(MR.strings.suggestions_similar_titles),
                                            color = if (colors.isDark) Color.White else colors.accent,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }

                        item(
                            key = ANIME_AURORA_EPISODES_HEADER_KEY,
                            contentType = ANIME_AURORA_EPISODES_HEADER_KEY,
                        ) {
                            Spacer(modifier = Modifier.height(20.dp))
                            EpisodesHeader(
                                itemCount = if (state.anime.fetchType == FetchType.Seasons) {
                                    seasons.size
                                } else {
                                    filteredEpisodes.size
                                },
                                fetchType = state.anime.fetchType,
                                modifier = Modifier.auroraCenteredMaxWidth(contentMaxWidthDp),
                            )
                        }

                        item(
                            key = "anime-aurora-seasons-switcher",
                            contentType = "anime-aurora-seasons-switcher",
                        ) {
                            if (seasonSwitcherItems.size > 1) {
                                AnimeSeasonSwitcherAurora(
                                    items = seasonSwitcherItems,
                                    onSeasonClicked = { onSeasonClicked?.invoke(it) },
                                    modifier = Modifier
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }

                        item(
                            key = "anime-aurora-virtual-seasons-switcher",
                            contentType = "anime-aurora-virtual-seasons-switcher",
                        ) {
                            if (distinctVirtualSeasons.size > 1) {
                                VirtualSeasonSwitcherAurora(
                                    seasons = distinctVirtualSeasons,
                                    selectedSeason = selectedVirtualSeason,
                                    onSeasonClicked = { selectedVirtualSeason = it },
                                    modifier = Modifier
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                )
                            }
                        }

                        if (state.anime.fetchType == FetchType.Seasons) {
                            items(
                                items = seasons,
                                key = { season -> season.seasonAnime.anime.id },
                                contentType = { "season" },
                            ) { item ->
                                AnimeSeasonListItem(
                                    anime = anime,
                                    item = item,
                                    containerHeight = screenHeight.value.toInt(),
                                    onSeasonClicked = { onSeasonClicked?.invoke(it) },
                                    onClickContinueWatching = onContinueWatchingClicked,
                                    listItemModifier = Modifier
                                        .fillMaxWidth()
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                )
                            }
                        } else {
                            if (missingEpisodesCount > 0) {
                                item(key = "missing-episodes-warning") {
                                    MissingItemCountListItem(count = missingEpisodesCount)
                                }
                            }

                            // Empty state for episodes
                            if (realEpisodeCount == 0) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .auroraCenteredMaxWidth(contentMaxWidthDp)
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
                                contentType = {
                                    when (it) {
                                        is EpisodeList.Item -> "episode"
                                        is EpisodeList.MissingCount -> "missing-count"
                                    }
                                },
                            ) { item ->
                                when (item) {
                                    is EpisodeList.Item -> {
                                        AnimeEpisodeCardCompact(
                                            anime = anime,
                                            item = item,
                                            selected = item.selected,
                                            isNew = item.episode.id in state.newEpisodeIds,
                                            isAnyEpisodeSelected = isAnyEpisodeSelected,
                                            episodeSwipeStartAction = episodeSwipeStartAction,
                                            episodeSwipeEndAction = episodeSwipeEndAction,
                                            showPreviews = state.showPreviews,
                                            showSummaries = state.showSummaries,
                                            onClick = {
                                                when (
                                                    resolveAuroraEpisodeClickAction(
                                                        isEpisodeSelected = item.selected,
                                                        isAnyEpisodeSelected = isAnyEpisodeSelected,
                                                    )
                                                ) {
                                                    AuroraEpisodeClickAction.OpenEpisode -> {
                                                        onEpisodeClicked(item.episode, false)
                                                    }
                                                    AuroraEpisodeClickAction.SelectEpisode -> {
                                                        onEpisodeSelected(item, true, true, false)
                                                        val shouldExpand = shouldAutoExpandAuroraEpisodesList(
                                                            episodesExpanded,
                                                            episodes.size,
                                                        )
                                                        if (shouldExpand) {
                                                            episodesExpanded = true
                                                        }
                                                    }
                                                    AuroraEpisodeClickAction.UnselectEpisode -> {
                                                        onEpisodeSelected(item, false, true, false)
                                                    }
                                                }
                                            },
                                            onLongClick = {
                                                onEpisodeSelected(item, !item.selected, true, true)
                                                if (!item.selected &&
                                                    shouldAutoExpandAuroraEpisodesList(
                                                        episodesExpanded,
                                                        episodes.size,
                                                    )
                                                ) {
                                                    episodesExpanded = true
                                                }
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                            onEpisodeSwipe = { action -> onEpisodeSwipe(item, action) },
                                            onDownloadEpisode = onDownloadEpisode,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .auroraCenteredMaxWidth(contentMaxWidthDp)
                                                .padding(horizontal = 16.dp, vertical = 4.dp),
                                        )
                                    }
                                    is EpisodeList.MissingCount -> {
                                        MissingItemCountListItem(count = item.count)
                                    }
                                }
                            }
                        }

                        // Show More button if there are more than 5 episodes
                        if (realEpisodeCount > 5) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AuroraEpisodeListToggleButton(
                                        text = if (episodesExpanded) {
                                            stringResource(AYMR.strings.action_show_less)
                                        } else {
                                            stringResource(
                                                AYMR.strings.action_show_all_episodes,
                                                realEpisodeCount,
                                            )
                                        },
                                        onClick = { episodesExpanded = !episodesExpanded },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Hero content (fixed at bottom of first screen) - fades out on scroll
            val heroThreshold = (screenHeight.value * 0.7f).toInt()
            if (
                shouldShowAnimeAuroraHeroContent(
                    useTwoPaneLayout = useTwoPaneLayout,
                    firstVisibleItemIndex = firstVisibleItemIndex,
                    scrollOffset = scrollOffset,
                    heroThreshold = heroThreshold,
                    isSelectionMode = isAnyEpisodeSelected,
                )
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .zIndex(AuroraZIndex.HERO)
                        .padding(bottom = 0.dp),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    val heroAlpha = (1f - (scrollOffset / heroThreshold.toFloat())).coerceIn(0f, 1f)

                    Box(
                        modifier = Modifier
                            .zIndex(AuroraZIndex.HERO)
                            .graphicsLayer { alpha = heroAlpha },
                    ) {
                        AnimeHeroContent(
                            anime = anime,
                            translation = auroraEntryTranslation,
                            hasWatchingProgress = hasWatchingProgress,
                            ratingText = animeDetailsSnapshot.ratingText,
                            episodeCount = state.episodes.size,
                            statusText = animeDetailsSnapshot.statusText,
                            note = anime.notes,
                            onEditNotesClicked = onEditNotesClicked,
                            onContinueWatching = onContinueWatching,
                            onDubbingClicked = onDubbingClicked,
                            selectedDubbing = selectedDubbing,
                        )
                    }
                }
            }

            // Floating Play button (shows after Hero Content is hidden)
            val showFab = firstVisibleItemIndex > 0 || scrollOffset > heroThreshold
            val shouldShowFab = !useTwoPaneLayout && showFab && !isAnyEpisodeSelected
            if (shouldShowTitleFastScrollFloatingActionButton(shouldShowFab, isThumbFastScrolling)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(AuroraZIndex.HERO)
                        .padding(end = 20.dp, bottom = 20.dp),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    AuroraTitleHeroActionFab(
                        onClick = onContinueWatching,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }

            val overlayChromeAlpha by animateFloatAsState(
                targetValue = if (!isAnyEpisodeSelected && showAnimeOverlayChrome) 1f else 0f,
                label = "overlayChromeAlpha",
            )
            val overlayChromeOffsetY by animateFloatAsState(
                targetValue = if (!isAnyEpisodeSelected && showAnimeOverlayChrome) 0f else -1f,
                label = "overlayChromeOffsetY",
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(AuroraZIndex.HERO)
                    .graphicsLayer {
                        alpha = overlayChromeAlpha
                        translationY = overlayChromeOffsetY * size.height
                    }
                    .padding(WindowInsets.statusBars.asPaddingValues())
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Back button - Aurora glassmorphism style
                AuroraActionButton(
                    onClick = navigateUp,
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
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
                            useAuroraStyle = true,
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
                    AuroraEntryDropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        AuroraEntryDropdownMenuItem(
                            text = stringResource(MR.strings.action_webview_refresh),
                            onClick = {
                                onRefresh()
                                showMenu = false
                            },
                        )
                        AuroraEntryDropdownMenuItem(
                            text = autoJumpToNextLabel,
                            onClick = {
                                onToggleAutoJumpToNext()
                                showMenu = false
                            },
                        )
                        if (globalSearchQuery != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_global_search),
                                onClick = {
                                    onSearch(globalSearchQuery, true)
                                    showMenu = false
                                },
                            )
                        }
                        if (entrySuggestionsEnabled && entrySuggestionsInOverflow) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.pref_entry_suggestions),
                                onClick = {
                                    onOpenSuggestions()
                                    showMenu = false
                                },
                            )
                        }
                        if (onMigrateClicked != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_migrate),
                                onClick = {
                                    onMigrateClicked()
                                    showMenu = false
                                },
                            )
                        }
                        if (onShareClicked != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_share),
                                onClick = {
                                    onShareClicked()
                                    showMenu = false
                                },
                            )
                        }
                        if (onEditNotesClicked != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_notes),
                                onClick = {
                                    onEditNotesClicked()
                                    showMenu = false
                                },
                            )
                        }
                        if (onClickEditInfo != null) {
                            AuroraEntryDropdownMenuItem(
                                text = stringResource(MR.strings.action_edit_info),
                                onClick = {
                                    onClickEditInfo()
                                    showMenu = false
                                },
                            )
                        }
                        if (onSettingsClicked != null) {
                            AuroraEntryDropdownMenuItem(
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

            AuroraEpisodeSelectionBottomStack(
                selected = selectedEpisodes,
                onSelectAll = { onAllEpisodeSelected(true) },
                onInvertSelection = onInvertSelection,
                onCancel = { onAllEpisodeSelected(false) },
                onEpisodeClicked = onEpisodeClicked,
                onMultiBookmarkClicked = onMultiBookmarkClicked,
                onMultiFillermarkClicked = onMultiFillermarkClicked,
                onMultiMarkAsSeenClicked = onMultiMarkAsSeenClicked,
                onMarkPreviousAsSeenClicked = onMarkPreviousAsSeenClicked,
                onDownloadEpisode = onDownloadEpisode,
                onMultiDeleteClicked = onMultiDeleteClicked,
                fillFraction = if (
                    auroraSelectionControlsPlacement() == AuroraSelectionControlsPlacement.BottomStack &&
                    useTwoPaneLayout
                ) {
                    0.5f
                } else {
                    1f
                },
                alwaysUseExternalPlayer = alwaysUseExternalPlayer,
                modifier = Modifier
                    .align(if (useTwoPaneLayout) Alignment.BottomEnd else Alignment.BottomCenter)
                    .zIndex(AuroraZIndex.SELECTION)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(AuroraZIndex.SNACKBAR)
                    .padding(WindowInsets.systemBars.asPaddingValues()),
            )
        }
    }
}

@Composable
private fun AuroraSelectionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        colors.accent.copy(alpha = 0.2f),
                        colors.accent.copy(alpha = 0.1f),
                    ),
                ),
            )
            .clickable(onClick = onClick)
            .padding(7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.textPrimary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun AuroraEpisodeSelectionBottomStack(
    selected: List<EpisodeList.Item>,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onCancel: () -> Unit,
    onEpisodeClicked: (Episode, Boolean) -> Unit,
    onMultiBookmarkClicked: (List<Episode>, bookmarked: Boolean) -> Unit,
    onMultiFillermarkClicked: (List<Episode>, fillermarked: Boolean) -> Unit,
    onMultiMarkAsSeenClicked: (List<Episode>, markAsSeen: Boolean) -> Unit,
    onMarkPreviousAsSeenClicked: (Episode) -> Unit,
    onDownloadEpisode: ((List<EpisodeList.Item>, EpisodeDownloadAction) -> Unit)?,
    onMultiDeleteClicked: (List<Episode>) -> Unit,
    fillFraction: Float,
    alwaysUseExternalPlayer: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(fillFraction)) {
        if (selected.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AuroraTheme.colors.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(AuroraTheme.colors.accent.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = selected.size.toString(),
                        color = AuroraTheme.colors.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.SelectAll,
                    contentDescription = stringResource(MR.strings.action_select_all),
                    onClick = onSelectAll,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Filled.SwapHoriz,
                    contentDescription = stringResource(MR.strings.action_select_inverse),
                    onClick = onInvertSelection,
                )
                AuroraSelectionIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(MR.strings.action_cancel),
                    onClick = onCancel,
                )
            }
        }

        EntryBottomActionMenu(
            visible = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.bookmark } },
            onRemoveBookmarkClicked = {
                onMultiBookmarkClicked(selected.map { it.episode }, false)
            }.takeIf { selected.all { it.episode.bookmark } },
            onFillermarkClicked = {
                onMultiFillermarkClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.fillermark } },
            onRemoveFillermarkClicked = {
                onMultiFillermarkClicked(selected.map { it.episode }, false)
            }.takeIf { selected.all { it.episode.fillermark } },
            onMarkAsViewedClicked = {
                onMultiMarkAsSeenClicked(selected.map { it.episode }, true)
            }.takeIf { selected.any { !it.episode.seen } },
            onMarkAsUnviewedClicked = {
                onMultiMarkAsSeenClicked(selected.map { it.episode }, false)
            }.takeIf { selected.any { it.episode.seen || it.episode.lastSecondSeen > 0L } },
            onMarkPreviousAsViewedClicked = {
                onMarkPreviousAsSeenClicked(selected.first().episode)
            }.takeIf { selected.size == 1 },
            onDownloadClicked = {
                onDownloadEpisode!!(selected, EpisodeDownloadAction.START)
            }.takeIf {
                onDownloadEpisode != null && selected.any { it.downloadState != AnimeDownload.State.DOWNLOADED }
            },
            onDeleteClicked = {
                onMultiDeleteClicked(selected.map { it.episode })
            }.takeIf {
                onDownloadEpisode != null && selected.any { it.downloadState == AnimeDownload.State.DOWNLOADED }
            },
            onExternalClicked = {
                onEpisodeClicked(selected.first().episode, true)
            }.takeIf { !alwaysUseExternalPlayer && selected.size == 1 },
            onInternalClicked = {
                onEpisodeClicked(selected.first().episode, true)
            }.takeIf { alwaysUseExternalPlayer && selected.size == 1 },
            isManga = false,
        )
    }
}

@Composable
private fun AnimeSeasonRailAurora(
    anime: Anime,
    seasons: List<AnimeSeasonItem>,
    onSeasonClicked: ((SeasonAnime) -> Unit)?,
    onContinueWatchingClicked: ((SeasonAnime) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (seasons.size <= 1) return

    val currentAnimeId = anime.id
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        seasons
            .sortedBy { it.seasonAnime.anime.seasonNumber }
            .forEach { item ->
                val onOpenSeason: () -> Unit = {
                    if (onSeasonClicked != null) {
                        onSeasonClicked(item.seasonAnime)
                    }
                }
                Box(
                    modifier = Modifier.width(132.dp),
                ) {
                    EntryComfortableGridItem(
                        isSelected = item.seasonAnime.anime.id == currentAnimeId,
                        title = item.seasonAnime.anime.title.ifBlank {
                            stringResource(
                                AYMR.strings.display_mode_season,
                                formatEpisodeNumber(item.seasonAnime.anime.seasonNumber),
                            )
                        },
                        onClick = onOpenSeason,
                        onLongClick = onOpenSeason,
                        coverData = AnimeCover(
                            animeId = item.seasonAnime.anime.id,
                            sourceId = item.seasonAnime.anime.source,
                            isAnimeFavorite = item.seasonAnime.anime.favorite,
                            url = item.seasonAnime.anime.thumbnailUrl,
                            lastModified = item.seasonAnime.anime.coverLastModified,
                        ),
                        onClickContinueViewing = if (onContinueWatchingClicked != null && item.showContinueOverlay) {
                            { onContinueWatchingClicked(item.seasonAnime) }
                        } else {
                            null
                        },
                    )
                }
            }
    }
}

internal fun shouldUseAnimeAuroraTwoPane(deviceClass: AuroraDeviceClass): Boolean {
    return deviceClass == AuroraDeviceClass.TabletExpanded
}

internal fun shouldUseAnimeAuroraPaneScopedFastScroller(useTwoPaneLayout: Boolean): Boolean {
    return useTwoPaneLayout
}

internal fun shouldShowAnimeAuroraHeroContent(
    useTwoPaneLayout: Boolean,
    firstVisibleItemIndex: Int,
    scrollOffset: Int,
    heroThreshold: Int,
    isSelectionMode: Boolean,
): Boolean {
    if (useTwoPaneLayout || isSelectionMode) return false
    return firstVisibleItemIndex == 0 && scrollOffset < heroThreshold
}

internal fun resolveAnimeAuroraFastScrollBlockStartIndex(
    useTwoPaneLayout: Boolean,
): Int {
    return if (useTwoPaneLayout) 1 else 3
}

private const val ANIME_AURORA_EPISODES_HEADER_KEY = "anime-aurora-episodes-header"
private val ANIME_AURORA_FAST_SCROLL_ITEM_TOP_INSET = 6.dp
internal enum class AuroraEpisodeClickAction {
    OpenEpisode,
    SelectEpisode,
    UnselectEpisode,
}

internal enum class AuroraSelectionControlsPlacement {
    BottomStack,
}

internal fun auroraSelectionControlsPlacement(): AuroraSelectionControlsPlacement {
    return AuroraSelectionControlsPlacement.BottomStack
}

internal fun shouldUseCompactAuroraSelectionActions(): Boolean {
    return true
}

internal fun resolveAuroraEpisodeClickAction(
    isEpisodeSelected: Boolean,
    isAnyEpisodeSelected: Boolean,
): AuroraEpisodeClickAction {
    return when {
        isEpisodeSelected -> AuroraEpisodeClickAction.UnselectEpisode
        isAnyEpisodeSelected -> AuroraEpisodeClickAction.SelectEpisode
        else -> AuroraEpisodeClickAction.OpenEpisode
    }
}

internal fun shouldAutoExpandAuroraEpisodesList(
    episodesExpanded: Boolean,
    totalEpisodes: Int,
): Boolean {
    return !episodesExpanded && totalEpisodes > 5
}

internal fun shouldAutoExpandAuroraEpisodesListForFastScroll(
    episodesExpanded: Boolean,
    totalEpisodes: Int,
): Boolean {
    return shouldAutoExpandAuroraEpisodesList(
        episodesExpanded = episodesExpanded,
        totalEpisodes = totalEpisodes,
    )
}

internal fun resolveCoverUrl(
    state: AnimeScreenModel.State.Success,
    useMetadataCovers: Boolean,
): ResolvedCover {
    return resolveExternalMetadataCover(
        baseCoverUrl = state.anime.thumbnailUrl.orEmpty(),
        metadata = state.animeMetadata,
        isMetadataLoading = state.isMetadataLoading,
        metadataError = state.metadataError,
        useMetadataCovers = useMetadataCovers,
    )
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

@Composable
fun VirtualSeasonSwitcherAurora(
    seasons: List<String>,
    selectedSeason: String,
    onSeasonClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier.horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        seasons.forEach { season ->
            val isSelected = season == selectedSeason
            val activeBgColors = listOf(
                colors.accent.copy(alpha = 0.24f),
                colors.accent.copy(alpha = 0.12f),
            )
            val inactiveBgColors = listOf(
                colors.surface.copy(alpha = 0.4f),
                colors.surface.copy(alpha = 0.15f),
            )
            val currentBgColors = if (isSelected) activeBgColors else inactiveBgColors

            val activeBorderColor = colors.accent.copy(alpha = 0.6f)
            val inactiveBorderColor = colors.divider.copy(alpha = 0.25f)
            val currentBorderColor = if (isSelected) activeBorderColor else inactiveBorderColor

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(brush = Brush.linearGradient(colors = currentBgColors))
                    .border(
                        width = 1.dp,
                        color = currentBorderColor,
                        shape = RoundedCornerShape(100.dp),
                    )
                    .auroraSpringClick { onSeasonClicked(season) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = getLocalizedSeasonLabel(season),
                    color = if (isSelected) colors.textPrimary else colors.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun getLocalizedSeasonLabel(season: String): String {
    val isRussian = LocalContext.current.resources.configuration.locales[0].language == "ru"
    return when {
        season.startsWith("Season ", ignoreCase = true) -> {
            val num = season.substringAfter("Season ").trim()
            stringResource(AYMR.strings.display_mode_season, num)
        }
        season.equals("Specials", ignoreCase = true) -> {
            if (isRussian) "Спешлы" else "Specials"
        }
        season.equals("Extras", ignoreCase = true) -> {
            if (isRussian) "Экстры" else "Extras"
        }
        else -> season
    }
}

@Composable
private fun AuroraEpisodeListToggleButton(
    text: String,
    onClick: () -> Unit,
) {
    val colors = AuroraTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = if (colors.isDark) {
                        listOf(
                            Color.White.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.08f),
                        )
                    } else {
                        listOf(
                            colors.surface.copy(alpha = 0.55f),
                            colors.surface.copy(alpha = 0.30f),
                        )
                    },
                ),
                shape = shape,
            )
            .auroraSpringClick(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            color = colors.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
