package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.browse.InLibraryBadge
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.translation.rememberBrowseNovelTitleTranslation
import eu.kanade.presentation.library.components.CommonEntryItemDefaults
import eu.kanade.presentation.library.components.EntryComfortableGridItem
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.EntryListItem
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.source.novel.NovelPluginImageWarmupEffect
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.domain.entries.novel.model.Novel
import tachiyomi.domain.entries.novel.model.NovelCover
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.source.novel.model.StubNovelSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.EmptyScreenAction
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import tachiyomi.presentation.core.util.collectAsState as collectPreferenceAsState

internal fun novelBrowseItemKey(url: String?, index: Int): String {
    return "novel/${url.orEmpty()}#$index"
}

@Composable
fun BrowseNovelSourceContent(
    source: NovelSource?,
    novels: LazyPagingItems<StateFlow<Novel>>,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)? = null,
) {
    val context = LocalContext.current
    val translationPreferences = remember { Injekt.get<UiPreferences>() }
    val browseTitleTranslationEnabled by translationPreferences
        .auroraEntryTranslationEnabled()
        .collectPreferenceAsState()
    val browseTitleTranslationSourceFamilies by translationPreferences
        .auroraEntryTranslationSourceLanguages()
        .collectPreferenceAsState()
    val effectiveContentPadding = contentPadding

    val errorState = novels.loadState.refresh.takeIf { it is LoadState.Error }
        ?: novels.loadState.append.takeIf { it is LoadState.Error }

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        state.error.formattedMessage(context)
    }

    LaunchedEffect(errorState) {
        if (novels.itemCount > 0 && errorState != null && errorState is LoadState.Error) {
            val result = snackbarHostState.showSnackbar(
                message = getErrorMessage(errorState),
                actionLabel = context.stringResource(MR.strings.action_retry),
                duration = SnackbarDuration.Indefinite,
            )
            when (result) {
                SnackbarResult.Dismissed -> snackbarHostState.currentSnackbarData?.dismiss()
                SnackbarResult.ActionPerformed -> novels.retry()
            }
        }
    }

    if (novels.itemCount <= 0 && errorState != null && errorState is LoadState.Error) {
        EmptyScreen(
            modifier = Modifier.padding(effectiveContentPadding),
            message = getErrorMessage(errorState),
            actions = persistentListOf(
                EmptyScreenAction(
                    stringRes = MR.strings.action_retry,
                    icon = Icons.Outlined.Refresh,
                    onClick = novels::refresh,
                ),
            ),
        )
        return
    }

    if (novels.itemCount == 0 && novels.loadState.refresh is LoadState.Loading) {
        LoadingScreen(
            modifier = Modifier.padding(effectiveContentPadding),
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            NovelListContent(
                novels = novels,
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.ComfortableGrid -> {
            NovelComfortableGridContent(
                novels = novels,
                columns = GridCells.Adaptive(120.dp),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.CompactGrid -> {
            NovelCompactGridContent(
                novels = novels,
                columns = GridCells.Adaptive(96.dp),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                showTitle = true,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
        LibraryDisplayMode.CoverOnlyGrid -> {
            NovelCompactGridContent(
                novels = novels,
                columns = GridCells.Adaptive(96.dp),
                sourceLanguage = source?.lang,
                contentPadding = effectiveContentPadding,
                showTitle = false,
                translationEnabled = browseTitleTranslationEnabled,
                allowedSourceFamilies = browseTitleTranslationSourceFamilies,
                onNovelClick = onNovelClick,
                onNovelLongClick = onNovelLongClick,
            )
        }
    }
}

@Composable
private fun NovelListContent(
    novels: LazyPagingItems<StateFlow<Novel>>,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.value?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyColumn(
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.value?.url, index) },
        ) { index ->
            val novel by novels[index]?.collectAsState() ?: return@items
            val cover = novel.asBrowseNovelCover()
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            EntryListItem(
                title = translatedTitle,
                coverData = cover,
                coverAlpha = if (novel.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                badge = { InLibraryBadge(enabled = novel.favorite) },
                onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
                onClick = { onNovelClick(novel) },
            )
        }
    }
}

@Composable
private fun NovelComfortableGridContent(
    novels: LazyPagingItems<StateFlow<Novel>>,
    columns: GridCells,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.value?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (novels.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.value?.url, index) },
        ) { index ->
            val novel by novels[index]?.collectAsState() ?: return@items
            val cover = novel.asBrowseNovelCover()
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            EntryComfortableGridItem(
                title = translatedTitle,
                coverData = cover,
                coverAlpha = if (novel.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                coverBadgeStart = { InLibraryBadge(enabled = novel.favorite) },
                onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
                onClick = { onNovelClick(novel) },
            )
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
private fun NovelCompactGridContent(
    novels: LazyPagingItems<StateFlow<Novel>>,
    columns: GridCells,
    sourceLanguage: String?,
    contentPadding: PaddingValues,
    showTitle: Boolean,
    translationEnabled: Boolean,
    allowedSourceFamilies: Set<String>,
    onNovelClick: (Novel) -> Unit,
    onNovelLongClick: ((Novel) -> Unit)?,
) {
    val warmupTargets by remember(novels.itemCount) {
        derivedStateOf {
            buildList {
                val upperBound = minOf(novels.itemCount, BROWSE_NOVEL_WARMUP_WINDOW)
                for (index in 0 until upperBound) {
                    add(novels[index]?.value?.thumbnailUrl)
                }
            }
        }
    }
    NovelPluginImageWarmupEffect(urls = warmupTargets, key = warmupTargets)

    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.auroraCenteredMaxWidth(
            auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp,
        ),
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridVerticalSpacer),
        horizontalArrangement = Arrangement.spacedBy(CommonEntryItemDefaults.GridHorizontalSpacer),
    ) {
        if (novels.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novelBrowseItemKey(novels[index]?.value?.url, index) },
        ) { index ->
            val novel by novels[index]?.collectAsState() ?: return@items
            val cover = novel.asBrowseNovelCover()
            val translatedTitle = rememberBrowseNovelTitleTranslation(
                title = novel.title,
                sourceLanguage = sourceLanguage,
                enabled = translationEnabled,
                allowedSourceFamilies = allowedSourceFamilies,
            )
            EntryCompactGridItem(
                title = translatedTitle.takeIf { showTitle },
                coverData = cover,
                coverAlpha = if (novel.favorite) CommonEntryItemDefaults.BrowseFavoriteCoverAlpha else 1f,
                coverBadgeStart = { InLibraryBadge(enabled = novel.favorite) },
                onLongClick = onNovelLongClick?.let { callback -> { callback(novel) } } ?: {},
                onClick = { onNovelClick(novel) },
            )
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

private const val BROWSE_NOVEL_WARMUP_WINDOW = 12

internal fun Novel.asBrowseNovelCover(): NovelCover {
    return NovelCover(
        novelId = id,
        sourceId = source,
        isNovelFavorite = favorite,
        url = thumbnailUrl,
        lastModified = coverLastModified,
    )
}

@Composable
internal fun MissingNovelSourceScreen(
    source: StubNovelSource,
    navigateUp: () -> Unit,
) {
    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = source.name,
                navigateUp = navigateUp,
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        EmptyScreen(
            message = stringResource(MR.strings.source_not_installed, source.toString()),
            modifier = Modifier.padding(paddingValues),
        )
    }
}
