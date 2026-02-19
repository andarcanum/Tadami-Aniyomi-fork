package eu.kanade.tachiyomi.ui.browse.novel.source.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.RemoveEntryDialog
import eu.kanade.presentation.browse.novel.BrowseNovelSourceContent
import eu.kanade.presentation.browse.novel.MissingNovelSourceScreen
import eu.kanade.presentation.browse.novel.components.BrowseNovelSourceToolbar
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.entries.novel.DuplicateNovelDialog
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.novelsource.ConfigurableNovelSource
import eu.kanade.tachiyomi.novelsource.NovelCatalogueSource
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.NovelFilter
import eu.kanade.tachiyomi.novelsource.model.NovelFilterList
import eu.kanade.tachiyomi.source.novel.NovelSiteSource
import eu.kanade.tachiyomi.ui.browse.novel.extension.details.NovelSourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialog
import eu.kanade.tachiyomi.ui.browse.novel.migration.search.MigrateNovelDialogScreenModel
import eu.kanade.tachiyomi.ui.category.CategoriesTab
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import mihon.presentation.core.util.collectAsLazyPagingItems
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import java.util.Locale

data class BrowseNovelSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { BrowseNovelSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val haptic = LocalHapticFeedback.current
        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubNovelSource) {
            MissingNovelSourceScreen(
                source = screenModel.source as StubNovelSource,
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
                            source = screenModel.source,
                        )?.let { screen ->
                            { navigator.push(screen) }
                        },
                        onSearch = screenModel::search,
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == BrowseNovelSourceScreenModel.Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = {
                                Text(text = stringResource(MR.strings.popular))
                            },
                        )
                        if ((screenModel.source as NovelCatalogueSource).supportsLatest) {
                            FilterChip(
                                selected = state.listing == BrowseNovelSourceScreenModel.Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(BrowseNovelSourceScreenModel.Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.latest))
                                },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is BrowseNovelSourceScreenModel.Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = {
                                    Text(text = stringResource(MR.strings.action_filter))
                                },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            BrowseNovelSourceContent(
                source = screenModel.source,
                novels = screenModel.novelPagerFlowFlow.collectAsLazyPagingItems(),
                displayMode = screenModel.displayMode,
                snackbarHostState = snackbarHostState,
                contentPadding = paddingValues,
                onNovelClick = { novel -> navigator.push(NovelScreen(novel.id, true)) },
                onNovelLongClick = { novel ->
                    scope.launchIO {
                        val duplicateNovel = screenModel.getDuplicateLibraryNovel(novel)
                        when {
                            novel.favorite -> screenModel.setDialog(
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
                        onUpdate = { screenModel.setFilters(state.filters) },
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
                        screenModel = MigrateNovelDialogScreenModel(),
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
        is NovelFilter.Select<*> -> this.takeUnless { it.isSortLikeSelect() }
        is NovelFilter.Group<*> -> {
            val visibleChildren = state
                .filterIsInstance<NovelFilter<*>>()
                .mapNotNull { it.withoutSortFiltersForLatest() }
            if (visibleChildren.isEmpty()) null else LatestVisibleGroupFilter(name, visibleChildren)
        }
        else -> this
    }
}

private fun NovelFilter.Select<*>.isSortLikeSelect(): Boolean {
    val normalizedName = name.normalizedForSortChecks()
    if (sortNameTokens.any(normalizedName::contains)) return true

    val normalizedKey = pluginFilterKeyOrNull()?.normalizedForSortChecks().orEmpty()
    if (normalizedKey.isNotBlank() && sortKeyTokens.any(normalizedKey::contains)) return true

    val optionMatches = values.count { option ->
        val normalizedOption = option.toString().normalizedForSortChecks()
        sortOptionTokens.any(normalizedOption::contains)
    }
    return optionMatches >= 2
}

private fun NovelFilter.Select<*>.pluginFilterKeyOrNull(): String? {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
        val keyField = currentClass.declaredFields.firstOrNull {
            it.name == "key" && it.type == String::class.java
        }
        if (keyField != null) {
            return runCatching {
                keyField.isAccessible = true
                keyField.get(this) as? String
            }.getOrNull()
        }
        currentClass = currentClass.superclass
    }
    return null
}

private fun String.normalizedForSortChecks(): String {
    return lowercase(Locale.ROOT).trim()
}

private class LatestVisibleGroupFilter(
    name: String,
    state: List<NovelFilter<*>>,
) : NovelFilter.Group<NovelFilter<*>>(name, state)

private val sortNameTokens = listOf(
    "sort",
    "order",
    "сорт",
    "поряд",
)

private val sortKeyTokens = listOf(
    "sort",
    "order",
)

private val sortOptionTokens = listOf(
    "popular",
    "popularity",
    "latest",
    "newest",
    "updated",
    "update",
    "rating",
    "rank",
    "name",
    "title",
    "relevance",
    "asc",
    "desc",
    "a-z",
    "z-a",
    "популяр",
    "нов",
    "обнов",
    "рейтинг",
    "алф",
)

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
    source: NovelSource,
): NovelSourcePreferencesScreen? {
    if (source !is ConfigurableNovelSource) return null
    return NovelSourcePreferencesScreen(sourceId)
}
