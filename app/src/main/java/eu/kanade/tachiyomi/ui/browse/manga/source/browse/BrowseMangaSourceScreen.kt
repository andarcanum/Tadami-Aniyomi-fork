package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifMangaSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.browse.manga.BrowseSourceContent
import eu.kanade.presentation.browse.manga.MissingSourceScreen
import eu.kanade.presentation.browse.manga.components.BrowseMangaSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.components.DuplicateEntryDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.TitleCarouselScreen
import eu.kanade.tachiyomi.ui.browse.TitleCarouselType
import eu.kanade.tachiyomi.ui.browse.manga.extension.details.MangaSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialog
import eu.kanade.tachiyomi.ui.browse.manga.migration.search.MigrateMangaDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.manga.model.StubMangaSource
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.source.local.entries.manga.LocalMangaSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseMangaSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    private val savedSearchId: Long? = null,
    private val parentScreen: cafe.adriel.voyager.core.screen.Screen? = null,
    // RESH-B1: genre requests travel as constructor args of a fresh instance (the GlobalSearch
    // query pattern) instead of the removed static queryEvent channel.
    private val genreQuery: String? = null,
    private val genresQuery: List<String>? = null,
) : Screen(), AssistContentScreen {

    private var assistUrl: String? = null

    override fun onProvideAssistUrl() = assistUrl

    @Composable
    override fun Content() {
        if (!ifMangaSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = if (parentScreen != null) {
            parentScreen.rememberScreenModel(tag = sourceId.toString()) {
                BrowseMangaSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        } else {
            rememberScreenModel {
                BrowseMangaSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val favoriteMangaUrls by screenModel.favoriteMangaUrls.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(
                    null,
                )
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubMangaSource) {
            MissingSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }

        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val uriHandler = LocalUriHandler.current
        val snackbarHostState = remember { SnackbarHostState() }

        val onHelpClick = { uriHandler.openUri(LocalMangaSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? HttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.getHomeUrl(),
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? HttpSource)?.getHomeUrl()
        }

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseMangaSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        // BRM-3: was navigator::pop - the smart navigateUp above (clears the
                        // toolbar query first when it is a non-user listing query) was dead
                        // code; the anime/novel toolbars use their smart variants.
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = {
                            navigator.push(MangaSourcePreferencesScreen(sourceId))
                        },
                        onSearch = { filterQuery ->
                            // BRM-4/RESH-B1: direct SM call (anime/novel etalon) - the static
                            // channel round-trip could suspend with no receiver and raced the
                            // pager screen's second collector.
                            screenModel.search(filterQuery)
                        },
                    )

                    // Sticky чип-бар листинга (прототип каталога): стеклянные плашки-пилюли
                    // с верхним димом; бар закреплён над списком как часть topBar.
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SourceListingChip(
                            text = stringResource(MR.strings.popular),
                            icon = Icons.Outlined.Favorite,
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                        )
                        if ((screenModel.source as CatalogueSource).supportsLatest) {
                            SourceListingChip(
                                text = stringResource(MR.strings.latest),
                                icon = Icons.Outlined.NewReleases,
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            // Бейдж числа активных фильтров: стейт не отдаёт счётчик —
                            // по требованию прототипа бейдж скрыт, пока count недоступен.
                            SourceListingChip(
                                text = stringResource(MR.strings.action_filter),
                                icon = Icons.Outlined.FilterList,
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                            )
                        }
                        state.savedSearches.forEach { (search, isActive) ->
                            SourceListingChip(
                                text = search.name,
                                selected = isActive,
                                onClick = { screenModel.openSavedSearch(search) },
                            )
                        }
                    }

                    TopBarGlowDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingManga = screenModel.mangaPagerFlowFlow.collectAsLazyPagingItems()
            BrowseSourceContent(
                source = screenModel.source,
                mangaList = pagingManga,
                favoriteMangaUrls = favoriteMangaUrls,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalSourceHelpClick = onHelpClick,
                onMangaClick = { manga ->
                    if (Injekt.get<SourcePreferences>().titleCarouselEnabled().get()) {
                        val snapshot = (0 until pagingManga.itemCount).mapNotNull { index -> pagingManga[index]?.id }
                        val index = snapshot.indexOf(manga.id)
                        // BFEED-17: -1 used to be coerced to 0 - when the clicked title was not
                        // in the loaded snapshot the carousel opened on an UNRELATED first
                        // title; fall back to the plain entry screen instead.
                        if (index < 0) {
                            navigator.push(MangaScreen(manga.id, true))
                        } else {
                            navigator.push(
                                TitleCarouselScreen(
                                    type = TitleCarouselType.Manga,
                                    sourceId = screenModel.source.id,
                                    initialTitleIds = snapshot,
                                    initialIndex = index,
                                    listingQuery = state.listing.query,
                                    filtersJson = state.filters
                                        .takeIf { it.isNotEmpty() }
                                        ?.let { screenModel.serializeFilters(it) },
                                ),
                            )
                        }
                    } else {
                        navigator.push(MangaScreen(manga.id, true))
                    }
                },
                onMangaLongClick = { manga ->
                    scope.launchIO {
                        val duplicateManga = screenModel.getDuplicateLibraryManga(manga)
                        when {
                            manga.favorite -> screenModel.setDialog(
                                BrowseMangaSourceScreenModel.Dialog.RemoveManga(manga),
                            )
                            duplicateManga != null -> screenModel.setDialog(
                                BrowseMangaSourceScreenModel.Dialog.AddDuplicateManga(
                                    manga,
                                    duplicateManga,
                                ),
                            )
                            else -> screenModel.addFavorite(manga)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseMangaSourceScreenModel.Dialog.Filter -> {
                SourceFilterMangaDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    // BRN-20: state.value inside composition bypasses snapshot observation -
                    // the saved-search list could render stale; use the collected state.
                    savedSearches = state.savedSearches,
                    onSaveSearch = screenModel::openSaveSearchDialog,
                    onOpenSavedSearch = screenModel::openSavedSearch,
                    onDeleteSavedSearch = {
                        screenModel.setDialog(BrowseMangaSourceScreenModel.Dialog.DeleteSavedSearch(it))
                    },
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.AddDuplicateManga -> {
                DuplicateEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.manga) },
                    onOpenEntry = { navigator.push(MangaScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.setDialog(
                            BrowseMangaSourceScreenModel.Dialog.Migrate(dialog.manga, dialog.duplicate),
                        )
                    },
                    openEntryLabel = stringResource(AYMR.strings.action_show_manga),
                )
            }

            is BrowseMangaSourceScreenModel.Dialog.Migrate -> {
                MigrateMangaDialog(
                    oldManga = dialog.oldManga,
                    newManga = dialog.newManga,
                    // BRM-1: was constructed INLINE - every recomposition of this when-scope
                    // (any browse state emission, rotation) recreated the SM mid-migration:
                    // isMigrating reset to false, a second tap started a PARALLEL migration of
                    // the same pair while the old coroutine kept running.
                    screenModel = rememberScreenModel { MigrateMangaDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(MangaScreen(dialog.oldManga.id)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.RemoveManga -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeMangaFavorite(dialog.manga)
                    },
                    entryToRemove = dialog.manga.title,
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.CreateSavedSearch -> {
                CreateSavedSearchDialog(
                    onDismiss = onDismissRequest,
                    onSave = { name -> screenModel.saveSearch(name) },
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.DeleteSavedSearch -> {
                DeleteSavedSearchDialog(
                    savedSearch = dialog.savedSearch,
                    onDismiss = onDismissRequest,
                    onConfirm = { screenModel.deleteSearch(dialog.savedSearch) },
                )
            }
            is BrowseMangaSourceScreenModel.Dialog.ChangeMangaCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = {
                        navigator.push(CategoriesTab)
                        CategoriesTab.showMangaCategory()
                    },
                    onConfirm = { include, _ ->
                        screenModel.changeMangaFavorite(dialog.manga)
                        screenModel.moveMangaToCategories(dialog.manga, include)
                    },
                )
            }
            else -> {}
        }

        // RESH-B1 (BRM-4/BGS-5): the static queryEvent Channel is gone. Its send() suspended
        // forever when no browse screen was composed, the pager screen's isVisible(current||
        // target) semantics allowed TWO collectors competing for one event, and a re-created
        // collector could replay nothing (rendezvous). Genre requests are constructor args of a
        // fresh screen instance now, applied exactly once per screen model - like listingQuery.
        LaunchedEffect(Unit) {
            screenModel.initGenreSearch(genreQuery, genresQuery)
        }
    }
}

@Composable
private fun CreateSavedSearchDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.save_search)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(AYMR.strings.saved_search_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(MR.strings.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

@Composable
private fun DeleteSavedSearchDialog(
    savedSearch: SavedSearch,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.saved_search_delete)) },
        text = { Text(stringResource(AYMR.strings.saved_search_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(MR.strings.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(MR.strings.action_cancel)) }
        },
    )
}

/**
 * Плашка-пилюля чип-бара каталога (prototype_source_catalog.html): стеклянная
 * пилюля (verticalGradient white .07→.03 dark / .60→.42 light), rim 1dp и
 * верхний дим 2dp; активная — accent-градиент + кромка accent .65 + белый текст.
 */
@Composable
private fun SourceListingChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = AuroraTheme.colors
    val chrome = chromeAccentDetails()
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (selected) Color.White else colors.textSecondary
    val appHaptics = LocalAppHaptics.current
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(shape)
                .background(
                    brush = if (selected) {
                        Brush.linearGradient(
                            listOf(
                                colors.accent.copy(alpha = 0.45f),
                                lerp(colors.accent, Color.Black, 0.45f).copy(alpha = 0.50f),
                            ),
                        )
                    } else {
                        Brush.verticalGradient(
                            if (colors.isDark) {
                                listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.60f), Color.White.copy(alpha = 0.42f))
                            },
                        )
                    },
                    shape = shape,
                )
                .border(
                    width = 1.dp,
                    color = when {
                        selected -> colors.accent.copy(alpha = 0.65f)
                        colors.isDark -> Color.White.copy(alpha = 0.10f)
                        else -> Color.Black.copy(alpha = 0.08f)
                    },
                    shape = shape,
                )
                .clickable {
                    appHaptics.tap()
                    onClick()
                }
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
        // Верхний дим плашки (цитатный язык): 2dp, гаснущий к краям.
        Box(modifier = Modifier.matchParentSize().clip(shape)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                if (selected) chrome.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
        }
    }
}

/** Светящийся двухслойный разделитель под чип-баром (язык Browse-хаба). */
@Composable
private fun TopBarGlowDivider() {
    val chrome = chromeAccentDetails()
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, chrome.copy(alpha = 0.12f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, chrome.copy(alpha = 0.45f), Color.Transparent),
                    ),
                ),
        )
    }
}
