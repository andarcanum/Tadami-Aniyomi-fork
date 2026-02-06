package eu.kanade.presentation.browse.novel

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import eu.kanade.presentation.browse.BrowseSourceLoadingItem
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.entries.components.ItemCover
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.novelsource.NovelSource
import eu.kanade.tachiyomi.novelsource.model.SNovel
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.core.common.i18n.stringResource
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

@Composable
fun BrowseNovelSourceContent(
    source: NovelSource?,
    novels: LazyPagingItems<SNovel>,
    displayMode: LibraryDisplayMode,
    snackbarHostState: SnackbarHostState,
    contentPadding: PaddingValues,
    onNovelClick: (SNovel) -> Unit,
) {
    val context = LocalContext.current

    val errorState = novels.loadState.refresh.takeIf { it is LoadState.Error }
        ?: novels.loadState.append.takeIf { it is LoadState.Error }

    val getErrorMessage: (LoadState.Error) -> String = { state ->
        with(context) { state.error.formattedMessage }
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
            modifier = Modifier.padding(contentPadding),
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
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    when (displayMode) {
        LibraryDisplayMode.List -> {
            NovelListContent(
                novels = novels,
                contentPadding = contentPadding,
                onNovelClick = onNovelClick,
            )
        }
        LibraryDisplayMode.ComfortableGrid -> {
            NovelGridContent(
                novels = novels,
                columns = GridCells.Adaptive(120.dp),
                contentPadding = contentPadding,
                showTitle = true,
                onNovelClick = onNovelClick,
            )
        }
        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> {
            NovelGridContent(
                novels = novels,
                columns = GridCells.Adaptive(96.dp),
                contentPadding = contentPadding,
                showTitle = displayMode != LibraryDisplayMode.CoverOnlyGrid,
                onNovelClick = onNovelClick,
            )
        }
    }
}

@Composable
private fun NovelListContent(
    novels: LazyPagingItems<SNovel>,
    contentPadding: PaddingValues,
    onNovelClick: (SNovel) -> Unit,
) {
    LazyColumn(contentPadding = contentPadding) {
        items(
            items = novels.itemSnapshotList.items,
            key = { it.url },
        ) { novel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = { onNovelClick(novel) })
                    .padding(
                        horizontal = MaterialTheme.padding.medium,
                        vertical = MaterialTheme.padding.small,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemCover.Book(
                    data = novel.thumbnail_url,
                    modifier = Modifier.fillMaxWidth(0.2f),
                )
                Column(
                    modifier = Modifier
                        .padding(start = MaterialTheme.padding.medium)
                        .weight(1f),
                ) {
                    Text(
                        text = novel.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!novel.author.isNullOrBlank()) {
                        Text(
                            text = novel.author.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelGridContent(
    novels: LazyPagingItems<SNovel>,
    columns: GridCells,
    contentPadding: PaddingValues,
    showTitle: Boolean,
    onNovelClick: (SNovel) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding + PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (novels.loadState.prepend is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }

        items(
            count = novels.itemCount,
            key = { index -> novels[index]?.url ?: index },
        ) { index ->
            val novel = novels[index] ?: return@items
            Column(
                modifier = Modifier
                    .combinedClickable(onClick = { onNovelClick(novel) }),
            ) {
                ItemCover.Book(
                    data = novel.thumbnail_url,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showTitle) {
                    Text(
                        text = novel.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }

        if (novels.loadState.refresh is LoadState.Loading || novels.loadState.append is LoadState.Loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BrowseSourceLoadingItem()
            }
        }
    }
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
