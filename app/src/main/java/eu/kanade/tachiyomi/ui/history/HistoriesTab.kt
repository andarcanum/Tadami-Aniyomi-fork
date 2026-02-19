package eu.kanade.tachiyomi.ui.history

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.anime.AnimeHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.anime.animeHistoryTab
import eu.kanade.tachiyomi.ui.history.anime.resumeLastEpisodeSeenEvent
import eu.kanade.tachiyomi.ui.history.manga.MangaHistoryScreenModel
import eu.kanade.tachiyomi.ui.history.manga.mangaHistoryTab
import eu.kanade.tachiyomi.ui.history.novel.novelHistoryTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object HistoriesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_history_enter)
            val index: UShort = when (currentNavigationStyle()) {
                NavStyle.MOVE_HISTORY_TO_MORE -> 5u
                NavStyle.MOVE_BROWSE_TO_MORE -> 3u
                else -> 2u
            }
            return TabOptions(
                index = index,
                title = stringResource(MR.strings.history),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        resumeLastEpisodeSeenEvent.send(Unit)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val fromMore = currentNavigationStyle() == NavStyle.MOVE_HISTORY_TO_MORE
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()
        // Hoisted for history tab's search bar
        val mangaHistoryScreenModel = rememberScreenModel { MangaHistoryScreenModel() }
        val mangaSearchQuery by mangaHistoryScreenModel.query.collectAsState()

        val animeHistoryScreenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val animeSearchQuery by animeHistoryScreenModel.query.collectAsState()

        val tabs = historyContentTabs()
            .map { tab ->
                when (tab) {
                    HistoryContentTab.ANIME -> animeHistoryTab(context, fromMore)
                    HistoryContentTab.MANGA -> mangaHistoryTab(context, fromMore)
                    HistoryContentTab.NOVEL -> novelHistoryTab(context, fromMore)
                }
            }
            .toPersistentList()

        if (theme.isAuroraStyle) {
            TabbedScreenAurora(
                titleRes = MR.strings.label_recent_manga,
                tabs = tabs,
                mangaSearchQuery = mangaSearchQuery,
                onChangeMangaSearchQuery = mangaHistoryScreenModel::search,
                animeSearchQuery = animeSearchQuery,
                onChangeAnimeSearchQuery = animeHistoryScreenModel::search,
            )
        } else {
            TabbedScreen(
                titleRes = MR.strings.label_recent_manga,
                tabs = tabs,
                mangaSearchQuery = mangaSearchQuery,
                onChangeMangaSearchQuery = mangaHistoryScreenModel::search,
                animeSearchQuery = animeSearchQuery,
                onChangeAnimeSearchQuery = animeHistoryScreenModel::search,
            )
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1

internal enum class HistoryContentTab {
    ANIME,
    MANGA,
    NOVEL,
}

internal fun historyContentTabs(): List<HistoryContentTab> {
    return listOf(
        HistoryContentTab.ANIME,
        HistoryContentTab.MANGA,
        HistoryContentTab.NOVEL,
    )
}
