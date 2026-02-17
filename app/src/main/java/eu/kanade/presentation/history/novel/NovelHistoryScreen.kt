package eu.kanade.presentation.history.novel

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.history.novel.components.NovelHistoryItem
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.tachiyomi.ui.history.novel.NovelHistoryScreenModel
import tachiyomi.domain.history.novel.model.NovelHistoryWithRelations
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.LocalDate

@Composable
fun NovelHistoryScreen(
    state: NovelHistoryScreenModel.State,
    snackbarHostState: SnackbarHostState,
    onClickCover: (novelId: Long) -> Unit,
    onClickResume: (chapterId: Long) -> Unit,
    onDialogChange: (NovelHistoryScreenModel.Dialog?) -> Unit,
) {
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()

    Scaffold(
        containerColor = if (theme.isAuroraStyle) Color.Transparent else MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { contentPadding ->
        state.list.let {
            if (it == null) {
                LoadingScreen(Modifier.padding(contentPadding))
            } else if (it.isEmpty()) {
                EmptyScreen(
                    stringRes = MR.strings.information_no_recent_manga,
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                NovelHistoryScreenContent(
                    history = it,
                    contentPadding = contentPadding,
                    onClickCover = { history -> onClickCover(history.novelId) },
                    onClickResume = { history -> onClickResume(history.chapterId) },
                    onClickDelete = { item -> onDialogChange(NovelHistoryScreenModel.Dialog.Delete(item)) },
                )
            }
        }
    }
}

@Composable
private fun NovelHistoryScreenContent(
    history: List<NovelHistoryUiModel>,
    contentPadding: PaddingValues,
    onClickCover: (NovelHistoryWithRelations) -> Unit,
    onClickResume: (NovelHistoryWithRelations) -> Unit,
    onClickDelete: (NovelHistoryWithRelations) -> Unit,
) {
    FastScrollLazyColumn(contentPadding = contentPadding) {
        items(
            items = history,
            key = { "novel-history-${it.hashCode()}" },
            contentType = {
                when (it) {
                    is NovelHistoryUiModel.Header -> "header"
                    is NovelHistoryUiModel.Item -> "item"
                }
            },
        ) { item ->
            when (item) {
                is NovelHistoryUiModel.Header -> {
                    ListGroupHeader(
                        modifier = Modifier.animateItemFastScroll(),
                        text = relativeDateText(item.date),
                    )
                }
                is NovelHistoryUiModel.Item -> {
                    val value = item.item
                    NovelHistoryItem(
                        modifier = Modifier.animateItemFastScroll(),
                        history = value,
                        onClickCover = { onClickCover(value) },
                        onClickResume = { onClickResume(value) },
                        onClickDelete = { onClickDelete(value) },
                    )
                }
            }
        }
    }
}

sealed interface NovelHistoryUiModel {
    data class Header(val date: LocalDate) : NovelHistoryUiModel
    data class Item(val item: NovelHistoryWithRelations) : NovelHistoryUiModel
}
