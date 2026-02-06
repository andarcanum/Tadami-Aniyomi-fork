package eu.kanade.tachiyomi.ui.reader.novel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.reader.novel.NovelReaderScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

class NovelReaderScreen(
    private val chapterId: Long,
) : eu.kanade.presentation.util.Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { NovelReaderScreenModel(chapterId) }
        val state by screenModel.state.collectAsStateWithLifecycle()

        when (val currentState = state) {
            is NovelReaderScreenModel.State.Loading -> LoadingScreen()
            is NovelReaderScreenModel.State.Error -> {
                val message = currentState.message ?: stringResource(MR.strings.unknown_error)
                EmptyScreen(message = message)
            }
            is NovelReaderScreenModel.State.Success -> NovelReaderScreen(
                state = currentState,
                onBack = navigator::pop,
                onReadingProgress = screenModel::updateReadingProgress,
                onOpenPreviousChapter = { previousChapterId ->
                    navigator.replace(NovelReaderScreen(previousChapterId))
                },
                onOpenNextChapter = { nextChapterId ->
                    navigator.replace(NovelReaderScreen(nextChapterId))
                },
            )
        }
    }
}
