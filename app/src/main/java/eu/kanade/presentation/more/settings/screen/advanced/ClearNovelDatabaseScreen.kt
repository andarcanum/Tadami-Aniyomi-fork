package eu.kanade.presentation.more.settings.screen.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.browse.novel.components.NovelSourceIcon
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AuroraFrostCancel
import eu.kanade.presentation.components.AuroraFrostConfirm
import eu.kanade.presentation.components.AuroraFrostDialog
import eu.kanade.presentation.more.settings.LocalSettingsUiStyle
import eu.kanade.presentation.more.settings.SettingsScaffold
import eu.kanade.presentation.more.settings.SettingsUiStyle
import eu.kanade.presentation.more.settings.canScroll
import eu.kanade.presentation.more.settings.rememberResolvedSettingsUiStyle
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.translation.profile.TranslationProfileServices
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchUI
import tachiyomi.core.common.util.lang.withNonCancellableContext
import tachiyomi.domain.source.novel.interactor.GetNovelSourcesWithNonLibraryNovels
import tachiyomi.domain.source.novel.model.NovelSourceWithCount
import tachiyomi.domain.source.novel.model.Source
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.novel.`data`.NovelDatabase
import tachiyomi.presentation.core.components.LazyColumnWithAction
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.selectedBackground
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class ClearNovelDatabaseScreen : Screen() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val model = rememberScreenModel { ClearNovelDatabaseScreenModel() }
        val state by model.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val uiStyle = rememberResolvedSettingsUiStyle()
        val listState = rememberLazyListState()

        when (val s = state) {
            is ClearNovelDatabaseScreenModel.State.Loading -> LoadingScreen()
            is ClearNovelDatabaseScreenModel.State.Ready -> {
                if (s.showConfirmation) {
                    val dialogContent: @Composable () -> Unit = {
                        Text(text = stringResource(AYMR.strings.clear_database_confirmation))
                    }
                    val onConfirm = {
                        scope.launchUI {
                            model.removeNovelsBySourceId()
                            model.clearSelection()
                            model.hideConfirmation()
                            context.toast(MR.strings.clear_database_completed)
                        }
                    }
                    if (LocalSettingsUiStyle.current == SettingsUiStyle.Aurora) {
                        AuroraFrostDialog(
                            onDismiss = model::hideConfirmation,
                            title = stringResource(AYMR.strings.pref_clear_novel_database),
                            footer = {
                                AuroraFrostCancel(
                                    label = stringResource(MR.strings.action_cancel),
                                    onClick = model::hideConfirmation,
                                )
                                AuroraFrostConfirm(
                                    label = stringResource(MR.strings.action_ok),
                                    onClick = { onConfirm() },
                                )
                            },
                        ) {
                            dialogContent()
                        }
                    } else {
                        AlertDialog(
                            onDismissRequest = model::hideConfirmation,
                            confirmButton = {
                                TextButton(onClick = { onConfirm() }) {
                                    Text(text = stringResource(MR.strings.action_ok))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = model::hideConfirmation) {
                                    Text(text = stringResource(MR.strings.action_cancel))
                                }
                            },
                            text = {
                                dialogContent()
                            },
                        )
                    }
                }

                SettingsScaffold(
                    title = stringResource(AYMR.strings.pref_clear_novel_database),
                    uiStyle = uiStyle,
                    onBackPressed = navigator::pop,
                    topBarCanScroll = { listState.canScroll() },
                    actions = {
                        if (s.items.isNotEmpty()) {
                            AppBarActions(
                                actions = persistentListOf(
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_all),
                                        icon = Icons.Outlined.SelectAll,
                                        onClick = model::selectAll,
                                    ),
                                    AppBar.Action(
                                        title = stringResource(MR.strings.action_select_inverse),
                                        icon = Icons.Outlined.FlipToBack,
                                        onClick = model::invertSelection,
                                    ),
                                ),
                            )
                        }
                    },
                ) { contentPadding ->
                    if (s.items.isEmpty()) {
                        EmptyScreen(
                            message = stringResource(MR.strings.database_clean),
                            modifier = Modifier.padding(contentPadding),
                        )
                    } else {
                        LazyColumnWithAction(
                            contentPadding = contentPadding,
                            state = listState,
                            actionLabel = stringResource(MR.strings.action_delete),
                            actionEnabled = s.selection.isNotEmpty(),
                            onClickAction = model::showConfirmation,
                        ) {
                            items(s.items) { sourceWithCount ->
                                ClearDatabaseItem(
                                    source = sourceWithCount.source,
                                    count = sourceWithCount.count,
                                    isSelected = s.selection.contains(sourceWithCount.source.id),
                                    onClickSelect = { model.toggleSelection(sourceWithCount.source) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ClearDatabaseItem(
        source: Source,
        count: Long,
        isSelected: Boolean,
        onClickSelect: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .selectedBackground(isSelected)
                .clickable(onClick = onClickSelect)
                .padding(horizontal = 8.dp)
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NovelSourceIcon(source = source)
            Column(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            ) {
                Text(
                    text = source.visualName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(text = stringResource(MR.strings.clear_database_source_item_count, count))
            }
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClickSelect() },
            )
        }
    }
}

private class ClearNovelDatabaseScreenModel : ScreenModel {
    private val getSourcesWithNonLibraryNovels: GetNovelSourcesWithNonLibraryNovels = Injekt.get()
    private val database: NovelDatabase = Injekt.get()

    private val selectionState = MutableStateFlow<List<Long>>(emptyList())
    private val showConfirmationState = MutableStateFlow(false)

    val state: StateFlow<State> = combine(
        getSourcesWithNonLibraryNovels.subscribe()
            .map { list -> list.sortedBy { it.source.name } },
        selectionState,
        showConfirmationState,
    ) { items, selection, showConfirmation ->
        State.Ready(
            items = items,
            selection = selection,
            showConfirmation = showConfirmation,
        )
    }.stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), State.Loading)

    suspend fun removeNovelsBySourceId() = withNonCancellableContext {
        val state = state.value as? State.Ready ?: return@withNonCancellableContext
        database.novelsQueries.deleteNovelsNotInLibraryBySourceIds(state.selection)
        database.novel_historyQueries.removeResettedHistory()
        TranslationProfileServices.repository.pruneOrphanNovelAssignments()
    }

    fun toggleSelection(source: Source) {
        selectionState.update { selection ->
            if (source.id in selection) selection - source.id else selection + source.id
        }
    }

    fun clearSelection() {
        selectionState.update { emptyList() }
    }

    fun selectAll() {
        selectionState.update {
            (state.value as? State.Ready)?.items?.fastMap { it.source.id } ?: it
        }
    }

    fun invertSelection() {
        selectionState.update {
            val ready = state.value as? State.Ready ?: return@update it
            ready.items.fastMap { it.source.id }.filterNot { id -> id in ready.selection }
        }
    }

    fun showConfirmation() {
        showConfirmationState.update { true }
    }

    fun hideConfirmation() {
        showConfirmationState.update { false }
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Ready(
            val items: List<NovelSourceWithCount>,
            val selection: List<Long> = emptyList(),
            val showConfirmation: Boolean = false,
        ) : State
    }
}
