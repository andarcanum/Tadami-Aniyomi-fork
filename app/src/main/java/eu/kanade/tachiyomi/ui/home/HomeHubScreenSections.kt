package eu.kanade.tachiyomi.ui.home
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import eu.kanade.domain.ui.model.HomeHeroCtaMode
import eu.kanade.domain.ui.model.HomeHubRecentCardMode
import eu.kanade.presentation.more.settings.screen.browse.AnimeExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.MangaExtensionReposScreen
import eu.kanade.presentation.more.settings.screen.browse.NovelExtensionReposScreen
import eu.kanade.presentation.theme.AuroraSurfaceLevel
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.theme.resolveAuroraSurfaceColor
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import kotlinx.coroutines.launch
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState

@Composable
internal fun AnimeHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { HomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current

    LaunchedEffect(screenModel) {
        HomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedAnimeSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Anime,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(AnimeScreen(it)) },
        onPlayHero = { screenModel.playHeroEpisode(context) },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedAnimeSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseAnimeSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(AnimeExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showAnimeExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = { tabNavigator.current = AnimeLibraryTab },
    )
}

@Composable
internal fun MangaHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { MangaHomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel) {
        MangaHomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedMangaSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Manga,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(MangaScreen(it)) },
        onPlayHero = { screenModel.readHeroChapter(context) },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedMangaSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseMangaSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(MangaExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = {
            scope.launch { AnimeLibraryTab.showMangaSection() }
            tabNavigator.current = AnimeLibraryTab
        },
    )
}

@Composable
internal fun NovelHomeHub(
    contentPadding: PaddingValues,
    searchQuery: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    activeSection: HomeHubSection,
    scrollResetToken: Int,
    onScrollSignal: (HomeHubSection, Float, Boolean) -> Unit,
) {
    val screenModel = HomeHubTab.rememberScreenModel { NovelHomeHubScreenModel() }
    val state by screenModel.state.collectAsState()
    val navigator = LocalNavigator.currentOrThrow
    val tabNavigator = LocalTabNavigator.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(screenModel) {
        NovelHomeHubScreenModel.setInstance(screenModel)
        screenModel.startLiveUpdates()
    }

    val lastSourceName = remember { screenModel.getLastUsedNovelSourceName() }

    HomeHubScreen(
        section = HomeHubSection.Novel,
        activeSection = activeSection,
        scrollResetToken = scrollResetToken,
        onScrollSignal = onScrollSignal,
        state = state.toUiState(),
        searchQuery = searchQuery,
        lastSourceName = lastSourceName,
        heroCtaMode = heroCtaMode,
        recentCardMode = recentCardMode,
        contentPadding = contentPadding,
        onEntryClick = { navigator.push(NovelScreen(it)) },
        onPlayHero = {
            screenModel.getHeroChapterId()?.let { chapterId ->
                navigator.push(NovelReaderScreen(chapterId))
            }
        },
        onSourceClick = {
            val sourceId = screenModel.getLastUsedNovelSourceId()
            if (sourceId != -1L) {
                navigator.push(BrowseNovelSourceScreen(sourceId, null))
            } else {
                tabNavigator.current = BrowseTab
            }
        },
        onBrowseClick = { navigator.push(NovelExtensionReposScreen()) },
        onExtensionClick = {
            tabNavigator.current = BrowseTab
            BrowseTab.showNovelExtension()
        },
        onHistoryClick = { tabNavigator.current = HistoriesTab },
        onLibraryClick = {
            scope.launch { AnimeLibraryTab.showNovelSection() }
            tabNavigator.current = AnimeLibraryTab
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
    lastSourceName: String?,
    heroCtaMode: HomeHeroCtaMode,
    recentCardMode: HomeHubRecentCardMode,
    contentPadding: PaddingValues,
    onEntryClick: (Long) -> Unit,
    onPlayHero: () -> Unit,
    onSourceClick: () -> Unit,
    onBrowseClick: () -> Unit,
    onExtensionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onLibraryClick: () -> Unit,
) {
    val trimmedQuery = searchQuery?.trim().orEmpty()
    val isFiltering = trimmedQuery.isNotEmpty()
    val matchesQuery: (String) -> Boolean = { title ->
        !isFiltering || title.contains(trimmedQuery, ignoreCase = true)
    }

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

    val hero = state.hero?.takeIf { matchesQuery(it.title) }
    val history = state.history.filter { matchesQuery(it.title) }
    val recommendations = state.recommendations.filter { matchesQuery(it.title) }
    val showWelcome = (state.showWelcome || state.showFilteredEmpty) && !isFiltering
    val enableScroll = shouldEnableHomeHubScroll(
        showWelcome = showWelcome,
        historyCount = history.size,
        recommendationCount = recommendations.size,
    )
    val reserveHeroSlot = shouldReserveHomeHubHeroSlot(
        hasHero = state.hero != null,
        isLoading = state.isLoading,
        showWelcome = showWelcome,
        isFiltering = isFiltering,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (enableScroll) Modifier.nestedScroll(nestedScrollConnection) else Modifier),
        contentPadding = contentPadding,
        userScrollEnabled = enableScroll,
    ) {
        if (showWelcome) {
            item(key = "welcome") {
                WelcomeSection(onBrowseClick = onBrowseClick, onExtensionClick = onExtensionClick)
            }
        } else {
            if (hero != null || reserveHeroSlot) {
                item(key = "hero") {
                    hero?.let { heroData ->
                        HeroSection(
                            hero = heroData,
                            section = section,
                            ctaMode = heroCtaMode,
                            onPlayClick = onPlayHero,
                            onEntryClick = { onEntryClick(heroData.entryId) },
                        )
                    } ?: HeroSectionPlaceholder()
                }
            }

            item(key = "quick_source") {
                QuickSourceButton(sourceName = lastSourceName, onClick = onSourceClick)
            }

            if (history.isNotEmpty()) {
                item(key = "history") {
                    HistoryRow(
                        history = history,
                        recentCardMode = recentCardMode,
                        onEntryClick = onEntryClick,
                        onViewAllClick = onHistoryClick,
                    )
                }
            }

            if (recommendations.isNotEmpty()) {
                item(key = "recommendations") {
                    RecommendationsGrid(
                        recommendations = recommendations,
                        recentCardMode = recentCardMode,
                        onEntryClick = onEntryClick,
                        onMoreClick = onLibraryClick,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
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
): Boolean {
    if (showWelcome) return false
    return historyCount > 0 || recommendationCount > 0
}
