package eu.kanade.tachiyomi.ui.updates.novel

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.updates.novel.NovelUpdatesScreen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.home.HomeScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.material.icons.outlined.SelectAll
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.i18n.MR

@Composable
fun Screen.novelUpdatesTab(
    context: Context,
    fromMore: Boolean,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelUpdatesScreenModel() }
    val state by screenModel.state.collectAsState()
    val scope = rememberCoroutineScope()

    val navigateUp: (() -> Unit)? = if (fromMore) {
        {
            if (navigator.lastItem == HomeScreen) {
                scope.launch { HomeScreen.openTab(HomeScreen.Tab.NovelLib()) }
            } else {
                navigator.pop()
            }
        }
    } else {
        null
    }

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            NovelUpdatesScreen(
                state = state,
                lastUpdated = screenModel.lastUpdated,
                contentPadding = contentPadding,
                onNovelClick = { novelId -> navigator.push(NovelScreen(novelId)) },
                onChapterClick = { chapterId -> navigator.push(NovelReaderScreen(chapterId)) },
                onToggleSelection = screenModel::toggleSelection,
                onMultiBookmarkClicked = screenModel::bookmarkUpdates,
                onMultiMarkAsReadClicked = screenModel::markUpdatesRead,
            )
        },
        actions = if (state.selected.isNotEmpty()) {
            persistentListOf(
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_select_all),
                    icon = Icons.Outlined.SelectAll,
                    onClick = { screenModel.toggleAllSelection(true) },
                ),
                AppBar.Action(
                    title = context.stringResource(MR.strings.action_select_inverse),
                    icon = Icons.Outlined.FlipToBack,
                    onClick = { screenModel.invertSelection() },
                ),
            )
        } else {
            persistentListOf()
        },
        navigateUp = navigateUp,
    )
}
