package eu.kanade.tachiyomi.ui.browse.manga.feed

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.FeedAddSearchDialog
import eu.kanade.presentation.browse.FeedAddSourceDialog
import eu.kanade.presentation.browse.FeedDeleteSourceDialog
import eu.kanade.presentation.browse.FeedOrderScreen
import eu.kanade.presentation.browse.manga.MangaFeedScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.feed.BaseFeedScreenModel.FeedDialog
import eu.kanade.tachiyomi.ui.browse.feed.BaseFeedScreenModel.FeedEvent
import eu.kanade.tachiyomi.ui.browse.manga.source.browse.BrowseMangaSourceScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.domain.source.manga.interactor.GetRemoteManga
import tachiyomi.domain.source.model.FeedListingType
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.mangaFeedTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { MangaFeedScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()
    val reorderRotation by animateFloatAsState(
        targetValue = if (state.isReordering) 90f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "reorderRotation",
    )

    return TabContent(
        titleRes = AYMR.strings.feed,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(AYMR.strings.feed_manage),
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
                    onAddClick = screenModel::openAddSourceDialog,
                )
            } else {
                MangaFeedScreen(
                    state = state,
                    contentPadding = contentPadding,
                    onClickSource = { source, item ->
                        when (item.feed.listingType) {
                            FeedListingType.SAVED_SEARCH -> {
                                navigator.push(BrowseMangaSourceScreen(source.id, null, item.feed.savedSearch))
                            }
                            FeedListingType.POPULAR -> {
                                navigator.push(BrowseMangaSourceScreen(source.id, GetRemoteManga.QUERY_POPULAR))
                            }
                            FeedListingType.LATEST -> {
                                navigator.push(BrowseMangaSourceScreen(source.id, GetRemoteManga.QUERY_LATEST))
                            }
                        }
                    },
                    onClickManga = { manga ->
                        navigator.push(MangaScreen(manga.id, true))
                    },
                    getMangaState = { manga -> screenModel.getManga(manga) },
                    onRefresh = screenModel::refresh,
                )
            }

            state.dialog?.let { dialog ->
                when (dialog) {
                    is FeedDialog.AddSource -> {
                        FeedAddSourceDialog(
                            sources = dialog.sources,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = screenModel::onSourceSelected,
                        )
                    }
                    is FeedDialog.AddSearch -> {
                        FeedAddSearchDialog(
                            source = dialog.source,
                            savedSearches = dialog.savedSearches,
                            onDismiss = screenModel::dismissDialog,
                            onAdd = { listingType, savedSearch ->
                                screenModel.addFeed(dialog.source, listingType, savedSearch)
                            },
                        )
                    }
                    is FeedDialog.DeleteSource -> {
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
                        FeedEvent.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
