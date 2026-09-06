package eu.kanade.tachiyomi.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.domain.ui.model.BottomNavAppearance
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.presentation.components.LocalHostScaffoldContentPadding
import eu.kanade.presentation.components.auroraCelestialBar
import eu.kanade.presentation.components.auroraCelestialHalo
import eu.kanade.presentation.components.auroraCelestialRail
import eu.kanade.presentation.components.auroraMenuRimLightBrush
import eu.kanade.presentation.components.latticeCircuitBar
import eu.kanade.presentation.components.latticeCircuitRail
import eu.kanade.presentation.components.rememberAuroraCelestialNavbarUnlocked
import eu.kanade.presentation.components.rememberLatticeCircuitNavbarUnlocked
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.LocalIsEInkMode
import eu.kanade.presentation.tutorial.coachAnchorForTab
import eu.kanade.presentation.util.BottomNavVisibilityController
import eu.kanade.presentation.util.LocalBottomNavVisibilityController
import eu.kanade.presentation.util.ResolvedNavigationTransitionMode
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.isTabletUi
import eu.kanade.presentation.util.resolveNavigationTransitionMode
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.download.DownloadsTab
import eu.kanade.tachiyomi.ui.entries.anime.AnimeScreen
import eu.kanade.tachiyomi.ui.entries.manga.MangaScreen
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.library.anime.AnimeLibraryTab
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryTab
import eu.kanade.tachiyomi.ui.more.MoreTab
import eu.kanade.tachiyomi.ui.updates.UpdatesTab
import eu.kanade.tachiyomi.util.system.animatorDurationScale
import eu.kanade.tachiyomi.util.system.powerManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import soup.compose.material.motion.animation.materialFadeThroughIn
import soup.compose.material.motion.animation.materialFadeThroughOut
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.NavigationBar
import tachiyomi.presentation.core.components.material.NavigationRail
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.pluralStringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

/**
 * Shared [HazeState] for the home scaffold content source.
 * Same state powers the Aurora bottom nav glass — overlays (e.g. nickname editor)
 * should reuse it so blur samples the real tab surface.
 */
val LocalHomeHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Slot for overlays that must draw above the whole home scaffold but OUTSIDE the
 * [LocalHomeHazeState] haze source. Content hosted here (e.g. the nickname editor)
 * can use hazeEffect surfaces that sample and blur the real home content behind them
 * — blurring only what sits under the surface instead of the entire screen.
 */
val LocalHomeOverlayHost =
    staticCompositionLocalOf<MutableState<(@Composable () -> Unit)?>?> { null }

object HomeScreen : Screen() {
    private val librarySearchEvent = Channel<String>()
    private val openTabEvent = Channel<Tab>()
    private val showBottomNavEvent = Channel<Boolean>()

    private const val TAB_FADE_DURATION = 200
    private const val TAB_MODERN_ENTER_DURATION = 300
    private const val TAB_MODERN_EXIT_DURATION = 300
    private val AURORA_EASING = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    private const val TAB_NAVIGATOR_KEY = "HomeTabs"

    private val uiPreferences: UiPreferences by injectLazy()
    private val startScreen = uiPreferences.startScreen().get()
    private val defaultTab = startScreen.tab

    @Composable
    override fun Content() {
        val context = LocalContext.current

        val navStyle by uiPreferences.navStyle().collectAsState()
        val bottomNavAppearance by uiPreferences.bottomNavAppearance().collectAsState()
        val isEInkMode = LocalIsEInkMode.current

        val selectedTransitionMode by uiPreferences.navigationTransitionMode().collectAsState()
        val resolvedTransitionMode by remember(
            selectedTransitionMode,
            context.animatorDurationScale,
            context.powerManager.isPowerSaveMode,
            isEInkMode,
        ) {
            derivedStateOf {
                resolveNavigationTransitionMode(
                    selectedMode = selectedTransitionMode,
                    animatorDurationScale = context.animatorDurationScale,
                    isPowerSaveMode = context.powerManager.isPowerSaveMode,
                    isEInkMode = isEInkMode,
                )
            }
        }

        val currentMoreTab = navStyle.moreTab
        val theme by uiPreferences.appTheme().collectAsState()
        val isAuroraTheme = theme.isAuroraStyle
        val useNavigationRail = isTabletUi()
        val useAuroraBottomNav = bottomNavAppearance == BottomNavAppearance.Aurora

        val navigator = LocalNavigator.currentOrThrow
        val bottomNavVisibilityController = remember { BottomNavVisibilityController() }
        val hazeState = remember { HazeState() }
        val homeOverlayContent = remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
        eu.kanade.presentation.tutorial.TutorialHost {
            TabNavigator(
                tab = defaultTab,
                key = TAB_NAVIGATOR_KEY,
            ) { tabNavigator ->
                val coachMarkState = eu.kanade.presentation.tutorial.LocalCoachMarkState.current
                LaunchedEffect(useNavigationRail) {
                    if (useNavigationRail) {
                        coachMarkState.isBottomBarVisible = false
                    }
                }
                LaunchedEffect(coachMarkState.activeTip) {
                    val activeTip = coachMarkState.activeTip ?: return@LaunchedEffect
                    val targetTabName = when (activeTip.anchor) {
                        eu.kanade.presentation.tutorial.TipAnchor.LIBRARY_TAB -> "Library"
                        eu.kanade.presentation.tutorial.TipAnchor.BROWSE_TAB -> "Browse"
                        eu.kanade.presentation.tutorial.TipAnchor.UPDATES_TAB -> "Updates"
                        eu.kanade.presentation.tutorial.TipAnchor.MORE_TAB -> "More"
                        eu.kanade.presentation.tutorial.TipAnchor.ADD_REPO_BUTTON -> "Browse"
                        else -> null
                    }
                    if (targetTabName != null) {
                        val matchingTab = navStyle.tabs.firstOrNull { tab ->
                            tab::class.simpleName.orEmpty().contains(targetTabName, ignoreCase = true)
                        } ?: if (targetTabName == "More") navStyle.moreTab else null

                        if (matchingTab != null && tabNavigator.current != matchingTab) {
                            tabNavigator.current = matchingTab
                        }
                        if (activeTip.anchor == eu.kanade.presentation.tutorial.TipAnchor.ADD_REPO_BUTTON) {
                            val uiPrefs = uy.kohesive.injekt.Injekt.get<eu.kanade.domain.ui.UiPreferences>()
                            if (uiPrefs.showAnimeSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showAnimeExtension()
                            } else if (uiPrefs.showMangaSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showExtension()
                            } else if (uiPrefs.showNovelSection().get()) {
                                eu.kanade.tachiyomi.ui.browse.BrowseTab.showNovelExtension()
                            }
                        }
                    }
                }

                // Provide usable navigator to content screen + shared haze for glass overlays
                CompositionLocalProvider(
                    LocalNavigator provides navigator,
                    LocalBottomNavVisibilityController provides bottomNavVisibilityController,
                    LocalHomeHazeState provides hazeState,
                    LocalHomeOverlayHost provides homeOverlayContent,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Scaffold(
                            startBar = {
                                if (useNavigationRail) {
                                    val circuitRail = !isEInkMode && rememberLatticeCircuitNavbarUnlocked()
                                    if (isAuroraTheme && useAuroraBottomNav && !isEInkMode) {
                                        AuroraNavigationRail(
                                            tabs = navStyle.tabs,
                                            hazeState = hazeState,
                                            circuitDecoration = circuitRail,
                                        )
                                    } else {
                                        val railModifier = if (circuitRail) {
                                            Modifier.latticeCircuitRail()
                                        } else {
                                            Modifier
                                        }
                                        NavigationRail(modifier = railModifier) {
                                            navStyle.tabs.fastForEach {
                                                NavigationRailItem(it)
                                            }
                                        }
                                    }
                                }
                            },
                            bottomBar = {
                                if (!useNavigationRail) {
                                    val bottomNavVisible by produceState(initialValue = true) {
                                        showBottomNavEvent.receiveAsFlow().collectLatest { value = it }
                                    }
                                    val showBottomNav = bottomNavVisible &&
                                        bottomNavVisibilityController.isVisible &&
                                        tabNavigator.current != currentMoreTab
                                    LaunchedEffect(showBottomNav) {
                                        coachMarkState.isBottomBarVisible = showBottomNav
                                    }
                                    val auroraColors = if (useAuroraBottomNav) {
                                        AuroraTheme.colorsForCurrentTheme()
                                    } else {
                                        null
                                    }
                                    val navBarShape = if (useAuroraBottomNav) {
                                        CircleShape
                                    } else {
                                        RoundedCornerShape(
                                            topStart = 20.dp,
                                            topEnd = 20.dp,
                                        )
                                    }
                                    val navContainerColor = if (useAuroraBottomNav) {
                                        Color.Transparent
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    }
                                    val navShadowElevation = 0.dp
                                    val navTonalElevation = 0.dp
                                    val navModifier = if (useAuroraBottomNav) {
                                        Modifier
                                            .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .auroraGlassNav(auroraColors!!, navBarShape, hazeState)
                                    } else {
                                        Modifier
                                    }
                                    val celestialUnlocked = rememberAuroraCelestialNavbarUnlocked()
                                    val celestialNavbar = useAuroraBottomNav && !isEInkMode && celestialUnlocked
                                    val celestialTabNavigator = if (celestialNavbar) LocalTabNavigator.current else null
                                    val celestialSelectedIndex = celestialTabNavigator?.let { tn ->
                                        navStyle.tabs.indexOfFirst { it::class == tn.current::class }
                                    } ?: -1
                                    val auroraSelectedIndex = navStyle.tabs.indexOfFirst {
                                        it::class ==
                                            tabNavigator.current::class
                                    }
                                    val slidingPillIndex = remember { Animatable(auroraSelectedIndex.toFloat()) }
                                    LaunchedEffect(auroraSelectedIndex) {
                                        slidingPillIndex.animateTo(
                                            targetValue = auroraSelectedIndex.toFloat(),
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                        )
                                    }
                                    val circuitNavbar = !isEInkMode && rememberLatticeCircuitNavbarUnlocked()
                                    val decoratedNavModifierBase = if (celestialNavbar && auroraColors != null) {
                                        navModifier.auroraCelestialBar(
                                            accent = auroraColors.accent,
                                            accentVariant = auroraColors.accentVariant,
                                            isDark = auroraColors.isDark,
                                            selectedIndex = celestialSelectedIndex,
                                            tabCount = navStyle.tabs.size,
                                        )
                                    } else {
                                        navModifier
                                    }
                                    val decoratedNavModifier = if (circuitNavbar) {
                                        decoratedNavModifierBase.latticeCircuitBar()
                                    } else {
                                        decoratedNavModifierBase
                                    }

                                    if (isEInkMode) {
                                        if (showBottomNav) {
                                            NavigationBar(
                                                containerColor = navContainerColor,
                                                contentColor = if (useAuroraBottomNav) {
                                                    auroraColors!!.textPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.contentColorFor(navContainerColor)
                                                },
                                                shadowElevation = navShadowElevation,
                                                tonalElevation = navTonalElevation,
                                                windowInsets = if (useAuroraBottomNav) {
                                                    WindowInsets(
                                                        0,
                                                    )
                                                } else {
                                                    NavigationBarDefaults.windowInsets
                                                },
                                                modifier = decoratedNavModifier,
                                                shape = navBarShape,
                                                contentPadding = if (useAuroraBottomNav) {
                                                    PaddingValues(horizontal = 8.dp)
                                                } else {
                                                    PaddingValues(0.dp)
                                                },
                                                height = if (useAuroraBottomNav) 64.dp else 80.dp,
                                            ) {
                                                navStyle.tabs.forEachIndexed { index, tab ->
                                                    NavigationBarItem(
                                                        tab,
                                                        useAuroraBottomNav,
                                                        index,
                                                        slidingPillIndex,
                                                        celestialUnlocked,
                                                        auroraColors,
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        AnimatedVisibility(
                                            visible = showBottomNav,
                                            enter = expandVertically(expandFrom = Alignment.Bottom),
                                            exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
                                        ) {
                                            NavigationBar(
                                                containerColor = navContainerColor,
                                                contentColor = if (useAuroraBottomNav) {
                                                    auroraColors!!.textPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.contentColorFor(navContainerColor)
                                                },
                                                shadowElevation = navShadowElevation,
                                                tonalElevation = navTonalElevation,
                                                windowInsets = if (useAuroraBottomNav) {
                                                    WindowInsets(
                                                        0,
                                                    )
                                                } else {
                                                    NavigationBarDefaults.windowInsets
                                                },
                                                modifier = decoratedNavModifier,
                                                shape = navBarShape,
                                                contentPadding = if (useAuroraBottomNav) {
                                                    PaddingValues(horizontal = 8.dp)
                                                } else {
                                                    PaddingValues(0.dp)
                                                },
                                                height = if (useAuroraBottomNav) 64.dp else 80.dp,
                                            ) {
                                                navStyle.tabs.forEachIndexed { index, tab ->
                                                    NavigationBarItem(
                                                        tab,
                                                        useAuroraBottomNav,
                                                        index,
                                                        slidingPillIndex,
                                                        celestialUnlocked,
                                                        auroraColors,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            contentWindowInsets = WindowInsets(0),
                        ) { contentPadding ->
                            val layoutDirection = LocalLayoutDirection.current
                            Box(
                                modifier = Modifier
                                    .padding(
                                        top = contentPadding.calculateTopPadding(),
                                        start = contentPadding.calculateStartPadding(layoutDirection),
                                        end = contentPadding.calculateEndPadding(layoutDirection),
                                    )
                                    .consumeWindowInsets(contentPadding)
                                    .hazeSource(hazeState),
                            ) {
                                CompositionLocalProvider(
                                    LocalHostScaffoldContentPadding provides PaddingValues(
                                        bottom = contentPadding.calculateBottomPadding(),
                                    ),
                                ) {
                                    if (resolvedTransitionMode == ResolvedNavigationTransitionMode.NONE) {
                                        val currentTab = tabNavigator.current
                                        tabNavigator.saveableState(key = "currentTab", currentTab) {
                                            currentTab.Content()
                                        }
                                    } else {
                                        AnimatedContent(
                                            targetState = tabNavigator.current,
                                            transitionSpec = {
                                                when (resolvedTransitionMode) {
                                                    ResolvedNavigationTransitionMode.NONE -> {
                                                        EnterTransition.None togetherWith ExitTransition.None
                                                    }
                                                    ResolvedNavigationTransitionMode.LEGACY -> {
                                                        materialFadeThroughIn(
                                                            initialScale = 1f,
                                                            durationMillis = TAB_FADE_DURATION,
                                                        ) togetherWith
                                                            materialFadeThroughOut(durationMillis = TAB_FADE_DURATION)
                                                    }
                                                    ResolvedNavigationTransitionMode.MODERN -> {
                                                        val direction = tabDirection(
                                                            initialTab = initialState,
                                                            targetTab = targetState,
                                                            currentMoreTab = currentMoreTab,
                                                            navStyle = navStyle,
                                                        )
                                                        val enter = slideInHorizontally(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_ENTER_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                            initialOffsetX = { width -> direction * (width / 4) },
                                                        ) + fadeIn(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_ENTER_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                        )
                                                        val exit = slideOutHorizontally(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_EXIT_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                            targetOffsetX = { width -> -direction * (width / 5) },
                                                        ) + fadeOut(
                                                            animationSpec = tween(
                                                                durationMillis = TAB_MODERN_EXIT_DURATION,
                                                                easing = AURORA_EASING,
                                                            ),
                                                        )
                                                        (enter togetherWith exit).apply {
                                                            targetContentZIndex = 1f
                                                        }
                                                    }
                                                }
                                            },
                                            label = "tabContent",
                                        ) { currentTab ->
                                            tabNavigator.saveableState(key = "currentTab", currentTab) {
                                                currentTab.Content()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // Overlay slot rendered OUTSIDE the hazeSource content above:
                        // overlays hosted here (e.g. the nickname editor) can use
                        // hazeEffect to blur the real home content behind their surface.
                        homeOverlayContent.value?.invoke()
                    }
                }

                val goToStartScreen = {
                    tabNavigator.current = resolveHomeStartTab(
                        defaultTab = defaultTab,
                        currentMoreTab = currentMoreTab,
                    )
                }
                BackHandler(
                    enabled = shouldHandleBackInHome(
                        currentTab = tabNavigator.current,
                        defaultTab = defaultTab,
                        currentMoreTab = currentMoreTab,
                    ),
                    onBack = goToStartScreen,
                )

                LaunchedEffect(Unit) {
                    if (startScreen == StartScreen.NOVEL) {
                        AnimeLibraryTab.showNovelSection()
                    }
                    launch {
                        librarySearchEvent.receiveAsFlow().collectLatest {
                            goToStartScreen()
                            when {
                                defaultTab == AnimeLibraryTab && startScreen == StartScreen.NOVEL -> {
                                    AnimeLibraryTab.searchNovel(it)
                                }
                                defaultTab == AnimeLibraryTab -> {
                                    // Search the section the user is actually in (manga/anime/novel),
                                    // not unconditionally anime.
                                    AnimeLibraryTab.searchActive(it)
                                }
                                defaultTab == MangaLibraryTab -> MangaLibraryTab.search(it)
                                else -> Unit
                            }
                        }
                    }
                    launch {
                        openTabEvent.receiveAsFlow().collectLatest {
                            tabNavigator.current = when (it) {
                                is Tab.AnimeLib -> AnimeLibraryTab
                                is Tab.Library -> resolveLibraryTabForOpenTab(isAuroraTheme)
                                is Tab.NovelLib -> AnimeLibraryTab
                                is Tab.Updates -> UpdatesTab
                                is Tab.History -> HistoriesTab
                                is Tab.Browse -> {
                                    if (it.toExtensions) {
                                        // BFEED-24: manga/anime-only routing - when the
                                        // requested section is disabled the consumer dropped
                                        // the event and the shortcut silently did nothing;
                                        // novels had no route at all. Fall back across the
                                        // visible sections.
                                        val showManga = uiPreferences.showMangaSection().get()
                                        val showAnime = uiPreferences.showAnimeSection().get()
                                        val showNovel = uiPreferences.showNovelSection().get()
                                        when {
                                            !it.anime && showManga -> BrowseTab.showExtension()
                                            it.anime && showAnime -> BrowseTab.showAnimeExtension()
                                            showManga -> BrowseTab.showExtension()
                                            showAnime -> BrowseTab.showAnimeExtension()
                                            showNovel -> BrowseTab.showNovelExtension()
                                        }
                                    }
                                    BrowseTab
                                }
                                is Tab.More -> MoreTab
                                is Tab.HomeHub -> HomeHubTab
                            }
                            if (it is Tab.NovelLib) {
                                AnimeLibraryTab.showNovelSection()
                            }
                            if (it is Tab.Library && isAuroraTheme) {
                                AnimeLibraryTab.showMangaSection()
                            }

                            if (it is Tab.AnimeLib && it.animeIdToOpen != null) {
                                navigator.push(AnimeScreen(it.animeIdToOpen))
                            }
                            if (it is Tab.Library && it.mangaIdToOpen != null) {
                                navigator.push(MangaScreen(it.mangaIdToOpen))
                            }
                            if (it is Tab.NovelLib && it.novelIdToOpen != null) {
                                navigator.push(eu.kanade.tachiyomi.ui.entries.novel.NovelScreen(it.novelIdToOpen))
                            }
                            if (it is Tab.More && it.toDownloads) {
                                navigator.push(DownloadsTab)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun RowScope.NavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        useAuroraBottomNav: Boolean,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors?,
    ) {
        if (useAuroraBottomNav) {
            AuroraNavigationBarItem(tab, index, slidingPillIndex, celestialHalo, auroraColors!!)
            return
        }

        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current

        val colors = NavigationBarItemDefaults.colors()

        NavigationBarItem(
            modifier = Modifier.coachAnchorForTab(tab),
            selected = selected,
            onClick = {
                appHaptics.tap()
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab, selected) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = colors,
        )
    }

    @Composable
    private fun RowScope.AuroraNavigationBarItem(
        tab: eu.kanade.presentation.util.Tab,
        index: Int,
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        val iconColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        val iconShape = RoundedCornerShape(999.dp)
        val tabTitle = tab.options.title
        val isPressed by interactionSource.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (isPressed) 0.96f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "auroraNavItemPressScale",
        )
        val itemWidthPx = remember { mutableStateOf(0f) }

        Box(
            modifier = Modifier
                .weight(1f)
                .coachAnchorForTab(tab)
                .fillMaxHeight()
                .onSizeChanged { itemWidthPx.value = it.width.toFloat() }
                .scale(pressScale)
                .padding(horizontal = 1.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        appHaptics.tap()
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = tabTitle
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected) {
                                Modifier.auroraCelestialHalo(
                                    accent = auroraColors.accent,
                                    accentVariant = auroraColors.accentVariant,
                                    isDark = auroraColors.isDark,
                                    shape = iconShape,
                                    enabled = celestialHalo,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .auroraSlidingPill(
                            slidingPillIndex = slidingPillIndex,
                            itemIndex = index,
                            itemWidthPx = itemWidthPx,
                            accent = auroraColors.accent,
                            accentVariant = auroraColors.accentVariant,
                            isDark = auroraColors.isDark,
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        NavigationIconItem(
                            tab = tab,
                            selected = selected,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Text(
                    text = tab.options.title,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f,
                    ),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    /**
     * Единая плашка активной вкладки: рисуется в координатах иконки каждого
     * айтема и плавно «переезжает» между ними по горизонтали. Каждый айтем
     * рисует плашку только пока её центр находится в его зоне
     * [index - 0.5, index + 0.5), поэтому между соседями нет ни двойной
     * отрисовки, ни разрыва.
     */
    @Composable
    private fun Modifier.auroraSlidingPill(
        slidingPillIndex: Animatable<Float, AnimationVector1D>,
        itemIndex: Int,
        itemWidthPx: State<Float>,
        accent: Color,
        accentVariant: Color,
        isDark: Boolean,
    ): Modifier {
        val pillBrush = remember(accent, accentVariant, isDark) {
            Brush.verticalGradient(
                listOf(
                    if (isDark) accent.copy(alpha = 0.28f) else accent.copy(alpha = 0.18f),
                    if (isDark) accentVariant.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.78f),
                ),
            )
        }
        val borderColor = if (isDark) {
            Color.White.copy(alpha = 0.12f)
        } else {
            accent.copy(alpha = 0.16f)
        }
        return this.drawBehind {
            val delta = slidingPillIndex.value - itemIndex
            if (delta < -0.5f || delta >= 0.5f) return@drawBehind
            val corner = CornerRadius(size.height / 2f, size.height / 2f)
            val topLeft = Offset(delta * itemWidthPx.value, 0f)
            drawRoundRect(
                brush = pillBrush,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = borderColor,
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

    /**
     * Общее «стекло» Aurora-навигации: тень + clip + haze-blur + rim-light.
     * Одинаково для нижнего бара и бокового рейла, отличается только формой.
     */
    private fun Modifier.auroraGlassNav(
        colors: AuroraColors,
        shape: Shape,
        hazeState: HazeState,
    ): Modifier {
        val hazeStyle = HazeStyle(
            backgroundColor = colors.background,
            tint = HazeTint(colors.surface.copy(alpha = 0.65f)),
            blurRadius = 24.dp,
            noiseFactor = 0.12f,
        )
        return if (colors.isDark) {
            this
                .shadow(
                    elevation = 10.dp,
                    shape = shape,
                    ambientColor = Color.White.copy(alpha = 0.12f),
                    spotColor = Color.White.copy(alpha = 0.08f),
                )
                .shadow(
                    elevation = 3.dp,
                    shape = shape,
                    ambientColor = Color.White.copy(alpha = 0.18f),
                    spotColor = Color.White.copy(alpha = 0.12f),
                )
                .clip(shape)
                // Opaque base under the frost: on first frames after navigation
                // (e.g. popping back to home) the haze layer has not captured
                // content yet, so without this the bar flashes transparent.
                .background(colors.background, shape)
                .hazeEffect(state = hazeState, style = hazeStyle)
                .border(
                    BorderStroke(width = 1.dp, brush = auroraMenuRimLightBrush(colors)),
                    shape = shape,
                )
        } else {
            this
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                // Opaque base under the frost (see dark branch comment).
                .background(colors.background, shape)
                .hazeEffect(state = hazeState, style = hazeStyle)
                .border(
                    BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.80f),
                                Color.White.copy(alpha = 0.20f),
                            ),
                        ),
                    ),
                    shape = shape,
                )
        }
    }

    @Composable
    private fun AuroraNavigationRail(
        tabs: List<eu.kanade.presentation.util.Tab>,
        hazeState: HazeState,
        circuitDecoration: Boolean,
    ) {
        val auroraColors = AuroraTheme.colorsForCurrentTheme()
        val railShape = RoundedCornerShape(28.dp)
        val tabNavigator = LocalTabNavigator.current
        val celestialRail = rememberAuroraCelestialNavbarUnlocked()
        val selectedIndex = tabs.indexOfFirst { it::class == tabNavigator.current::class }
        val baseModifier = Modifier
            .windowInsetsPadding(NavigationRailDefaults.windowInsets)
            .padding(start = 12.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)
        val glassModifier = baseModifier.auroraGlassNav(auroraColors, railShape, hazeState)
        val celestialModifier = if (celestialRail) {
            glassModifier.auroraCelestialRail(
                accent = auroraColors.accent,
                accentVariant = auroraColors.accentVariant,
                isDark = auroraColors.isDark,
                selectedIndex = selectedIndex,
                tabCount = tabs.size,
            )
        } else {
            glassModifier
        }
        val decoratedModifier = if (circuitDecoration) {
            celestialModifier.latticeCircuitRail()
        } else {
            celestialModifier
        }

        NavigationRail(
            modifier = decoratedModifier,
            containerColor = Color.Transparent,
            contentColor = auroraColors.textPrimary,
            windowInsets = WindowInsets(0),
        ) {
            tabs.forEachIndexed { _, tab -> AuroraNavigationRailItem(tab, celestialRail, auroraColors) }
        }
    }

    @Composable
    private fun AuroraNavigationRailItem(
        tab: eu.kanade.presentation.util.Tab,
        celestialHalo: Boolean,
        auroraColors: AuroraColors,
    ) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val interactionSource = remember { MutableInteractionSource() }
        val iconColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.72f else 0.78f)
        }
        val labelColor = if (selected) {
            auroraColors.accent
        } else {
            auroraColors.textSecondary.copy(alpha = if (auroraColors.isDark) 0.82f else 0.88f)
        }
        val iconBackgroundBrush = if (selected) {
            Brush.verticalGradient(
                listOf(
                    if (auroraColors.isDark) {
                        auroraColors.accent.copy(alpha = 0.28f)
                    } else {
                        auroraColors.accent.copy(alpha = 0.18f)
                    },
                    if (auroraColors.isDark) {
                        auroraColors.accentVariant.copy(alpha = 0.18f)
                    } else {
                        Color.White.copy(alpha = 0.78f)
                    },
                ),
            )
        } else {
            null
        }
        val iconShape = RoundedCornerShape(999.dp)
        val tabTitle = tab.options.title

        Box(
            modifier = Modifier
                .coachAnchorForTab(tab)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .selectable(
                    selected = selected,
                    role = Role.Tab,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        appHaptics.tap()
                        if (!selected) {
                            tabNavigator.current = tab
                        } else {
                            scope.launch { tab.onReselect(navigator) }
                        }
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = tabTitle
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected) {
                                Modifier
                                    .auroraCelestialHalo(
                                        accent = auroraColors.accent,
                                        accentVariant = auroraColors.accentVariant,
                                        isDark = auroraColors.isDark,
                                        shape = iconShape,
                                        enabled = celestialHalo,
                                    )
                                    .background(iconBackgroundBrush!!, iconShape)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (auroraColors.isDark) {
                                                Color.White.copy(alpha = 0.12f)
                                            } else {
                                                auroraColors.accent.copy(alpha = 0.16f)
                                            },
                                        ),
                                        iconShape,
                                    )
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CompositionLocalProvider(LocalContentColor provides iconColor) {
                        NavigationIconItem(
                            tab = tab,
                            selected = selected,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }

                Text(
                    text = tab.options.title,
                    color = labelColor,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize * 0.82f,
                    ),
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    fun NavigationRailItem(tab: eu.kanade.presentation.util.Tab) {
        val tabNavigator = LocalTabNavigator.current
        val navigator = LocalNavigator.currentOrThrow
        val scope = rememberCoroutineScope()
        val selected = tabNavigator.current::class == tab::class
        val appHaptics = LocalAppHaptics.current
        val theme by uiPreferences.appTheme().collectAsState()
        val isAurora = theme.isAuroraStyle

        val colors = if (isAurora) {
            val auroraColors = AuroraTheme.colors
            androidx.compose.material3.NavigationRailItemDefaults.colors(
                selectedIconColor = auroraColors.accent,
                selectedTextColor = auroraColors.accent,
                indicatorColor = auroraColors.accent.copy(alpha = 0.1f),
                unselectedIconColor = auroraColors.textSecondary,
                unselectedTextColor = auroraColors.textSecondary,
            )
        } else {
            androidx.compose.material3.NavigationRailItemDefaults.colors()
        }

        NavigationRailItem(
            selected = selected,
            onClick = {
                appHaptics.tap()
                if (!selected) {
                    tabNavigator.current = tab
                } else {
                    scope.launch { tab.onReselect(navigator) }
                }
            },
            icon = { NavigationIconItem(tab, selected) },
            label = {
                Text(
                    text = tab.options.title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            alwaysShowLabel = true,
            colors = colors,
        )
    }

    @Composable
    private fun NavigationIconItem(
        tab: eu.kanade.presentation.util.Tab,
        selected: Boolean,
        modifier: Modifier = Modifier,
    ) {
        BadgedBox(
            badge = {
                when {
                    UpdatesTab::class.isInstance(tab) -> {
                        val count by produceState(initialValue = 0) {
                            val pref = Injekt.get<LibraryPreferences>()
                            combine(
                                pref.newAnimeUpdatesCount().changes(),
                                pref.newMangaUpdatesCount().changes(),
                                pref.newNovelUpdatesCount().changes(),
                            ) { countAnime, countManga, countNovel ->
                                countAnime + countManga + countNovel
                            }
                                .collectLatest { value = if (pref.newShowUpdatesCount().get()) it else 0 }
                        }
                        if (count > 0) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.notification_chapters_generic,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                    BrowseTab::class.isInstance(tab) -> {
                        val pref = Injekt.get<SourcePreferences>()
                        val seenCount by produceState(
                            initialValue = pref.browseExtensionUpdatesSeenCount().get(),
                        ) {
                            pref.browseExtensionUpdatesSeenCount().changes()
                                .collectLatest { value = it }
                        }
                        val count by produceState(initialValue = 0) {
                            combine(
                                pref.mangaExtensionUpdatesCount().changes(),
                                pref.animeExtensionUpdatesCount().changes(),
                                pref.novelExtensionUpdatesCount().changes(),
                            ) { mangaCount, animeCount, novelCount ->
                                ExtensionUpdateCounts.sum(mangaCount, animeCount, novelCount)
                            }
                                .collectLatest { value = it }
                        }
                        LaunchedEffect(selected, count) {
                            if (selected) {
                                pref.browseExtensionUpdatesSeenCount().set(count)
                            }
                        }
                        if (shouldShowBrowseExtensionBadge(selected, count, seenCount)) {
                            Badge {
                                val desc = pluralStringResource(
                                    MR.plurals.update_check_notification_ext_updates,
                                    count = count,
                                    count,
                                )
                                Text(
                                    text = count.toString(),
                                    modifier = Modifier.semantics { contentDescription = desc },
                                )
                            }
                        }
                    }
                }
            },
        ) {
            Icon(
                modifier = modifier,
                painter = tab.options.icon!!,
                contentDescription = tab.options.title,
                // TODO: https://issuetracker.google.com/u/0/issues/316327367
                tint = LocalContentColor.current,
            )
        }
    }

    suspend fun search(query: String) {
        librarySearchEvent.send(query)
    }

    suspend fun openTab(tab: Tab) {
        openTabEvent.send(tab)
    }

    suspend fun showBottomNav(show: Boolean) {
        showBottomNavEvent.send(show)
    }

    sealed interface Tab {
        data class AnimeLib(val animeIdToOpen: Long? = null) : Tab
        data class Library(val mangaIdToOpen: Long? = null) : Tab
        data class NovelLib(val novelIdToOpen: Long? = null) : Tab
        data object Updates : Tab
        data object History : Tab
        data class Browse(val toExtensions: Boolean = false, val anime: Boolean = false) : Tab
        data class More(val toDownloads: Boolean) : Tab
        data object HomeHub : Tab
    }
}

internal fun shouldShowBrowseExtensionBadge(
    selected: Boolean,
    currentCount: Int,
    seenCount: Int,
): Boolean {
    return !selected && currentCount > 0 && currentCount != seenCount
}

internal fun resolveHomeStartTab(
    defaultTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): cafe.adriel.voyager.navigator.tab.Tab {
    return if (defaultTab != currentMoreTab) defaultTab else AnimeLibraryTab
}

internal fun resolveLibraryTabForOpenTab(isAuroraTheme: Boolean): eu.kanade.presentation.util.Tab {
    return if (isAuroraTheme) AnimeLibraryTab else MangaLibraryTab
}

internal fun shouldHandleBackInHome(
    currentTab: cafe.adriel.voyager.navigator.tab.Tab,
    defaultTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): Boolean {
    return (currentTab == currentMoreTab || currentTab != defaultTab) &&
        (currentTab != AnimeLibraryTab || defaultTab != currentMoreTab)
}

private fun tabDirection(
    initialTab: cafe.adriel.voyager.navigator.tab.Tab,
    targetTab: cafe.adriel.voyager.navigator.tab.Tab,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
    navStyle: eu.kanade.domain.ui.model.NavStyle,
): Int {
    val initialIndex = tabOrderIndex(initialTab, navStyle, currentMoreTab)
    val targetIndex = tabOrderIndex(targetTab, navStyle, currentMoreTab)
    return if (targetIndex >= initialIndex) 1 else -1
}

private fun tabOrderIndex(
    tab: cafe.adriel.voyager.navigator.tab.Tab,
    navStyle: eu.kanade.domain.ui.model.NavStyle,
    currentMoreTab: cafe.adriel.voyager.navigator.tab.Tab,
): Int {
    val visibleIndex = navStyle.tabs.indexOfFirst { it::class == tab::class }
    return when {
        visibleIndex >= 0 -> visibleIndex
        tab::class == currentMoreTab::class -> navStyle.tabs.size
        else -> navStyle.tabs.size + 1
    }
}
