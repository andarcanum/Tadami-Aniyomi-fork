package eu.kanade.tachiyomi.ui.home
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHeroMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionStoreScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionStoreScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionStoreScreen
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.data.discovery.CompositeTrendingSource
import eu.kanade.tachiyomi.data.discovery.DiscoveryLibraryAdder
import eu.kanade.tachiyomi.data.discovery.DiscoveryMeta
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.discovery.DiscoveryDirectOpenResolver
import eu.kanade.tachiyomi.ui.discovery.DiscoveryFeedScreen
import eu.kanade.tachiyomi.ui.discovery.DiscoveryPreviewSheet
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.suggestions.toDirectEntryScreenOrNull
import eu.kanade.tachiyomi.ui.entries.suggestions.toGlobalSearchScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.core.common.i18n.stringResource as contextStringResource

/**
 * Показывает разовые уведомления Home Hub: модели эмитят типизированные эффекты
 * ([BaseHomeHubScreenModel.effects]) вместо прямого Toast, потому что пайплайны подборки
 * работают на Dispatchers.IO, а Toast требует Looper на вызывающем потоке.
 */
@Composable
private fun HomeHubEffects(screenModel: BaseHomeHubScreenModel) {
    val context = LocalContext.current
    val poolLimitedMessage = stringResource(AYMR.strings.for_you_limited_pool)
    val refreshFailedMessage = stringResource(AYMR.strings.for_you_refresh_failed)
    LaunchedEffect(screenModel) {
        screenModel.effects.collect { effect ->
            when (effect) {
                HomeHubEffect.DiscoveryPoolLimited -> context.toast(poolLimitedMessage)
            }
        }
    }
    LaunchedEffect(screenModel) {
        val discoveryPreferences = Injekt.get<eu.kanade.domain.discovery.service.DiscoveryPreferences>()
        screenModel.discoveryRefreshFailures.collect { pending ->
            pending.forEach { (mediaType, requestId) ->
                if (discoveryPreferences.claimManualRefreshFailure(mediaType, requestId)) {
                    context.toast(refreshFailedMessage)
                }
                screenModel.acknowledgeDiscoveryRefreshFailure(mediaType, requestId)
            }
        }
    }
}

@Composable
internal fun AnimeHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    heroMode: HomeHeroMode,
    hiddenSnackbar: String? = null,
    onUndoHidden: () -> Unit = {},
    onDiscoveryLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onDiscoveryBlacklistTag: ((HomeHubDiscoveryItem, String, Int) -> Unit)? = null,
    onDiscoveryItemFocused: ((HomeHubDiscoveryItem) -> Unit)? = null,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
    providedScreenModel: HomeHubScreenModel? = null,
) {
    val screenModel = providedScreenModel ?: HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
    HomeHubEffects(screenModel)
    val state by screenModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel, activeSection) {
        HomeHubScreenModel.setInstance(screenModel)
        if (activeSection == HomeHubSection.Anime) {
            screenModel.startLiveUpdates()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeSection) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activeSection == HomeHubSection.Anime) {
                    screenModel.onScreenReentry()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(activeSection, tabNavigator.current) {
        if (activeSection == HomeHubSection.Anime && tabNavigator.current is HomeHubTab) {
            if (isFirstComposition) {
                isFirstComposition = false
            } else {
                screenModel.onScreenReentry()
            }
        }
    }

    var lastSourceId by remember(activeSection) {
        mutableLongStateOf(
            if (activeSection == HomeHubSection.Anime) screenModel.getLastUsedAnimeSourceId() else -1L,
        )
    }
    var lastSourceName by remember(activeSection) {
        mutableStateOf<String?>(
            if (activeSection ==
                HomeHubSection.Anime
            ) {
                screenModel.getLastUsedAnimeSourceName()
            } else {
                null
            },
        )
    }
    val availableSources = state.availableSources

    HomeHubScreen(
        section = HomeHubSection.Anime,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state,
        searchQuery = searchQuery,
        sourceId = lastSourceId,
        sourceName = lastSourceName,
        availableSources = availableSources,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        heroMode = heroMode,
        hiddenSnackbar = hiddenSnackbar,
        onUndoHidden = onUndoHidden,
        onDiscoveryLongClick = onDiscoveryLongClick,
        onDiscoveryBlacklistTag = onDiscoveryBlacklistTag,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(AnimeScreen(it)) },
        openDirectEntry = { navigator.push(AnimeScreen(it, fromSource = true)) },
        onPlayHero = { screenModel.playHeroEpisode(context) },
        onSearchClick = { query ->
            val sourceId = screenModel.getLastUsedAnimeSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseAnimeSourceScreen(sourceId, query))
            } else {
                navigator.push(GlobalAnimeSearchScreen(query))
            }
        },
        onOpenCatalog = { sourceId ->
            if (sourceId != -1L) {
                navigator.push(BrowseAnimeSourceScreen(sourceId, null))
            } else {
                navigator.push(GlobalAnimeSearchScreen())
            }
        },
        onSelectSource = { selectedId, selectedName ->
            lastSourceId = selectedId
            lastSourceName = selectedName
            screenModel.setLastUsedAnimeSourceId(selectedId)
        },
        onBrowseClick = { navigator.push(AnimeExtensionStoreScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showAnimeExtension()
        },
        onHistoryClick = {
            HistoriesTab.showAnimeSection()
            navigator.push(HistoriesTab)
        },
        onLibraryClick = { tabNavigator.current = AnimeLibraryTab },
        onForYouMoreClick = { navigator.push(DiscoveryFeedScreen(DiscoveryMediaType.ANIME.key)) },
        onDiscoveryRefreshClick = { screenModel.rotateOrRefreshDiscovery() },
        onDiscoveryItemFocused = onDiscoveryItemFocused ?: { screenModel.markDiscoveryItemShown(it) },
        onDiscoveryLikeClick = { screenModel.likeDiscoveryItem(it) },
        onDiscoveryItemClick = { item ->
            scope.launch {
                val suggestionItem = item.toSuggestionItem()
                navigator.push(suggestionItem.toDirectEntryScreenOrNull() ?: suggestionItem.toGlobalSearchScreen())
            }
        },
    )
}

@Composable
internal fun MangaHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    heroMode: HomeHeroMode,
    hiddenSnackbar: String? = null,
    onUndoHidden: () -> Unit = {},
    onDiscoveryLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onDiscoveryBlacklistTag: ((HomeHubDiscoveryItem, String, Int) -> Unit)? = null,
    onDiscoveryItemFocused: ((HomeHubDiscoveryItem) -> Unit)? = null,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
    providedScreenModel: MangaHomeHubScreenModel? = null,
) {
    val screenModel = providedScreenModel ?: HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
    HomeHubEffects(screenModel)
    val state by screenModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel, activeSection) {
        MangaHomeHubScreenModel.setInstance(screenModel)
        if (activeSection == HomeHubSection.Manga) {
            screenModel.startLiveUpdates()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeSection) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activeSection == HomeHubSection.Manga) {
                    screenModel.onScreenReentry()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(activeSection, tabNavigator.current) {
        if (activeSection == HomeHubSection.Manga && tabNavigator.current is HomeHubTab) {
            if (isFirstComposition) {
                isFirstComposition = false
            } else {
                screenModel.onScreenReentry()
            }
        }
    }

    var lastSourceId by remember(activeSection) {
        mutableLongStateOf(
            if (activeSection == HomeHubSection.Manga) screenModel.getLastUsedMangaSourceId() else -1L,
        )
    }
    var lastSourceName by remember(activeSection) {
        mutableStateOf<String?>(
            if (activeSection ==
                HomeHubSection.Manga
            ) {
                screenModel.getLastUsedMangaSourceName()
            } else {
                null
            },
        )
    }
    val availableSources = state.availableSources

    HomeHubScreen(
        section = HomeHubSection.Manga,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state,
        searchQuery = searchQuery,
        sourceId = lastSourceId,
        sourceName = lastSourceName,
        availableSources = availableSources,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        heroMode = heroMode,
        hiddenSnackbar = hiddenSnackbar,
        onUndoHidden = onUndoHidden,
        onDiscoveryLongClick = onDiscoveryLongClick,
        onDiscoveryBlacklistTag = onDiscoveryBlacklistTag,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(MangaScreen(it)) },
        openDirectEntry = { navigator.push(MangaScreen(it, fromSource = true)) },
        onPlayHero = { screenModel.readHeroChapter(context) },
        onSearchClick = { query ->
            val sourceId = screenModel.getLastUsedMangaSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseMangaSourceScreen(sourceId, query))
            } else {
                navigator.push(GlobalMangaSearchScreen(query))
            }
        },
        onOpenCatalog = { sourceId ->
            if (sourceId != -1L) {
                navigator.push(BrowseMangaSourceScreen(sourceId, null))
            } else {
                navigator.push(GlobalMangaSearchScreen())
            }
        },
        onSelectSource = { selectedId, selectedName ->
            lastSourceId = selectedId
            lastSourceName = selectedName
            screenModel.setLastUsedMangaSourceId(selectedId)
        },
        onBrowseClick = { navigator.push(MangaExtensionStoreScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showExtension()
        },
        onHistoryClick = {
            HistoriesTab.showMangaSection()
            navigator.push(HistoriesTab)
        },
        onLibraryClick = {
            scope.launch { AnimeLibraryTab.showMangaSection() }
            tabNavigator.current = AnimeLibraryTab
        },
        onForYouMoreClick = { navigator.push(DiscoveryFeedScreen(DiscoveryMediaType.MANGA.key)) },
        onDiscoveryRefreshClick = { screenModel.rotateOrRefreshDiscovery() },
        onDiscoveryItemFocused = onDiscoveryItemFocused ?: { screenModel.markDiscoveryItemShown(it) },
        onDiscoveryLikeClick = { screenModel.likeDiscoveryItem(it) },
        onDiscoveryItemClick = { item ->
            scope.launch {
                val suggestionItem = item.toSuggestionItem()
                navigator.push(suggestionItem.toDirectEntryScreenOrNull() ?: suggestionItem.toGlobalSearchScreen())
            }
        },
    )
}

@Composable
internal fun NovelHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    heroMode: HomeHeroMode,
    hiddenSnackbar: String? = null,
    onUndoHidden: () -> Unit = {},
    onDiscoveryLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onDiscoveryBlacklistTag: ((HomeHubDiscoveryItem, String, Int) -> Unit)? = null,
    onDiscoveryItemFocused: ((HomeHubDiscoveryItem) -> Unit)? = null,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
    providedScreenModel: NovelHomeHubScreenModel? = null,
) {
    val screenModel = providedScreenModel ?: HomeHubTab.rememberScreenModel { NovelHomeHubScreenModel() }
    HomeHubEffects(screenModel)
    val state by screenModel.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.currentOrThrow
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel, activeSection) {
        NovelHomeHubScreenModel.setInstance(screenModel)
        if (activeSection == HomeHubSection.Novel) {
            screenModel.startLiveUpdates()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeSection) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activeSection == HomeHubSection.Novel) {
                    screenModel.onScreenReentry()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isFirstComposition by remember { mutableStateOf(true) }
    LaunchedEffect(activeSection, tabNavigator.current) {
        if (activeSection == HomeHubSection.Novel && tabNavigator.current is HomeHubTab) {
            if (isFirstComposition) {
                isFirstComposition = false
            } else {
                screenModel.onScreenReentry()
            }
        }
    }

    var lastSourceId by remember(activeSection) {
        mutableLongStateOf(
            if (activeSection == HomeHubSection.Novel) screenModel.getLastUsedNovelSourceId() else -1L,
        )
    }
    var lastSourceName by remember(activeSection) {
        mutableStateOf<String?>(
            if (activeSection ==
                HomeHubSection.Novel
            ) {
                screenModel.getLastUsedNovelSourceName()
            } else {
                null
            },
        )
    }
    val availableSources = state.availableSources

    HomeHubScreen(
        section = HomeHubSection.Novel,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state,
        searchQuery = searchQuery,
        sourceId = lastSourceId,
        sourceName = lastSourceName,
        availableSources = availableSources,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        heroMode = heroMode,
        hiddenSnackbar = hiddenSnackbar,
        onUndoHidden = onUndoHidden,
        onDiscoveryLongClick = onDiscoveryLongClick,
        onDiscoveryBlacklistTag = onDiscoveryBlacklistTag,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(NovelScreen(it)) },
        openDirectEntry = { navigator.push(NovelScreen(it, fromSource = true)) },
        onPlayHero = {
            screenModel.getHeroChapterId()?.let { chapterId ->
                navigator.push(NovelReaderScreen(chapterId))
            }
        },
        onSearchClick = { query ->
            val sourceId = screenModel.getLastUsedNovelSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseNovelSourceScreen(sourceId, query))
            } else {
                navigator.push(GlobalNovelSearchScreen(query))
            }
        },
        onOpenCatalog = { sourceId ->
            if (sourceId != -1L) {
                navigator.push(BrowseNovelSourceScreen(sourceId, null))
            } else {
                navigator.push(GlobalNovelSearchScreen())
            }
        },
        onSelectSource = { selectedId, selectedName ->
            lastSourceId = selectedId
            lastSourceName = selectedName
            screenModel.setLastUsedNovelSourceId(selectedId)
        },
        onBrowseClick = { navigator.push(NovelExtensionStoreScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showNovelExtension()
        },
        onHistoryClick = {
            HistoriesTab.showNovelSection()
            navigator.push(HistoriesTab)
        },
        onLibraryClick = {
            scope.launch { AnimeLibraryTab.showNovelSection() }
            tabNavigator.current = AnimeLibraryTab
        },
        onForYouMoreClick = { navigator.push(DiscoveryFeedScreen(DiscoveryMediaType.NOVEL.key)) },
        onDiscoveryRefreshClick = { screenModel.rotateOrRefreshDiscovery() },
        onDiscoveryItemFocused = onDiscoveryItemFocused ?: { screenModel.markDiscoveryItemShown(it) },
        onDiscoveryLikeClick = { screenModel.likeDiscoveryItem(it) },
        onDiscoveryItemClick = { item ->
            scope.launch {
                val suggestionItem = item.toSuggestionItem()
                navigator.push(suggestionItem.toDirectEntryScreenOrNull() ?: suggestionItem.toGlobalSearchScreen())
            }
        },
    )
}

@Composable
private fun HomeHubScreen(
    section: HomeHubSection,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
    state: HomeHubUiState,
    searchQuery: String?,
    sourceId: Long,
    sourceName: String?,
    availableSources: List<HomeSourceItem>,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    heroMode: HomeHeroMode,
    hiddenSnackbar: String? = null,
    onUndoHidden: () -> Unit = {},
    onDiscoveryLongClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onDiscoveryBlacklistTag: ((HomeHubDiscoveryItem, String, Int) -> Unit)? = null,
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onSearchClick: (String) -> Unit,
    onOpenCatalog: (Long) -> Unit,
    onSelectSource: (Long, String?) -> Unit,
    onBrowseClick: () -> Unit,
    onExtensionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onForYouMoreClick: () -> Unit,
    onDiscoveryRefreshClick: (() -> Unit)? = null,
    onDiscoveryItemFocused: (HomeHubDiscoveryItem) -> Unit = {},
    onDiscoveryLikeClick: ((HomeHubDiscoveryItem) -> Unit)? = null,
    onDiscoveryItemClick: (HomeHubDiscoveryItem) -> Unit,
    // Direct open тизера: пушит экран тайтла своего медиатипа (fromSource = true).
    openDirectEntry: ((Long) -> Unit)? = null,
) {
    val trimmedQuery = searchQuery?.trim().orEmpty()
    val filteredContent = remember(
        state.hero,
        state.history,
        state.recommendations,
        state.discovery,
        state.discoveryPool,
        trimmedQuery,
    ) {
        resolveHomeHubFilteredContent(
            hero = state.hero,
            history = state.history,
            recommendations = state.recommendations,
            discovery = state.discovery,
            discoveryPool = state.discoveryPool,
            query = trimmedQuery,
        )
    }
    val isFiltering = filteredContent.isFiltering

    // Home hub should open from the top after app relaunch; avoid saveable scroll restoration.
    val listState = remember(section) { LazyListState() }
    LaunchedEffect(section, activeSection, scrollResetToken) {
        if (section == activeSection) {
            listState.scrollToItem(0)
        }
    }
    val nestedScrollConnection = remember(section, activeSection, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (section != activeSection) return Offset.Zero
                if (available.y != 0f) {
                    val isAtTop = listState.firstVisibleItemIndex == 0 &&
                        listState.firstVisibleItemScrollOffset == 0
                    onScrollSignal(section, available.y, isAtTop)
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(section, activeSection, listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { isAtTop ->
            if (section == activeSection && isAtTop) {
                onScrollSignal(section, 0f, true)
            }
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewItem by remember { mutableStateOf<HomeHubDiscoveryItem?>(null) }
    var previewMeta by remember { mutableStateOf<DiscoveryMeta?>(null) }
    var previewMetaLoading by remember { mutableStateOf(false) }
    var longPressItem by remember { mutableStateOf<HomeHubDiscoveryItem?>(null) }
    var openingTeaser by remember { mutableStateOf<HomeHubDiscoveryItem?>(null) }
    val trendingSource = remember { CompositeTrendingSource() }
    val directOpenResolver = remember { DiscoveryDirectOpenResolver() }

    // Direct open для Home-тизеров: plugin-bound карточка открывает экран тайтла
    // напрямую (materialize по sourceId/sourceUrl); неполная привязка, неустановленный
    // источник или любая ошибка деградирует в прежнюю шторку предпросмотра.
    // Guard повторного тапа: openingTeaser сбрасывается в finally (в т.ч. при отмене).
    val tasteRepository = remember {
        Injekt.get<tachiyomi.domain.discovery.repository.DiscoveryRepository>()
    }
    val openTeaser: (HomeHubDiscoveryItem) -> Unit = openDirectEntry?.let { openEntry ->
        { item ->
            if (openingTeaser != item) {
                // Taste Engine: клик по тизеру = слабый позитивный сигнал (guard исключает дубль).
                scope.launch {
                    eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder.record(
                        tasteRepository,
                        item.toDiscoverySuggestion(),
                        tachiyomi.domain.discovery.model.DiscoverySignalType.CLICK,
                    )
                }
                scope.launch {
                    openingTeaser = item
                    val entryId = try {
                        directOpenResolver.resolveEntryId(item.toDiscoverySuggestion())
                    } finally {
                        openingTeaser = null
                    }
                    if (entryId != null) {
                        openEntry(entryId)
                    } else {
                        previewItem = item
                    }
                }
            }
        }
    } ?: { item -> previewItem = item }

    LaunchedEffect(previewItem) {
        val item = previewItem ?: return@LaunchedEffect
        previewMeta = null
        previewMetaLoading = true
        previewMeta = runCatching { trendingSource.fetchMeta(item.title, item.mediaType) }.getOrNull()
        previewMetaLoading = false
    }

    val hero = filteredContent.hero
    val history = filteredContent.history
    val recommendations = filteredContent.recommendations
    val discovery = filteredContent.discovery
    // Hero (Stage/Collage) рендерит пул целиком: решение режима, рендер слота и
    // scroll-эвристика считаются от пула, а не от тизерного окна — иначе пустой
    // на миг тизер деградировал hero в Continue и обратно («мигание» режима).
    val heroFeedItems = resolveStageItems(filteredContent.discoveryPool, discovery)
    val showWelcome = (state.showWelcome || state.showFilteredEmpty) && !isFiltering
    val reserveHeroSlot = shouldReserveHomeHubHeroSlot(
        hasHero = state.hero != null,
        isLoading = state.isLoading,
        showWelcome = showWelcome,
        isFiltering = isFiltering,
    )
    val heroPresentation = resolveHeroPresentation(
        prefMode = heroMode,
        discoveryEnabled = state.discoveryEnabled,
        discoveryCount = heroFeedItems.size,
    )
    // Коллаж и гибрид уже отображают «Для тебя» в слоте героя; для них нижний дублирующий ряд не нужен.
    val forYouItems = resolveForYouItems(
        heroPresentation = heroPresentation,
        hasHero = hero != null,
        discovery = discovery,
    )
    // В Collage/Stage hero-слот занят «Для тебя»: возвращаем последний прочитанный
    // тайтл первым элементом ряда «Недавно просмотренные» (без визуального выделения —
    // пользователь и так знает, что читал последним).
    val historyItems = remember(history, hero, heroPresentation, section) {
        prependLastReadHero(hero, history, heroPresentation, section)
    }
    // Считается после historyItems: scroll-эвристика обязана видеть ряд вместе
    // с возвращённым последним прочитанным (иначе расхождение с рендером).
    val enableScroll = shouldEnableHomeHubScroll(
        showWelcome = showWelcome,
        historyCount = historyItems.size,
        recommendationCount = recommendations.size,
        discoveryCount = heroFeedItems.size,
    )

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (enableScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
            contentPadding = contentPadding,
            userScrollEnabled = enableScroll,
        ) {
            if (showWelcome) {
                item(key = "welcome", contentType = "home_hub_welcome") {
                    WelcomeSection(onBrowseClick = onBrowseClick, onExtensionClick = onExtensionClick)
                }
                // Онбординг не должен лишать discovery: тизер под приветственным блоком.
                if (discovery.isNotEmpty()) {
                    item(key = "for_you_welcome", contentType = "home_hub_for_you") {
                        ForYouSection(
                            items = discovery,
                            coverMediaType = section.toDiscoveryMediaType(),
                            onMoreClick = onForYouMoreClick,
                            onItemClick = { openTeaser(it) },
                            onLongClick = { longPressItem = it },
                        )
                    }
                }
            } else {
                if (
                    shouldRenderHomeHubHeroSlot(
                        heroPresentation = heroPresentation,
                        hasHero = hero != null,
                        reserveHeroSlot = reserveHeroSlot,
                        hasDiscovery = heroFeedItems.isNotEmpty(),
                    )
                ) {
                    item(key = "hero", contentType = "home_hub_hero") {
                        when {
                            // Ветка stage идёт первой: при наличии hero она обязана перебивать HeroSection,
                            // иначе режим не отрисуется никогда.
                            heroPresentation == HomeHeroMode.Stage -> DiscoveryHeroStage(
                                items = heroFeedItems,
                                coverMediaType = section.toDiscoveryMediaType(),
                                onMoreClick = onForYouMoreClick,
                                onItemClick = { openTeaser(it) },
                                onLongClick = { longPressItem = it },
                                onItemFocused = onDiscoveryItemFocused,
                            )
                            heroPresentation == HomeHeroMode.Collage -> DiscoveryHeroCollage(
                                items = heroFeedItems,
                                coverMediaType = section.toDiscoveryMediaType(),
                                onMoreClick = onForYouMoreClick,
                                onItemClick = { openTeaser(it) },
                                onLongClick = { longPressItem = it },
                                onItemFocused = onDiscoveryItemFocused,
                            )
                            heroPresentation == HomeHeroMode.Hybrid && hero != null -> Column {
                                HeroSection(
                                    hero = hero,
                                    section = section,
                                    ctaMode = heroCtaMode,
                                    compact = true,
                                    onPlayClick = onPlayHero,
                                    onEntryClick = { onEntryClick(hero.entryId) },
                                )
                                HybridDiscoveryStrip(
                                    items = discovery,
                                    coverMediaType = section.toDiscoveryMediaType(),
                                    onMoreClick = onForYouMoreClick,
                                    onItemClick = { openTeaser(it) },
                                    onLongClick = { longPressItem = it },
                                    isRefreshing = state.isDiscoveryRefreshing,
                                    onRefreshClick = onDiscoveryRefreshClick,
                                )
                            }
                            heroPresentation == HomeHeroMode.Hybrid && hero == null -> HybridDiscoveryStrip(
                                items = discovery,
                                coverMediaType = section.toDiscoveryMediaType(),
                                onMoreClick = onForYouMoreClick,
                                onItemClick = { openTeaser(it) },
                                onLongClick = { longPressItem = it },
                                isRefreshing = state.isDiscoveryRefreshing,
                                onRefreshClick = onDiscoveryRefreshClick,
                            )
                            hero != null -> HeroSection(
                                hero = hero,
                                section = section,
                                ctaMode = heroCtaMode,
                                onPlayClick = onPlayHero,
                                onEntryClick = { onEntryClick(hero.entryId) },
                            )
                            else -> HeroSectionPlaceholder()
                        }
                    }
                }

                item(key = "home_search_bar", contentType = "home_hub_search_bar") {
                    HomeSearchBarWithSourceChip(
                        section = section,
                        sourceId = sourceId,
                        sourceName = sourceName,
                        availableSources = availableSources,
                        onSearchClick = onSearchClick,
                        onOpenCatalog = onOpenCatalog,
                        onSelectSource = onSelectSource,
                    )
                }

                if (historyItems.isNotEmpty()) {
                    item(key = "history", contentType = "home_hub_history") {
                        HistoryRow(
                            history = historyItems,
                            recentCardMode = recentCardMode,
                            section = section,
                            onEntryClick = onEntryClick,
                            onViewAllClick = onHistoryClick,
                        )
                    }
                }

                if (
                    shouldShowForYouSection(state.discoveryEnabled) &&
                    heroPresentation != HomeHeroMode.Collage &&
                    heroPresentation != HomeHeroMode.Stage &&
                    (forYouItems.isNotEmpty() || discovery.isEmpty())
                ) {
                    item(key = "for_you", contentType = "home_hub_for_you") {
                        ForYouSection(
                            items = forYouItems,
                            coverMediaType = section.toDiscoveryMediaType(),
                            onMoreClick = onForYouMoreClick,
                            onItemClick = { openTeaser(it) },
                            onLongClick = { longPressItem = it },
                            isRefreshing = state.isDiscoveryRefreshing,
                            onRefreshClick = onDiscoveryRefreshClick,
                        )
                    }
                }

                if (recommendations.isNotEmpty()) {
                    item(key = "recommendations", contentType = "home_hub_recommendations") {
                        RecommendationsGrid(
                            recommendations = recommendations,
                            section = section,
                            recentCardMode = recentCardMode,
                            onEntryClick = onEntryClick,
                            onMoreClick = onLibraryClick,
                        )
                    }
                }
            }

            item(key = "bottom_spacer", contentType = "home_hub_spacer") { Spacer(Modifier.height(24.dp)) }
        }
        if (hiddenSnackbar != null) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AuroraTheme.colors.surface)
                    .border(1.dp, AuroraTheme.colors.divider, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        hiddenSnackbar,
                        color = AuroraTheme.colors.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(AYMR.strings.for_you_undo),
                        color = AuroraTheme.colors.accent,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onUndoHidden() },
                    )
                }
            }
        }
        previewItem?.let { item ->
            DiscoveryPreviewSheet(
                item = item.toDiscoverySuggestion(),
                meta = previewMeta,
                isMetaLoading = previewMetaLoading,
                coverMediaType = section.toDiscoveryMediaType(),
                onDismiss = { previewItem = null },
                onAdd = {
                    previewItem = null
                    scope.launch {
                        val adder = DiscoveryLibraryAdder()
                        val added = adder.addFromProvider(item.mediaType, item.title, item.provider)
                        if (added) {
                            context.toast(
                                context.contextStringResource(AYMR.strings.for_you_added_snackbar, item.title),
                            )
                        } else {
                            context.toast(
                                context.contextStringResource(AYMR.strings.for_you_select_source_to_read),
                            )
                            onDiscoveryItemClick(item)
                        }
                    }
                },
                onFind = {
                    previewItem = null
                    onDiscoveryItemClick(item)
                },
                onHide = {
                    previewItem = null
                    onDiscoveryLongClick?.invoke(item)
                },
                hazeState = LocalHomeHazeState.current,
            )
        }
        longPressItem?.let { lpItem ->
            DiscoveryHideOptionsSheet(
                itemTitle = lpItem.title,
                tag = firstBlacklistTag(lpItem.genres, lpItem.rowType, lpItem.reasonPayload),
                onHide = {
                    longPressItem = null
                    onDiscoveryLongClick?.invoke(lpItem)
                },
                onBlacklistTag = { tag ->
                    longPressItem = null
                    onDiscoveryBlacklistTag?.invoke(lpItem, tag, countAffectedTeasers(state.discovery, tag))
                },
                onDismiss = { longPressItem = null },
                onMoreLikeThis = {
                    longPressItem = null
                    // Taste Engine: явный лайк — сильный позитивный сигнал + локальная замена карточки в тизере.
                    if (onDiscoveryLikeClick != null) {
                        onDiscoveryLikeClick.invoke(lpItem)
                    } else {
                        scope.launch {
                            eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder.record(
                                tasteRepository,
                                lpItem.toDiscoverySuggestion(),
                                tachiyomi.domain.discovery.model.DiscoverySignalType.LIKE,
                            )
                        }
                    }
                    context.toast(
                        context.contextStringResource(AYMR.strings.for_you_more_like_this_toast),
                    )
                },
                onMarkConsumed = {
                    longPressItem = null
                    // Taste Engine: «просмотрено» — нейтральное исключение (вкус не трогает).
                    scope.launch {
                        eu.kanade.tachiyomi.data.discovery.TasteSignalRecorder.record(
                            tasteRepository,
                            lpItem.toDiscoverySuggestion(),
                            tachiyomi.domain.discovery.model.DiscoverySignalType.CONSUMED,
                        )
                    }
                    context.toast(
                        context.contextStringResource(AYMR.strings.for_you_mark_consumed_toast),
                    )
                },
            )
        }
    }
}

@Composable
private fun WelcomeSection(onBrowseClick: () -> Unit, onExtensionClick: () -> Unit) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp

    Box(
        modifier = Modifier
            .auroraCenteredMaxWidth(contentMaxWidthDp)
            .padding(16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Subtle))
            .border(1.dp, colors.divider, RoundedCornerShape(24.dp))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = colors.accent, modifier = Modifier.size(80.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_title),
                color = colors.textPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(AYMR.strings.aurora_welcome_subtitle),
                color = colors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    appHaptics.tap()
                    onBrowseClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = colors.textOnAccent, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_browse_sources),
                    color = colors.textOnAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    appHaptics.tap()
                    onExtensionClick()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = resolveAuroraSurfaceColor(colors, AuroraSurfaceLevel.Glass),
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Filled.Extension, null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(AYMR.strings.aurora_add_extension),
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

internal data class HomeHubFilteredContent(
    val hero: HomeHubHero?,
    val history: List<HomeHubHistory>,
    val recommendations: List<HomeHubRecommendation>,
    val discovery: List<HomeHubDiscoveryItem> = emptyList(),
    val discoveryPool: List<HomeHubDiscoveryItem> = emptyList(),
    val isFiltering: Boolean,
)

internal fun resolveHomeHubFilteredContent(
    hero: HomeHubHero?,
    history: List<HomeHubHistory>,
    recommendations: List<HomeHubRecommendation>,
    query: String,
    discovery: List<HomeHubDiscoveryItem> = emptyList(),
    discoveryPool: List<HomeHubDiscoveryItem> = emptyList(),
): HomeHubFilteredContent {
    if (query.isEmpty()) {
        return HomeHubFilteredContent(
            hero = hero,
            history = history,
            recommendations = recommendations,
            discovery = discovery,
            discoveryPool = discoveryPool,
            isFiltering = false,
        )
    }

    return HomeHubFilteredContent(
        hero = hero?.takeIf { it.title.contains(query, ignoreCase = true) },
        history = history.filter { it.title.contains(query, ignoreCase = true) },
        recommendations = recommendations.filter { it.title.contains(query, ignoreCase = true) },
        discovery = discovery.filter { it.title.contains(query, ignoreCase = true) },
        discoveryPool = discoveryPool.filter { it.title.contains(query, ignoreCase = true) },
        isFiltering = true,
    )
}

/** Источник карточек для hero-карусели: полный пул подборки, а при его отсутствии — тизерное окно. */
internal fun resolveStageItems(
    discoveryPool: List<HomeHubDiscoveryItem>,
    discovery: List<HomeHubDiscoveryItem>,
): List<HomeHubDiscoveryItem> = discoveryPool.ifEmpty { discovery }

internal fun shouldRenderHomeHubHeroSlot(
    heroPresentation: HomeHeroMode,
    hasHero: Boolean,
    reserveHeroSlot: Boolean,
    hasDiscovery: Boolean,
): Boolean {
    if ((
            heroPresentation == HomeHeroMode.Collage || heroPresentation == HomeHeroMode.Stage ||
                heroPresentation == HomeHeroMode.Hybrid
            ) &&
        hasDiscovery
    ) {
        return true
    }
    return hasHero || reserveHeroSlot
}

internal fun shouldReserveHomeHubHeroSlot(
    hasHero: Boolean,
    isLoading: Boolean,
    showWelcome: Boolean,
    isFiltering: Boolean,
): Boolean {
    if (hasHero) return false
    if (!isLoading) return false
    if (showWelcome) return false
    if (isFiltering) return false
    return true
}

internal fun shouldEnableHomeHubScroll(
    showWelcome: Boolean,
    historyCount: Int,
    recommendationCount: Int,
    discoveryCount: Int = 0,
): Boolean {
    // Под Welcome-блоком скролл нужен, если есть discovery-контент (тизер под онбордингом).
    if (showWelcome) return discoveryCount > 0
    return historyCount > 0 || recommendationCount > 0 || discoveryCount > 0
}

internal fun resolveForYouItems(
    heroPresentation: HomeHeroMode,
    hasHero: Boolean,
    discovery: List<HomeHubDiscoveryItem>,
): List<HomeHubDiscoveryItem> = when {
    heroPresentation == HomeHeroMode.Collage -> emptyList()
    heroPresentation == HomeHeroMode.Stage -> emptyList()
    heroPresentation == HomeHeroMode.Hybrid && hasHero -> emptyList()
    else -> discovery
}
