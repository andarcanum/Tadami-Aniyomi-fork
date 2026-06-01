package eu.kanade.tachiyomi.ui.download.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.TabContent
import tachiyomi.i18n.aniyomi.AYMR
import uy.kohesive.injekt.api.get

@Composable
fun Screen.novelDownloadTab(
    nestedScrollConnection: NestedScrollConnection,
): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val context = LocalContext.current
    val screenModel = rememberScreenModel { NovelDownloadQueueScreenModel() }
    val state by screenModel.state.collectAsStateWithLifecycle()

    return TabContent(
        titleRes = AYMR.strings.label_novel,
        searchEnabled = false,
        content = { contentPadding, _ ->
            NovelDownloadQueueScreen(
                contentPadding = contentPadding,
                screenModel = screenModel,
                state = state,
                nestedScrollConnection = nestedScrollConnection,
            )
        },
        numberTitle = state.queueCount,
        navigateUp = navigator::pop,
    )
}
