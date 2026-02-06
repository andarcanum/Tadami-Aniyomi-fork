package eu.kanade.tachiyomi.ui.browse.novel.source

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.NovelSourceOptionsDialog
import eu.kanade.presentation.browse.novel.NovelSourcesScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.tachiyomi.ui.browse.novel.source.browse.BrowseNovelSourceScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

@Composable
fun Screen.novelSourcesTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelSourcesScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel_sources,
        actions = persistentListOf(
            AppBar.Action(
                title = stringResource(MR.strings.action_filter),
                icon = Icons.Outlined.FilterList,
                onClick = { navigator.push(NovelSourcesFilterScreen()) },
            ),
        ),
        content = { contentPadding, snackbarHostState ->
            NovelSourcesScreen(
                state = state,
                contentPadding = contentPadding,
                onClickItem = { source, listing ->
                    navigator.push(BrowseNovelSourceScreen(source.id, listing.query))
                },
                onClickPin = screenModel::togglePin,
                onLongClickItem = screenModel::showSourceDialog,
                searchQuery = state.searchQuery,
                onChangeSearchQuery = screenModel::search,
                onToggleLanguage = screenModel::toggleLanguage,
            )

            state.dialog?.let { dialog ->
                NovelSourceOptionsDialog(
                    source = dialog.source,
                    onClickPin = {
                        screenModel.togglePin(dialog.source)
                        screenModel.closeDialog()
                    },
                    onClickDisable = {
                        screenModel.toggleSource(dialog.source)
                        screenModel.closeDialog()
                    },
                    onDismiss = screenModel::closeDialog,
                )
            }

            val internalErrString = stringResource(MR.strings.internal_error)
            LaunchedEffect(Unit) {
                screenModel.events.collectLatest { event ->
                    when (event) {
                        NovelSourcesScreenModel.Event.FailedFetchingSources -> {
                            launch { snackbarHostState.showSnackbar(internalErrString) }
                        }
                    }
                }
            }
        },
    )
}
