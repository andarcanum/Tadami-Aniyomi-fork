package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.injectLazy

data object BrowseTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            val isSelected = LocalTabNavigator.current.current is BrowseTab
            val image = AnimatedImageVector.animatedVectorResource(R.drawable.anim_browse_enter)
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.browse),
                icon = rememberAnimatedVectorPainter(image, isSelected),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(GlobalAnimeSearchScreen())
    }

    private val uiPreferences: UiPreferences by injectLazy()

    private val switchToTabNumberChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    fun showExtension() {
        switchToTabNumberChannel.trySend(TAB_MANGA_EXTENSIONS)
    }

    fun showAnimeExtension() {
        switchToTabNumberChannel.trySend(TAB_ANIME_EXTENSIONS)
    }

    private const val TAB_ANIME_EXTENSIONS = 1
    private const val TAB_MANGA_EXTENSIONS = 4

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val showMangaSection by uiPreferences.showMangaSection().collectAsState()
        val colors = AuroraTheme.colors

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsState()

        val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsState()

        val animeTabs = buildList {
            add(animeSourcesTab())
            add(animeExtensionsTab(animeExtensionsScreenModel))
            add(migrateAnimeSourceTab())
        }.toPersistentList()

        val mangaTabs = buildList {
            add(mangaSourcesTab())
            add(mangaExtensionsTab(mangaExtensionsScreenModel))
            add(migrateMangaSourceTab())
        }.toPersistentList()

        var isAnimeTab by rememberSaveable { mutableStateOf(true) }
        val currentTabs = if (isAnimeTab) animeTabs else mangaTabs
        val state = rememberPagerState { currentTabs.size }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundGradient),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabbedScreenAurora(
                    titleRes = MR.strings.browse,
                    tabs = currentTabs,
                    state = state,
                    mangaSearchQuery = if (!isAnimeTab) mangaExtensionsState.searchQuery else null,
                    onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
                    animeSearchQuery = if (isAnimeTab) animeExtensionsState.searchQuery else null,
                    onChangeAnimeSearchQuery = animeExtensionsScreenModel::search,
                    isMangaTab = { !isAnimeTab },
                    scrollable = true,
                    applyStatusBarsPadding = true,
                    extraHeaderContent = {
                        if (showMangaSection) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AuroraTabRow(
                                tabs = kotlinx.collections.immutable.persistentListOf(
                                    TabContent(AYMR.strings.label_anime, content = { _, _ -> }),
                                    TabContent(AYMR.strings.label_manga, content = { _, _ -> }),
                                ),
                                selectedIndex = if (isAnimeTab) 0 else 1,
                                onTabSelected = { isAnimeTab = it == 0 },
                                scrollable = false,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    },
                )
            }
        }

        LaunchedEffect(currentTabs.size) {
            val lastIndex = currentTabs.lastIndex
            if (lastIndex >= 0 && state.currentPage > lastIndex) {
                state.scrollToPage(lastIndex)
            }
        }

        LaunchedEffect(Unit) {
            switchToTabNumberChannel.receiveAsFlow()
                .collectLatest { targetIndex ->
                    if (targetIndex == TAB_MANGA_EXTENSIONS && showMangaSection) {
                        isAnimeTab = false
                        // Extensions is index 1 in the manga sub-list
                        state.scrollToPage(1)
                    } else if (targetIndex == TAB_ANIME_EXTENSIONS) {
                        isAnimeTab = true
                        // Extensions is index 1 in the anime sub-list
                        state.scrollToPage(1)
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
