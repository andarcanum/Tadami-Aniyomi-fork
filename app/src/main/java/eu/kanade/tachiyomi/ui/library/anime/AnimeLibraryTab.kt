package eu.kanade.tachiyomi.ui.library.anime

import androidx.activity.compose.BackHandler
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.anime.AnimeLibraryAuroraContent
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.manga.MangaLibraryAuroraContent
import eu.kanade.presentation.library.manga.MangaLibrarySettingsDialog
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySettingsScreenModel
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

data object AnimeLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = AYMR.strings.label_titles
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(
                R.drawable.anim_animelibrary_leave,
            )
            return TabOptions(
                index = 0u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        requestOpenSettingsSheet()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val mangaScreenModel = rememberScreenModel { MangaLibraryScreenModel() }
        val settingsScreenModel = rememberScreenModel { AnimeLibrarySettingsScreenModel() }
        val mangaSettingsScreenModel = rememberScreenModel { MangaLibrarySettingsScreenModel() }
        val state by screenModel.state.collectAsState()
        val mangaState by mangaScreenModel.state.collectAsState()

        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()
        val isAurora = theme.isAuroraStyle

        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            val started = AnimeLibraryUpdateJob.startNow(context, category)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        val onClickRefreshManga: (Category?) -> Boolean = { category ->
            val started = MangaLibraryUpdateJob.startNow(context, category)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
            started
        }

        suspend fun openEpisode(episode: Episode) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
        }

        val defaultTitle = stringResource(AYMR.strings.label_anime_library)

        val animeTab = TabContent(
            titleRes = AYMR.strings.label_anime,
            searchEnabled = true,
            content = { contentPadding, _ ->
                val currentCategoryItems = state.getAnimelibItemsByPage(screenModel.activeCategoryIndex)
                val libraryItems = currentCategoryItems.map { it.libraryAnime }
                AnimeLibraryAuroraContent(
                    items = libraryItems,
                    onAnimeClicked = { navigator.push(AnimeScreen(it)) },
                    contentPadding = contentPadding,
                    onFilterClicked = screenModel::showSettingsDialog,
                    onRefresh = { onClickRefresh(state.categories[screenModel.activeCategoryIndex]) },
                    onGlobalUpdate = { onClickRefresh(null) },
                    onOpenRandomEntry = {
                        scope.launch {
                            val randomItem = screenModel.getRandomAnimelibItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(AnimeScreen(randomItem.libraryAnime.anime.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                )
            },
        )
        val mangaTab = TabContent(
            titleRes = AYMR.strings.label_manga,
            searchEnabled = true,
            content = { contentPadding, _ ->
                val currentCategoryItems = mangaState.getLibraryItemsByPage(mangaScreenModel.activeCategoryIndex)
                val libraryItems = currentCategoryItems.map { it.libraryManga }
                MangaLibraryAuroraContent(
                    items = libraryItems,
                    onMangaClicked = { navigator.push(MangaScreen(it)) },
                    contentPadding = contentPadding,
                    onFilterClicked = mangaScreenModel::showSettingsDialog,
                    onRefresh = {
                        onClickRefreshManga(state.categories.getOrNull(mangaScreenModel.activeCategoryIndex))
                    },
                    onGlobalUpdate = { onClickRefreshManga(null) },
                    onOpenRandomEntry = {
                        scope.launch {
                            val randomItem = mangaScreenModel.getRandomLibraryItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                )
            },
        )
        val auroraTabs = listOfNotNull(
            animeTab.takeIf { showAnimeSection },
            mangaTab.takeIf { showMangaSection },
        ).toImmutableList()
        val mangaTabIndex = when {
            showMangaSection && showAnimeSection -> 1
            showMangaSection -> 0
            else -> -1
        }
        val isMangaTab: (Int) -> Boolean = { index -> index == mangaTabIndex }

        val savedAuroraPage = rememberSaveable { mutableIntStateOf(0) }
        val auroraPageCount = auroraTabs.size.coerceAtLeast(1)
        val initialAuroraPage = savedAuroraPage.intValue.coerceIn(0, auroraPageCount - 1)
        val auroraPagerState = rememberPagerState(initialAuroraPage) { auroraPageCount }

        LaunchedEffect(auroraPageCount) {
            if (auroraPagerState.currentPage > auroraPageCount - 1) {
                auroraPagerState.scrollToPage(auroraPageCount - 1)
            }
        }

        LaunchedEffect(auroraPagerState.currentPage, auroraPageCount, isAurora) {
            if (isAurora) {
                savedAuroraPage.intValue = auroraPagerState.currentPage.coerceAtMost(auroraPageCount - 1)
            }
        }

        val isAnimeLibraryEmpty = state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty
        val isMangaLibraryEmpty =
            mangaState.searchQuery.isNullOrEmpty() && !mangaState.hasActiveFilters && mangaState.isLibraryEmpty
        val isLibraryEmpty = if (isAurora) {
            when {
                showAnimeSection && showMangaSection -> isAnimeLibraryEmpty && isMangaLibraryEmpty
                showAnimeSection -> isAnimeLibraryEmpty
                showMangaSection -> isMangaLibraryEmpty
                else -> isAnimeLibraryEmpty
            }
        } else {
            isAnimeLibraryEmpty
        }
        val isLoading = if (isAurora) {
            when {
                showAnimeSection && showMangaSection -> state.isLoading && mangaState.isLoading
                showAnimeSection -> state.isLoading
                showMangaSection -> mangaState.isLoading
                else -> state.isLoading
            }
        } else {
            state.isLoading
        }

        Scaffold(
            topBar = { scrollBehavior ->
                if (isAurora) return@Scaffold

                val title = state.getToolbarTitle(
                    defaultTitle = defaultTitle,
                    defaultCategoryTitle = stringResource(MR.strings.label_default),
                    page = screenModel.activeCategoryIndex,
                )
                val tabVisible = state.showCategoryTabs && state.categories.size > 1
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = state.selection.size,
                    title = title,
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                    onClickInvertSelection = {
                        screenModel.invertSelection(
                            screenModel.activeCategoryIndex,
                        )
                    },
                    onClickFilter = screenModel::showSettingsDialog,
                    onClickRefresh = {
                        onClickRefresh(
                            state.categories[screenModel.activeCategoryIndex],
                        )
                    },
                    onClickGlobalUpdate = { onClickRefresh(null) },
                    onClickOpenRandomEntry = {
                        scope.launch {
                            val randomItem = screenModel.getRandomAnimelibItemForCurrentCategory()
                            if (randomItem != null) {
                                navigator.push(AnimeScreen(randomItem.libraryAnime.anime.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior.takeIf { !tabVisible }, // For scroll overlay when no tab
                )
            },
            bottomBar = {
                LibraryBottomActionMenu(
                    visible = state.selectionMode,
                    onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                    onMarkAsViewedClicked = { screenModel.markSeenSelection(true) },
                    onMarkAsUnviewedClicked = { screenModel.markSeenSelection(false) },
                    onDownloadClicked = screenModel::runDownloadActionSelection
                        .takeIf { state.selection.fastAll { !it.anime.isLocal() } },
                    onDeleteClicked = screenModel::openDeleteAnimeDialog,
                    isManga = false,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                isLibraryEmpty -> {
                    val handler = LocalUriHandler.current
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                        actions = persistentListOf(
                            EmptyScreenAction(
                                stringRes = MR.strings.getting_started_guide,
                                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                                onClick = { handler.openUri(GETTING_STARTED_URL) },
                            ),
                        ),
                    )
                }
                else -> {
                    if (isAurora) {
                        TabbedScreenAurora(
                            titleRes = null,
                            tabs = auroraTabs,
                            state = auroraPagerState,
                            animeSearchQuery = state.searchQuery,
                            onChangeAnimeSearchQuery = screenModel::search,
                            mangaSearchQuery = mangaState.searchQuery,
                            onChangeMangaSearchQuery = mangaScreenModel::search,
                            isMangaTab = isMangaTab,
                            showTabs = false,
                        )
                    } else {
                        AnimeLibraryContent(
                            categories = state.categories,
                            searchQuery = state.searchQuery,
                            selection = state.selection,
                            contentPadding = contentPadding,
                            currentPage = { screenModel.activeCategoryIndex },
                            hasActiveFilters = state.hasActiveFilters,
                            showPageTabs = state.showCategoryTabs || !state.searchQuery.isNullOrEmpty(),
                            onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                            onAnimeClicked = { navigator.push(AnimeScreen(it)) },
                            onContinueWatchingClicked = { it: LibraryAnime ->
                                scope.launchIO {
                                    val episode = screenModel.getNextUnseenEpisode(it.anime)
                                    if (episode != null) openEpisode(episode)
                                }
                                Unit
                            }.takeIf { state.showAnimeContinueButton },
                            onToggleSelection = screenModel::toggleSelection,
                            onToggleRangeSelection = {
                                screenModel.toggleRangeSelection(it)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onRefresh = onClickRefresh,
                            onGlobalSearchClicked = {
                                navigator.push(
                                    GlobalAnimeSearchScreen(screenModel.state.value.searchQuery ?: ""),
                                )
                            },
                            getNumberOfAnimeForCategory = { state.getAnimeCountForCategory(it) },
                            getDisplayMode = { screenModel.getDisplayMode() },
                            getColumnsForOrientation = {
                                screenModel.getColumnsPreferenceForCurrentOrientation(
                                    it,
                                )
                            },
                        ) { state.getAnimelibItemsByPage(it) }
                    }
                }
            }
        }

        val onDismissRequest = screenModel::closeDialog
        when (val dialog = state.dialog) {
            is AnimeLibraryScreenModel.Dialog.SettingsSheet -> run {
                val category = state.categories.getOrNull(screenModel.activeCategoryIndex)
                if (category == null) {
                    onDismissRequest()
                    return@run
                }
                AnimeLibrarySettingsDialog(
                    onDismissRequest = onDismissRequest,
                    screenModel = settingsScreenModel,
                    category = category,
                )
            }
            is AnimeLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        screenModel.clearSelection()
                        navigator.push(CategoriesTab)
                    },
                    onConfirm = { include, exclude ->
                        screenModel.clearSelection()
                        screenModel.setAnimeCategories(dialog.anime, include, exclude)
                    },
                )
            }
            is AnimeLibraryScreenModel.Dialog.DeleteAnime -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.anime.any(Anime::isLocal),
                    onDismissRequest = onDismissRequest,
                    onConfirm = { deleteAnime, deleteEpisode ->
                        screenModel.removeAnimes(dialog.anime, deleteAnime, deleteEpisode)
                        screenModel.clearSelection()
                    },
                    isManga = false,
                )
            }
            null -> {}
        }

        val onDismissMangaRequest = mangaScreenModel::closeDialog
        when (val dialog = mangaState.dialog) {
            is MangaLibraryScreenModel.Dialog.SettingsSheet -> run {
                val category = mangaState.categories.getOrNull(mangaScreenModel.activeCategoryIndex)
                if (category == null) {
                    onDismissMangaRequest()
                    return@run
                }
                MangaLibrarySettingsDialog(
                    onDismissRequest = onDismissMangaRequest,
                    screenModel = mangaSettingsScreenModel,
                    category = category,
                )
            }
            is MangaLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissMangaRequest,
                    onEditCategories = {
                        mangaScreenModel.clearSelection()
                        navigator.push(CategoriesTab)
                        CategoriesTab.showMangaCategory()
                    },
                    onConfirm = { include, exclude ->
                        mangaScreenModel.clearSelection()
                        mangaScreenModel.setMangaCategories(dialog.manga, include, exclude)
                    },
                )
            }
            is MangaLibraryScreenModel.Dialog.DeleteManga -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = dialog.manga.any(Manga::isLocal),
                    onDismissRequest = onDismissMangaRequest,
                    onConfirm = { deleteManga, deleteChapter ->
                        mangaScreenModel.removeMangas(dialog.manga, deleteManga, deleteChapter)
                        mangaScreenModel.clearSelection()
                    },
                    isManga = true,
                )
            }
            null -> {}
        }

        val hasAnimeSearchQuery = state.searchQuery != null
        val hasMangaSearchQuery = mangaState.searchQuery != null
        val isMangaPage = isAurora && isMangaTab(auroraPagerState.currentPage)

        BackHandler(enabled = state.selectionMode || hasAnimeSearchQuery || (isAurora && hasMangaSearchQuery)) {
            when {
                state.selectionMode -> screenModel.clearSelection()
                isAurora -> {
                    when {
                        isMangaPage && hasMangaSearchQuery -> mangaScreenModel.search(null)
                        !isMangaPage && hasAnimeSearchQuery -> screenModel.search(null)
                        hasMangaSearchQuery -> mangaScreenModel.search(null)
                        hasAnimeSearchQuery -> screenModel.search(null)
                    }
                }
                hasAnimeSearchQuery -> screenModel.search(null)
            }
        }

        LaunchedEffect(state.selectionMode, state.dialog) {
            HomeScreen.showBottomNav(!state.selectionMode)
        }

        LaunchedEffect(isLoading) {
            if (!isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}
