package eu.kanade.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.more.settings.AuroraTopBarIconButton
import eu.kanade.presentation.more.settings.AuroraTopBarTitleText
import eu.kanade.presentation.theme.AuroraColors
import eu.kanade.presentation.theme.AuroraTheme
import eu.kanade.presentation.theme.aurora.adaptive.auroraCenteredMaxWidth
import eu.kanade.presentation.theme.aurora.adaptive.rememberAuroraAdaptiveSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.LocalAppHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

data class TabState(
    val tabs: ImmutableList<TabContent>,
    val selectedIndex: Int,
    val onTabSelected: (Int) -> Unit,
)
val LocalTabState = compositionLocalOf<TabState?> { null }

/**
 * Surface to propagate the host Scaffold's [PaddingValues] (most importantly its bottom inset that
 * accounts for the in-app NavigationBar height) down to every tab content rendered through
 * [TabbedScreenAurora]. The host (e.g. `HomeScreen`) intentionally extends the body under the
 * bottomBar for an edge-to-edge look, so without this hook every tab's LazyColumn would scroll
 * its last items behind the bar.
 *
 * `null` means there is no host padding (e.g. preview/test) and consumers should fall back to a
 * sensible default.
 */
val LocalHostScaffoldContentPadding = compositionLocalOf<PaddingValues?> { null }

internal fun resolveTabbedScreenAuroraContentPadding(
    hostContentPadding: PaddingValues?,
    layoutDirection: LayoutDirection,
    extraBottom: Dp = 16.dp,
): PaddingValues {
    val hostBottom = hostContentPadding?.calculateBottomPadding() ?: 0.dp
    val hostStart = hostContentPadding?.calculateStartPadding(layoutDirection) ?: 0.dp
    val hostEnd = hostContentPadding?.calculateEndPadding(layoutDirection) ?: 0.dp
    return PaddingValues(
        start = hostStart,
        end = hostEnd,
        bottom = hostBottom + extraBottom,
    )
}

@Composable
fun TabbedScreenAurora(
    titleRes: StringResource?,
    tabs: ImmutableList<TabContent>,
    modifier: Modifier = Modifier,
    state: PagerState = rememberPagerState { tabs.size },
    mangaSearchQuery: String? = null,
    onChangeMangaSearchQuery: (String?) -> Unit = {},
    scrollable: Boolean = false,
    animeSearchQuery: String? = null,
    onChangeAnimeSearchQuery: (String?) -> Unit = {},
    isMangaTab: (Int) -> Boolean = { it % 2 == 1 },
    showCompactHeader: Boolean = false,
    userName: String? = null,
    userAvatar: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onNameClick: (() -> Unit)? = null,
    applyStatusBarsPadding: Boolean = true,
    showTabs: Boolean = true,
    showTabRowBorder: Boolean = true,
    instantTabSwitching: Boolean = false,
    highlightSearchAction: Boolean = false,
    highlightedActionTitle: String? = null,
    extraSearchToActionsGap: Dp = 0.dp,
    extraActionGapAfterTitle: String? = null,
    extraHeaderContent: @Composable () -> Unit = {},
    onPagerSwipeOverride: ((forward: Boolean) -> Boolean)? = null,
) {
    val auroraAdaptiveSpec = rememberAuroraAdaptiveSpec()
    val contentMaxWidthDp = auroraAdaptiveSpec.updatesMaxWidthDp ?: auroraAdaptiveSpec.entryMaxWidthDp
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val switchThresholdPx = with(density) { 120.dp.toPx() }
    val maxBouncePx = with(density) { 16.dp.toPx() }
    var instantSelectedPage by rememberSaveable { mutableIntStateOf(0) }
    var previousInstantTabSwitching by remember { mutableStateOf(instantTabSwitching) }
    val currentPage = if (tabs.isEmpty()) {
        0
    } else if (instantTabSwitching) {
        instantSelectedPage.coerceIn(0, tabs.lastIndex)
    } else {
        state.currentPage.coerceIn(0, tabs.lastIndex)
    }

    val isMangaPage = isMangaTab(currentPage)
    val activeSearchQuery = if (isMangaPage) mangaSearchQuery else animeSearchQuery
    val onChangeSearchQuery = if (isMangaPage) onChangeMangaSearchQuery else onChangeAnimeSearchQuery
    val isSearchActive = activeSearchQuery != null

    val colors = AuroraTheme.colors
    var edgeBounceTargetPx by remember { mutableFloatStateOf(0f) }
    val edgeBounceOffsetPx by animateFloatAsState(
        targetValue = edgeBounceTargetPx,
        label = "auroraEdgeBounce",
    )

    val onTabSelected: (Int) -> Unit = { index ->
        if (tabs.isNotEmpty()) {
            val targetIndex = index.coerceIn(0, tabs.lastIndex)
            if (instantTabSwitching) {
                instantSelectedPage = targetIndex
            } else {
                scope.launch {
                    state.animateScrollToPage(targetIndex)
                }
            }
        }
    }

    LaunchedEffect(instantTabSwitching, tabs.size) {
        if (tabs.isEmpty()) return@LaunchedEffect
        val lastIndex = tabs.lastIndex
        if (instantTabSwitching) {
            instantSelectedPage = state.currentPage.coerceIn(0, lastIndex)
        } else if (state.currentPage > lastIndex) {
            state.scrollToPage(lastIndex)
        }
    }
    LaunchedEffect(instantTabSwitching, state.currentPage, tabs.size) {
        if (!instantTabSwitching || tabs.isEmpty()) return@LaunchedEffect
        val targetIndex = state.currentPage.coerceIn(0, tabs.lastIndex)
        if (instantSelectedPage != targetIndex) {
            instantSelectedPage = targetIndex
        }
    }
    LaunchedEffect(instantTabSwitching, instantSelectedPage, state.currentPage, tabs.size) {
        if (tabs.isEmpty()) return@LaunchedEffect

        if (instantTabSwitching) {
            val targetIndex = instantSelectedPage.coerceIn(0, tabs.lastIndex)
            if (state.currentPage != targetIndex) {
                state.scrollToPage(targetIndex)
            }
        } else {
            // Sync pager only when transitioning from instant switching to pager mode.
            if (previousInstantTabSwitching) {
                val targetIndex = instantSelectedPage.coerceIn(0, tabs.lastIndex)
                if (state.currentPage != targetIndex) {
                    state.scrollToPage(targetIndex)
                }
            }
        }

        previousInstantTabSwitching = instantTabSwitching
    }

    val hostScaffoldContentPadding = LocalHostScaffoldContentPadding.current
    val tabContentPadding = resolveTabbedScreenAuroraContentPadding(
        hostContentPadding = hostScaffoldContentPadding,
        layoutDirection = LocalLayoutDirection.current,
    )

    AuroraBackground(
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (applyStatusBarsPadding) {
                Spacer(modifier = Modifier.statusBarsPadding())
                Spacer(modifier = Modifier.height(5.dp))
            }

            if (!showCompactHeader && titleRes != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                ) {
                    AuroraTabHeader(
                        title = stringResource(titleRes),
                        isSearchActive = isSearchActive,
                        searchQuery = activeSearchQuery ?: "",
                        onSearchClick = { onChangeSearchQuery("") },
                        onSearchClose = { onChangeSearchQuery(null) },
                        onSearchQueryChange = { onChangeSearchQuery(it) },
                        tabs = tabs,
                        currentPage = currentPage,
                        navigateUp = tabs.getOrNull(currentPage)?.navigateUp,
                        highlightSearchAction = highlightSearchAction,
                        highlightedActionTitle = highlightedActionTitle,
                        extraSearchToActionsGap = extraSearchToActionsGap,
                        extraActionGapAfterTitle = extraActionGapAfterTitle,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .auroraCenteredMaxWidth(contentMaxWidthDp),
            ) {
                extraHeaderContent()
            }

            // Add tabs for Browse
            if (showTabs) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .auroraCenteredMaxWidth(contentMaxWidthDp),
                ) {
                    AuroraTabRow(
                        tabs = tabs,
                        selectedIndex = currentPage,
                        onTabSelected = onTabSelected,
                        scrollable = scrollable,
                        showBorder = showTabRowBorder,
                    )
                }
            }

            if (instantTabSwitching) {
                val tabState = TabState(
                    tabs = tabs,
                    selectedIndex = currentPage,
                    onTabSelected = onTabSelected,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .auroraCenteredMaxWidth(contentMaxWidthDp)
                        .pointerInput(currentPage, tabs.size, switchThresholdPx, maxBouncePx) {
                            var totalDragPx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragPx += dragAmount
                                },
                                onDragCancel = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onDragEnd = {
                                    val decision = resolveInstantTabSwitch(
                                        currentIndex = currentPage,
                                        lastIndex = tabs.lastIndex,
                                        totalDragPx = totalDragPx,
                                        switchThresholdPx = switchThresholdPx,
                                    )

                                    if (decision.shouldBounceEdge) {
                                        val direction = if (totalDragPx > 0f) 1f else -1f
                                        scope.launch {
                                            edgeBounceTargetPx = direction * maxBouncePx
                                            delay(90)
                                            edgeBounceTargetPx = 0f
                                        }
                                    } else {
                                        edgeBounceTargetPx = 0f
                                    }

                                    if (decision.targetIndex != currentPage) {
                                        onTabSelected(decision.targetIndex)
                                    }

                                    totalDragPx = 0f
                                },
                            )
                        }
                        .offset { IntOffset(edgeBounceOffsetPx.roundToInt(), 0) },
                ) {
                    if (tabs.isNotEmpty()) {
                        val page = currentPage
                        key(page) {
                            CompositionLocalProvider(LocalTabState provides tabState) {
                                tabs[page].content(
                                    tabContentPadding,
                                    snackbarHostState,
                                )
                            }
                        }
                    }
                }
            } else {
                val pagerOverrideActive = onPagerSwipeOverride != null
                val latestPagerSwipeOverride = rememberUpdatedState(onPagerSwipeOverride)
                val pagerContainerModifier = if (pagerOverrideActive) {
                    Modifier
                        .fillMaxSize()
                        .pointerInput(switchThresholdPx, maxBouncePx) {
                            var totalDragPx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    totalDragPx += dragAmount
                                },
                                onDragCancel = {
                                    totalDragPx = 0f
                                    edgeBounceTargetPx = 0f
                                },
                                onDragEnd = {
                                    val dragMagnitude = abs(totalDragPx)
                                    if (dragMagnitude >= switchThresholdPx) {
                                        // Swipe-left (negative) means "forward" (next item).
                                        val forward = totalDragPx < 0f
                                        val consumed = latestPagerSwipeOverride.value
                                            ?.invoke(forward) == true
                                        if (!consumed) {
                                            val direction = if (totalDragPx > 0f) 1f else -1f
                                            scope.launch {
                                                edgeBounceTargetPx = direction * maxBouncePx
                                                delay(90)
                                                edgeBounceTargetPx = 0f
                                            }
                                        } else {
                                            edgeBounceTargetPx = 0f
                                        }
                                    } else {
                                        edgeBounceTargetPx = 0f
                                    }
                                    totalDragPx = 0f
                                },
                            )
                        }
                        .offset { IntOffset(edgeBounceOffsetPx.roundToInt(), 0) }
                } else {
                    Modifier.fillMaxSize()
                }
                Box(modifier = pagerContainerModifier) {
                    HorizontalPager(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        verticalAlignment = Alignment.Top,
                        userScrollEnabled = !pagerOverrideActive,
                    ) { page ->
                        val tabState = TabState(
                            tabs = tabs,
                            selectedIndex = state.currentPage,
                            onTabSelected = onTabSelected,
                        )
                        CompositionLocalProvider(LocalTabState provides tabState) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .auroraCenteredMaxWidth(contentMaxWidthDp),
                            ) {
                                tabs[page].content(
                                    tabContentPadding,
                                    snackbarHostState,
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun AuroraTabHeader(
    title: String,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchClick: () -> Unit,
    onSearchClose: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    tabs: ImmutableList<TabContent>,
    currentPage: Int,
    navigateUp: (() -> Unit)?,
    highlightSearchAction: Boolean,
    highlightedActionTitle: String?,
    extraSearchToActionsGap: Dp,
    extraActionGapAfterTitle: String?,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val currentTab = tabs.getOrNull(currentPage)
    val actions = currentTab?.actions.orEmpty()
    val iconActions = actions.filterIsInstance<AppBar.Action>()
    val overflowActions = actions.filterIsInstance<AppBar.OverflowAction>()
    var showOverflowMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (isSearchActive) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigateUp != null) {
            AuroraTopBarIconButton(
                onClick = navigateUp,
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = stringResource(MR.strings.action_bar_up_description),
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        if (isSearchActive) {
            TextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = stringResource(MR.strings.action_search),
                        color = colors.textSecondary,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.cardBackground,
                    unfocusedContainerColor = colors.cardBackground,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(22.dp),
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            appHaptics.tap()
                            onSearchClose()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = colors.textSecondary,
                        )
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                AuroraTopBarTitleText(title = title)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (currentTab?.searchEnabled == true) {
                    AuroraTopBarIconButton(
                        onClick = onSearchClick,
                        icon = Icons.Filled.Search,
                        contentDescription = stringResource(MR.strings.action_search),
                        tint = if (highlightSearchAction) colors.accent else colors.textPrimary,
                    )

                    if (extraSearchToActionsGap > 0.dp && iconActions.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(extraSearchToActionsGap))
                    }
                }

                iconActions.forEachIndexed { index, appBarAction ->
                    val buttonModifier = if (index > 0) {
                        Modifier.padding(start = 4.dp)
                    } else {
                        Modifier
                    }
                    AuroraTopBarIconButton(
                        onClick = appBarAction.onClick,
                        icon = appBarAction.icon,
                        contentDescription = appBarAction.title,
                        tint = if (appBarAction.title == highlightedActionTitle) {
                            colors.accent
                        } else {
                            colors.textPrimary
                        },
                        modifier = buttonModifier,
                        iconRotation = appBarAction.iconRotation,
                    )

                    if (
                        appBarAction.title == extraActionGapAfterTitle &&
                        index < iconActions.lastIndex
                    ) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }

                if (overflowActions.isNotEmpty()) {
                    Box {
                        AuroraTopBarIconButton(
                            onClick = { showOverflowMenu = true },
                            icon = Icons.Outlined.MoreVert,
                            contentDescription = stringResource(MR.strings.action_menu_overflow_description),
                            modifier = Modifier.padding(start = 4.dp),
                        )

                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            overflowActions.forEach { action ->
                                DropdownMenuItem(
                                    text = { Text(action.title, fontWeight = FontWeight.Normal) },
                                    onClick = {
                                        appHaptics.tap()
                                        action.onClick()
                                        showOverflowMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AuroraTabRow(
    tabs: ImmutableList<TabContent>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    scrollable: Boolean,
    showBorder: Boolean = true,
) {
    val colors = AuroraTheme.colors
    val scrollState = rememberScrollState()
    val menuBorderBrush = remember(colors) { auroraMenuRimLightBrush(colors) }
    val tabContainerColor = resolveAuroraTabContainerColor(colors)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                tabContainerColor,
                RoundedCornerShape(28.dp),
            )
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 0.75.dp,
                        brush = menuBorderBrush,
                        shape = RoundedCornerShape(28.dp),
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .then(if (scrollable) Modifier.horizontalScroll(scrollState) else Modifier),
            horizontalArrangement = if (scrollable) Arrangement.Start else Arrangement.SpaceEvenly,
        ) {
            tabs.forEachIndexed { index, tab ->
                val isSelected = index == selectedIndex
                AuroraTab(
                    text = stringResource(tab.titleRes),
                    isSelected = isSelected,
                    badgeCount = tab.badgeNumber,
                    onClick = { onTabSelected(index) },
                    fillAvailableWidth = !scrollable,
                    modifier = if (scrollable) Modifier.padding(horizontal = 4.dp) else Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun AuroraTab(
    text: String,
    isSelected: Boolean,
    badgeCount: Int?,
    onClick: () -> Unit,
    fillAvailableWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = AuroraTheme.colors
    val appHaptics = LocalAppHaptics.current
    val tabShape = RoundedCornerShape(20.dp)
    val maxTabTextWidth = if (!fillAvailableWidth) {
        (LocalConfiguration.current.screenWidthDp.dp - 72.dp).coerceAtMost(220.dp)
    } else {
        Dp.Unspecified
    }
    val selectedTabBrush = remember(colors.accent) {
        Brush.linearGradient(
            colors = listOf(
                if (colors.isDark) {
                    lerp(colors.accent, Color.White, 0.18f).copy(alpha = 0.32f)
                } else {
                    colors.accent.copy(alpha = 0.20f)
                },
                if (colors.isDark) {
                    colors.accent.copy(alpha = 0.18f)
                } else {
                    Color.White.copy(alpha = 0.85f)
                },
            ),
            start = androidx.compose.ui.geometry.Offset.Zero,
            end = androidx.compose.ui.geometry.Offset(0f, 240f),
        )
    }

    // Segmented tab style: lighter filled background for active tab (good contrast on dark mode)
    Box(
        modifier = modifier
            .clip(tabShape)
            .background(
                brush = if (isSelected) {
                    selectedTabBrush
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                },
                shape = tabShape,
            )
            .then(
                if (isSelected) {
                    Modifier.border(1.dp, resolveAuroraTabSelectionBorderColor(colors), tabShape)
                } else {
                    Modifier
                },
            )
            .clickable {
                appHaptics.tap()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = if (fillAvailableWidth) Modifier.fillMaxWidth() else Modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                modifier = if (fillAvailableWidth) {
                    Modifier.weight(1f)
                } else {
                    Modifier.widthIn(max = maxTabTextWidth)
                },
                color = if (isSelected) colors.textPrimary else colors.textSecondary,
                style = resolveAuroraTabTextStyle(
                    baseStyle = MaterialTheme.typography.bodyLarge,
                    isSelected = isSelected,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                textAlign = TextAlign.Center,
            )

            if (badgeCount != null && badgeCount > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .background(colors.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        color = colors.textOnAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

internal fun resolveAuroraTabTextStyle(
    baseStyle: TextStyle,
    isSelected: Boolean,
): TextStyle {
    return TextStyle(
        fontFamily = baseStyle.fontFamily,
        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        fontSize = 14.sp,
        hyphens = Hyphens.None,
    )
}

internal fun auroraMenuRimLightAlphaStops(): List<Pair<Float, Float>> {
    return listOf(
        0.00f to 0.24f,
        0.28f to 0.12f,
        0.62f to 0.00f,
        1.00f to 0.00f,
    )
}

internal fun auroraLightTopRimBrush(colors: AuroraColors): Brush {
    val topColor = colors.accent
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to topColor.copy(alpha = 0.30f),
            0.06f to topColor.copy(alpha = 0.15f),
            0.18f to topColor.copy(alpha = 0.05f),
            0.35f to Color.Transparent,
            1.00f to Color.Transparent,
        ),
    )
}

internal fun auroraMenuRimLightBrush(colors: AuroraColors): Brush {
    if (colors.isEInk) {
        return Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to Color(0xFFE8E8E8),
                0.28f to Color(0xFFD4D4D4),
                0.62f to Color(0xFFC0C0C0),
                1.00f to Color(0xFFB4B4B4),
            ),
        )
    }
    val stops = auroraMenuRimLightAlphaStops()
        .map { (stop, alpha) ->
            stop to if (colors.isDark) {
                Color.White.copy(alpha = alpha)
            } else {
                colors.accent.copy(alpha = alpha * 0.5f)
            }
        }
        .toTypedArray()
    return Brush.verticalGradient(colorStops = stops)
}

internal fun resolveAuroraTabContainerColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFFF6F6F6)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        colors.accent.copy(alpha = 0.05f).compositeOver(Color(0xFFF0F4F8))
    }
}

internal fun resolveAuroraTabSelectionBorderColor(colors: AuroraColors): Color {
    if (colors.isEInk) {
        return Color(0xFF9A9A9A)
    }
    return if (colors.isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        colors.accent.copy(alpha = 0.28f)
    }
}
