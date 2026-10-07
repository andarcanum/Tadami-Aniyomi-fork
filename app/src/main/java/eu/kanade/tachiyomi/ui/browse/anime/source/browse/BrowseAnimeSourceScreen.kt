package eu.kanade.tachiyomi.ui.browse.anime.source.browse

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
import eu.kanade.core.util.ifAnimeSourcesLoaded
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.anime.BrowseAnimeSourceContent
import eu.kanade.presentation.browse.anime.MissingSourceScreen
import eu.kanade.presentation.browse.anime.components.BrowseAnimeSourceToolbar
import eu.kanade.presentation.browse.components.chromeAccentDetails
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.components.DuplicateEntryDialog
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.AssistContentScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.ui.browse.TitleCarouselScreen
import eu.kanade.tachiyomi.ui.browse.TitleCarouselType
import eu.kanade.tachiyomi.ui.browse.anime.extension.details.AnimeSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.anime.season.MigrateSeasonSelectScreen
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialog
import eu.kanade.tachiyomi.ui.browse.anime.migration.search.MigrateAnimeDialogScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.search.SavedSearchFilterSerializer
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BrowseAnimeSourceScreen(
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
        if (!ifAnimeSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = if (parentScreen != null) {
            parentScreen.rememberScreenModel(tag = sourceId.toString()) {
                BrowseAnimeSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        } else {
            rememberScreenModel {
                BrowseAnimeSourceScreenModel(sourceId, listingQuery, savedSearchId)
            }
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        val favoriteAnimeUrls by screenModel.favoriteAnimeUrls.collectAsStateWithLifecycle()

        val navigator = LocalNavigator.currentOrThrow
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(
                    null,
                )
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubAnimeSource) {
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

        val onHelpClick = { uriHandler.openUri(LocalAnimeSource.HELP_URL) }
        val onWebViewClick = f@{
            val source = screenModel.source as? AnimeHttpSource ?: return@f
            navigator.push(
                WebViewScreen(
                    url = source.baseUrl,
                    initialTitle = source.name,
                    sourceId = source.id,
                ),
            )
        }

        LaunchedEffect(screenModel.source) {
            assistUrl = (screenModel.source as? AnimeHttpSource)?.baseUrl
        }

        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseAnimeSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = onWebViewClick,
                        onHelpClick = onHelpClick,
                        onSettingsClick = { navigator.push(AnimeSourcePreferencesScreen(sourceId)) },
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
                        AnimeListingChip(
                            text = stringResource(MR.strings.popular),
                            icon = Icons.Outlined.Favorite,
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                        )
                        if ((screenModel.source as AnimeCatalogueSource).supportsLatest) {
                            AnimeListingChip(
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
                            AnimeListingChip(
                                text = stringResource(MR.strings.action_filter),
                                icon = Icons.Outlined.FilterList,
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                            )
                        }
                        state.savedSearches.forEach { (search, isActive) ->
                            AnimeListingChip(
                                text = search.name,
                                selected = isActive,
                                onClick = { screenModel.openSavedSearch(search) },
                            )
                        }
                    }

                    AnimeTopBarGlowDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            val pagingAnime = screenModel.animePagerFlowFlow.collectAsLazyPagingItems()
            BrowseAnimeSourceContent(
                source = screenModel.source,
                animeList = pagingAnime,
                favoriteAnimeUrls = favoriteAnimeUrls,
                columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onWebViewClick = onWebViewClick,
                onHelpClick = { uriHandler.openUri(Constants.URL_HELP) },
                onLocalAnimeSourceHelpClick = onHelpClick,
                onAnimeClick = { anime ->
                    if (Injekt.get<SourcePreferences>().titleCarouselEnabled().get()) {
                        val snapshot = (0 until pagingAnime.itemCount).mapNotNull { index -> pagingAnime[index]?.id }
                        val index = snapshot.indexOf(anime.id)
                        // BFEED-17: -1 used to be coerced to 0 - the carousel opened on an
                        // UNRELATED first title; fall back to the plain entry screen.
                        if (index < 0) {
                            navigator.push(AnimeScreen(anime.id, true))
                        } else {
                            navigator.push(
                                TitleCarouselScreen(
                                    type = TitleCarouselType.Anime,
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
                        navigator.push(AnimeScreen(anime.id, true))
                    }
                },
                onAnimeLongClick = { anime ->
                    scope.launchIO {
                        val duplicateAnime = screenModel.getDuplicateAnimelibAnime(anime)
                        when {
                            anime.favorite -> screenModel.setDialog(
                                BrowseAnimeSourceScreenModel.Dialog.RemoveAnime(anime),
                            )
                            duplicateAnime != null -> screenModel.setDialog(
                                BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime(
                                    anime,
                                    duplicateAnime,
                                ),
                            )
                            else -> screenModel.addFavorite(anime)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                },
            )
        }

        val onDismissRequest = { screenModel.setDialog(null) }
        when (val dialog = state.dialog) {
            is BrowseAnimeSourceScreenModel.Dialog.Filter -> {
                SourceFilterAnimeDialog(
                    onDismissRequest = onDismissRequest,
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                    savedSearches = screenModel.state.value.savedSearches,
                    onSaveSearch = screenModel::openSaveSearchDialog,
                    onOpenSavedSearch = screenModel::openSavedSearch,
                    onDeleteSavedSearch = {
                        screenModel.setDialog(BrowseAnimeSourceScreenModel.Dialog.DeleteSavedSearch(it))
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.AddDuplicateAnime -> {
                DuplicateEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = { screenModel.addFavorite(dialog.anime) },
                    onOpenEntry = { navigator.push(AnimeScreen(dialog.duplicate.id)) },
                    onMigrate = {
                        screenModel.setDialog(
                            BrowseAnimeSourceScreenModel.Dialog.Migrate(dialog.anime, dialog.duplicate),
                        )
                    },
                    openEntryLabel = stringResource(AYMR.strings.action_show_anime),
                )
            }

            is BrowseAnimeSourceScreenModel.Dialog.Migrate -> {
                MigrateAnimeDialog(
                    oldAnime = dialog.oldAnime,
                    newAnime = dialog.newAnime,
                    // BRA-4/BRM-1: was constructed INLINE - recreated on every recomposition of
                    // this when-scope, resetting isMigrating mid-migration (see the manga site).
                    screenModel = rememberScreenModel { MigrateAnimeDialogScreenModel() },
                    onDismissRequest = onDismissRequest,
                    onClickTitle = { navigator.push(AnimeScreen(dialog.oldAnime.id)) },
                    onClickSeasons = { navigator.push(MigrateSeasonSelectScreen(dialog.oldAnime, dialog.newAnime)) },
                    onPopScreen = {
                        onDismissRequest()
                    },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.RemoveAnime -> {
                RemoveEntryDialog(
                    onDismissRequest = onDismissRequest,
                    onConfirm = {
                        screenModel.changeAnimeFavorite(dialog.anime)
                    },
                    entryToRemove = dialog.anime.title,
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.CreateSavedSearch -> {
                CreateSavedSearchDialog(
                    onDismiss = onDismissRequest,
                    onSave = { name -> screenModel.saveSearch(name) },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.DeleteSavedSearch -> {
                DeleteSavedSearchDialog(
                    savedSearch = dialog.savedSearch,
                    onDismiss = onDismissRequest,
                    onConfirm = { screenModel.deleteSearch(dialog.savedSearch) },
                )
            }
            is BrowseAnimeSourceScreenModel.Dialog.ChangeAnimeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = onDismissRequest,
                    onEditCategories = { navigator.push(CategoriesTab) },
                    onConfirm = { include, _ ->
                        screenModel.changeAnimeFavorite(dialog.anime)
                        screenModel.moveAnimeToCategories(dialog.anime, include)
                    },
                )
            }
            else -> {}
        }

        // RESH-B1 (BRA-3/BGS-5): the static queryEvent Channel is gone (send() suspended with
        // no composed browse screen; the pager screen's visibility semantics allowed two
        // collectors competing for one event). Genre requests are constructor args of a fresh
        // screen instance now, applied exactly once per screen model - like listingQuery.
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
private fun AnimeListingChip(
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
private fun AnimeTopBarGlowDivider() {
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
