package eu.kanade.tachiyomi.ui.browse.novel.source.browse

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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifNovelSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.browse.novel.BrowseNovelSourceContent
import eu.kanade.presentation.browse.novel.MissingNovelSourceScreen
import eu.kanade.presentation.browse.novel.components.BrowseNovelSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.novel.DuplicateNovelDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.browse.TitleCarouselScreen
import eu.kanade.tachiyomi.ui.browse.TitleCarouselType
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
data class BrowseNovelSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
    private val savedSearchId: Long? = null,
    private val parentScreen: cafe.adriel.voyager.core.screen.Screen? = null,
    // RESH-B1: genre requests travel as constructor args of a fresh instance (the GlobalSearch
    // query pattern) instead of the removed static queryEvent channel.
    private val genreQuery: String? = null,
    private val genresQuery: List<String>? = null,
) : Screen() {

    @Composable
    override fun Content() {
        // BRN-1: gate BEFORE the screen model is created (manga/anime etalon) - on a cold
        // deep-link the novel source manager is still initializing, the SM captured a
        // StubNovelSource and the screen was stuck on "Source not installed" permanently.
        if (!ifNovelSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = if (parentScreen != null) {
            parentScreen.rememberScreenModel(tag = sourceId.toString()) {
                BrowseNovelSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        } else {
            rememberScreenModel {
                BrowseNovelSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val favoriteNovelUrls by screenModel.favoriteNovelUrls.collectAsStateWithLifecycle()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current

        // RESH-B1 (BRN-10/BGS-5): the static queryEvent Channel is gone (send() suspended with
        // no composed browse screen; the pager screen's visibility semantics allowed two
        // collectors competing for one event). Genre requests are constructor args of a fresh
        // screen instance now, applied exactly once per screen model - like listingQuery.
        LaunchedEffect(Unit) {
            screenModel.initGenreSearch(genreQuery, genresQuery)
        }

        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubNovelSource) {
            MissingNovelSourceScreen(
                source = screenModel.source,
                navigateUp = navigateUp,
            )
            return
        }
        val sourceWebUrl = resolveNovelSourceWebUrl(screenModel.source)

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseNovelSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = sourceWebUrl?.let { url ->
                            {
                                navigator.push(
                                    WebViewScreen(
                                        url = url,
                                        initialTitle = screenModel.source.name,
                                        sourceId = screenModel.source.id,
                                    ),
                                )
                            }
                        },
                        onSettingsClick = novelSourcePreferencesScreenOrNull(
                            sourceId = sourceId,
                            isSourceConfigurable = state.isSourceConfigurable,
                        )?.let { screen ->
                            { navigator.push(screen) }
                        },
                        onSearch = screenModel::search,
                    )

                    // Sticky чип-бар листинга (прототип каталога): стеклянные плашки-пилюли
                    // с верхним димом; бар закреплён над списком как часть topBar.
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NovelListingChip(
                            text = stringResource(MR.strings.popular),
                            icon = Icons.Outlined.Favorite,
                            selected = state.listing == BrowseNovelSourceScreenModel.Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Popular)
                            },
                        )
                        if ((screenModel.source as NovelCatalogueSource).supportsLatest) {
                            NovelListingChip(
                                text = stringResource(MR.strings.latest),
                                icon = Icons.Outlined.NewReleases,
                                selected = state.listing == BrowseNovelSourceScreenModel.Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Latest)
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            // Бейдж числа активных фильтров: стейт не отдаёт счётчик —
                            // по требованию прототипа бейдж скрыт, пока count недоступен.
                            NovelListingChip(
                                text = stringResource(MR.strings.action_filter),
                                icon = Icons.Outlined.FilterList,
                                selected = state.listing is BrowseNovelSourceScreenModel.Listing.Search,
                                onClick = screenModel::openFilterSheet,
                            )
                        }
                        state.savedSearches.forEach { (search, isActive) ->
                            NovelListingChip(
                                text = search.name,
                                selected = isActive,
                                onClick = { screenModel.openSavedSearch(search) },
                            )
                        }
                    }

                    NovelTopBarGlowDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingNovels = screenModel.novelPagerFlowFlow.collectAsLazyPagingItems()
            BrowseNovelSourceContent(
                source = screenModel.source,
                novels = pagingNovels,
                favoriteNovelUrls = favoriteNovelUrls,
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onNovelClick = { novel ->
                    if (Injekt.get<SourcePreferences>().titleCarouselEnabled().get()) {
                        val snapshot = (0 until pagingNovels.itemCount).mapNotNull { index -> pagingNovels[index]?.id }
                        val index = snapshot.indexOf(novel.id)
                        // BFEED-17: -1 used to be coerced to 0 - the carousel opened on an
                        // UNRELATED first title; fall back to the plain entry screen.
                        if (index < 0) {
                            navigator.push(NovelScreen(novel.id, true))
                        } else {
                            navigator.push(
                                TitleCarouselScreen(
                                    type = TitleCarouselType.Novel,
                                    sourceId = screenModel.source.id,
                                    initialTitleIds = snapshot,
                                    initialIndex = index,
                                    listingQuery = state.listing.query,
                                    filtersJson = state.filters
                                        .takeIf { it.isNotEmpty() }
                                        ?.let { SavedSearchFilterSerializer.serialize(it) },
                                ),
                            )
                        }
                    } else {
                        navigator.push(NovelScreen(novel.id, true))
                    }
                },
                onNovelLongClick = { novel ->
                    scope.launchIO {
                        val duplicateNovel = screenModel.getDuplicateLibraryNovel(novel)
                        val isFavorite = novel.url in favoriteNovelUrls
                        when {
                            isFavorite -> screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.RemoveNovel(novel),
                            )
                            duplicateNovel != null -> screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel(
                                    novel = novel,
                                    duplicate = duplicateNovel,
                                ),
                            )
                            else -> screenModel.addFavorite(novel)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )

            val onDismissRequest = { screenModel.setDialog(null) }
            when (val dialog = state.dialog) {
                BrowseNovelSourceScreenModel.Dialog.Filter -> {
                    SourceFilterNovelDialog(
                        onDismissRequest = onDismissRequest,
                        filters = visibleNovelFiltersForListing(state.listing, state.filters),
                        onReset = screenModel::resetFilters,
                        onFilter = screenModel::applyFilters,
                        onUpdate = screenModel::setFilters,
                        // BRN-20: state.value inside composition bypasses snapshot observation;
                        // use the collected state.
                        savedSearches = state.savedSearches,
                        onSaveSearch = screenModel::openSaveSearchDialog,
                        onOpenSavedSearch = screenModel::openSavedSearch,
                        onDeleteSavedSearch = {
                            screenModel.setDialog(BrowseNovelSourceScreenModel.Dialog.DeleteSavedSearch(it))
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.AddDuplicateNovel -> {
                    DuplicateNovelDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = { screenModel.addFavorite(dialog.novel) },
                        onOpenNovel = { navigator.push(NovelScreen(dialog.duplicate.id, true)) },
                        onMigrate = {
                            screenModel.setDialog(
                                BrowseNovelSourceScreenModel.Dialog.Migrate(
                                    newNovel = dialog.novel,
                                    oldNovel = dialog.duplicate,
                                ),
                            )
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.Migrate -> {
                    MigrateNovelDialog(
                        oldNovel = dialog.oldNovel,
                        newNovel = dialog.newNovel,
                        // BRN-9: was constructed INLINE - recreated on every recomposition of
                        // this when-scope, resetting isMigrating mid-migration (see the manga site).
                        screenModel = rememberScreenModel { MigrateNovelDialogScreenModel() },
                        onDismissRequest = onDismissRequest,
                        onClickTitle = { navigator.push(NovelScreen(dialog.oldNovel.id)) },
                        onPopScreen = {
                            onDismissRequest()
                        },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.RemoveNovel -> {
                    RemoveEntryDialog(
                        onDismissRequest = onDismissRequest,
                        onConfirm = {
                            screenModel.changeNovelFavorite(dialog.novel)
                        },
                        entryToRemove = dialog.novel.title,
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.CreateSavedSearch -> {
                    CreateSavedSearchDialog(
                        onDismiss = onDismissRequest,
                        onSave = { name -> screenModel.saveSearch(name) },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.DeleteSavedSearch -> {
                    DeleteSavedSearchDialog(
                        savedSearch = dialog.savedSearch,
                        onDismiss = onDismissRequest,
                        onConfirm = { screenModel.deleteSearch(dialog.savedSearch) },
                    )
                }
                is BrowseNovelSourceScreenModel.Dialog.ChangeNovelCategory -> {
                    ChangeCategoryDialog(
                        initialSelection = dialog.initialSelection,
                        onDismissRequest = onDismissRequest,
                        onEditCategories = {
                            navigator.push(CategoriesTab)
                            CategoriesTab.showNovelCategory()
                        },
                        onConfirm = { include, _ ->
                            screenModel.changeNovelFavorite(dialog.novel)
                            screenModel.moveNovelToCategories(dialog.novel, include)
                        },
                    )
                }
                null -> Unit
            }
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

internal fun visibleNovelFiltersForListing(
    listing: BrowseNovelSourceScreenModel.Listing,
    filters: NovelFilterList,
): NovelFilterList {
    if (listing != BrowseNovelSourceScreenModel.Listing.Latest) return filters
    return NovelFilterList(filters.mapNotNull { it.withoutSortFiltersForLatest() })
}

private fun NovelFilter<*>.withoutSortFiltersForLatest(): NovelFilter<*>? {
    return when (this) {
        is NovelFilter.Sort -> null
        is NovelFilter.Group<*> -> {
            val visibleChildren = state
                .filterIsInstance<NovelFilter<*>>()
                .mapNotNull { it.withoutSortFiltersForLatest() }
            if (visibleChildren.isEmpty()) null else LatestVisibleGroupFilter(name, visibleChildren)
        }
        else -> this
    }
}

private class LatestVisibleGroupFilter(
    name: String,
    state: List<NovelFilter<*>>,
) : NovelFilter.Group<NovelFilter<*>>(name, state)

internal fun resolveNovelSourceWebUrl(source: NovelSource?): String? {
    val siteUrl = (source as? NovelSiteSource)?.siteUrl?.trim().orEmpty()
    if (siteUrl.isBlank()) return null

    val normalizedUrl = if (
        siteUrl.startsWith("http://", ignoreCase = true) ||
        siteUrl.startsWith("https://", ignoreCase = true)
    ) {
        siteUrl
    } else {
        "https://$siteUrl"
    }

    return normalizedUrl.toHttpUrlOrNull()?.toString()
}

internal fun novelSourcePreferencesScreenOrNull(
    sourceId: Long,
    isSourceConfigurable: Boolean,
): NovelSourcePreferencesScreen? {
    if (!isSourceConfigurable) return null
    return NovelSourcePreferencesScreen(sourceId)
}

/**
 * Плашка-пилюля чип-бара каталога (prototype_source_catalog.html): стеклянная
 * пилюля (verticalGradient white .07→.03 dark / .60→.42 light), rim 1dp и
 * верхний дим 2dp; активная — accent-градиент + кромка accent .65 + белый текст.
 */
@Composable
private fun NovelListingChip(
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
private fun NovelTopBarGlowDivider() {
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
