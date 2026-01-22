package eu.kanade.tachiyomi.ui.stats.anime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.more.stats.AnimeStatsAuroraContent
import eu.kanade.presentation.more.stats.AnimeStatsScreenContent
import eu.kanade.presentation.more.stats.StatsScreenState
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun Screen.animeStatsTab(): TabContent {
    val navigator = LocalNavigator.currentOrThrow
    val uiPreferences = Injekt.get<UiPreferences>()
    val theme by uiPreferences.appTheme().collectAsState()

    val screenModel = rememberScreenModel { AnimeStatsScreenModel() }
    val state by screenModel.state.collectAsState()

    if (state is StatsScreenState.Loading) {
        LoadingScreen()
    }

    return TabContent(
        titleRes = AYMR.strings.label_anime,
        content = { contentPadding, _ ->

            if (state is StatsScreenState.Loading) {
                LoadingScreen()
            } else {
                if (theme.isAuroraStyle) {
                    AnimeStatsAuroraContent(
                        state = state as StatsScreenState.SuccessAnime,
                        paddingValues = contentPadding,
                    )
                } else {
                    AnimeStatsScreenContent(
                        state = state as StatsScreenState.SuccessAnime,
                        paddingValues = contentPadding,
                    )
                }
            }
        },
        navigateUp = navigator::pop,
    )
}
