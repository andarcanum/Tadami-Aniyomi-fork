package eu.kanade.tachiyomi.ui.deeplink.novel

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.entries.novel.NovelScreen
import eu.kanade.tachiyomi.ui.reader.novel.NovelReaderScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen

class DeepLinkNovelScreen(
    val query: String = "",
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val screenModel = rememberScreenModel {
            DeepLinkNovelScreenModel(query = query)
        }
        val state by screenModel.state.collectAsStateWithLifecycle()
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.action_search_hint),
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            when (state) {
                is DeepLinkNovelScreenModel.State.Loading -> {
                    LoadingScreen(Modifier.padding(contentPadding))
                }

                is DeepLinkNovelScreenModel.State.NoResults -> {
                    // BGS-4/CONTROL-6: pop to the GlobalNovelSearchScreen MainActivity pushed
                    // right before this screen - replace() created a duplicate stack and the
                    // back press re-ran the whole fan-out search (see the manga site).
                    LaunchedEffect(Unit) {
                        navigator.pop()
                    }
                }

                is DeepLinkNovelScreenModel.State.Result -> {
                    val resultState = state as DeepLinkNovelScreenModel.State.Result
                    if (resultState.chapterId == null) {
                        navigator.replace(
                            NovelScreen(
                                resultState.novel.id,
                            ),
                        )
                    } else {
                        navigator.replace(NovelReaderScreen(resultState.chapterId, resultState.novel.source))
                    }
                }
            }
        }
    }
}
