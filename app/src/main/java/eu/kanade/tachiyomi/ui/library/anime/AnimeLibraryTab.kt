package eu.kanade.tachiyomi.ui.library.anime

import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenu
import eu.kanade.presentation.entries.components.AuroraEntryDropdownMenuItem
import eu.kanade.presentation.entries.components.LibraryBottomActionMenu
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.anime.AnimeLibraryAuroraContent
import eu.kanade.presentation.library.anime.AnimeLibraryContent
import eu.kanade.presentation.library.anime.AnimeLibrarySettingsDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.manga.MangaLibraryAuroraContent
import eu.kanade.presentation.library.manga.MangaLibrarySettingsDialog
import eu.kanade.presentation.library.novel.NovelLibraryAuroraContent
import eu.kanade.presentation.library.novel.NovelLibrarySettingsDialog
import eu.kanade.presentation.library.novel.components.AddToSeriesDialog
import eu.kanade.presentation.library.novel.components.CreateSeriesDialog
import eu.kanade.presentation.more.onboarding.GETTING_STARTED_URL
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.LocalBottomNavVisibilityController
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.novel.NovelTranslatedDownloadFormat
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.config.MigrationConfigScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelSearchScreen
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelBatchDownloadDialog
import eu.kanade.tachiyomi.ui.entries.novel.NovelDownloadChapterPickerDialog
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelTranslatedDownloadDialog
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.library.LibraryImmersiveChromeState
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.manga.MangaLibrarySettingsScreenModel
import eu.kanade.tachiyomi.ui.library.novel.NovelLibraryScreenModel
import eu.kanade.tachiyomi.ui.library.resolveLibraryImmersiveChromeState
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderPreferences
import eu.kanade.tachiyomi.ui.series.manga.MangaSeriesScreen
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.category.novel.interactor.GetVisibleNovelCategories
import tachiyomi.domain.category.novel.model.NovelCategory
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.novel.interactor.GetLibraryNovel
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.source.local.entries.anime.isLocal
import tachiyomi.source.local.entries.manga.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import eu.kanade.presentation.library.manga.components.AddToSeriesDialog as MangaAddToSeriesDialog
import tachiyomi.domain.items.novelchapter.model.NovelChapter as DomainNovelChapter

data object AnimeLibraryTab : Tab {

    enum class Section {
        Anime,
        Manga,
        Novel,
    }

    private var lastAuroraSection: Section = Section.Anime

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
        val novelReaderPreferences = remember { Injekt.get<NovelReaderPreferences>() }
        val isNovelTranslatorEnabled by novelReaderPreferences.geminiEnabled().collectAsState()

        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()
        val showNovelSection by uiPreferences.showNovelSection().collectAsState()
        val immersiveModeEnabled by uiPreferences.auroraLibraryImmersiveMode().collectAsState()
        val bottomNavVisibilityController = LocalBottomNavVisibilityController.current
        val useSeparateDisplayModePerMedia by settingsScreenModel
            .libraryPreferences
            .separateDisplayModePerMedia()
            .collectAsState()
        val showContinueViewingButton by settingsScreenModel
            .libraryPreferences
            .showContinueViewingButton()
            .collectAsState()
        val showCategoryTabs by settingsScreenModel
            .libraryPreferences
            .categoryTabs()
            .collectAsState()
        val showCategoryNumberOfItems by settingsScreenModel
            .libraryPreferences
            .categoryNumberOfItems()
            .collectAsState()
        val getVisibleNovelCategories = remember { Injekt.get<GetVisibleNovelCategories>() }
        val visibleNovelCategories by getVisibleNovelCategories.subscribe().collectAsState(initial = emptyList())
        val isAurora = theme.isAuroraStyle
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val availableSections = listOfNotNull(
            Section.Anime.takeIf { showAnimeSection },
            Section.Manga.takeIf { showMangaSection },
            Section.Novel.takeIf { showNovelSection },
        )
        val auroraPageCount = availableSections.size.coerceAtLeast(1)
        val initialAuroraPage = availableSections.indexOf(lastAuroraSection)
            .takeIf { it >= 0 }
            ?.coerceIn(0, auroraPageCount - 1)
            ?: 0
        val auroraPagerState = rememberPagerState(initialAuroraPage) { auroraPageCount }
        val sectionAtPage: (Int) -> Section? = { index ->
            resolveAuroraLibrarySection(availableSections, index)
        }
        val auroraCurrentSection = if (isAurora) {
            sectionAtPage(auroraPagerState.currentPage.coerceAtMost(auroraPageCount - 1))
        } else {
            null
        }
        val shouldActivateNovelLibrary = showNovelSection && auroraCurrentSection == Section.Novel
        val inactiveNovelRawItems = if (showNovelSection && !shouldActivateNovelLibrary) {
            val getLibraryNovel = remember { Injekt.get<GetLibraryNovel>() }
            val items by getLibraryNovel.subscribe().collectAsState(initial = emptyList())
            items
        } else {
            emptyList()
        }
        val novelScreenModel = if (showNovelSection) {
            rememberScreenModel { NovelLibraryScreenModel() }
        } else {
            null
        }
        val novelState = novelScreenModel?.state?.collectAsState()?.value ?: NovelLibraryScreenModel.State(
            isLoading = false,
            rawItems = inactiveNovelRawItems.map { eu.kanade.presentation.library.novel.NovelLibraryItem.Single(it) },
            items = inactiveNovelRawItems.map { eu.kanade.presentation.library.novel.NovelLibraryItem.Single(it) },
        )
        val animeDisplayMode by remember(useSeparateDisplayModePerMedia) {
            screenModel.getDisplayMode(useSeparateDisplayModePerMedia)
        }
        val mangaDisplayMode by remember(useSeparateDisplayModePerMedia) {
            mangaScreenModel.getDisplayMode(useSeparateDisplayModePerMedia)
        }
        val animeColumns by remember(isLandscape) {
            screenModel.getColumnsPreferenceForCurrentOrientation(isLandscape)
        }
        val mangaColumns by remember(isLandscape) {
            mangaScreenModel.getColumnsPreferenceForCurrentOrientation(isLandscape)
        }

        val snackbarHostState = remember { SnackbarHostState() }
        val epubImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri != null) {
                    val activeNovelScreenModel = novelScreenModel ?: return@rememberLauncherForActivityResult
                    scope.launchIO {
                        try {
                            activeNovelScreenModel.importEpub(uri)
                            snackbarHostState.showSnackbar(
                                context.stringResource(AYMR.strings.novel_library_import_success),
                                duration = SnackbarDuration.Short,
                            )
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(
                                context.stringResource(AYMR.strings.novel_library_import_failed),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                }
            },
        )
        var showNovelBatchDownloadDialog by remember { mutableStateOf(false) }
        var showNovelBatchChapterPickerDialog by remember { mutableStateOf(false) }
        var novelBatchPickerChapters by remember { mutableStateOf<List<DomainNovelChapter>>(emptyList()) }
        var showNovelTranslatedDownloadDialog by remember { mutableStateOf(false) }
        var showNovelTranslatedChapterPickerDialog by remember { mutableStateOf(false) }
        var novelTranslatedPickerFormat by remember { mutableStateOf(NovelTranslatedDownloadFormat.TXT) }
        var novelTranslatedPickerChapters by remember { mutableStateOf<List<DomainNovelChapter>>(emptyList()) }
        var pendingNovelSearchQuery by remember { mutableStateOf<String?>(null) }
        val updatingAnimeMessage = context.stringResource(AYMR.strings.aurora_updating_anime)
        val updatingMangaMessage = context.stringResource(AYMR.strings.aurora_updating_manga)
        val updatingNovelMessage = context.stringResource(MR.strings.updating_library)
        val updateAlreadyRunningMessage = context.stringResource(MR.strings.update_already_running)
        val hideThresholdPx = with(LocalDensity.current) { 56.dp.toPx() }
        var immersiveChromeState by remember { mutableStateOf(LibraryImmersiveChromeState()) }
        val forceChromeVisible = state.searchQuery != null ||
            mangaState.searchQuery != null ||
            novelState.searchQuery != null ||
            state.selectionMode ||
            mangaState.selectionMode ||
            novelState.selectionMode ||
            showNovelBatchDownloadDialog ||
            showNovelBatchChapterPickerDialog ||
            showNovelTranslatedDownloadDialog ||
            showNovelTranslatedChapterPickerDialog

        val immersiveScrollConnection = remember(immersiveModeEnabled, forceChromeVisible, hideThresholdPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (!immersiveModeEnabled || source != NestedScrollSource.UserInput) return Offset.Zero

                    val nextState = resolveLibraryImmersiveChromeState(
                        currentState = immersiveChromeState,
                        scrollDeltaPx = -available.y,
                        enabled = immersiveModeEnabled,
                        forceVisible = forceChromeVisible,
                        hideThresholdPx = hideThresholdPx,
                    )
                    if (nextState != immersiveChromeState) {
                        immersiveChromeState = nextState
                    }

                    return Offset.Zero
                }
            }
        }

        LaunchedEffect(immersiveModeEnabled, forceChromeVisible, immersiveChromeState.isVisible) {
            if (!immersiveModeEnabled || forceChromeVisible) {
                immersiveChromeState = LibraryImmersiveChromeState()
                bottomNavVisibilityController.updateVisible(true)
            } else {
                bottomNavVisibilityController.updateVisible(immersiveChromeState.isVisible)
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                bottomNavVisibilityController.updateVisible(true)
            }
        }

        fun showLibraryUpdateFeedback(started: Boolean, startedMessage: String) {
            if (isAurora) {
                val message = if (started) startedMessage else updateAlreadyRunningMessage
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                return
            }
            scope.launch {
                val msgRes = if (started) MR.strings.updating_category else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
        }

        val onClickRefresh: (Category?) -> Boolean = { category ->
            val started = AnimeLibraryUpdateJob.startNow(context, category)
            showLibraryUpdateFeedback(started, updatingAnimeMessage)
            started
        }

        val onClickRefreshManga: (Category?) -> Boolean = { category ->
            val started = MangaLibraryUpdateJob.startNow(context, category)
            showLibraryUpdateFeedback(started, updatingMangaMessage)
            started
        }
        val onClickRefreshNovel: () -> Boolean = {
            val started = NovelLibraryUpdateJob.startNow(context)
            showLibraryUpdateFeedback(started, updatingNovelMessage)
            started
        }

        suspend fun openEpisode(episode: Episode) {
            val playerPreferences: PlayerPreferences by injectLazy()
            val extPlayer = playerPreferences.alwaysUseExternalPlayer().get()
            MainActivity.startPlayerActivity(context, episode.animeId, episode.id, extPlayer)
        }

        val defaultTitle = stringResource(AYMR.strings.label_anime_library)
        val animeCategoryIndex = coerceAuroraLibraryCategoryIndex(
            requestedIndex = screenModel.activeCategoryIndex,
            categoryCount = state.categories.size,
        )
        val mangaCategoryIndex = coerceAuroraLibraryCategoryIndex(
            requestedIndex = mangaScreenModel.activeCategoryIndex,
            categoryCount = mangaState.categories.size,
        )
        val novelsByCategory = remember(novelState.items) {
            novelState.items.groupBy { it.category }
        }
        val novelCategories = remember(visibleNovelCategories, novelsByCategory) {
            val mappedCategories = visibleNovelCategories.map(NovelCategory::toCategory)
            if (novelsByCategory.isNotEmpty() && !novelsByCategory.containsKey(Category.UNCATEGORIZED_ID)) {
                mappedCategories.filterNot(Category::isSystemCategory)
            } else {
                mappedCategories
            }
        }
        val novelCategoryIndex = coerceAuroraLibraryCategoryIndex(
            requestedIndex = novelScreenModel?.activeCategoryIndex ?: 0,
            categoryCount = novelCategories.size,
        )
        val currentNovelCategoryItems = remember(novelState.items, novelCategories, novelCategoryIndex) {
            val categoryId = novelCategories.getOrNull(novelCategoryIndex)?.id
            if (categoryId == null) {
                novelState.items
            } else {
                novelState.items.filter { it.category == categoryId }
            }
        }

        LaunchedEffect(state.categories.size, animeCategoryIndex) {
            if (screenModel.activeCategoryIndex != animeCategoryIndex) {
                screenModel.activeCategoryIndex = animeCategoryIndex
            }
        }
        LaunchedEffect(mangaState.categories.size, mangaCategoryIndex) {
            if (mangaScreenModel.activeCategoryIndex != mangaCategoryIndex) {
                mangaScreenModel.activeCategoryIndex = mangaCategoryIndex
            }
        }
        LaunchedEffect(novelCategories.size, novelCategoryIndex) {
            if (novelScreenModel?.activeCategoryIndex != novelCategoryIndex) {
                novelScreenModel?.activeCategoryIndex = novelCategoryIndex
            }
        }

        val animeTab = TabContent(
            titleRes = AYMR.strings.label_anime,
            searchEnabled = true,
            content = { contentPadding, _ ->
                val currentCategoryItems = state.getAnimelibItemsByPage(animeCategoryIndex)
                AnimeLibraryAuroraContent(
                    items = currentCategoryItems,
                    selection = state.selection,
                    searchQuery = state.searchQuery,
                    hasActiveFilters = state.hasActiveFilters,
                    displayMode = animeDisplayMode,
                    columns = animeColumns,
                    onAnimeClicked = { navigator.push(AnimeScreen(it)) },
                    onToggleSelection = screenModel::toggleSelection,
                    onToggleRangeSelection = {
                        screenModel.toggleRangeSelection(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTogglePinned = { screenModel.togglePinned(it.libraryAnime) },
                    onContinueWatchingClicked = { item: LibraryAnime ->
                        scope.launchIO {
                            val episode = screenModel.getNextUnseenEpisode(item.anime)
                            if (episode != null) openEpisode(episode)
                        }
                        Unit
                    }.takeIf { showContinueViewingButton },
                    onGlobalSearchClicked = {
                        navigator.push(GlobalAnimeSearchScreen(state.searchQuery ?: ""))
                    },
                    contentPadding = contentPadding,
                )
            },
        )
        val mangaTab = TabContent(
            titleRes = AYMR.strings.label_manga,
            searchEnabled = true,
            content = { contentPadding, _ ->
                val currentCategoryItems = mangaState.getLibraryItemsByPage(mangaCategoryIndex)
                MangaLibraryAuroraContent(
                    items = currentCategoryItems,
                    selection = mangaState.selection,
                    searchQuery = mangaState.searchQuery,
                    hasActiveFilters = mangaState.hasActiveFilters,
                    displayMode = mangaDisplayMode,
                    columns = mangaColumns,
                    onMangaClicked = { navigator.push(MangaScreen(it)) },
                    onSeriesClicked = { navigator.push(MangaSeriesScreen(it)) },
                    onToggleSelection = mangaScreenModel::toggleSelection,
                    onToggleRangeSelection = {
                        mangaScreenModel.toggleRangeSelection(it)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTogglePinned = mangaScreenModel::togglePinned,
                    onContinueReadingClicked = { item: LibraryManga ->
                        scope.launchIO {
                            val chapter = mangaScreenModel.getNextUnreadChapter(item.manga)
                            if (chapter != null) {
                                context.startActivity(
                                    ReaderActivity.newIntent(context, chapter.mangaId, chapter.id),
                                )
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.no_next_chapter),
                                )
                            }
                        }
                        Unit
                    }.takeIf { showContinueViewingButton },
                    onGlobalSearchClicked = {
                        navigator.push(GlobalMangaSearchScreen(mangaState.searchQuery ?: ""))
                    },
                    contentPadding = contentPadding,
                )
            },
        )
        val novelTab = TabContent(
            titleRes = AYMR.strings.label_novel,
            searchEnabled = true,
            content = { contentPadding, _ ->
                val activeNovelScreenModel = novelScreenModel
                    ?: return@TabContent LoadingScreen(Modifier.padding(contentPadding))

                val epubImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                    onResult = { uri ->
                        if (uri != null) {
                            scope.launchIO {
                                try {
                                    activeNovelScreenModel.importEpub(uri)
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(AYMR.strings.novel_library_import_success),
                                        duration = SnackbarDuration.Short,
                                    )
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(
                                        context.stringResource(AYMR.strings.novel_library_import_failed),
                                        duration = SnackbarDuration.Short,
                                    )
                                }
                            }
                        }
                    },
                )

                NovelLibraryAuroraContent(
                    items = currentNovelCategoryItems,
                    selection = novelState.selection,
                    searchQuery = novelState.searchQuery,
                    onSearchQueryChange = activeNovelScreenModel::search,
                    onNovelClicked = { id ->
                        if (id < 0) {
                            navigator.push(NovelSeriesScreen(-id))
                        } else {
                            navigator.push(NovelScreen(id))
                        }
                    },
                    onToggleSelection = activeNovelScreenModel::toggleSelection,
                    onToggleRangeSelection = { novelItem ->
                        activeNovelScreenModel.toggleRangeSelection(novelItem)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTogglePinned = activeNovelScreenModel::togglePinned,
                    contentPadding = contentPadding,
                    hasActiveFilters = novelState.hasActiveFilters,
                    onFilterClicked = activeNovelScreenModel::showSettingsDialog,
                    onRefresh = { onClickRefreshNovel() },
                    onGlobalUpdate = { onClickRefreshNovel() },
                    onOpenRandomEntry = {
                        scope.launch {
                            val randomItem = novelState.items.randomOrNull()
                            if (randomItem != null) {
                                if (randomItem is eu.kanade.presentation.library.novel.NovelLibraryItem.Series) {
                                    navigator.push(NovelSeriesScreen(randomItem.librarySeries.id))
                                } else {
                                    navigator.push(NovelScreen(randomItem.id))
                                }
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.information_no_entries_found),
                                )
                            }
                        }
                    },
                    onContinueReadingClicked = { item: eu.kanade.presentation.library.novel.NovelLibraryItem ->
                        scope.launch {
                            val chapter = withContext(Dispatchers.IO) {
                                activeNovelScreenModel.getNextUnreadChapter(item)
                            }
                            if (chapter != null) {
                                navigator.push(NovelReaderScreen(chapter.id))
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.stringResource(MR.strings.no_next_chapter),
                                )
                            }
                        }
                        Unit
                    }.takeIf { showContinueViewingButton },
                    onImportEpub = { epubImportLauncher.launch(arrayOf("application/epub+zip")) },
                    showInlineHeader = false,
                )
            },
        )

        val sectionTabs = listOfNotNull(
            (Section.Anime to animeTab).takeIf { showAnimeSection },
            (Section.Manga to mangaTab).takeIf { showMangaSection },
            (Section.Novel to novelTab).takeIf { showNovelSection },
        )
        val auroraSections = sectionTabs.map { it.first }
        val auroraTabs = sectionTabs.map { it.second }.toImmutableList()
        val mangaTabIndex = sectionTabs.indexOfFirst { it.first == Section.Manga }.takeIf { it >= 0 } ?: -1
        val novelTabIndex = sectionTabs.indexOfFirst { it.first == Section.Novel }.takeIf { it >= 0 } ?: -1
        val isMangaTab: (Int) -> Boolean = { index -> index == mangaTabIndex }

        LaunchedEffect(auroraPageCount) {
            if (auroraPagerState.currentPage > auroraPageCount - 1) {
                auroraPagerState.scrollToPage(auroraPageCount - 1)
            }
        }

        LaunchedEffect(auroraPagerState.currentPage, auroraPageCount, isAurora) {
            if (isAurora) {
                sectionAtPage(auroraPagerState.currentPage.coerceAtMost(auroraPageCount - 1))?.let {
                    lastAuroraSection = it
                }
            }
        }

        val isAnimeLibraryEmpty = state.searchQuery.isNullOrEmpty() && !state.hasActiveFilters && state.isLibraryEmpty
        val isMangaLibraryEmpty = mangaState.searchQuery.isNullOrEmpty() &&
            !mangaState.hasActiveFilters &&
            mangaState.isLibraryEmpty
        val isNovelLibraryEmpty = novelState.searchQuery.isNullOrEmpty() && novelState.isLibraryEmpty
        val isSectionEmpty: (Section) -> Boolean = { section ->
            when (section) {
                Section.Anime -> isAnimeLibraryEmpty
                Section.Manga -> isMangaLibraryEmpty
                Section.Novel -> isNovelLibraryEmpty
            }
        }
        val isLibraryEmpty = if (isAurora) {
            sectionTabs.all { (section, _) -> isSectionEmpty(section) }
        } else {
            isAnimeLibraryEmpty
        }
        val isNovelLoading = novelState.isLoading
        val isSectionLoading: (Section) -> Boolean = { section ->
            when (section) {
                Section.Anime -> state.isLoading
                Section.Manga -> mangaState.isLoading
                Section.Novel -> isNovelLoading
            }
        }
        val isLoading = if (isAurora) {
            sectionTabs.all { (section, _) -> isSectionLoading(section) }
        } else {
            state.isLoading
        }
        val auroraSearchQuery = when (auroraCurrentSection) {
            Section.Anime -> state.searchQuery
            Section.Manga -> mangaState.searchQuery
            Section.Novel -> novelState.searchQuery
            null -> null
        }
        val onAuroraSearchQueryChange: (String?) -> Unit = { query ->
            when (auroraCurrentSection) {
                Section.Anime -> screenModel.search(query)
                Section.Manga -> mangaScreenModel.search(query)
                Section.Novel -> {
                    pendingNovelSearchQuery = query
                    novelScreenModel?.search(query)
                }
                null -> Unit
            }
        }
        val auroraCategories = when (auroraCurrentSection) {
            Section.Anime -> state.categories
            Section.Manga -> mangaState.categories
            Section.Novel -> novelCategories
            null -> emptyList()
        }
        val auroraCategoryIndex = when (auroraCurrentSection) {
            Section.Anime -> coerceAuroraLibraryCategoryIndex(
                requestedIndex = screenModel.activeCategoryIndex,
                categoryCount = state.categories.size,
            )
            Section.Manga -> coerceAuroraLibraryCategoryIndex(
                requestedIndex = mangaScreenModel.activeCategoryIndex,
                categoryCount = mangaState.categories.size,
            )
            Section.Novel -> coerceAuroraLibraryCategoryIndex(
                requestedIndex = novelScreenModel?.activeCategoryIndex ?: 0,
                categoryCount = novelCategories.size,
            )
            null -> 0
        }
        val showAuroraCategoryTabs = when (auroraCurrentSection) {
            Section.Anime -> shouldShowAuroraLibraryCategoryTabsRow(
                section = Section.Anime,
                categoryCount = state.categories.size,
                showCategoryTabs = state.showCategoryTabs,
                searchQuery = state.searchQuery,
            )
            Section.Manga -> shouldShowAuroraLibraryCategoryTabsRow(
                section = Section.Manga,
                categoryCount = mangaState.categories.size,
                showCategoryTabs = mangaState.showCategoryTabs,
                searchQuery = mangaState.searchQuery,
            )
            Section.Novel -> shouldShowAuroraLibraryCategoryTabsRow(
                section = Section.Novel,
                categoryCount = novelCategories.size,
                showCategoryTabs = showCategoryTabs,
                searchQuery = novelState.searchQuery,
            )
            null -> false
        }
        val onAuroraCategorySelected: (Int) -> Unit = { index ->
            when (auroraCurrentSection) {
                Section.Anime -> {
                    screenModel.activeCategoryIndex = coerceAuroraLibraryCategoryIndex(
                        requestedIndex = index,
                        categoryCount = state.categories.size,
                    )
                }
                Section.Manga -> {
                    mangaScreenModel.activeCategoryIndex = coerceAuroraLibraryCategoryIndex(
                        requestedIndex = index,
                        categoryCount = mangaState.categories.size,
                    )
                }
                Section.Novel -> {
                    novelScreenModel?.activeCategoryIndex = coerceAuroraLibraryCategoryIndex(
                        requestedIndex = index,
                        categoryCount = novelCategories.size,
                    )
                }
                null -> Unit
            }
        }
        val onAuroraFilterClick: () -> Unit = {
            when (auroraCurrentSection) {
                Section.Anime -> screenModel.showSettingsDialog()
                Section.Manga -> mangaScreenModel.showSettingsDialog()
                Section.Novel -> novelScreenModel?.showSettingsDialog()
                null -> Unit
            }
        }
        val onAuroraRefreshCurrent: () -> Unit = {
            when (auroraCurrentSection) {
                Section.Anime -> onClickRefresh(state.categories.getOrNull(animeCategoryIndex))
                Section.Manga -> onClickRefreshManga(mangaState.categories.getOrNull(mangaCategoryIndex))
                Section.Novel -> onClickRefreshNovel()
                null -> Unit
            }
        }
        val onAuroraRefreshGlobal: () -> Unit = {
            when (auroraCurrentSection) {
                Section.Anime -> onClickRefresh(null)
                Section.Manga -> onClickRefreshManga(null)
                Section.Novel -> onClickRefreshNovel()
                null -> Unit
            }
        }
        val onAuroraOpenRandom: () -> Unit = {
            when (auroraCurrentSection) {
                Section.Anime -> {
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
                }
                Section.Manga -> {
                    scope.launch {
                        val randomItem = mangaScreenModel.getRandomLibraryItemForCurrentCategory()
                        if (randomItem != null) {
                            if (randomItem is MangaLibraryItem.Series) {
                                navigator.push(MangaSeriesScreen(randomItem.librarySeries.id))
                            } else {
                                navigator.push(MangaScreen(randomItem.libraryManga.manga.id))
                            }
                        } else {
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.information_no_entries_found),
                            )
                        }
                    }
                }
                Section.Novel -> {
                    scope.launch {
                        val randomItem = novelState.items.randomOrNull()
                        if (randomItem != null) {
                            if (randomItem is eu.kanade.presentation.library.novel.NovelLibraryItem.Series) {
                                navigator.push(NovelSeriesScreen(randomItem.librarySeries.id))
                            } else {
                                navigator.push(NovelScreen(randomItem.coverNovel!!.id))
                            }
                        } else {
                            snackbarHostState.showSnackbar(
                                context.stringResource(MR.strings.information_no_entries_found),
                            )
                        }
                    }
                }
                null -> Unit
            }
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
                when {
                    !isAurora || auroraCurrentSection == Section.Anime || auroraCurrentSection == null -> {
                        LibraryBottomActionMenu(
                            visible = state.selectionMode,
                            onChangeCategoryClicked = screenModel::openChangeCategoryDialog,
                            onTogglePinnedClicked = { pinned ->
                                state.selection.forEach { screenModel.setPinned(it, pinned) }
                            },
                            isPinned = state.selection.fastAll { it.pinned },
                            onMarkAsViewedClicked = { screenModel.markSeenSelection(true) },
                            onMarkAsUnviewedClicked = { screenModel.markSeenSelection(false) },
                            onDownloadClicked = screenModel::runDownloadActionSelection
                                .takeIf { state.selection.fastAll { !it.anime.isLocal() } },
                            onDeleteClicked = screenModel::openDeleteAnimeDialog,
                            isManga = false,
                        )
                    }
                    auroraCurrentSection == Section.Manga -> {
                        LibraryBottomActionMenu(
                            visible = mangaState.selectionMode,
                            onChangeCategoryClicked = mangaScreenModel::openChangeCategoryDialog,
                            onTogglePinnedClicked = { pinned ->
                                mangaState.selection.forEach { mangaScreenModel.setPinned(it, pinned) }
                            },
                            isPinned = mangaState.selection.fastAll { it.pinned },
                            onMarkAsViewedClicked = { mangaScreenModel.markReadSelection(true) },
                            onMarkAsUnviewedClicked = { mangaScreenModel.markReadSelection(false) },
                            onDownloadClicked = mangaScreenModel::runDownloadActionSelection
                                .takeIf {
                                    mangaState.selection.fastAll { selected ->
                                        when (selected) {
                                            is MangaLibraryItem.Single -> !selected.libraryManga.manga.isLocal()
                                            is MangaLibraryItem.Series -> selected.librarySeries.entries.fastAll {
                                                !it.manga.isLocal()
                                            }
                                        }
                                    }
                                },
                            onMigrateClicked = {
                                val selectionIds = mangaState.selection
                                    .flatMap { selected ->
                                        when (selected) {
                                            is MangaLibraryItem.Single -> listOf(selected.libraryManga.id)
                                            is MangaLibraryItem.Series -> selected.librarySeries.entries.map { it.id }
                                        }
                                    }
                                    .distinct()
                                mangaScreenModel.clearSelection()
                                if (selectionIds.isNotEmpty()) {
                                    navigator.push(MigrationConfigScreen(selectionIds))
                                }
                            },
                            onSeriesClicked = { mangaScreenModel.openAddToSeries() },
                            onDeleteClicked = mangaScreenModel::openDeleteMangaDialog,
                            isManga = true,
                        )
                    }
                    auroraCurrentSection == Section.Novel -> {
                        LibraryBottomActionMenu(
                            visible = novelState.selectionMode,
                            onChangeCategoryClicked = { novelScreenModel?.openChangeCategoryDialog() },
                            onTogglePinnedClicked = { pinned ->
                                novelScreenModel?.let { screenModel ->
                                    novelState.selection.forEach { screenModel.setPinned(it, pinned) }
                                }
                            },
                            isPinned = novelState.selection.fastAll { it.pinned },
                            onMarkAsViewedClicked = { novelScreenModel?.markReadSelection(true) },
                            onMarkAsUnviewedClicked = { novelScreenModel?.markReadSelection(false) },
                            onDownloadClicked = null,
                            onOpenDownloadDialog = { showNovelBatchDownloadDialog = true },
                            onMigrateClicked = {
                                val selectionIds = novelState.selection.map { it.id }
                                novelScreenModel?.clearSelection()
                                if (selectionIds.size == 1) {
                                    navigator.push(MigrateNovelSearchScreen(selectionIds.single()))
                                }
                            }.takeIf { novelState.selection.size == 1 },
                            onTranslatedDownloadClicked = {
                                showNovelTranslatedDownloadDialog = true
                            }.takeIf { isNovelTranslatorEnabled },
                            onSeriesClicked = { novelScreenModel?.openAddToSeries() },
                            onDeleteClicked = { novelScreenModel?.openDeleteNovelsDialog() },
                            isManga = true,
                        )
                    }
                }
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
                            modifier = if (immersiveModeEnabled) {
                                Modifier.nestedScroll(immersiveScrollConnection)
                            } else {
                                Modifier
                            },
                            titleRes = null,
                            tabs = auroraTabs,
                            state = auroraPagerState,
                            isMangaTab = isMangaTab,
                            showCompactHeader = true,
                            showTabs = false,
                            instantTabSwitching = false,
                            extraHeaderContent = {
                                AuroraLibraryPinnedHeader(
                                    title = stringResource(AYMR.strings.label_titles),
                                    tabs = auroraTabs,
                                    selectedSectionIndex = auroraPagerState.currentPage.coerceIn(
                                        0,
                                        (auroraTabs.size - 1).coerceAtLeast(0),
                                    ),
                                    onSectionSelected = { index ->
                                        if (index in auroraTabs.indices && auroraPagerState.currentPage != index) {
                                            scope.launch { auroraPagerState.animateScrollToPage(index) }
                                        }
                                    },
                                    searchQuery = auroraSearchQuery,
                                    onSearchQueryChange = onAuroraSearchQueryChange,
                                    onFilterClick = onAuroraFilterClick,
                                    topChromeVisible = immersiveChromeState.isVisible,
                                    onRefreshCurrent = onAuroraRefreshCurrent,
                                    onRefreshGlobal = onAuroraRefreshGlobal,
                                    onOpenRandomEntry = onAuroraOpenRandom,
                                    onImportEpub = { epubImportLauncher.launch(arrayOf("application/epub+zip")) },
                                    categories = auroraCategories,
                                    selectedCategoryIndex = auroraCategoryIndex,
                                    showCategories = showAuroraCategoryTabs,
                                    onCategorySelected = onAuroraCategorySelected,
                                    getCountForCategory = { category ->
                                        when (auroraCurrentSection) {
                                            Section.Anime -> state.getAnimeCountForCategory(category)
                                            Section.Manga -> mangaState.getMangaCountForCategory(category)
                                            Section.Novel -> {
                                                if (showCategoryNumberOfItems ||
                                                    !novelState.searchQuery.isNullOrEmpty()
                                                ) {
                                                    novelsByCategory[category.id]?.size ?: 0
                                                } else {
                                                    null
                                                }
                                            }
                                            null -> null
                                        }
                                    },
                                )
                            },
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
                            onTogglePinned = { screenModel.togglePinned(it.libraryAnime) },
                            onRefresh = onClickRefresh,
                            onGlobalSearchClicked = {
                                navigator.push(
                                    GlobalAnimeSearchScreen(screenModel.state.value.searchQuery ?: ""),
                                )
                            },
                            getNumberOfAnimeForCategory = { state.getAnimeCountForCategory(it) },
                            getDisplayMode = {
                                screenModel.getDisplayMode(useSeparateDisplayModePerMedia)
                            },
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
            MangaLibraryScreenModel.Dialog.CreateSeries -> {
                CreateSeriesDialog(
                    onDismissRequest = onDismissMangaRequest,
                    onCreate = mangaScreenModel::createSeries,
                )
            }
            is MangaLibraryScreenModel.Dialog.AddToSeries -> {
                MangaAddToSeriesDialog(
                    onDismissRequest = onDismissMangaRequest,
                    series = dialog.series,
                    onSelect = mangaScreenModel::addSelectionToSeries,
                    onCreateSeries = mangaScreenModel::openCreateSeries,
                )
            }
            null -> {}
        }

        novelScreenModel?.let { activeNovelScreenModel ->
            when (val dialog = novelState.dialog) {
                NovelLibraryScreenModel.Dialog.Settings -> {
                    NovelLibrarySettingsDialog(
                        onDismissRequest = activeNovelScreenModel::closeDialog,
                        screenModel = activeNovelScreenModel,
                    )
                }
                is NovelLibraryScreenModel.Dialog.ChangeCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = activeNovelScreenModel::closeDialog,
                        onEditCategories = {
                            activeNovelScreenModel.clearSelection()
                            navigator.push(CategoriesTab)
                            CategoriesTab.showNovelCategory()
                        },
                        onConfirm = { include, exclude ->
                            activeNovelScreenModel.clearSelection()
                            activeNovelScreenModel.updateNovelCategories(dialog.novels, include, exclude)
                        },
                    )
                }
                is NovelLibraryScreenModel.Dialog.DeleteNovels -> {
                    DeleteLibraryEntryDialog(
                        containsLocalEntry = false,
                        onDismissRequest = activeNovelScreenModel::closeDialog,
                        onConfirm = { deleteFromLibrary, deleteChapters ->
                            activeNovelScreenModel.removeNovels(dialog.novels, deleteFromLibrary, deleteChapters)
                            activeNovelScreenModel.clearSelection()
                        },
                        isManga = true,
                    )
                }
                NovelLibraryScreenModel.Dialog.CreateSeries -> {
                    CreateSeriesDialog(
                        onDismissRequest = activeNovelScreenModel::closeDialog,
                        onCreate = activeNovelScreenModel::createSeries,
                    )
                }
                is NovelLibraryScreenModel.Dialog.AddToSeries -> {
                    AddToSeriesDialog(
                        onDismissRequest = activeNovelScreenModel::closeDialog,
                        series = dialog.series,
                        onSelect = activeNovelScreenModel::addSelectionToSeries,
                        onCreateSeries = activeNovelScreenModel::openCreateSeries,
                    )
                }
                null -> {}
            }
        }

        if (showNovelBatchDownloadDialog && novelScreenModel != null) {
            val activeNovelScreenModel = novelScreenModel
            NovelBatchDownloadDialog(
                onDismissRequest = { showNovelBatchDownloadDialog = false },
                onSelectChapters = {
                    scope.launch {
                        val candidates = activeNovelScreenModel.getSingleSelectionDownloadCandidates(
                            onlyNotDownloaded = true,
                        )
                        if (candidates.isEmpty()) {
                            snackbarHostState.showSnackbar(
                                message = context.stringResource(AYMR.strings.novel_download_no_available),
                                duration = SnackbarDuration.Short,
                            )
                            return@launch
                        }
                        novelBatchPickerChapters = candidates
                        showNovelBatchDownloadDialog = false
                        showNovelBatchChapterPickerDialog = true
                    }
                },
                onActionSelected = { action, amount ->
                    scope.launch {
                        val added = activeNovelScreenModel.runDownloadActionSelection(action, amount)
                        val message = if (added > 0) {
                            context.stringResource(AYMR.strings.novel_download_queue_started_count, added)
                        } else {
                            context.stringResource(AYMR.strings.novel_download_no_available)
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                    showNovelBatchDownloadDialog = false
                },
            )
        }

        if (showNovelBatchChapterPickerDialog && novelScreenModel != null) {
            val activeNovelScreenModel = novelScreenModel
            NovelDownloadChapterPickerDialog(
                title = context.stringResource(AYMR.strings.novel_download_select_chapters_title),
                chapters = novelBatchPickerChapters,
                onDismissRequest = { showNovelBatchChapterPickerDialog = false },
                onConfirm = { chapterIds ->
                    scope.launch {
                        val added = activeNovelScreenModel.runDownloadForSingleSelectionChapterIds(chapterIds)
                        val message = if (added > 0) {
                            context.stringResource(AYMR.strings.novel_download_queue_started_count, added)
                        } else {
                            context.stringResource(AYMR.strings.novel_download_no_available)
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                    showNovelBatchChapterPickerDialog = false
                },
            )
        }

        if (showNovelTranslatedDownloadDialog && novelScreenModel != null) {
            val activeNovelScreenModel = novelScreenModel
            NovelTranslatedDownloadDialog(
                onDismissRequest = { showNovelTranslatedDownloadDialog = false },
                onSelectChapters = { format ->
                    scope.launch {
                        novelTranslatedPickerFormat = format
                        val candidates = activeNovelScreenModel.getSingleSelectionTranslatedCandidates(
                            format = format,
                            onlyNotDownloaded = true,
                        )
                        if (candidates.isEmpty()) {
                            snackbarHostState.showSnackbar(
                                message = context.stringResource(AYMR.strings.novel_translated_download_no_available),
                                duration = SnackbarDuration.Short,
                            )
                            return@launch
                        }
                        novelTranslatedPickerChapters = candidates
                        showNovelTranslatedDownloadDialog = false
                        showNovelTranslatedChapterPickerDialog = true
                    }
                },
                onActionSelected = { action, amount, format ->
                    scope.launch {
                        val added = activeNovelScreenModel.runTranslatedDownloadActionSelection(
                            action = action,
                            amount = amount,
                            format = format,
                        )
                        val message = if (added > 0) {
                            context.stringResource(AYMR.strings.novel_download_queue_started_count, added)
                        } else {
                            context.stringResource(AYMR.strings.novel_translated_download_no_available)
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                    showNovelTranslatedDownloadDialog = false
                },
            )
        }

        if (showNovelTranslatedChapterPickerDialog && novelScreenModel != null) {
            val activeNovelScreenModel = novelScreenModel
            NovelDownloadChapterPickerDialog(
                title = context.stringResource(AYMR.strings.novel_translated_download_select_title),
                chapters = novelTranslatedPickerChapters,
                onDismissRequest = { showNovelTranslatedChapterPickerDialog = false },
                onConfirm = { chapterIds ->
                    scope.launch {
                        val added = activeNovelScreenModel.runTranslatedDownloadForSingleSelectionChapterIds(
                            chapterIds = chapterIds,
                            format = novelTranslatedPickerFormat,
                        )
                        val message = if (added > 0) {
                            context.stringResource(AYMR.strings.novel_download_queue_started_count, added)
                        } else {
                            context.stringResource(AYMR.strings.novel_translated_download_no_available)
                        }
                        snackbarHostState.showSnackbar(
                            message = message,
                            duration = SnackbarDuration.Short,
                        )
                    }
                    showNovelTranslatedChapterPickerDialog = false
                },
            )
        }

        val hasAnimeSearchQuery = state.searchQuery != null
        val hasMangaSearchQuery = mangaState.searchQuery != null
        val hasNovelSearchQuery = novelState.searchQuery != null
        val currentSection = if (isAurora) auroraCurrentSection else Section.Anime
        val currentSelectionMode = resolveAuroraLibrarySelectionMode(
            isAurora = isAurora,
            section = currentSection,
            animeSelectionMode = state.selectionMode,
            mangaSelectionMode = mangaState.selectionMode,
            novelSelectionMode = novelState.selectionMode,
        )

        BackHandler(
            enabled = currentSelectionMode ||
                hasAnimeSearchQuery ||
                (
                    isAurora &&
                        (hasMangaSearchQuery || hasNovelSearchQuery)
                    ),
        ) {
            when {
                currentSelectionMode -> {
                    when (currentSection) {
                        Section.Anime -> screenModel.clearSelection()
                        Section.Manga -> mangaScreenModel.clearSelection()
                        Section.Novel -> novelScreenModel?.clearSelection()
                        null -> Unit
                    }
                }
                isAurora -> {
                    when {
                        currentSection == Section.Novel && hasNovelSearchQuery -> novelScreenModel?.search(null)
                        currentSection == Section.Manga && hasMangaSearchQuery -> mangaScreenModel.search(null)
                        currentSection == Section.Anime && hasAnimeSearchQuery -> screenModel.search(null)
                        hasNovelSearchQuery -> novelScreenModel?.search(null)
                        hasMangaSearchQuery -> mangaScreenModel.search(null)
                        hasAnimeSearchQuery -> screenModel.search(null)
                    }
                }
                hasAnimeSearchQuery -> screenModel.search(null)
            }
        }

        LaunchedEffect(currentSelectionMode, state.dialog, mangaState.dialog, currentSection, isAurora) {
            HomeScreen.showBottomNav(!currentSelectionMode)
        }

        LaunchedEffect(isLoading) {
            if (!isLoading) {
                (context as? MainActivity)?.ready = true
            }
        }

        LaunchedEffect(Unit) {
            launch { queryEvent.receiveAsFlow().collect(screenModel::search) }
            launch {
                novelQueryEvent.receiveAsFlow().collect { query ->
                    pendingNovelSearchQuery = query
                    novelScreenModel?.search(query)
                }
            }
            launch { requestSettingsSheetEvent.receiveAsFlow().collectLatest { screenModel.showSettingsDialog() } }
            launch {
                requestSectionEvent.receiveAsFlow().collectLatest { section ->
                    if (!isAurora) return@collectLatest
                    val targetPage = when (section) {
                        Section.Anime -> sectionTabs.indexOfFirst { it.first == Section.Anime }
                        Section.Manga -> sectionTabs.indexOfFirst { it.first == Section.Manga }
                        Section.Novel -> novelTabIndex
                    }
                    if (targetPage in 0 until auroraPageCount && auroraPagerState.currentPage != targetPage) {
                        auroraPagerState.scrollToPage(targetPage)
                    }
                }
            }
        }
        LaunchedEffect(novelScreenModel, pendingNovelSearchQuery) {
            val query = pendingNovelSearchQuery ?: return@LaunchedEffect
            val activeNovelScreenModel = novelScreenModel ?: return@LaunchedEffect
            activeNovelScreenModel.search(query)
            pendingNovelSearchQuery = null
        }
    }

    // For invoking search from other screen
    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)
    private val novelQueryEvent = Channel<String>(capacity = Channel.BUFFERED)
    suspend fun searchNovel(query: String) {
        requestSection(Section.Novel)
        novelQueryEvent.send(query)
    }

    private val requestSectionEvent = Channel<Section>(capacity = Channel.BUFFERED)
    suspend fun requestSection(section: Section) = requestSectionEvent.send(section)
    suspend fun showNovelSection() = requestSection(Section.Novel)

    // For opening settings sheet in LibraryController
    private val requestSettingsSheetEvent = Channel<Unit>()
    private suspend fun requestOpenSettingsSheet() = requestSettingsSheetEvent.send(Unit)
}

@Composable
private fun AuroraLibraryPinnedHeader(
    title: String,
    tabs: List<TabContent>,
    selectedSectionIndex: Int,
    onSectionSelected: (Int) -> Unit,
    searchQuery: String?,
    onSearchQueryChange: (String?) -> Unit,
    onFilterClick: () -> Unit,
    topChromeVisible: Boolean,
    onRefreshCurrent: () -> Unit,
    onRefreshGlobal: () -> Unit,
    onOpenRandomEntry: () -> Unit,
    onImportEpub: (() -> Unit)?,
    categories: List<Category>,
    selectedCategoryIndex: Int,
    showCategories: Boolean,
    onCategorySelected: (Int) -> Unit,
    getCountForCategory: (Category) -> Int?,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    var isSearchExpanded by remember(selectedSectionIndex) { mutableStateOf(searchQuery != null) }
    var previousSearchQuery by remember(selectedSectionIndex) { mutableStateOf(searchQuery) }
    val isSearchActive = shouldShowAuroraSearchField(
        isSearchExpanded = isSearchExpanded,
        searchQuery = searchQuery,
    )
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, selectedSectionIndex) {
        when {
            previousSearchQuery == null && searchQuery != null -> isSearchExpanded = true
            previousSearchQuery != null && searchQuery == null -> isSearchExpanded = false
        }
        previousSearchQuery = searchQuery
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        AnimatedVisibility(
            visible = topChromeVisible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSearchActive) {
                        TextField(
                            value = searchQuery.orEmpty(),
                            onValueChange = { onSearchQueryChange(it.ifBlank { null }) },
                            placeholder = {
                                Text(
                                    text = stringResource(MR.strings.action_search),
                                    color = colors.textSecondary,
                                )
                            },
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = colors.textSecondary,
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        appHaptics.tap()
                                        isSearchExpanded = false
                                        onSearchQueryChange(null)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                    )
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = colors.cardBackground,
                                unfocusedContainerColor = colors.cardBackground,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = title,
                            color = colors.textPrimary,
                            fontSize = 22.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )

                        Row {
                            val tabContainerColor = if (colors.background.luminance() < 0.5f) {
                                Color.White.copy(alpha = 0.05f)
                            } else {
                                Color.Black.copy(alpha = 0.03f)
                            }
                            IconButton(
                                onClick = {
                                    appHaptics.tap()
                                    isSearchExpanded = true
                                },
                                modifier = Modifier
                                    .background(tabContainerColor, CircleShape)
                                    .size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = stringResource(MR.strings.action_search),
                                    tint = colors.textPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    appHaptics.tap()
                                    onFilterClick()
                                },
                                modifier = Modifier
                                    .background(tabContainerColor, CircleShape)
                                    .size(44.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FilterList,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.foundation.layout.Box {
                                IconButton(
                                    onClick = {
                                        appHaptics.tap()
                                        showMenu = true
                                    },
                                    modifier = Modifier
                                        .background(tabContainerColor, CircleShape)
                                        .size(44.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        tint = colors.textPrimary,
                                    )
                                }
                                AuroraEntryDropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    auroraLibraryPinnedHeaderMenuItems(
                                        includeImportEpub = onImportEpub != null,
                                    ).forEach { item ->
                                        AuroraEntryDropdownMenuItem(
                                            text = when (item) {
                                                AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent ->
                                                    stringResource(MR.strings.action_update_library)
                                                AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal ->
                                                    stringResource(MR.strings.pref_category_library_update)
                                                AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry ->
                                                    stringResource(MR.strings.action_open_random_manga)
                                                AuroraLibraryPinnedHeaderMenuItem.ImportEpub ->
                                                    stringResource(AYMR.strings.novel_library_import_epub)
                                            },
                                            leadingIcon = when (item) {
                                                AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent,
                                                AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal,
                                                -> Icons.Filled.Refresh
                                                AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry -> {
                                                    Icons.Filled.Shuffle
                                                }
                                                AuroraLibraryPinnedHeaderMenuItem.ImportEpub -> {
                                                    Icons.Filled.Add
                                                }
                                            },
                                            onClick = {
                                                when (item) {
                                                    AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent -> {
                                                        onRefreshCurrent()
                                                    }
                                                    AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal -> {
                                                        onRefreshGlobal()
                                                    }
                                                    AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry -> {
                                                        onOpenRandomEntry()
                                                    }
                                                    AuroraLibraryPinnedHeaderMenuItem.ImportEpub -> {
                                                        onImportEpub?.invoke()
                                                    }
                                                }
                                                showMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (tabs.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AuroraTabRow(
                        tabs = tabs.toImmutableList(),
                        selectedIndex = selectedSectionIndex,
                        onTabSelected = onSectionSelected,
                        scrollable = false,
                    )
                }
            }
        }

        if (showCategories && categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            AuroraLibraryCategoryTabs(
                categories = categories,
                selectedIndex = selectedCategoryIndex,
                onCategorySelected = onCategorySelected,
                getCountForCategory = getCountForCategory,
            )
        }
    }
}

internal enum class AuroraLibraryPinnedHeaderMenuItem {
    RefreshCurrent,
    RefreshGlobal,
    OpenRandomEntry,
    ImportEpub,
}

internal fun auroraLibraryPinnedHeaderMenuItems(
    includeImportEpub: Boolean,
): List<AuroraLibraryPinnedHeaderMenuItem> {
    return buildList {
        add(AuroraLibraryPinnedHeaderMenuItem.RefreshCurrent)
        add(AuroraLibraryPinnedHeaderMenuItem.RefreshGlobal)
        add(AuroraLibraryPinnedHeaderMenuItem.OpenRandomEntry)
        if (includeImportEpub) {
            add(AuroraLibraryPinnedHeaderMenuItem.ImportEpub)
        }
    }
}

@Composable
private fun AuroraLibraryCategoryTabs(
    categories: List<Category>,
    selectedIndex: Int,
    onCategorySelected: (Int) -> Unit,
    getCountForCategory: (Category) -> Int?,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val coercedSelected = coerceAuroraLibraryCategoryIndex(selectedIndex, categories.size)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colors.glass,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(horizontal = 2.dp),
        ) {
            itemsIndexed(
                items = categories,
                key = { _, category -> category.id },
            ) { index, category ->
                val isSelected = index == coercedSelected
                val badgeCount = getCountForCategory(category)
                val tabBackground = when {
                    isSelected && colors.isDark -> Color.White.copy(alpha = 0.16f)
                    isSelected -> colors.accentVariant
                    colors.isDark -> Color.Transparent
                    else -> colors.cardBackground
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(tabBackground)
                        .clickable {
                            appHaptics.tap()
                            onCategorySelected(index)
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = category.visualName,
                        color = if (isSelected) colors.textPrimary else colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (badgeCount != null) {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (isSelected) {
                                        colors.accent
                                    } else {
                                        colors.cardBackground
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badgeCount.toString(),
                                color = if (isSelected) colors.textOnAccent else colors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun resolveAuroraLibrarySection(
    sections: List<AnimeLibraryTab.Section>,
    page: Int,
): AnimeLibraryTab.Section? {
    return sections.getOrNull(page)
}

internal fun resolveAuroraLibrarySelectionMode(
    isAurora: Boolean,
    section: AnimeLibraryTab.Section?,
    animeSelectionMode: Boolean,
    mangaSelectionMode: Boolean,
    novelSelectionMode: Boolean,
): Boolean {
    if (!isAurora) return animeSelectionMode
    return when (section) {
        AnimeLibraryTab.Section.Anime -> animeSelectionMode
        AnimeLibraryTab.Section.Manga -> mangaSelectionMode
        AnimeLibraryTab.Section.Novel -> novelSelectionMode
        null -> false
    }
}

internal fun shouldShowAuroraLibraryCategoryTabs(section: AnimeLibraryTab.Section?): Boolean {
    return section == AnimeLibraryTab.Section.Anime ||
        section == AnimeLibraryTab.Section.Manga ||
        section == AnimeLibraryTab.Section.Novel
}

internal fun shouldShowAuroraLibraryCategoryTabsRow(
    section: AnimeLibraryTab.Section?,
    categoryCount: Int,
    showCategoryTabs: Boolean,
    searchQuery: String?,
): Boolean {
    if (!shouldShowAuroraLibraryCategoryTabs(section)) return false
    if (categoryCount <= 1) return false
    return showCategoryTabs || !searchQuery.isNullOrEmpty()
}

internal fun shouldShowAuroraSearchField(
    isSearchExpanded: Boolean,
    searchQuery: String?,
): Boolean {
    return isSearchExpanded || searchQuery != null
}

internal fun coerceAuroraLibraryCategoryIndex(requestedIndex: Int, categoryCount: Int): Int {
    if (categoryCount <= 0) return 0
    return requestedIndex.coerceIn(0, categoryCount - 1)
}

private fun NovelCategory.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = hidden,
        hiddenFromHomeHub = false,
    )
}
