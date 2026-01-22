package eu.kanade.tachiyomi.ui.updates

import android.widget.Toast
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreen
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.updates.anime.AnimeUpdatesAuroraContent
import eu.kanade.presentation.updates.manga.MangaUpdatesAuroraContent
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.updates.anime.AnimeUpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.anime.animeUpdatesTab
import eu.kanade.tachiyomi.ui.updates.manga.MangaUpdatesScreenModel
import eu.kanade.tachiyomi.ui.updates.manga.mangaUpdatesTab
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data object UpdatesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current.key == key
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_updates_enter)
            val index: UShort = when (currentNavigationStyle()) {
                NavStyle.MOVE_UPDATES_TO_MORE -> 5u
                NavStyle.MOVE_HISTORY_TO_MORE -> 2u
                NavStyle.MOVE_BROWSE_TO_MORE -> 2u
                NavStyle.MOVE_MANGA_TO_MORE -> 1u
            }
            return TabOptions(
                index = index,
                title = stringResource(MR.strings.label_recent_updates),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }
    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(DownloadsTab)
    }

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val uiPreferences = Injekt.get<UiPreferences>()
        val theme by uiPreferences.appTheme().collectAsState()
        val fromMore = currentNavigationStyle() == NavStyle.MOVE_UPDATES_TO_MORE

        if (theme.isAuroraStyle) {
            val navigator = LocalNavigator.currentOrThrow
            val showAnimeSection by uiPreferences.showAnimeSection().collectAsState()
            val showMangaSection by uiPreferences.showMangaSection().collectAsState()
            val internalErrorMessage = stringResource(MR.strings.internal_error)
            val updatingLibraryMessage = stringResource(MR.strings.updating_library)
            val updateAlreadyRunningMessage = stringResource(MR.strings.update_already_running)

            val animeScreenModel = rememberScreenModel { AnimeUpdatesScreenModel() }
            val animeState by animeScreenModel.state.collectAsState()
            val mangaScreenModel = rememberScreenModel { MangaUpdatesScreenModel() }
            val mangaState by mangaScreenModel.state.collectAsState()

            var selectedTab by rememberSaveable { mutableIntStateOf(TAB_ANIME) }

            val tabIds = remember(showAnimeSection, showMangaSection) {
                buildList {
                    if (showAnimeSection) {
                        add(TAB_ANIME)
                    }
                    if (showMangaSection) {
                        add(TAB_MANGA)
                    }
                }
            }

            val tabs = persistentListOf<TabContent>().builder().apply {
                if (showAnimeSection) {
                    add(
                        TabContent(
                            titleRes = AYMR.strings.label_anime,
                            content = { contentPadding, _ ->
                                AnimeUpdatesAuroraContent(
                                    items = animeState.getUiModel(),
                                    onAnimeClicked = {
                                        navigator.push(eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen(it))
                                    },
                                    onDownloadClicked = {},
                                    onRefresh = animeScreenModel::updateLibrary,
                                    contentPadding = PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                )
                            },
                        ),
                    )
                }
                if (showMangaSection) {
                    add(
                        TabContent(
                            titleRes = AYMR.strings.label_manga,
                            content = { contentPadding, _ ->
                                MangaUpdatesAuroraContent(
                                    items = mangaState.getUiModel(),
                                    onMangaClicked = {
                                        navigator.push(eu.kanade.tachiyomi.ui.entries.manga.MangaScreen(it))
                                    },
                                    onDownloadClicked = {},
                                    onRefresh = mangaScreenModel::updateLibrary,
                                    contentPadding = PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                )
                            },
                        ),
                    )
                }
            }.build()

            val initialPage = tabIds.indexOf(selectedTab).coerceAtLeast(0)
            val state = rememberPagerState(initialPage) { tabs.size }

            LaunchedEffect(state.currentPage, tabIds) {
                selectedTab = tabIds.getOrElse(state.currentPage) { TAB_ANIME }
            }

            LaunchedEffect(tabIds, selectedTab) {
                val targetIndex = tabIds.indexOf(selectedTab).takeIf { it >= 0 } ?: 0
                if (state.currentPage != targetIndex) {
                    state.scrollToPage(targetIndex)
                }
            }

            fun showLibraryUpdateToast(started: Boolean) {
                val msg = if (started) updatingLibraryMessage else updateAlreadyRunningMessage
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }

            LaunchedEffect(animeScreenModel) {
                animeScreenModel.events.collect { event ->
                    when (event) {
                        AnimeUpdatesScreenModel.Event.InternalError -> {
                            Toast.makeText(context, internalErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                        is AnimeUpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                            showLibraryUpdateToast(event.started)
                        }
                    }
                }
            }

            LaunchedEffect(mangaScreenModel) {
                mangaScreenModel.events.collect { event ->
                    when (event) {
                        MangaUpdatesScreenModel.Event.InternalError -> {
                            Toast.makeText(context, internalErrorMessage, Toast.LENGTH_SHORT).show()
                        }
                        is MangaUpdatesScreenModel.Event.LibraryUpdateTriggered -> {
                            showLibraryUpdateToast(event.started)
                        }
                    }
                }
            }

            TabbedScreenAurora(
                titleRes = null,
                tabs = tabs,
                state = state,
                isMangaTab = { tabIds.getOrNull(it) == TAB_MANGA },
                showTabs = false,
            )
        } else {
            TabbedScreen(
                titleRes = MR.strings.label_recent_updates,
                tabs = persistentListOf(
                    animeUpdatesTab(context, fromMore),
                    mangaUpdatesTab(context, fromMore),
                ),
            )
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}

private const val TAB_ANIME = 0
private const val TAB_MANGA = 1
