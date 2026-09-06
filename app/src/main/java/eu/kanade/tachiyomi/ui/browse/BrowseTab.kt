package eu.kanade.tachiyomi.ui.browse

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import com.tadami.aurora.R
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.UserProfilePreferences
import eu.kanade.presentation.components.AuroraTabRow
import eu.kanade.presentation.components.TabContent
import eu.kanade.presentation.components.TabbedScreenAurora
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.AuroraDeviceClass
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.anime.extension.AnimeExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.anime.extension.animeExtensionsTab
import eu.kanade.tachiyomi.ui.browse.anime.feed.animeFeedTab
import eu.kanade.tachiyomi.ui.browse.anime.migration.sources.migrateAnimeSourceTab
import eu.kanade.tachiyomi.ui.browse.anime.source.animeSourcesTab
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.extension.MangaExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.manga.extension.mangaExtensionsTab
import eu.kanade.tachiyomi.ui.browse.manga.feed.mangaFeedTab
import eu.kanade.tachiyomi.ui.browse.manga.migration.sources.migrateMangaSourceTab
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.ui.browse.manga.source.mangaSourcesTab
import eu.kanade.tachiyomi.ui.browse.novel.extension.NovelExtensionsScreenModel
import eu.kanade.tachiyomi.ui.browse.novel.extension.novelExtensionsTab
import eu.kanade.tachiyomi.ui.browse.novel.feed.novelFeedTab
import eu.kanade.tachiyomi.ui.browse.novel.migration.sources.migrateNovelSourceTab
import eu.kanade.tachiyomi.ui.browse.novel.source.globalsearch.GlobalNovelSearchScreen
import eu.kanade.tachiyomi.ui.browse.novel.source.novelSourcesTab
import eu.kanade.tachiyomi.ui.main.MainActivity
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsStateWithLifecycle
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
        // Respect the section the user is actually working in: re-tapping Browse from the
        // manga section must open the manga global search, not the anime one.
        val screen = when (resolveInitialSection()) {
            BrowseSection.Manga -> GlobalMangaSearchScreen()
            BrowseSection.Novel -> GlobalNovelSearchScreen()
            BrowseSection.Anime -> GlobalAnimeSearchScreen()
        }
        // BGS-12: re-tapping Browse while the matching global search is already on top used to
        // push another copy (identical screens stacked, each running a full fan-out search on
        // reveal).
        if (navigator.lastItem?.let { it::class == screen::class } == true) return
        navigator.push(screen)
    }

    private val uiPreferences: UiPreferences by injectLazy()
    private val userProfilePreferences: UserProfilePreferences by injectLazy()

    /** Initial/last browse section from preferences, validated against the enabled sections. */
    private fun resolveInitialSection(): BrowseSection {
        val enabled = buildBrowseSections(
            showAnimeSection = uiPreferences.showAnimeSection().get(),
            showMangaSection = uiPreferences.showMangaSection().get(),
            showNovelSection = uiPreferences.showNovelSection().get(),
        )
        val stored = userProfilePreferences.browseLastSection().get()
        return enabled.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            ?: enabled.firstOrNull()
            ?: BrowseSection.Anime
    }

    // BFEED-25 (residual, accepted-mitigated): buffered capacity 1 + DROP_OLDEST, consumed
    // once by the tab's LaunchedEffect collector; senders are HomeScreen's deep-link routing
    // (which since BFEED-23/24 only fires for VISIBLE sections) - an event sent while the tab
    // is not composed is consumed on its next composition (intended for the deep-link jump).
    private val switchToTabNumberChannel = Channel<Int>(1, BufferOverflow.DROP_OLDEST)

    internal enum class BrowseSection {
        Anime,
        Manga,
        Novel,
    }

    internal fun buildBrowseSections(
        showAnimeSection: Boolean,
        showMangaSection: Boolean,
        showNovelSection: Boolean,
    ): List<BrowseSection> {
        return buildList {
            if (showAnimeSection) add(BrowseSection.Anime)
            if (showMangaSection) add(BrowseSection.Manga)
            if (showNovelSection) add(BrowseSection.Novel)
        }
    }

    internal fun extensionTabIndex(hideFeedTab: Boolean): Int {
        return if (hideFeedTab) 1 else 2
    }

    fun showExtension() {
        switchToTabNumberChannel.trySend(TAB_MANGA_EXTENSIONS)
    }

    fun showAnimeExtension() {
        switchToTabNumberChannel.trySend(TAB_ANIME_EXTENSIONS)
    }

    fun showNovelExtension() {
        switchToTabNumberChannel.trySend(TAB_NOVEL_EXTENSIONS)
    }

    private const val TAB_ANIME_EXTENSIONS = 1
    private const val TAB_MANGA_EXTENSIONS = 4
    private const val TAB_NOVEL_EXTENSIONS = 7

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val showAnimeSection by uiPreferences.showAnimeSection().collectAsStateWithLifecycle()
        val showMangaSection by uiPreferences.showMangaSection().collectAsStateWithLifecycle()
        val showNovelSection by uiPreferences.showNovelSection().collectAsStateWithLifecycle()
        val colors = AuroraTheme.colors

        val animeExtensionsScreenModel = rememberScreenModel { AnimeExtensionsScreenModel() }
        val animeExtensionsState by animeExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val mangaExtensionsScreenModel = rememberScreenModel { MangaExtensionsScreenModel() }
        val mangaExtensionsState by mangaExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val novelExtensionsScreenModel = rememberScreenModel { NovelExtensionsScreenModel() }
        val novelExtensionsState by novelExtensionsScreenModel.state.collectAsStateWithLifecycle()

        val hideFeedTab by uiPreferences.hideFeedTab().collectAsStateWithLifecycle()
        val feedTabInFront by uiPreferences.feedTabInFront().collectAsStateWithLifecycle()

        val animeTabs = buildList {
            val showFeed = !hideFeedTab
            when {
                showFeed && feedTabInFront -> {
                    add(animeFeedTab())
                    add(animeSourcesTab())
                }
                showFeed -> {
                    add(animeSourcesTab())
                    add(animeFeedTab())
                }
                else -> {
                    add(animeSourcesTab())
                }
            }
            add(animeExtensionsTab(animeExtensionsScreenModel))
            add(migrateAnimeSourceTab())
        }.toPersistentList()

        val mangaTabs = buildList {
            val showFeed = !hideFeedTab
            when {
                showFeed && feedTabInFront -> {
                    add(mangaFeedTab())
                    add(mangaSourcesTab())
                }
                showFeed -> {
                    add(mangaSourcesTab())
                    add(mangaFeedTab())
                }
                else -> {
                    add(mangaSourcesTab())
                }
            }
            add(mangaExtensionsTab(mangaExtensionsScreenModel))
            add(migrateMangaSourceTab())
        }.toPersistentList()

        val novelTabs = buildList {
            val showFeed = !hideFeedTab
            when {
                showFeed && feedTabInFront -> {
                    add(novelFeedTab())
                    add(novelSourcesTab())
                }
                showFeed -> {
                    add(novelSourcesTab())
                    add(novelFeedTab())
                }
                else -> {
                    add(novelSourcesTab())
                }
            }
            add(novelExtensionsTab(novelExtensionsScreenModel))
            add(migrateNovelSourceTab())
        }.toPersistentList()

        val sections = remember(showAnimeSection, showMangaSection, showNovelSection) {
            buildBrowseSections(
                showAnimeSection = showAnimeSection,
                showMangaSection = showMangaSection,
                showNovelSection = showNovelSection,
            )
        }
        var currentSection by rememberSaveable { mutableStateOf(resolveInitialSection()) }
        LaunchedEffect(sections) {
            if (currentSection !in sections) {
                currentSection = sections.firstOrNull() ?: BrowseSection.Anime
            }
        }
        LaunchedEffect(currentSection) {
            userProfilePreferences.browseLastSection().set(currentSection.name)
        }
        val effectiveSection = currentSection
        val currentTabs = when (effectiveSection) {
            BrowseSection.Anime -> animeTabs
            BrowseSection.Manga -> mangaTabs
            BrowseSection.Novel -> novelTabs
        }
        val state = rememberPagerState { currentTabs.size }
        val isMangaSection = effectiveSection == BrowseSection.Manga
        val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
        val useWideBrowseTabs = auroraAdaptiveSpec.deviceClass != AuroraDeviceClass.Phone

        Column(modifier = Modifier.fillMaxSize()) {
            TabbedScreenAurora(
                titleRes = MR.strings.browse,
                tabs = currentTabs,
                state = state,
                mangaSearchQuery = if (isMangaSection) mangaExtensionsState.searchQuery else null,
                onChangeMangaSearchQuery = mangaExtensionsScreenModel::search,
                animeSearchQuery = when (effectiveSection) {
                    BrowseSection.Anime -> animeExtensionsState.searchQuery
                    BrowseSection.Novel -> novelExtensionsState.searchQuery
                    BrowseSection.Manga -> null
                },
                onChangeAnimeSearchQuery = { query ->
                    when (effectiveSection) {
                        BrowseSection.Anime -> animeExtensionsScreenModel.search(query)
                        BrowseSection.Novel -> novelExtensionsScreenModel.search(query)
                        BrowseSection.Manga -> Unit
                    }
                },
                isMangaTab = { isMangaSection },
                // Keep browse sub-tabs content-sized to avoid the full-width stretched segmented bar.
                scrollable = true,
                showTabRowBorder = false,
                applyStatusBarsPadding = true,
                highlightSearchAction = false,
                highlightedActionTitle = null,
                extraActionGapAfterTitle = null,
                extraHeaderContent = {
                    if (sections.size > 1) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                        ) {
                            Spacer(modifier = Modifier.height(6.dp))
                            AuroraTabRow(
                                tabs = sections.map { section ->
                                    when (section) {
                                        BrowseSection.Anime -> {
                                            TabContent(AYMR.strings.label_anime, content = { _, _ -> })
                                        }
                                        BrowseSection.Manga -> {
                                            TabContent(AYMR.strings.label_manga, content = { _, _ -> })
                                        }
                                        BrowseSection.Novel -> {
                                            TabContent(AYMR.strings.label_novel, content = { _, _ -> })
                                        }
                                    }
                                }.toPersistentList(),
                                selectedIndex = when (effectiveSection) {
                                    BrowseSection.Anime -> sections.indexOf(BrowseSection.Anime)
                                    BrowseSection.Manga -> sections.indexOf(BrowseSection.Manga)
                                    BrowseSection.Novel -> sections.indexOf(BrowseSection.Novel)
                                },
                                onTabSelected = { index ->
                                    currentSection = sections.getOrNull(index) ?: BrowseSection.Anime
                                },
                                scrollable = false,
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                },
            )
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
                        currentSection = BrowseSection.Manga
                        state.scrollToPage(extensionTabIndex(hideFeedTab))
                    } else if (targetIndex == TAB_ANIME_EXTENSIONS && showAnimeSection) {
                        // BFEED-23: the anime branch had no showAnimeSection guard (manga/novel
                        // do) - with the anime section disabled it switched currentSection to
                        // Anime and scrolled to a page index that no longer matched the tabs.
                        currentSection = BrowseSection.Anime
                        state.scrollToPage(extensionTabIndex(hideFeedTab))
                    } else if (targetIndex == TAB_NOVEL_EXTENSIONS && showNovelSection) {
                        currentSection = BrowseSection.Novel
                        state.scrollToPage(extensionTabIndex(hideFeedTab))
                    }
                }
        }

        LaunchedEffect(Unit) {
            (context as? MainActivity)?.ready = true
        }
    }
}
