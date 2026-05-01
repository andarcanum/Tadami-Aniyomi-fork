package eu.kanade.tachiyomi.ui.browse.anime.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedOrderScreen
import eu.kanade.presentation.browse.anime.AnimeFeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.ui.browse.anime.source.browse.BrowseAnimeSourceScreen
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.util.system.LocaleHelper
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.source.anime.interactor.GetRemoteAnime
import tachiyomi.domain.source.model.SavedSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import java.util.TreeMap

@Composable
fun Screen.animeFeedTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { AnimeFeedScreenModel() }
    val state by screenModel.state.collectAsState()
    val reorderRotation by animateFloatAsState(
        targetValue = if (state.isReordering) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "reorderRotation",
    )

    return TabContent(
        titleRes = AYMR.strings.feed,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(AYMR.strings.feed_add),
                icon = Icons.Outlined.Add,
                onClick = screenModel::openAddSourceDialog,
            ),
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.SwapVert,
                iconRotation = reorderRotation,
                onClick = screenModel::toggleReordering,
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            if (state.isReordering) {
                FeedOrderScreen(
                    isLoading = state.isLoading,
                    isEmpty = state.isEmpty,
                    items = state.items,
                    itemFeed = { it.feed },
                    itemTitle = { it.title },
                    itemSubtitle = { it.subtitle },
                    onClickDelete = { feed -> screenModel.openDeleteDialog(feed) },
                    onChangeOrder = { feed, newIndex -> screenModel.reorderFeed(feed, newIndex) },
                )
            } else {
                AnimeFeedScreen(
                    state = state,
                    contentPadding = contentPadding,
                    onClickSource = { source, item ->
                        if (item.feed.savedSearch != null) {
                            navigator.push(BrowseAnimeSourceScreen(source.id, null, item.feed.savedSearch))
                        } else {
                            navigator.push(BrowseAnimeSourceScreen(source.id, GetRemoteAnime.QUERY_LATEST))
                        }
                    },
                    onClickAnime = { anime ->
                        navigator.push(AnimeScreen(anime.id, true))
                    },
                    getAnimeState = { anime -> screenModel.getAnime(anime) },
                    onRefresh = screenModel::refresh,
                )
            }

            state.dialog?.let { dialog ->
                when (dialog) {
                    is AnimeFeedScreenModel.Dialog.AddSource -> {
                        FeedAddSourceDialog(
                            sources = dialog.sources,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = { source -> screenModel.onSourceSelected(source) },
                        )
                    }
                    is AnimeFeedScreenModel.Dialog.AddSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.savedSearches,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = { savedSearch -> screenModel.addFeed(dialog.source, savedSearch) },
                        )
                    }
                    is AnimeFeedScreenModel.Dialog.DeleteSource -> {
                        FeedDeleteSourceDialog(
                            source = dialog.source,
                            onDismiss = screenModel::dismissDialog,
                            onConfirm = { screenModel.removeSource(dialog.feed) },
                        )
                    }
                }
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        AnimeFeedScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun FeedAddSourceDialog(
    sources: List<AnimeCatalogueSource>,
    onDismiss: () -> Unit,
    onAdd: (AnimeCatalogueSource) -> Unit,
) {
    val grouped = remember(sources) {
        TreeMap<String, MutableList<AnimeCatalogueSource>>().apply {
            sources.forEach { source ->
                val langName = LocaleHelper.getLocalizedDisplayName(source.lang)
                getOrPut(langName) { mutableListOf() }.add(source)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.feed_add)) },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                grouped.forEach { (lang, langSources) ->
                    item(key = "header_$lang") {
                        Text(
                            text = lang,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    items(langSources, key = { it.id }) { source ->
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAdd(source) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = source.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = LocaleHelper.getLocalizedDisplayName(source.lang),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FeedAddSearchDialog(
    source: AnimeCatalogueSource,
    savedSearches: List<SavedSearch>,
    onDismiss: () -> Unit,
    onAdd: (SavedSearch?) -> Unit,
) {
    var selected by remember { mutableStateOf(-1) }
    val latestLabel = stringResource(AYMR.strings.feed_latest)
    val popularLabel = stringResource(AYMR.strings.feed_popular)

    val options = remember(savedSearches, source.supportsLatest) {
        buildList {
            if (source.supportsLatest) add(null as SavedSearch?)
            add(null as SavedSearch?)
            savedSearches.forEach { add(it) }
        }
    }
    val labels = remember(options, latestLabel, popularLabel) {
        buildList {
            if (source.supportsLatest) add(latestLabel)
            add(popularLabel)
            savedSearches.forEach { add(it.name) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(source.name) },
        text = {
            LazyColumn {
                items(labels.size) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == index, onClick = { selected = index })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(labels[index], style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (selected >= 0) {
                        val savedSearch = options[selected]
                        onAdd(savedSearch)
                    }
                },
                enabled = selected >= 0,
            ) {
                Text(stringResource(MR.strings.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}

@Composable
private fun FeedDeleteSourceDialog(
    source: AnimeCatalogueSource,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AYMR.strings.feed_delete_source_title)) },
        text = { Text(stringResource(AYMR.strings.feed_delete_source_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(MR.strings.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
    )
}
