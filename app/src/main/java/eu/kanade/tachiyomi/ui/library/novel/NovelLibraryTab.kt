package eu.kanade.tachiyomi.ui.library.novel

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.library.DeleteLibraryEntryDialog
import eu.kanade.presentation.library.components.LibraryToolbar
import eu.kanade.presentation.library.novel.NovelLibraryContent
import eu.kanade.presentation.library.novel.NovelLibraryItem
import eu.kanade.presentation.library.novel.NovelLibrarySettingsDialog
import eu.kanade.presentation.library.novel.components.AddToSeriesDialog
import eu.kanade.presentation.library.novel.components.CreateSeriesDialog
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.data.download.novel.NovelDownloadCache
import eu.kanade.tachiyomi.data.library.novel.NovelLibraryUpdateJob
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.series.novel.NovelSeriesScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.novel.service.NovelSourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object NovelLibraryTab : Tab {

    @OptIn(ExperimentalAnimationGraphicsApi::class)
    override val options: TabOptions
        @Composable
        get() {
            val title = MR.strings.label_library
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_library_enter)
            return TabOptions(
                index = 2u,
                title = stringResource(title),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {}

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val screenModel = rememberScreenModel { NovelLibraryScreenModel() }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val libraryPreferences = remember { Injekt.get<LibraryPreferences>() }
        val sourceManager = remember { Injekt.get<NovelSourceManager>() }
        val downloadCache = remember { Injekt.get<NovelDownloadCache>() }
        val useSeparateDisplayModePerMedia by libraryPreferences
            .separateDisplayModePerMedia()
            .collectAsStateWithLifecycle()
        val displayModePreference = remember(useSeparateDisplayModePerMedia) {
            if (useSeparateDisplayModePerMedia) {
                libraryPreferences.novelDisplayMode()
            } else {
                libraryPreferences.displayMode()
            }
        }
        val displayMode by displayModePreference.collectAsStateWithLifecycle()
        val showDownloadBadge by libraryPreferences.downloadBadge().collectAsStateWithLifecycle()
        val showUnreadBadge by libraryPreferences.unreadBadge().collectAsStateWithLifecycle()
        val showLanguageBadge by libraryPreferences.languageBadge().collectAsStateWithLifecycle()
        val configuration = LocalConfiguration.current
        val columnPreference = remember(configuration.orientation) {
            if (configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                libraryPreferences.novelLandscapeColumns()
            } else {
                libraryPreferences.novelPortraitColumns()
            }
        }
        val columns by columnPreference.collectAsStateWithLifecycle()
        val downloadedIds by downloadCache.downloadedIds.collectAsStateWithLifecycle()
        val downloadedNovelIds = remember(state.library, showDownloadBadge, downloadedIds) {
            if (!showDownloadBadge) return@remember emptySet<Long>()
            val libraryNovelIds = state.library.values.flatten().mapNotNullTo(HashSet()) {
                (it as? NovelLibraryItem.Single)?.libraryNovel?.novel?.id
            }
            downloadedIds.intersect(libraryNovelIds)
        }
        val sourceLanguageByNovelId = remember(state.library, showLanguageBadge) {
            if (!showLanguageBadge) return@remember emptyMap()

            state.library.values.flatten().mapNotNull { item ->
                val source = (item as? NovelLibraryItem.Single)?.libraryNovel?.novel?.source ?: return@mapNotNull null
                item.id to sourceManager.getOrStub(source).lang
            }.toMap()
        }
        val snackbarHostState = remember { SnackbarHostState() }

        val onClickRefresh: () -> Unit = {
            val started = NovelLibraryUpdateJob.startNow(context)
            scope.launch {
                val msgRes = if (started) MR.strings.updating_library else MR.strings.update_already_running
                snackbarHostState.showSnackbar(context.stringResource(msgRes))
            }
        }
        val epubImportLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri ->
                if (uri != null) {
                    scope.launchIO {
                        try {
                            screenModel.importEpub(uri)
                            snackbarHostState.showSnackbar(
                                context.stringResource(AYMR.strings.novel_library_import_success),
                                duration = SnackbarDuration.Short,
                            )
                        } catch (_: Exception) {
                            snackbarHostState.showSnackbar(
                                context.stringResource(AYMR.strings.novel_library_import_failed),
                                duration = SnackbarDuration.Short,
                            )
                        }
                    }
                }
            },
        )

        Scaffold(
            topBar = { scrollBehavior ->
                val selectedCount = state.selection.size
                LibraryToolbar(
                    hasActiveFilters = state.hasActiveFilters,
                    selectedCount = selectedCount,
                    title = state.getToolbarTitle(
                        defaultTitle = stringResource(MR.strings.label_library),
                        defaultCategoryTitle = stringResource(MR.strings.label_default),
                        page = screenModel.activeCategoryIndex,
                    ),
                    onClickUnselectAll = screenModel::clearSelection,
                    onClickSelectAll = { screenModel.selectAll(screenModel.activeCategoryIndex) },
                    onClickInvertSelection = { screenModel.invertSelection(screenModel.activeCategoryIndex) },
                    onClickFilter = screenModel::showSettingsDialog,
                    onClickRefresh = onClickRefresh,
                    onClickGlobalUpdate = onClickRefresh,
                    onClickOpenRandomEntry = {
                        scope.launch {
                            val randomItem = state.library.values.flatten().randomOrNull()
                            if (randomItem != null) {
                                if (randomItem is NovelLibraryItem.Series) {
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
                    onClickImportEpub = { epubImportLauncher.launch(arrayOf("application/epub+zip")) },
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = screenModel::search,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isLibraryEmpty -> {
                    EmptyScreen(
                        stringRes = MR.strings.information_empty_library,
                        modifier = Modifier.padding(contentPadding),
                    )
                }
                else -> {
                    NovelLibraryContent(
                        categories = state.categories,
                        searchQuery = state.searchQuery,
                        selection = state.selection,
                        contentPadding = contentPadding,
                        currentPage = { screenModel.activeCategoryIndex },
                        hasActiveFilters = state.hasActiveFilters,
                        showPageTabs = state.showCategoryTabs,
                        onChangeCurrentPage = { screenModel.activeCategoryIndex = it },
                        onNovelClicked = { item ->
                            if (item is NovelLibraryItem.Series) {
                                navigator.push(NovelSeriesScreen(item.librarySeries.id))
                            } else {
                                navigator.push(NovelScreen(item.id))
                            }
                        },
                        onToggleSelection = screenModel::toggleSelection,
                        onToggleRangeSelection = screenModel::toggleRangeSelection,
                        onRefresh = { category ->
                            val categoryId = category?.id ?: -1L
                            val started = NovelLibraryUpdateJob.startNow(context, categoryId = categoryId)
                            started
                        },
                        getNumberOfNovelForCategory = { category ->
                            state.getNovelCountForCategory(category)
                        },
                        displayMode = displayMode,
                        columns = columns,
                        showDownloadBadge = showDownloadBadge,
                        downloadedNovelIds = downloadedNovelIds,
                        showUnreadBadge = showUnreadBadge,
                        showLanguageBadge = showLanguageBadge,
                        sourceLanguageByNovelId = sourceLanguageByNovelId,
                        getLibraryForPage = { page ->
                            state.getLibraryItemsByPage(page)
                        },
                    )
                }
            }
        }

        LaunchedEffect(Unit) {
            queryEvent.receiveAsFlow().collectLatest { screenModel.search(it) }
        }

        when (val dialog = state.dialog) {
            NovelLibraryScreenModel.Dialog.Settings -> {
                NovelLibrarySettingsDialog(
                    onDismissRequest = screenModel::closeDialog,
                    screenModel = screenModel,
                )
            }
            is NovelLibraryScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = screenModel::closeDialog,
                    onEditCategories = {
                        navigator.push(CategoriesTab)
                        CategoriesTab.showNovelCategory()
                    },
                    onConfirm = { include, exclude ->
                        screenModel.updateNovelCategories(dialog.novels, include, exclude)
                    },
                )
            }
            is NovelLibraryScreenModel.Dialog.DeleteNovels -> {
                DeleteLibraryEntryDialog(
                    containsLocalEntry = false,
                    onDismissRequest = screenModel::closeDialog,
                    onConfirm = { deleteFromLibrary, deleteChapters ->
                        screenModel.removeNovels(dialog.novels, deleteFromLibrary, deleteChapters)
                        screenModel.clearSelection()
                    },
                    isManga = false,
                )
            }
            NovelLibraryScreenModel.Dialog.CreateSeries -> {
                CreateSeriesDialog(
                    onDismissRequest = screenModel::closeDialog,
                    onCreate = screenModel::createSeries,
                )
            }
            is NovelLibraryScreenModel.Dialog.AddToSeries -> {
                AddToSeriesDialog(
                    series = dialog.series,
                    onDismissRequest = screenModel::closeDialog,
                    onSelect = screenModel::addSelectionToSeries,
                    onCreateSeries = screenModel::openCreateSeries,
                )
            }
            null -> {}
        }
    }

    private val queryEvent = Channel<String>()
    suspend fun search(query: String) = queryEvent.send(query)
}
