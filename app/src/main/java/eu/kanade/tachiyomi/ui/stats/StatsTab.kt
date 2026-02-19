package eu.kanade.tachiyomi.ui.stats

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.stats.anime.animeStatsTab
import eu.kanade.tachiyomi.ui.stats.manga.mangaStatsTab
import eu.kanade.tachiyomi.ui.stats.novel.novelStatsTab
import kotlinx.collections.immutable.toPersistentList
import tachiyomi.data.achievement.handler.AchievementHandler
import tachiyomi.data.achievement.model.AchievementEvent
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object StatsTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            return TabOptions(
                index = 8u,
                title = stringResource(MR.strings.label_stats),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()

        val tabs = statsContentTabs()
            .map { tab ->
                when (tab) {
                    StatsContentTab.ANIME -> animeStatsTab()
                    StatsContentTab.MANGA -> mangaStatsTab()
                    StatsContentTab.NOVEL -> novelStatsTab()
                }
            }
            .toPersistentList()
        val state = rememberPagerState { tabs.size }

        if (theme.isAuroraStyle) {
            TabbedScreenAurora(
                titleRes = MR.strings.label_stats,
                tabs = tabs,
                state = state,
                isMangaTab = { it == 1 },
                scrollable = false,
            )
        } else {
            TabbedScreen(
                titleRes = MR.strings.label_stats,
                tabs = tabs,
                state = state,
            )
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true

            // Track stats visit for achievement
            val achievementHandler = Injekt.get<AchievementHandler>()
            achievementHandler.trackFeatureUsed(AchievementEvent.Feature.STATS)
        }
    }
}

internal enum class StatsContentTab {
    ANIME,
    MANGA,
    NOVEL,
}

internal fun statsContentTabs(): List<StatsContentTab> {
    return listOf(
        StatsContentTab.ANIME,
        StatsContentTab.MANGA,
        StatsContentTab.NOVEL,
    )
}
