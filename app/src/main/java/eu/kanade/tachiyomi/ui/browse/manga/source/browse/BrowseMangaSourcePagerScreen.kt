package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.screen.Screen
import tachiyomi.presentation.core.screens.LoadingScreen

data class BrowseMangaSourcePagerScreen(
    val initialSourceId: Long,
    val sourceIds: List<Long>,
    val listingQuery: String? = null,
) : Screen {

    @Composable
    override fun Content() {
        val initialPage = remember(initialSourceId, sourceIds) {
            sourceIds.indexOf(initialSourceId).coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(initialPage = initialPage) { sourceIds.size }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val sourceId = sourceIds[page]
            val isVisible = page == pagerState.currentPage || page == pagerState.targetPage
            if (isVisible) {
                val screen = remember(sourceId) {
                    BrowseMangaSourceScreen(sourceId, listingQuery, parentScreen = this@BrowseMangaSourcePagerScreen)
                }
                screen.Content()
            } else {
                LoadingScreen()
            }
        }
    }
}
