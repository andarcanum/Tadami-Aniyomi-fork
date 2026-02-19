package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR

@Composable
fun Screen.novelDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val screenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
    val state by screenModel.state.collectAsState()

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            NovelDownloadQueueScreen(
                contentPadding = contentPadding,
                state = state,
                onRefresh = screenModel::refresh,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        numberTitle = state.downloadCount,
        navigateUp = navigator::pop,
    )
}
